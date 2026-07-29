package com.cashewteam.novatext.extra

import android.os.Bundle
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cashewteam.novatext.extra.vector.NovaTextAccessibilityBridge

class ExtraActivity : ComponentActivity() {
    private var resumeVersion by mutableIntStateOf(0)

    override fun onResume() {
        super.onResume()
        resumeVersion++
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val settings = ExtraSettings(this)
        VectorServiceBridge.connect(settings)
        setContent {
            ExtraTheme {
                ExtraScreen(
                    settings = settings,
                    resumeVersion = resumeVersion,
                )
            }
        }
    }
}

private enum class ExtraPage { HOME, SYSTEM, TRIGGER, STATUS }

@Composable
private fun ExtraTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = androidx.compose.material3.lightColorScheme(
        primary = Color(0xFF4E7EDB),
        background = Color(0xFFF1F2F4),
        surface = Color.White,
    ), content = content)
}

@Composable
private fun ExtraScreen(
    settings: ExtraSettings,
    @Suppress("UNUSED_PARAMETER") resumeVersion: Int,
) {
    var page by remember { mutableStateOf(ExtraPage.HOME) }
    var version by remember { mutableIntStateOf(0) }
    val refresh = { version += 1; Unit }
    val config = remember(version) { settings.config() }
    BackHandler(enabled = page != ExtraPage.HOME) { page = ExtraPage.HOME }

    Column(Modifier.fillMaxSize().background(Color(0xFFF1F2F4))) {
        ExtraTopBar(page, onBack = { page = ExtraPage.HOME })
        AnimatedContent(page, label = "extra-page") { current ->
            when (current) {
                ExtraPage.HOME -> HomePage(
                    onOpen = { page = it },
                    enabled = config.enabled,
                    calibrated = config.calibrated,
                )
                ExtraPage.SYSTEM -> SystemPage(settings, config, refresh)
                ExtraPage.TRIGGER -> TriggerSettingsPage(settings, config, refresh)
                ExtraPage.STATUS -> StatusPage(settings, config, version)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExtraTopBar(page: ExtraPage, onBack: () -> Unit) {
    val title = when (page) {
        ExtraPage.HOME -> "Nova Text Extra"
        ExtraPage.SYSTEM -> "系统触控监听"
        ExtraPage.TRIGGER -> "触发设置"
        ExtraPage.STATUS -> "状态与诊断"
    }
    TopAppBar(
        title = { Text(title, fontWeight = FontWeight.SemiBold) },
        navigationIcon = if (page == ExtraPage.HOME) ({} ) else ({ TextButton(onClick = onBack) { Text("返回") } }),
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White),
    )
}

@Composable
private fun HomePage(onOpen: (ExtraPage) -> Unit, enabled: Boolean, calibrated: Boolean) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { IntroCard(enabled, calibrated) }
        item { NavigationCard("系统触控监听", "Xposed 模块、作用域与稳定触发", { onOpen(ExtraPage.SYSTEM) }) }
        item { NavigationCard("触发设置", "App 内实时读取触控数据并设置阈值", { onOpen(ExtraPage.TRIGGER) }) }
        item { NavigationCard("状态与诊断", "框架、权限与最近配置状态", { onOpen(ExtraPage.STATUS) }) }
    }
}

@Composable
private fun IntroCard(enabled: Boolean, calibrated: Boolean) = ExtraCard {
    Text("触控触发扩展", fontSize = 24.sp, fontWeight = FontWeight.Bold)
    Text(
        if (enabled) "系统监听已启用" else if (calibrated) "阈值已保存，等待启用" else "先设置触发阈值，再启用系统监听",
        color = Color(0xFF60656D),
    )
}

@Composable
private fun NavigationCard(title: String, subtitle: String, onClick: () -> Unit) = ExtraCard(Modifier.clickable(onClick = onClick)) {
    Text(title, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
    Text(subtitle, color = Color(0xFF60656D), fontSize = 14.sp)
}

@Composable
private fun SystemPage(settings: ExtraSettings, config: TriggerConfig, refresh: () -> Unit) {
    val context = LocalContext.current
    val novaTextAccessibilityEnabled = rememberNovaTextAccessibilityEnabled(context)
    PageList {
        item {
            ExtraCard {
                Text("Xposed 模块", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Text(VectorServiceBridge.status, color = Color(0xFF60656D))
                Button(onClick = {
                    VectorServiceBridge.requestRequiredScopes(context) {
                        Toast.makeText(context, it, Toast.LENGTH_LONG).show()
                    }
                }) {
                    Text("授权所需作用域")
                }
            }
        }
        item {
            ExtraCard {
                SwitchRow("启用系统触控监听", "仅观察触控，不接管或回放事件", config.enabled, enabled = config.calibrated) {
                    if (it && !NovaTextAccessibilityBridge.isEnabled(context)) {
                        Toast.makeText(context, "请先开启 Nova Text 无障碍", Toast.LENGTH_LONG).show()
                    } else {
                        settings.triggerEnabled = it
                        refresh()
                    }
                }
                if (!config.calibrated) Text("请先保存触发设置", color = Color(0xFFB05D00), fontSize = 13.sp)
            }
        }
        item {
            ExtraCard {
                Text("Nova Text 无障碍", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                SwitchRow(
                    "自动开启 Nova Text 无障碍",
                    "启用前请先授权所需作用域；首次授权 Android 系统作用域后请重启设备。",
                    settings.autoEnableNovaTextAccessibility,
                ) { enabled ->
                    settings.autoEnableNovaTextAccessibility = enabled
                    refresh()
                }
                Text(
                    if (novaTextAccessibilityEnabled) "Nova Text 无障碍：已开启" else "Nova Text 无障碍：未开启",
                    color = Color(0xFF60656D),
                    fontSize = 13.sp,
                )
            }
        }
    }
}

@Composable
private fun rememberNovaTextAccessibilityEnabled(context: android.content.Context): Boolean {
    val appContext = context.applicationContext
    var enabled by remember { mutableStateOf(NovaTextAccessibilityBridge.isEnabled(appContext)) }
    DisposableEffect(appContext) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                enabled = NovaTextAccessibilityBridge.isEnabled(appContext)
            }
        }
        appContext.contentResolver.registerContentObserver(
            Settings.Secure.getUriFor(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES),
            false,
            observer,
        )
        enabled = NovaTextAccessibilityBridge.isEnabled(appContext)
        onDispose { appContext.contentResolver.unregisterContentObserver(observer) }
    }
    return enabled
}

@Composable
private fun TriggerSettingsPage(settings: ExtraSettings, config: TriggerConfig, refresh: () -> Unit) {
    var mode by remember { mutableStateOf(config.mode) }
    var threshold by remember(mode) {
        mutableFloatStateOf(when (mode) {
            TriggerMode.PRESSURE -> settings.pressureThreshold
            TriggerMode.SIZE -> settings.sizeThreshold
            TriggerMode.TOUCH_AREA -> settings.touchAreaThreshold
            TriggerMode.SINGLE_LONG_PRESS -> settings.longPressDuration
            TriggerMode.TWO_FINGER_TAP -> settings.twoFingerTapDuration
            TriggerMode.THREE_FINGER_TAP -> settings.threeFingerTapDuration
        })
    }
    PageList {
        item {
            ExtraCard {
                Text("选择触发数据", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SelectButton("压感", mode == TriggerMode.PRESSURE) { mode = TriggerMode.PRESSURE }
                    SelectButton("Size", mode == TriggerMode.SIZE) { mode = TriggerMode.SIZE }
                    SelectButton("椭圆面积", mode == TriggerMode.TOUCH_AREA) { mode = TriggerMode.TOUCH_AREA }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SelectButton("单指长按", mode == TriggerMode.SINGLE_LONG_PRESS) { mode = TriggerMode.SINGLE_LONG_PRESS }
                    SelectButton("双指单击", mode == TriggerMode.TWO_FINGER_TAP) { mode = TriggerMode.TWO_FINGER_TAP }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SelectButton("三指单击", mode == TriggerMode.THREE_FINGER_TAP) { mode = TriggerMode.THREE_FINGER_TAP }
                }
                Text(
                    when {
                        TriggerPolicy.isSensorMode(mode) -> "支持 Xposed；免 Root 实验性触控监听已迁移至 Nova Text。"
                        TriggerPolicy.isMultiFingerTap(mode) -> "支持 Xposed；免 Root 双指/三指触发已迁移至 Nova Text。"
                        else -> "该触发方式仅在 Xposed 系统触控监听中生效。"
                    },
                    color = Color(0xFF60656D),
                    fontSize = 14.sp,
                )
            }
        }
        item {
            ExtraCard {
                Text(when (mode) {
                    TriggerMode.PRESSURE -> "压感阈值"
                    TriggerMode.SIZE -> "Size 阈值"
                    TriggerMode.TOUCH_AREA -> "椭圆接触面积阈值"
                    TriggerMode.SINGLE_LONG_PRESS -> "单指长按时长"
                    TriggerMode.TWO_FINGER_TAP -> "双指单击最长时长"
                    TriggerMode.THREE_FINGER_TAP -> "三指单击最长时长"
                }, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    when (mode) {
                        TriggerMode.PRESSURE -> "%.3f".format(threshold)
                        TriggerMode.SIZE -> "%d%%  ·  %.3f".format((threshold * 100f).toInt(), threshold)
                        TriggerMode.TOUCH_AREA -> "%.1f px²".format(threshold)
                        TriggerMode.SINGLE_LONG_PRESS, TriggerMode.TWO_FINGER_TAP, TriggerMode.THREE_FINGER_TAP -> "%.0f ms".format(threshold)
                    },
                    color = Color(0xFF60656D),
                )
                Slider(
                    value = threshold,
                    onValueChange = { threshold = it },
                    valueRange = when (mode) {
                        TriggerMode.PRESSURE -> 0f..3f
                        TriggerMode.SIZE -> 0.01f..1f
                        TriggerMode.TOUCH_AREA -> 0f..2_000f
                        TriggerMode.SINGLE_LONG_PRESS -> 300f..1_500f
                        TriggerMode.TWO_FINGER_TAP, TriggerMode.THREE_FINGER_TAP -> 100f..600f
                    },
                )
            }
        }
        if (TriggerPolicy.isSensorMode(mode)) {
            item { TouchEventTest() }
        }
        item {
            ExtraCard {
                Text("重要提醒", color = Color(0xFFB05D00), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("请确认触发设置正确且设备支持，避免普通触控被误判为触发。")
            }
        }
        item {
            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = threshold > 0f && threshold.isFinite(),
                onClick = {
                    settings.mode = mode
                    settings.calibrated = true
                    when (mode) {
                        TriggerMode.PRESSURE -> settings.pressureThreshold = threshold
                        TriggerMode.SIZE -> settings.sizeThreshold = threshold
                        TriggerMode.TOUCH_AREA -> settings.touchAreaThreshold = threshold
                        TriggerMode.SINGLE_LONG_PRESS -> settings.longPressDuration = threshold
                        TriggerMode.TWO_FINGER_TAP -> settings.twoFingerTapDuration = threshold
                        TriggerMode.THREE_FINGER_TAP -> settings.threeFingerTapDuration = threshold
                    }
                    settings.triggerEnabled = false
                    refresh()
                },
            ) { Text("保存触发设置") }
            if (threshold <= 0f || !threshold.isFinite()) {
                Text("阈值必须大于 0，否则所有普通触摸都会被判定为触发。", color = Color(0xFFB05D00), fontSize = 13.sp)
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun TouchEventTest() = ExtraCard {
    var pressure by remember { mutableFloatStateOf(0f) }
    var size by remember { mutableFloatStateOf(0f) }
    var touchMajor by remember { mutableFloatStateOf(0f) }
    var touchMinor by remember { mutableFloatStateOf(0f) }
    Text("触控数据测试", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
    Box(
        Modifier.fillMaxWidth().height(150.dp).background(Color(0xFFF6F7F8), RoundedCornerShape(14.dp))
            .pointerInteropFilter { event ->
                if (event.pointerCount > 0) {
                    pressure = event.getPressure(0)
                    size = event.getSize(0)
                    touchMajor = event.getTouchMajor(0)
                    touchMinor = event.getTouchMinor(0)
                }
                true
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                TouchValue("压感", pressure)
                TouchValue("Size", size)
                TouchValue("椭圆面积", TriggerPolicy.touchArea(touchMajor, touchMinor))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                TouchValue("TouchMajor", touchMajor)
                TouchValue("TouchMinor", touchMinor)
            }
        }
    }
    Text("在此区域按压或滑动，数值仅用于观察和设置阈值。", color = Color(0xFF60656D), fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun TouchValue(label: String, value: Float) = Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Text("%.3f".format(value), fontSize = 22.sp, fontWeight = FontWeight.Bold)
    Text(label, color = Color(0xFF60656D), fontSize = 13.sp)
}

@Composable
private fun StatusPage(settings: ExtraSettings, config: TriggerConfig, @Suppress("UNUSED_PARAMETER") version: Int) {
    val context = LocalContext.current
    val novaInstalled = remember { context.packageManager.getLaunchIntentForPackage(TriggerPolicy.NOVA_TEXT) != null }
    PageList {
        item {
            ExtraCard {
                Text("运行状态", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                StatusRow("Xposed", VectorServiceBridge.status)
                StatusRow("作用域", VectorServiceBridge.scopeStatus())
                StatusRow("Nova Text", if (novaInstalled) "已安装" else "未安装")
                StatusRow("触发设置", if (config.calibrated) "已保存" else "未保存")
                StatusRow("触发方式", when (config.mode) {
                    TriggerMode.PRESSURE -> "压感"
                    TriggerMode.SIZE -> "Size"
                    TriggerMode.TOUCH_AREA -> "椭圆接触面积"
                    TriggerMode.SINGLE_LONG_PRESS -> "单指长按（仅 Xposed）"
                    TriggerMode.TWO_FINGER_TAP -> "双指单击（Xposed / 免 Root）"
                    TriggerMode.THREE_FINGER_TAP -> "三指单击（Xposed / 免 Root）"
                })
                StatusRow("阈值", when (config.mode) {
                    TriggerMode.TOUCH_AREA -> "%.1f px²".format(config.threshold)
                    TriggerMode.SINGLE_LONG_PRESS, TriggerMode.TWO_FINGER_TAP, TriggerMode.THREE_FINGER_TAP -> "%.0f ms".format(config.threshold)
                    else -> "%.3f".format(config.threshold)
                })
                StatusRow("系统监听", if (settings.triggerEnabled) "已启用" else "未启用")
                StatusRow("自动开启无障碍", if (settings.autoEnableNovaTextAccessibility) "已请求" else "未请求")
                StatusRow("Nova Text 无障碍", if (NovaTextAccessibilityBridge.isEnabled(context)) "已开启" else "未开启")
                StatusRow("免 Root 监听", "已迁移至 Nova Text")
                StatusRow("最近触发", ExtraDiagnostics.last(context))
            }
        }
    }
}

@Composable
private fun StatusRow(name: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(name, color = Color(0xFF60656D))
        Text(value, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = Color(0xFF60656D), fontSize = 13.sp)
        }
        Switch(checked = checked, enabled = enabled, onCheckedChange = onChange)
    }
}

@Composable
private fun SelectButton(label: String, selected: Boolean, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, colors = ButtonDefaults.outlinedButtonColors(contentColor = if (selected) MaterialTheme.colorScheme.primary else Color.DarkGray)) { Text(label) }
}

@Composable
private fun PageList(content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
}

@Composable
private fun ExtraCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}
