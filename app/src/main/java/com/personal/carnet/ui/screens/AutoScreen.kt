package com.personal.carnet.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.personal.carnet.ai.AiException
import com.personal.carnet.ai.ClaudeClient
import com.personal.carnet.domain.Categories
import com.personal.carnet.domain.Money
import com.personal.carnet.domain.Snapshot
import com.personal.carnet.ui.MainViewModel
import com.personal.carnet.ui.components.ChipRow
import com.personal.carnet.ui.components.CtaButton
import com.personal.carnet.ui.components.GlassCard
import com.personal.carnet.ui.components.GlassField
import com.personal.carnet.ui.components.GlassSwitch
import com.personal.carnet.ui.components.Label
import com.personal.carnet.ui.components.LocalToast
import com.personal.carnet.ui.components.ScreenHeader
import com.personal.carnet.ui.components.Strong
import com.personal.carnet.ui.glass.Palette
import kotlinx.coroutines.launch

@Composable
fun AutoScreen(snapshot: Snapshot, vm: MainViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val toast = LocalToast.current
    val scope = rememberCoroutineScope()
    val config by vm.autoConfig.collectAsState()
    val accounts = snapshot.accounts.map { it.account }
    val ledgers = snapshot.ledgers.map { it.ledger }

    var granted by remember { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) {
        granted = context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context)
        onPauseOrDispose {}
    }

    var key by rememberSaveable { mutableStateOf(config.claudeKey) }
    var model by rememberSaveable { mutableStateOf(config.claudeModel) }
    var testTitle by rememberSaveable { mutableStateOf("") }
    var testText by rememberSaveable { mutableStateOf("") }
    var testResult by rememberSaveable { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { ScreenHeader("自动记账和 Claude", onBack = onBack) }

        item {
            GlassCard(padding = 14.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Strong("Google 钱包付款自动记账")
                    Label(
                        "刷卡后 Google 钱包会发一条通知，Carnet 读到就记一笔，再发通知让你「算我们俩的」或「撤销」。只读 Google 钱包的通知，别的应用的不看也不存。",
                        size = 12.sp, weight = FontWeight.Medium,
                    )
                    if (!granted) {
                        Label("还没给 Carnet「通知使用权」", color = Palette.Rose, weight = FontWeight.SemiBold)
                        CtaButton("去打开通知使用权") {
                            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Strong(if (config.walletEnabled) "开着" else "关着", size = 14.sp)
                                Label("通知使用权已经给了", size = 12.sp, weight = FontWeight.Medium)
                            }
                            GlassSwitch(config.walletEnabled, "自动记账") { vm.setWalletEnabled(it) }
                        }
                    }
                }
            }
        }

        item {
            GlassCard(padding = 14.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ChipRow(ledgers.map { it.id to it.name }, config.ledgerId ?: ledgers.firstOrNull { !it.shared }?.id, "记到") { vm.setAutoLedger(it) }
                    if (accounts.isNotEmpty()) {
                        ChipRow(accounts.map { it.id to it.name }, config.defaultAccountId, "认不出卡时") { vm.setDefaultAccount(it) }
                    }
                    val cards = (config.cards.keys + config.unknownCards).sorted()
                    if (cards.isEmpty()) {
                        Label("刷过一次卡后，这里会列出通知里的卡号后四位，选一下各是哪张卡。", size = 12.sp, weight = FontWeight.Medium)
                    }
                    cards.forEach { last4 ->
                        ChipRow(accounts.map { it.id to it.name }, config.cards[last4], "··$last4") { vm.setCard(last4, it) }
                    }
                }
            }
        }

        item {
            GlassCard(padding = 14.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Strong("试一下")
                    Label("还不知道 Google 钱包通知的确切样子。把一条通知的标题和内容贴进来，看能不能认对；认不对就截图发给我调。", size = 12.sp, weight = FontWeight.Medium)
                    GlassField(testTitle, { testTitle = it }, "通知标题，比如 Toast Box", Modifier.fillMaxWidth())
                    GlassField(testText, { testText = it }, "通知内容，比如 S$5.60 with Visa ••4821", Modifier.fillMaxWidth(), singleLine = false)
                    CtaButton(if (busy) "正在认…" else "认一下") {
                        if (busy) return@CtaButton
                        busy = true
                        scope.launch {
                            val r = vm.tryNotification(testTitle, testText)
                            testResult = r.payment?.let { p ->
                                (if (p.refund) "退款 " else "") + Money.format(p.cents, p.currency) + " · " + p.merchant +
                                    " · 卡 " + (p.last4?.let { "··$it" } ?: "认不出") + " · 记到 " + (r.accountName ?: "?") +
                                    " · " + Categories.of(r.category ?: "more").label
                            } ?: (r.problem ?: "认不出")
                            busy = false
                        }
                    }
                    testResult?.let { Strong(it, size = 14.sp, color = Palette.Violet) }
                }
            }
        }

        item {
            GlassCard(padding = 14.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Strong("Claude API")
                    Label("用来：认不出的通知、猜商户是什么分类、记一笔里的「一句话记账」。Key 只存在这台手机上。", size = 12.sp, weight = FontWeight.Medium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Label("Key", modifier = Modifier.width(48.dp))
                        GlassField(key, { key = it.trim() }, "sk-ant-…", Modifier.weight(1f), secret = true)
                    }
                    ChipRow(ClaudeClient.MODELS, model, "模型") { model = it }
                    CtaButton(if (busy) "正在试…" else "保存并试一下") {
                        if (busy) return@CtaButton
                        vm.setClaude(key, model)
                        if (key.isBlank()) { toast.show("已清掉 Claude API Key"); return@CtaButton }
                        busy = true
                        scope.launch {
                            try {
                                vm.pingClaude()
                                toast.show("Claude 能用了")
                            } catch (e: AiException) {
                                toast.show(e.message ?: "Claude 出错了")
                            } finally {
                                busy = false
                            }
                        }
                    }
                }
            }
        }
    }
}
