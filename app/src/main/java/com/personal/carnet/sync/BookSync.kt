package com.personal.carnet.sync

import com.personal.carnet.ui.Partner

import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.SetOptions
import com.personal.carnet.data.CarnetDao
import com.personal.carnet.data.Entry
import com.personal.carnet.data.Ledger
import com.personal.carnet.data.SettleMode
import com.personal.carnet.data.Settlement
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import java.security.SecureRandom
import java.util.UUID

class SyncException(message: String) : Exception(message)

/** uploading：还有多少笔没传上去（正在传的时候才有） */
data class SyncStatus(val members: Int? = null, val error: String? = null, val uploading: Int? = null)

/**
 * 共用账本和她同步。云端是 books/{邀请码}（成员、账本名、货币、各自出几成、怎么结），
 * books/{邀请码}/entries/{syncId}（一笔开销）和 books/{邀请码}/settlements/{syncId}（两清）。
 *
 * 只同步共用账本里的账：金额、分类、备注、谁付的、记进账本多少；**你的账户不上传**，
 * 对方记的账在你这边不动你的账户。「谁付的」存成相对记账人的（paidByAuthor），各自换成「我 / 她」。
 * 删掉的账在云端留一条 deleted 标记，对方那边也删掉。
 */
class BookSync(private val dao: CarnetDao, private val scope: CoroutineScope) {

    /** 占位的 google-services.json 编出来的包不能同步 */
    val available: Boolean by lazy {
        runCatching { FirebaseApp.getInstance().options.projectId }.getOrNull()?.let { it != PLACEHOLDER_PROJECT } ?: false
    }

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val firestore by lazy { FirebaseFirestore.getInstance() }

    private val _status = MutableStateFlow<Map<Long, SyncStatus>>(emptyMap())
    /** 每个在同步的账本（本机 id）现在怎样 */
    val status: StateFlow<Map<Long, SyncStatus>> = _status.asStateFlow()

    fun start() {
        if (!available) return
        scope.launch {
            dao.ledgers()
                .map { list -> list.filter { it.syncCode != null }.map { it.id to it.syncCode!! } }
                .distinctUntilChanged()
                .collectLatest { synced ->
                    coroutineScope { synced.forEach { (id, code) -> launch { syncForever(id, code) } } }
                }
        }
    }

    /** 把一个共用账本传上去，返回邀请码，发给她 */
    suspend fun invite(ledger: Ledger): String {
        ledger.syncCode?.let { return it }
        requireAvailable()
        val uid = signIn()
        val code = SyncCodes.generate(SecureRandom())
        remote { book(code).set(bookMeta(ledger, uid) + mapOf("members" to listOf(uid), "createdAt" to FieldValue.serverTimestamp())).await() }
        // 以前同步过（停止后重新邀请）的账，标记是旧邀请码下的：清掉，全部重新传到新的下面
        dao.clearEntrySyncIds(ledger.id)
        dao.clearSettlementSyncIds(ledger.id)
        dao.update(ledger.copy(syncCode = code))
        return code
    }

    /** 用她给的邀请码加入，本机新建一个对应的共用账本 */
    suspend fun join(input: String): Ledger {
        requireAvailable()
        val code = SyncCodes.normalize(input) ?: throw SyncException("邀请码是 ${SyncCodes.LENGTH} 位字母和数字")
        dao.ledgersNow().firstOrNull { it.syncCode == code }?.let { return it }
        val uid = signIn()
        val snap = remote {
            book(code).update("members", FieldValue.arrayUnion(uid)).await()
            book(code).get().await()
        }
        val ledger = ledgerFrom(snap, code, uid)
        // 第一次打开时自动建的那个空「我们俩」就直接用，不再多建一个
        val empty = dao.ledgersNow().firstOrNull { l ->
            l.shared && l.syncCode == null && dao.entriesInLedger(l.id) == 0 && dao.settlementsInNow(l.id).isEmpty()
        }
        if (empty != null) {
            val reused = ledger.copy(id = empty.id, createdAt = empty.createdAt)
            dao.update(reused)
            return reused
        }
        val id = dao.insert(ledger)
        return ledger.copy(id = id)
    }

