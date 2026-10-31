package com.personal.carnet.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.personal.carnet.ui.components.LocalToast
import com.personal.carnet.ui.components.ToastHost
import com.personal.carnet.ui.components.ToastState
import com.personal.carnet.ui.glass.LiquidBackground
import com.personal.carnet.ui.glass.Palette
import com.personal.carnet.ui.glass.glass
import com.personal.carnet.ui.glass.glassControl
import com.personal.carnet.ui.glass.jelly
import com.personal.carnet.ui.glass.tinted
import com.personal.carnet.ui.screens.AccountEditScreen
import com.personal.carnet.ui.screens.AccountsScreen
import com.personal.carnet.ui.screens.AddScreen
import com.personal.carnet.ui.screens.HomeScreen
import com.personal.carnet.ui.screens.LedgerEditScreen
import com.personal.carnet.ui.screens.LedgersScreen
import com.personal.carnet.ui.screens.MeScreen
import com.personal.carnet.ui.screens.ImportScreen
import com.personal.carnet.ui.screens.AutoScreen
import com.personal.carnet.ui.screens.RecurringEditScreen
import com.personal.carnet.ui.screens.RecurringScreen
import androidx.lifecycle.compose.LifecycleResumeEffect

private object Routes {
    const val HOME = "home"
    const val LEDGERS = "ledgers"
    const val ACCOUNTS = "accounts"
    const val ADD = "add"
    const val NEW_ACCOUNT = "account/new"
    const val NEW_LEDGER = "ledger/new"
    const val ME = "me"
    const val RECURRING = "recurring"
    const val NEW_RULE = "recurring/new"
    const val IMPORT = "import"
    const val AUTO = "auto"
}

private data class Tab(val route: String, val label: String, val icon: String)

private val tabs = listOf(
    Tab(Routes.HOME, "总览", IconPaths.HOME),
    Tab(Routes.LEDGERS, "账本", IconPaths.BOOK),
    Tab(Routes.ACCOUNTS, "账户", IconPaths.CARD),
    Tab(Routes.ME, "我的", IconPaths.PERSON),
)

@Composable
fun CarnetRoot(vm: MainViewModel) {
    val nav = rememberNavController()
    val toast = remember { ToastState() }
    val snapshot by vm.snapshot.collectAsState()
    val update by vm.update.collectAsState()
    val autoConfig by vm.autoConfig.collectAsState()
    // 回到前台时把到日子的固定收支记上
    LifecycleResumeEffect(Unit) {
        vm.onResume()
        onPauseOrDispose {}
    }
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route

    CompositionLocalProvider(LocalToast provides toast) {
        Box(Modifier.fillMaxSize()) {
            LiquidBackground()
            val snap = snapshot
            if (snap != null) {
                NavHost(nav, startDestination = Routes.HOME, modifier = Modifier.fillMaxSize()) {
                    composable(Routes.HOME) {
                        HomeScreen(
                            snap,
                            update = update,
                            onOpenUpdate = { nav.switchTab(Routes.ME) },
                            onNewAccount = { nav.navigate(Routes.NEW_ACCOUNT) },
                            onOpenAccounts = { nav.switchTab(Routes.ACCOUNTS) },
                            onOpenLedgers = { nav.switchTab(Routes.LEDGERS) },
                            onOpenRecurring = { nav.navigate(Routes.RECURRING) },
                            onConfirm = vm::confirm,
                            onSkip = vm::skip,
                            onDelete = { vm.deleteEntry(it.id) },
                            recentAuto = autoConfig.recent,
                            onShareAuto = vm::shareAuto,
                            onUndoAuto = vm::undoAuto,
                            onKeepAuto = vm::keepAuto,
                        )
                    }
                    composable(Routes.LEDGERS) {
                        LedgersScreen(snap, onNewLedger = { nav.navigate(Routes.NEW_LEDGER) }, onSettle = { vm.settle(it.ledger.id, it.balanceCents) })
                    }
                    composable(Routes.ACCOUNTS) {
                        AccountsScreen(snap, onNewAccount = { nav.navigate(Routes.NEW_ACCOUNT) }, onSetRate = vm::setDefaultRate)
                    }
                    composable(Routes.ADD) {
                        AddScreen(snap, vm, onClose = { nav.popBackStack() }, onNewAccount = { nav.navigate(Routes.NEW_ACCOUNT) })
                    }
                    composable(Routes.NEW_ACCOUNT) { AccountEditScreen(vm, onDone = { nav.popBackStack() }) }
                    composable(Routes.NEW_LEDGER) { LedgerEditScreen(vm, onDone = { nav.popBackStack() }) }
                    composable(Routes.ME) {
                        MeScreen(
                            snap, update, vm.currentBuild,
                            onCheckUpdate = { vm.checkUpdate() },
                            onInstall = vm::installUpdate,
                            onOpenRecurring = { nav.navigate(Routes.RECURRING) },
                            onOpenAccounts = { nav.switchTab(Routes.ACCOUNTS) },
                            onOpenImport = { nav.navigate(Routes.IMPORT) },
                            onOpenAuto = { nav.navigate(Routes.AUTO) },
                        )
                    }
                    composable(Routes.AUTO) { AutoScreen(snap, vm, onBack = { nav.popBackStack() }) }
                    composable(Routes.IMPORT) { ImportScreen(snap, vm, onBack = { nav.popBackStack() }) }
                    composable(Routes.RECURRING) {
                        RecurringScreen(
                            snap,
                            onBack = { nav.popBackStack() },
                            onNew = { nav.navigate(Routes.NEW_RULE) },
                            onSetActive = { rule, active -> vm.setRuleActive(rule, active) },
                            onDelete = { vm.deleteRule(it.id) },
                        )
                    }
                    composable(Routes.NEW_RULE) { RecurringEditScreen(snap, vm, onDone = { nav.popBackStack() }) }
                }
                if (route in tabs.map { it.route }) {
                    TabBar(route, onTab = { nav.switchTab(it) }, onAdd = { nav.navigate(Routes.ADD) }, modifier = Modifier.align(Alignment.BottomCenter))
                }
            }
            ToastHost(toast, Modifier.align(Alignment.TopCenter))
        }
    }
}

private fun NavHostController.switchTab(route: String) {
    navigate(route) {
        popUpTo(Routes.HOME) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/** 悬浮的玻璃胶囊底栏，右边一颗单独的「记一笔」 */
@Composable
private fun TabBar(current: String?, onTab: (String) -> Unit, onAdd: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            Modifier.weight(1f).height(64.dp).glass(RoundedCornerShape(32.dp)).padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            tabs.forEach { tab ->
                val on = tab.route == current
                val color = if (on) Palette.Violet else Palette.Muted
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .then(if (on) Modifier.glassControl(RoundedCornerShape(26.dp)) else Modifier)
                        .semantics { selected = on; role = Role.Tab }
                        .jelly { onTab(tab.route) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    LineIcon(tab.icon, color, size = 21.dp, width = if (on) 2f else 1.9f)
                    Text(tab.label, fontSize = 11.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.SemiBold, color = color)
                }
            }
        }
        Box(
            Modifier.size(64.dp).tinted(CircleShape, Palette.Cta).semantics { contentDescription = "记一笔"; role = Role.Button }.jelly(onClick = onAdd),
            contentAlignment = Alignment.Center,
        ) { LineIcon(IconPaths.PLUS, Color.White, size = 26.dp, width = 2.4f) }
    }
}
