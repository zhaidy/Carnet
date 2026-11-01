package com.personal.carnet.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.personal.carnet.ui.IconPaths
import com.personal.carnet.ui.LineIcon
import com.personal.carnet.ui.Sparkle
import com.personal.carnet.ui.glass.Palette
import com.personal.carnet.ui.glass.glass
import com.personal.carnet.ui.glass.glassControl
import com.personal.carnet.ui.glass.jelly
import com.personal.carnet.ui.glass.tinted
import kotlinx.coroutines.delay

@Composable
fun Label(text: String, size: TextUnit = 13.sp, color: Color = Palette.Muted, weight: FontWeight = FontWeight.Normal, modifier: Modifier = Modifier) {
    Text(text, fontSize = size, color = color, fontWeight = weight, modifier = modifier)
}

@Composable
fun Strong(text: String, size: TextUnit = 15.sp, color: Color = Palette.Ink, modifier: Modifier = Modifier) {
    Text(text, fontSize = size, color = color, fontWeight = FontWeight.SemiBold, modifier = modifier)
}

/** 一块玻璃卡片 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    padding: Dp = 16.dp,
    content: @Composable () -> Unit,
) {
    Box(modifier.fillMaxWidth().glass().padding(padding)) { content() }
}

/** 选中时变成染色玻璃，选中那一下会「啵」地弹一下 */
@Composable
fun GlassChip(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val pop = remember { Animatable(1f) }
    LaunchedEffect(selected) {
        if (selected) {
            pop.snapTo(0.84f)
            pop.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = 500f))
        }
    }
    Box(
        modifier
            .graphicsLayer { scaleX = pop.value; scaleY = pop.value }
            .heightIn(min = 40.dp)
            .then(if (selected) Modifier.tinted() else Modifier.glassControl())
            .semantics { this.selected = selected; role = Role.RadioButton }
            .jelly(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.SemiBold,
            color = if (selected) Color.White else Palette.Ink,
        )
    }
}

/** 一排可以左右滑的标签 */
@Composable
fun <K> ChipRow(
    options: List<Pair<K, String>>,
    selected: K?,
    label: String? = null,
    onSelect: (K) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        if (label != null) {
            Label(label, modifier = Modifier.width(56.dp))
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 3.dp),
        ) {
            options.forEach { (key, text) -> GlassChip(text, key == selected) { onSelect(key) } }
        }
    }
}

/** 紫到玫红的主按钮 */
@Composable
fun CtaButton(text: String, modifier: Modifier = Modifier, sub: String? = null, onClick: () -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .tinted(RoundedCornerShape(22.dp), Palette.Cta)
            .jelly(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Strong(text, size = 16.sp, color = Color.White)
        if (sub != null) Text(sub, fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
    }
}

/** 玻璃圆按钮，放图标 */
@Composable
fun RoundGlassButton(icon: String, description: String, size: Dp = 44.dp, onClick: () -> Unit) {
    Box(
        Modifier
            .size(size)
            .glassControl(CircleShape)
            .semantics { contentDescription = description; role = Role.Button }
            .jelly(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        LineIcon(icon, Palette.Ink, size = 20.dp, width = 2f)
    }
}

@Composable
fun PillButton(text: String, icon: String? = null, onClick: () -> Unit) {
    Row(
        Modifier
            .heightIn(min = 44.dp)
            .glassControl()
            .semantics { role = Role.Button }
            .jelly(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (icon != null) LineIcon(icon, Palette.Ink, size = 16.dp, width = 2.2f)
        Strong(text, size = 14.sp)
    }
}

/** 玻璃开关，圆钮带弹簧 */
@Composable
fun GlassSwitch(checked: Boolean, description: String, onChange: (Boolean) -> Unit) {
    val x by animateDpAsState(if (checked) 22.dp else 0.dp, spring(dampingRatio = 0.5f, stiffness = 500f), label = "knob")
    Box(
        Modifier
            .width(52.dp)
            .height(32.dp)
            .clip(RoundedCornerShape(16.dp))
            .then(if (checked) Modifier.background(Palette.Cta) else Modifier.background(Color.White.copy(alpha = 0.5f)))
            .semantics { role = Role.Switch; contentDescription = description; selected = checked }
            .jelly { onChange(!checked) }
            .padding(3.dp),
    ) {
        Box(
            Modifier
                .offset(x = x)
                .size(26.dp)
                .clip(CircleShape)
                .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.White, Color(0xFFECE6FA)))),
        )
    }
}

/** − 数字 + */
@Composable
fun Stepper(value: String, description: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StepButton("−", "$description 减") { onMinus() }
        Strong(value, modifier = Modifier.width(64.dp), size = 15.sp)
        StepButton("+", "$description 加") { onPlus() }
    }
}

@Composable
private fun StepButton(text: String, description: String, onClick: () -> Unit) {
    Box(
        Modifier.size(36.dp).glassControl(CircleShape).semantics { contentDescription = description; role = Role.Button }.jelly(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Strong(text, size = 18.sp) }
}

/** 玻璃输入框 */
@Composable
fun GlassField(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    decimal: Boolean = false,
    fontSize: TextUnit = 15.sp,
    secret: Boolean = false,
    singleLine: Boolean = true,
) {
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = singleLine,
        visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
        textStyle = TextStyle(fontSize = fontSize, fontWeight = FontWeight.SemiBold, color = Palette.Ink),
        cursorBrush = SolidColor(Palette.Violet),
        keyboardOptions = if (decimal) KeyboardOptions(keyboardType = KeyboardType.Decimal) else KeyboardOptions.Default,
        modifier = modifier
            .heightIn(min = 42.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.58f)),
        decorationBox = { inner ->
            Box(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) Text(placeholder, fontSize = fontSize, color = Palette.Muted.copy(alpha = 0.8f))
                inner()
            }
        },
    )
}