    /** 不再同步：本机的账都留着 */
    suspend fun leave(ledger: Ledger) {
        dao.update(ledger.copy(syncCode = null))
        dao.clearEntrySyncIds(ledger.id)
        dao.clearSettlementSyncIds(ledger.id)
        _status.update { it - ledger.id }
    }

    /** 本机删掉一笔同步过的账：云端留删除标记，她那边也删 */
    fun onDeleted(entry: Entry, ledger: Ledger?) {
        val code = ledger?.syncCode ?: return
        val syncId = entry.syncId ?: return
        if (!available) return
        scope.launch {
            runCatching {
                signIn()
                book(code).collection(ENTRIES).document(syncId)
                    .set(mapOf("deleted" to true, "updatedAt" to FieldValue.serverTimestamp()), SetOptions.merge()).await()
            }
        }
    }

    /** 本机改了一笔同步过的账：整笔重新传上去，她那边跟着改 */
    fun onUpdated(entry: Entry, ledger: Ledger?) {
        val code = ledger?.syncCode ?: return
        val syncId = entry.syncId ?: return
        if (!available) return
        scope.launch {
            runCatching {
                val uid = signIn()
                book(code).collection(ENTRIES).document(syncId).set(SyncedEntry.toMap(entry, uid)).await()
            }.onFailure { Log.w(TAG, "update failed", it) }
        }
    }

    /** 本机改了、撤销了一次同步过的两清 */
    fun onSettlementChanged(s: Settlement, ledger: Ledger?, undone: Boolean) {
        val code = ledger?.syncCode ?: return
        val syncId = s.syncId ?: return
        if (!available) return
        scope.launch {
            runCatching {
                val uid = signIn()
                val doc = book(code).collection(SETTLEMENTS).document(syncId)
                if (undone) doc.set(mapOf("deleted" to true), SetOptions.merge()).await()
                else doc.set(mapOf("timeMillis" to s.timeMillis, "cents" to s.cents, "author" to uid, "deleted" to false)).await()
            }.onFailure { Log.w(TAG, "settlement update failed", it) }
        }
    }

