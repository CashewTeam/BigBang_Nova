# 触控压感与触控面积触发集成

> 贡献者：EX3124

## 目的与定位

本文档记录“触控压感（pressure）”和“触控面积（size）”作为 Nova Text 触发条件的实现思路，供独立 Android App 或系统级扩展集成使用。

该能力不在 Nova Text 主应用内通过全屏透明触摸层实现。全屏拦截后再用无障碍手势回放原始点击和滑动，会改变真实触控链路；在不支持压感的设备上还可能使用户难以关闭触发服务。独立模块应只负责采集、判定和拉起 Nova Text，不能接管或转发普通触控事件。

## 推荐架构

```text
触控数据来源
  ├─ Root / Xposed：系统级监听，不拦截原始事件（推荐）
  └─ 独立 App：仅在自身有合法触摸上下文时采样
        ↓
阈值与设备能力判定
        ↓
Nova Text BIGBANG_CAPTURE 自定义 Action
        ↓
无障碍抓文 / OCR 分流 → BigBang
```

触控数据来源和 Nova Text 的内容捕获应保持解耦：模块只传递“触发发生在何处”，Nova Text 继续使用既有的无障碍文本、截图与 OCR 主链路。

## 触控数据采集

Android 的 `MotionEvent` 提供以下相对值：

| 数据 | API | 常见范围 | 注意事项 |
|---|---|---|---|
| 压力 | `event.pressure` | 通常 `0f..1f` | 许多普通电容屏会始终报告约 `1f`，不能将其视作真实压感支持。 |
| 面积 | `event.size` | 通常 `0f..1f` | 表示接触区域的归一化近似值，设备差异很大。 |
| 坐标 | `event.rawX` / `event.rawY` | 屏幕像素 | 用作 Nova Text 展开动画与最近文本选择锚点。 |

必须在目标设备上展示实时数值并完成校准后，才允许用户启用触发。应将“设备不支持有效压力/面积数据”视为不支持该功能，而不是退化为全局点击触发。

建议按设备型号保存独立阈值，并提供关闭入口。阈值比较应只在明确的 `ACTION_DOWN` / `ACTION_MOVE` 生命周期中进行；一次手势最多触发一次，`ACTION_UP` 和 `ACTION_CANCEL` 都必须复位状态。

## 系统级实现建议

若要在任意第三方 App 上监听触控，同时保持原有点击和滑动手感，应使用不会消费原始事件的系统级实现：

- Root 模式：在输入事件观察层读取数据，不修改、延迟或重放事件。
- Xposed 模式：仅对输入分发链路做观察，在确认触发后异步启动 Nova Text，不改变原调用结果。
- 无障碍模式：仅用于 Nova Text 的文本抓取、截图和内容分流；不要用 `dispatchGesture()` 回放被透明 overlay 吞掉的常规触控。

实现模块应避免在 Nova Text、自身设置页、系统设置、权限页和锁屏界面触发，避免自我拉起、设置页无法操作或误触。

## 拉起 Nova Text

触发模块完成设备能力判断后，应调用稳定的 `BIGBANG_CAPTURE` Action，而不是直接引用 Nova Text 的 Activity 类名：

```kotlin
val intent = Intent("com.cashewteam.novatext.android.action.BIGBANG_CAPTURE").apply {
    setPackage("com.cashewteam.novatext.android")
    putExtra("caller_pkg", foregroundPackageName)
    putExtra("boom_startx", touchX)
    putExtra("boom_starty", touchY)
    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}

if (intent.resolveActivity(packageManager) != null) {
    startActivity(intent)
}
```

- `caller_pkg` 必须是触发时实际承载内容的前台应用包名，不能填 Nova Text 自身包名。
- `boom_startx`、`boom_starty` 使用屏幕像素坐标。
- Nova Text 无障碍服务必须已由用户在系统设置中启用；它负责后续无障碍抓文、截图与 OCR 分流。
- 完整 Action 与参数定义见 [third-party-integration.md](third-party-integration.md)。

## 验收要求

- 在目标设备上记录并验证普通点击、长按、滚动、快速滑动、多指触控的原始手感没有变化。
- 不支持有效压感/面积数据的设备默认关闭，且不能通过普通点击误触发。
- 模块停止、权限撤销、触控取消和屏幕旋转后，不残留任何可拦截输入的窗口或状态。
- 在 Nova Text 设置页、无障碍设置页和模块自身设置页中不会触发。
- 触发后能正确传递前台包名和触点，并由 Nova Text 按既有流程完成文本抓取或 OCR。