/** 圆角方块里放一个线条图标，带一点按图标挑的颜色 */
@Composable
fun IconTile(icon: String, size: Dp = 42.dp, key: String = icon) {
    val (bg, fg) = Palette.tintFor(key)
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size * 0.33f)).background(bg.copy(alpha = 0.88f)),
        contentAlignment = Alignment.Center,
    ) { LineIcon(icon, fg, size = size * 0.46f, width = 1.9f) }
}

@Composable
fun Avatar(text: String, her: Boolean, size: Dp = 32.dp, ring: Boolean = false) {
    Box(
        Modifier.size(size).clip(CircleShape).background(if (her) Palette.HerAvatar else Palette.MeAvatar)
            // 两个头像叠着放时，压在上面的那个描一圈白边，下面的字不会看起来被切掉
            .then(if (ring) Modifier.border(2.dp, Color.White.copy(alpha = 0.9f), CircleShape) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = (size.value * 0.4f).sp)
    }
}

/** 顶部：返回或关闭 + 标题 + 右边可选的按钮 */
@Composable
fun ScreenHeader(
    title: String,
    backIcon: String? = IconPaths.BACK,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    // 左右不留边：放在已经有 16dp 边距的列表里，返回键和卡片左边对齐；没有返回键时标题再缩 4dp，和总览的「Carnet」对齐
    Row(
        modifier.fillMaxWidth().statusBarsPadding().padding(top = 12.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val hasBack = onBack != null && backIcon != null
        if (hasBack) RoundGlassButton(backIcon!!, if (backIcon == IconPaths.CLOSE) "关闭" else "返回", onClick = onBack!!)
        Text(
            title, fontSize = 28.sp, fontWeight = FontWeight.SemiBold, color = Palette.Ink, letterSpacing = (-0.3).sp,
            modifier = Modifier.weight(1f).padding(start = if (hasBack) 0.dp else 4.dp),
            maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        )
        trailing()
    }
}

/** 顶上弹出来的提示条 */
@Stable
class ToastState {
    var message by mutableStateOf<String?>(null)
        private set
    var key by mutableIntStateOf(0)
        private set

    fun show(text: String) {
        message = text
        key++
    }

    fun clear() {
        message = null
    }
}

val LocalToast = staticCompositionLocalOf { ToastState() }

@Composable
fun ToastHost(state: ToastState, modifier: Modifier = Modifier) {
    LaunchedEffect(state.key) {
        if (state.message != null) {
            delay(2400)
            state.clear()
        }
    }
    AnimatedVisibility(
        visible = state.message != null,
        enter = scaleIn(spring(dampingRatio = 0.45f, stiffness = 500f), initialScale = 0.7f) + fadeIn(),
        exit = scaleOut(targetScale = 0.9f) + fadeOut(),
        modifier = modifier,
    ) {
        Row(
            Modifier
                .statusBarsPadding()
                .padding(top = 12.dp, start = 20.dp, end = 20.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(Color(0xE61F1640))
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Sparkle(Color(0xFFFFD3EC), 16.dp)
            Text(state.message.orEmpty(), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/**
 * 放在编辑页最底下、默认收起的「更多操作」：删除、停止同步这类不能反悔的放这里，
 * 要滚到底、点开、再点一次确认，不容易误触
 */
@Composable
fun DangerZone(content: @Composable () -> Unit) {
    var open by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(top = 28.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            Modifier.align(Alignment.CenterHorizontally).semantics { role = Role.Button }.jelly { open = !open }.padding(horizontal = 16.dp, vertical = 10.dp),
        ) { Label(if (open) "收起" else "更多操作", size = 13.sp, weight = FontWeight.SemiBold) }
        if (open) content()
    }
}

/** 更多操作里的一项：点了先问一次；[blocked] 不是 null 时说明为什么做不了 */
@Composable
fun DangerAction(
    title: String,
    question: String,
    detail: String? = null,
    confirmText: String = "确定",
    blocked: String? = null,
    onConfirm: () -> Unit,
) {
    var asking by androidx.compose.runtime.saveable.rememberSaveable(title) { mutableStateOf(false) }
    GlassCard(padding = 14.dp) {
        if (!asking) {
            Box(Modifier.fillMaxWidth().semantics { role = Role.Button }.jelly { asking = true }.padding(vertical = 4.dp)) {
                Strong(title, size = 14.sp, color = Palette.Rose)
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (blocked != null) {
                    Strong("现在做不了", size = 14.sp)
                    Label(blocked, size = 12.sp, weight = FontWeight.Medium)
                    Box(Modifier.fillMaxWidth().glassControl(RoundedCornerShape(18.dp)).jelly { asking = false }.padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                        Strong("知道了", size = 14.sp)
                    }
                } else {
                    Strong(question, size = 14.sp)
                    if (detail != null) Label(detail, size = 12.sp, weight = FontWeight.Medium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            Modifier.weight(1f).glassControl(RoundedCornerShape(18.dp)).jelly { asking = false; onConfirm() }.padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center,
                        ) { Strong(confirmText, size = 14.sp, color = Palette.Rose) }
                        Box(
                            Modifier.weight(1f).glassControl(RoundedCornerShape(18.dp)).jelly { asking = false }.padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center,
                        ) { Strong("算了", size = 14.sp) }
                    }
                }
            }
        }
    }
}
