package com.personal.carnet.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 新版本信息：build 号就是 CI 的 run number，同时也是 versionCode。
 * [changes] 是当前版本之后每个 build 的更新内容，新的在前
 */
data class UpdateInfo(val build: Long, val apkUrl: String, val changes: List<String> = emptyList())

/** 从 GitHub Releases 检查并安装新版本（CI 每次推 main 会发一个 build-N 的 Release） */
class UpdateChecker(private val context: Context) {
    private val client = OkHttpClient()

    /** 不跟跳转：读 releases/latest 跳到哪个 tag */
    private val noRedirect = client.newBuilder().followRedirects(false).followSslRedirects(false).build()

    /**
     * 有更新返回 [UpdateInfo]，已是最新返回 null；网络出错会抛异常。
     * 先用 GitHub API（能拿到每个版本的更新说明）；API 不登录每个网络出口每小时只能查 60 次，
     * 手机流量很多人共用出口，常被限流（403）。这时退回读网页上 releases/latest 跳到哪个 build，没有更新说明但能更新。
     */
    suspend fun check(): UpdateInfo? = withContext(Dispatchers.IO) {
        val current = currentBuild()
        val api = runCatching {
            val request = Request.Builder()
                .url("https://api.github.com/repos/$REPO/releases?per_page=30")
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "Carnet")
                .build()
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) error("HTTP ${resp.code}")
                resp.body.string()
            }
        }
        val body = api.getOrNull()
        if (body != null) {
            pickUpdate(body, current)
        } else {
            val latest = runCatching { latestFromWeb() }.getOrNull() ?: throw api.exceptionOrNull()!!
            if (latest > current) UpdateInfo(latest, apkUrlFor(latest)) else null
        }
    }

    private fun latestFromWeb(): Long? {
        val request = Request.Builder().url("https://github.com/$REPO/releases/latest").header("User-Agent", "Carnet").build()
        return noRedirect.newCall(request).execute().use { resp -> buildFromLocation(resp.header("Location")) }
    }

    /** 下载 APK 到缓存目录；先清掉以前下的包，最多只留一个 */
    suspend fun download(info: UpdateInfo, onProgress: (Float) -> Unit = {}): File = withContext(Dispatchers.IO) {
        val dir = updatesDir().apply { deleteRecursively(); mkdirs() }
        val file = File(dir, "carnet-${info.build}.apk")
        val request = Request.Builder().url(info.apkUrl).build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) error("HTTP ${resp.code}")
            val total = resp.body.contentLength()
            resp.body.byteStream().use { input ->
                file.outputStream().use { out ->
                    val buffer = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        out.write(buffer, 0, n)
                        done += n
                        if (total > 0) onProgress(done.toFloat() / total)
                    }
                }
            }
        }
        file
    }

    /**
     * 用 PackageInstaller 装新版。Android 12+ 且安装来源是 Carnet 自己时直接装好、不弹确认；
     * 否则系统会经 [InstallResultReceiver] 弹一次确认。还没授权「安装未知应用」时先跳到授权页，返回 false
     */
    suspend fun install(file: File): Boolean {
        if (!context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            return false
        }
        withContext(Dispatchers.IO) {
            val installer = context.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                setAppPackageName(context.packageName)
                if (Build.VERSION.SDK_INT >= 31) {
                    setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
                }
            }
            val sessionId = installer.createSession(params)
            installer.openSession(sessionId).use { session ->
                file.inputStream().use { input ->
                    session.openWrite("carnet.apk", 0, file.length()).use { output ->
                        input.copyTo(output)
                        session.fsync(output)
                    }
                }
                // FLAG_MUTABLE：系统要往里面填安装结果
                val callback = PendingIntent.getBroadcast(
                    context, sessionId,
                    Intent(context, InstallResultReceiver::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
                )
                session.commit(callback.intentSender)
            }
        }
        return true
    }

    /** 删掉下载过的安装包；启动时调用，装完新版后旧包就没用了 */
    suspend fun cleanup() = withContext(Dispatchers.IO) {
        updatesDir().deleteRecursively()
    }

    fun currentBuild(): Long = context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode

    private fun updatesDir() = File(context.cacheDir, "updates")

    companion object {
        const val REPO = "zhaidy/Carnet"

        /** releases/latest 跳到 …/releases/tag/build-24 → 24 */
        fun buildFromLocation(location: String?): Long? =
            location?.let { Regex("""/releases/tag/build-(\d+)/?$""").find(it) }?.groupValues?.get(1)?.toLongOrNull()

        /** CI 发的安装包都叫 carnet.apk */
        fun apkUrlFor(build: Long) = "https://github.com/$REPO/releases/download/build-$build/carnet.apk"

        /** 从 Releases 列表里挑出比当前新的最新一个，把中间每个版本的更新说明都拼起来，跳过好几个版本也不漏 */
        fun pickUpdate(releasesJson: String, current: Long): UpdateInfo? {
            val arr = JSONArray(releasesJson)
            val releases = (0 until arr.length()).map { arr.getJSONObject(it) }
                .filter { !it.optBoolean("draft") && !it.optBoolean("prerelease") }
                .mapNotNull { r -> r.optString("tag_name").removePrefix("build-").toLongOrNull()?.let { it to r } }
                .filter { (build, _) -> build > current }
                .sortedByDescending { (build, _) -> build }
            val (build, latest) = releases.firstOrNull() ?: return null
            val apkUrl = apkUrl(latest) ?: return null
            val changes = releases.flatMap { (_, r) -> parseChanges(r.optString("body")) }.distinct()
            return UpdateInfo(build, apkUrl, changes)
        }

        private fun apkUrl(release: JSONObject): String? {
            val assets = release.optJSONArray("assets") ?: return null
            return (0 until assets.length()).map { assets.getJSONObject(it) }
                .firstOrNull { it.optString("name").endsWith(".apk") }
                ?.optString("browser_download_url")
        }

        /** Release 说明是 CI 用提交标题生成的 Markdown 列表，一行一条 */
        fun parseChanges(body: String): List<String> =
            body.lines()
                .map { it.trim().removePrefix("- ").removePrefix("* ").trim() }
                .filter { it.isNotEmpty() }
    }
}