    private suspend fun syncForever(ledgerId: Long, code: String) {
        while (true) {
            try {
                val uid = signIn()
                sync(ledgerId, book(code), uid)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "sync failed", e)
                setStatus(ledgerId) { it.copy(error = (e as? SyncException)?.message ?: e.message ?: e.javaClass.simpleName) }
            }
            delay(RETRY_MS)
        }
    }

    /** 对方自己设的称呼收到了 */
    var onPartnerName: ((String) -> Unit)? = null

    /** 自己的称呼改了：写到每个在同步的账本下面，对方那边跟着变 */
    fun pushMyName(name: String) {
        if (!available || name.isBlank()) return
        scope.launch {
            runCatching {
                val uid = signIn()
                for (l in dao.ledgersNow().filter { it.syncCode != null }) {
                    book(l.syncCode!!).collection(MEMBERS).document(uid).set(mapOf("name" to name)).await()
                }
            }.onFailure { Log.w(TAG, "name push failed", it) }
        }
    }

    private suspend fun sync(ledgerId: Long, book: DocumentReference, uid: String) = coroutineScope {
        // 称呼：每个人写自己的（books/{code}/members/{uid}），读对方的
        launch {
            Partner.mine.takeIf { it.isNotBlank() }?.let { name ->
                remote { book.collection(MEMBERS).document(uid).set(mapOf("name" to name)).await() }
            }
        }
        launch {
            querySnapshots(book.collection(MEMBERS)).collect { snap ->
                snap.documents.firstOrNull { it.id != uid }?.getString("name")?.takeIf { it.isNotBlank() }?.let { onPartnerName?.invoke(it) }
            }
        }
        launch {
            snapshots(book).collect { snap ->
                setStatus(ledgerId) { SyncStatus(members = (snap.get("members") as? List<*>)?.size, error = null) }
            }
        }
        // 云端 → 本机
        launch {
            querySnapshots(book.collection(ENTRIES)).collect { snap ->
                for (change in snap.documentChanges) {
                    if (change.type == DocumentChange.Type.REMOVED) continue
                    applyRemoteEntry(ledgerId, change.document, uid)
                }
            }
        }
        launch {
            querySnapshots(book.collection(SETTLEMENTS)).collect { snap ->
                for (change in snap.documentChanges) {
                    if (change.type == DocumentChange.Type.REMOVED) continue
                    applyRemoteSettlement(ledgerId, change.document, uid)
                }
            }
        }
        // 本机 → 云端：还没有 syncId 的就是新记的。新的先传，一批 400 笔
        launch {
            dao.entriesIn(ledgerId).collect { entries ->
                val pending = entries.filter { it.syncId == null }.sortedByDescending { it.timeMillis }
                var left = pending.size
                for (chunk in pending.chunked(BATCH)) {
                    setStatus(ledgerId) { it.copy(uploading = left) }
                    // 先在本机记上 syncId，云端的回音回来时才认得是自己这笔，不会再插一遍
                    val withIds = chunk.map { it.copy(syncId = UUID.randomUUID().toString()) }
                    dao.updateEntries(withIds)
                    val batch = firestore.batch()
                    withIds.forEach { batch.set(book.collection(ENTRIES).document(it.syncId!!), SyncedEntry.toMap(it, uid)) }
                    try {
                        withTimeout(UPLOAD_TIMEOUT_MS) { batch.commit().await() }
                    } catch (e: TimeoutCancellationException) {
                        // 没网时这批留在 Firestore 本地的队列里，联网后它自己会传上去
                    } catch (e: FirebaseFirestoreException) {
                        dao.updateEntries(chunk)
                        throw SyncException("上传失败：${e.message}")
                    }
                    left -= chunk.size
                }
                if (pending.isNotEmpty()) setStatus(ledgerId) { it.copy(uploading = null) }
            }
        }
        launch {
            dao.settlementsIn(ledgerId).collect { list ->
                for (s in list.filter { it.syncId == null }) {
                    val syncId = UUID.randomUUID().toString()
                    dao.update(s.copy(syncId = syncId))
                    remote {
                        book.collection(SETTLEMENTS).document(syncId)
                            .set(mapOf("timeMillis" to s.timeMillis, "cents" to s.cents, "author" to uid)).await()
                    }
                }
            }
        }
    }

    private suspend fun applyRemoteEntry(ledgerId: Long, doc: DocumentSnapshot, uid: String) {
        val data = doc.data ?: return
        val existing = dao.entryBySyncId(doc.id)
        if (data["deleted"] == true) {
            if (existing != null) dao.deleteEntryBySyncId(doc.id)
            return
        }
        val remote = SyncedEntry.fromMap(doc.id, data, ledgerId, uid) ?: return
        when {
            existing == null -> dao.insert(remote)
            // 自己记的那笔本机最全（带账户），不用云端的覆盖
            data["author"] == uid -> Unit
            else -> {
                val currency = existing.accountId?.let { id -> dao.accountsNow().firstOrNull { it.id == id }?.currency }
                dao.update(SyncedEntry.merge(existing, remote, currency))
            }
        }
    }

    private suspend fun applyRemoteSettlement(ledgerId: Long, doc: DocumentSnapshot, uid: String) {
        val existing = dao.settlementBySyncId(doc.id)
        if (doc.get("deleted") == true) {
            existing?.let { dao.deleteSettlement(it.id) }
            return
        }
        val time = (doc.get("timeMillis") as? Number)?.toLong() ?: return
        val cents = (doc.get("cents") as? Number)?.toLong() ?: 0
        val mine = doc.get("author") == uid
        val remote = Settlement(ledgerId = ledgerId, timeMillis = time, cents = if (mine) cents else -cents, syncId = doc.id)
        when {
            existing == null -> dao.insert(remote)
            existing.timeMillis != remote.timeMillis || existing.cents != remote.cents -> dao.update(remote.copy(id = existing.id))
        }
    }

    private fun bookMeta(ledger: Ledger, uid: String) = mapOf(
        "name" to ledger.name,
        "currency" to (ledger.currency?.code ?: "SGD"),
        "percent" to mapOf(uid to ledger.myPercent),
        "settleMode" to ledger.settleMode.name,
        "settleDay" to ledger.settleDay,
        "settleEpochDay" to ledger.settleEpochDay,
    )

    private fun ledgerFrom(snap: DocumentSnapshot, code: String, uid: String): Ledger {
        if (!snap.exists()) throw SyncException("邀请码不对")
        val percent = (snap.get("percent") as? Map<*, *>).orEmpty()
        val mine = (percent[uid] as? Number)?.toInt()
            ?: percent.values.firstOrNull()?.let { 100 - (it as Number).toInt() }
            ?: 50
        return Ledger(
            name = snap.getString("name") ?: "我们俩",
            currency = com.personal.carnet.domain.Currency.of(snap.getString("currency")) ?: com.personal.carnet.domain.Currency.SGD,
            shared = true,
            myPercent = mine,
            settleMode = runCatching { SettleMode.valueOf(snap.getString("settleMode")!!) }.getOrDefault(SettleMode.MONTHLY),
            settleDay = (snap.get("settleDay") as? Number)?.toInt() ?: 31,
            settleEpochDay = (snap.get("settleEpochDay") as? Number)?.toLong(),
            syncCode = code,
        )
    }

    private fun book(code: String) = firestore.collection(BOOKS).document(code)

    private fun requireAvailable() {
        if (!available) throw SyncException("这个版本还没接上 Firebase，暂时不能和${Partner.name}同步")
    }

    private suspend fun signIn(): String = remote {
        auth.currentUser?.uid ?: requireNotNull(auth.signInAnonymously().await().user).uid
    }

    private fun setStatus(ledgerId: Long, change: (SyncStatus) -> SyncStatus) =
        _status.update { it + (ledgerId to change(it[ledgerId] ?: SyncStatus())) }

    /** 联网操作：超时或出错都转成能直接给人看的提示 */
    private suspend fun <T> remote(block: suspend () -> T): T = try {
        withTimeout(TIMEOUT_MS) { block() }
    } catch (e: TimeoutCancellationException) {
        throw SyncException("连不上服务器，检查一下网络")
    } catch (e: FirebaseFirestoreException) {
        throw SyncException(
            when (e.code) {
                FirebaseFirestoreException.Code.NOT_FOUND -> "邀请码不对"
                FirebaseFirestoreException.Code.PERMISSION_DENIED -> "没有权限：邀请码不对，或者 Firestore 规则还没贴上"
                FirebaseFirestoreException.Code.UNAVAILABLE -> "连不上服务器，检查一下网络"
                else -> "出错了：${e.message}"
            },
        )
    } catch (e: FirebaseAuthException) {
        throw SyncException("登录失败：${e.message}")
    }

    private fun snapshots(ref: DocumentReference): Flow<DocumentSnapshot> = callbackFlow {
        val reg = ref.addSnapshotListener { snap, e ->
            if (e != null) close(e) else if (snap != null) trySend(snap)
        }
        awaitClose { reg.remove() }
    }

    private fun querySnapshots(ref: CollectionReference): Flow<QuerySnapshot> = callbackFlow {
        val reg = ref.addSnapshotListener { snap, e ->
            if (e != null) close(e) else if (snap != null) trySend(snap)
        }
        awaitClose { reg.remove() }
    }

    private companion object {
        const val TAG = "BookSync"
        const val PLACEHOLDER_PROJECT = "carnet-local-placeholder"
        const val BOOKS = "books"
        const val ENTRIES = "entries"
        const val SETTLEMENTS = "settlements"
        const val MEMBERS = "members"
        const val TIMEOUT_MS = 20_000L
        const val UPLOAD_TIMEOUT_MS = 60_000L
        /** Firestore 一批最多 500 个写入 */
        const val BATCH = 400
        const val RETRY_MS = 30_000L
    }
}
