package com.cashewteam.novatext.extra

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
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
                    requestAccessibility = { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                    requestBatteryExemption = {
                        startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                            data = Uri.parse("package:$packageName")
                        })
                    },
                    requestAppPermissions = {
                        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.parse("package:$packageName")
                        })
                    },
                    onMessage = { Toast.makeText(this, it, Toast.LENGTH_SHORT).show() },
                )
            }
        }
    }
}

private enum class ExtraPage { HOME, SYSTEM, TRIGGER, EXPERIMENTAL, STATUS }

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
    requestAccessibility: () -> Unit,
    requestBatteryExemption: () -> Unit,
    requestAppPermissions: () -> Unit,
    onMessage: (String) -> Unit,
) {
    val context = LocalContext.current
    var page by remember { mutableStateOf(ExtraPage.HOME) }
    var version by remember { mutableIntStateOf(0) }
    var riskDialog by remember { mutableStateOf(false) }
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
                ExtraPage.SYSTEM -> SystemPage(settings, config, refresh, onMessage)
                ExtraPage.TRIGGER -> TriggerSettingsPage(settings, config, refresh, onMessage)
                ExtraPage.EXPERIMENTAL -> ExperimentalPage(
                    settings = settings,
                    onEnable = { riskDialog = true },
                    onStop = { ExperimentalTouchController.stop(context); refresh() },
                    requestAccessibility = requestAccessibility,
                    requestBatteryExemption = requestBatteryExemption,
                    requestAppPermissions = requestAppPermissions,
                )
                ExtraPage.STATUS -> StatusPage(settings, config, version)
            }
        }
    }
    if (riskDialog) {
        AlertDialog(
            onDismissRequest = { riskDialog = false },
            title = { Text("启用实验性触控监听？") },
            text = { Text("仅支持 Android 13 及以上。普通触控会直接委托给当前应用；压感或面积达到阈值时，本次触控将被消费并触发 Nova Text。") },
            confirmButton = {
                TextButton(onClick = {
                    riskDialog = false
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                        onMessage("实验模式需要 Android 13 或更高版本")
                    } else if (!ExperimentalTouchController.hasBatteryExemption(context)) {
                        onMessage("请先允许 Extra 在后台持续运行")
                        requestBatteryExemption()
                    } else if (ExtraAccessibilityService.active == null) {
                        requestAccessibility()
                    } else if (!ExperimentalTouchController.start(context)) {
                        onMessage("请先保存触发设置，并完成无障碍授权")
                    } else refresh()
                }) { Text("继续") }
            },
            dismissButton = { TextButton(onClick = { riskDialog = false }) { Text("取消") } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExtraTopBar(page: ExtraPage, onBack: () -> Unit) {
    val title = when (page) {
        ExtraPage.HOME -> "Nova Text Extra"
        ExtraPage.SYSTEM -> "系统触控监听"
        ExtraPage.TRIGGER -> "触发设置"
        ExtraPage.EXPERIMENTAL -> "实验性触控监听"
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
        item { NavigationCard("实验性触控监听", "免 Root，Android 13+ 原样委托普通触控", { onOpen(ExtraPage.EXPERIMENTAL) }) }
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
private fun SystemPage(settings: ExtraSettings, config: TriggerConfig, refresh: () -> Unit, onMessage: (String) -> Unit) {
    val context = LocalContext.current
    PageList {
        item {
            ExtraCard {
                Text("Xposed 模块", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Text(VectorServiceBridge.status, color = Color(0xFF60656D))
                Button(onClick = { VectorServiceBridge.requestAllThirdPartyApps(context, onMessage) }) {
                    Text("授权全部第三方应用")
                }
            }
        }
        item {
            ExtraCard {
                SwitchRow("启用系统触控监听", "仅观察触控，不接管或回放事件", config.enabled, enabled = config.calibrated) {
                    settings.triggerEnabled = it; refresh()
                }
                if (!config.calibrated) Text("请先保存触发设置", color = Color(0xFFB05D00), fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun TriggerSettingsPage(settings: ExtraSettings, config: TriggerConfig, refresh: () -> Unit, onMessage: (String) -> Unit) {
    val context = LocalContext.current
    var mode by remember { mutableStateOf(config.mode) }
    var threshold by remember(mode) {
        mutableFloatStateOf(when (mode) {
            TriggerMode.PRESSURE -> settings.pressureThreshold
            TriggerMode.SIZE -> settings.sizeThreshold
            TriggerMode.TOUCH_AREA -> settings.touchAreaThreshold
            TriggerMode.SINGLE_LONG_PRESS -> settings.longPressDuration
            TriggerMode.TWO_FINGER_TAP -> settings.twoFingerTapDuration
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
                    SelectButton("双指短按", mode == TriggerMode.TWO_FINGER_TAP) { mode = TriggerMode.TWO_FINGER_TAP }
                }
                Text(
                    if (TriggerPolicy.isSensorMode(mode)) "阈值由滑条设置；仅在 Extra 的测试区域读取本机触控数据，不使用 Xposed。"
                    else "该触发方式仅在 Xposed 系统触控监听中生效。",
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
                    TriggerMode.TWO_FINGER_TAP -> "双指短按最长时长"
                }, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    when (mode) {
                        TriggerMode.PRESSURE -> "%.3f".format(threshold)
                        TriggerMode.SIZE -> "%d%%  ·  %.3f".format((threshold * 100f).toInt(), threshold)
                        TriggerMode.TOUCH_AREA -> "%.1f px²".format(threshold)
                        TriggerMode.SINGLE_LONG_PRESS, TriggerMode.TWO_FINGER_TAP -> "%.0f ms".format(threshold)
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
                        TriggerMode.TWO_FINGER_TAP -> 100f..600f
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
                Text("请确认触发设置正确并且设备支持，否则可能会导致无障碍触控拦截无法关闭。")
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
                    }
                    settings.triggerEnabled = false
                    if (!TriggerPolicy.isSensorMode(mode)) ExperimentalTouchController.stop(context)
                    refresh()
                    onMessage("触发阈值已保存")
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
private fun ExperimentalPage(
    settings: ExtraSettings,
    onEnable: () -> Unit,
    onStop: () -> Unit,
    requestAccessibility: () -> Unit,
    requestBatteryExemption: () -> Unit,
    requestAppPermissions: () -> Unit,
) {
    val context = LocalContext.current
    val accessibilityEnabled = ExtraAccessibilityService.isEnabled(context)
    val accessibilityConnected = ExtraAccessibilityService.active != null
    val supported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    val batteryExempt = ExperimentalTouchController.hasBatteryExemption(context)
    val running = ExperimentalTouchController.running
    PageList {
        item {
            ExtraCard {
                Text("实验性功能", color = Color(0xFFB05D00), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("使用 Android 13 的 TouchInteractionController。普通点击、滑动和多指操作会原样委托给当前应用；仅首个按下事件超过阈值时才消费并触发 Nova Text。")
                SwitchRow("启用实验模式", "仅 Android 13+，不需要悬浮窗或手势回放", running, enabled = supported) { enabled ->
                    if (enabled) onEnable() else onStop()
                }
            }
        }
        item {
            ExtraCard {
                Text("权限与运行状态", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                StatusRow("系统版本", if (supported) "Android 13+，已支持" else "需要 Android 13+")
                StatusRow("后台持续运行", if (batteryExempt) "已允许" else "未允许，触控监听不会启动")
                StatusRow("后台弹出页面", "需在系统权限管理中手动允许")
                StatusRow("无障碍权限", when {
                    accessibilityConnected -> "已授权并连接"
                    accessibilityEnabled -> "已授权，等待连接"
                    else -> "未授权"
                })
                StatusRow("触控控制器", when {
                    ExperimentalTouchController.listening -> "监听中"
                    running -> "等待无障碍连接"
                    else -> "未监听"
                })
                if (!accessibilityEnabled || !accessibilityConnected) {
                    OutlinedButton(onClick = requestAccessibility) { Text("打开无障碍设置") }
                }
                if (!batteryExempt) {
                    OutlinedButton(onClick = requestBatteryExemption) { Text("允许后台持续运行") }
                }
                OutlinedButton(onClick = requestAppPermissions) { Text("打开应用权限设置") }
                if (!settings.calibrated) Text("请先在“触发设置”中保存阈值", color = Color(0xFFB05D00), fontSize = 13.sp)
            }
        }
    }
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
                    TriggerMode.TWO_FINGER_TAP -> "双指短按（仅 Xposed）"
                })
                StatusRow("阈值", when (config.mode) {
                    TriggerMode.TOUCH_AREA -> "%.1f px²".format(config.threshold)
                    TriggerMode.SINGLE_LONG_PRESS, TriggerMode.TWO_FINGER_TAP -> "%.0f ms".format(config.threshold)
                    else -> "%.3f".format(config.threshold)
                })
                StatusRow("系统监听", if (settings.triggerEnabled) "已启用" else "未启用")
                StatusRow("后台持续运行", if (ExperimentalTouchController.hasBatteryExemption(context)) "已允许" else "未允许")
                StatusRow("实验触控监听", when {
                    ExperimentalTouchController.listening -> "运行中"
                    ExperimentalTouchController.running -> "等待无障碍连接"
                    else -> "未运行"
                })
                StatusRow("Extra 无障碍", if (ExtraAccessibilityService.active != null) "已连接" else "未连接")
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
