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
fun Label(text: String, size: TextUnit = 13.sp, color: Color = Palette.Muted, weight: FontWeight = FontWeight.SemiBold, modifier: Modifier = Modifier) {
    Text(text, fontSize = size, color = color, fontWeight = weight, modifier = modifier)
}

@Composable
fun Strong(text: String, size: TextUnit = 15.sp, color: Color = Palette.Ink, modifier: Modifier = Modifier) {
    Text(text, fontSize = size, color = color, fontWeight = FontWeight.Bold, modifier = modifier)
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
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
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
) {
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        textStyle = TextStyle(fontSize = fontSize, fontWeight = FontWeight.Bold, color = Palette.Ink),
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

@Composable
fun Avatar(text: String, her: Boolean, size: Dp = 32.dp) {
    Box(
        Modifier.size(size).clip(CircleShape).background(if (her) Palette.HerAvatar else Palette.MeAvatar),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Color.White, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.4f).sp)
    }
}

/** 顶部：返回或关闭 + 标题 + 右边可选的按钮 */
@Composable
fun ScreenHeader(title: String, backIcon: String? = IconPaths.BACK, onBack: (() -> Unit)? = null, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(start = 12.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (onBack != null && backIcon != null) RoundGlassButton(backIcon, if (backIcon == IconPaths.CLOSE) "关闭" else "返回", onClick = onBack)
        else Spacer(Modifier.width(8.dp))
        Text(title, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Palette.Ink, modifier = Modifier.weight(1f))
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
