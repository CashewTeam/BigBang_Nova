# 第三方应用调用

## 调用方式总览

| 方式 | 是否弹选择器 | 推荐度 | 说明 |
|---|---|---|---|
| 自定义 Action | 否 | 推荐 | 直接打开 BigBang，不依赖类名 |
| OCR 自定义 Action | 否 | 推荐 | 传入图片和触点，进入 OCR 范围选择页 |
| 辅助模式自定义 Action | 否 | 推荐 | 传入触点和调用方包名，使用无障碍抓文；可提供图片用于 OCR 回退 |
| 完整分流自定义 Action | 否 | 推荐 | 复用悬浮球默认流程：白名单直走 OCR，其余无障碍抓文并在失败后 OCR 回退 |
| 显式 Intent（setClassName） | 否 | 可用 | 最直接，但耦合具体类名 |
| ACTION_SEND | 是 | 通用分享 | 走系统分享选择器，用户自选 |

## 自定义 Action 调用（推荐）

BigBang 注册了自定义 Action `com.cashewteam.novatext.android.action.BIGBANG_TEXT`，第三方应用可通过隐式 Intent + `setPackage()` 直接打开 BigBang，无需弹选择器：

```kotlin
val intent = Intent("com.cashewteam.novatext.android.action.BIGBANG_TEXT").apply {
    setPackage("com.cashewteam.novatext.android")
    putExtra(Intent.EXTRA_TEXT, "要分解的文本内容")
    // 可选：动画锚点
    putExtra("boom_startx", 540)
    putExtra("boom_starty", 1200)
    // 可选：自动选中第 3 个字符所在的分词
    putExtra("extra_selected_char_index", 2)
    // 可选：炸了又炸 — 前一段落
    putExtra("extra_adjacent_text_before", "前一段落内容...")
    // 可选：炸了又炸 — 后一段落
    putExtra("extra_adjacent_text_after", "后一段落内容...")
}
// 先检查是否安装了 BigBang
if (intent.resolveActivity(packageManager) != null) {
    startActivity(intent)
}
```

### 检查 BigBang 是否可用

```kotlin
fun isBigBangAvailable(context: Context): Boolean {
    val intent = Intent("com.cashewteam.novatext.android.action.BIGBANG_TEXT")
    intent.setPackage("com.cashewteam.novatext.android")
    return intent.resolveActivity(context.packageManager) != null
}
```

## 图片 OCR 调用

BigBang 注册了 `com.cashewteam.novatext.android.action.BIGBANG_OCR`。调用后会直接进入现有的 OCR 图片范围选择页，用户可以调整识别范围和临时识别语言，再进入 BigBang。

图片必须是可读取的 `content://` Uri。调用方需要将读取权限随 Intent 一起授予 BigBang；不要传递仅限自身进程读取的 `file://` Uri。

```kotlin
val imageUri: Uri = /* 调用方提供的 content:// 图片 */

val intent = Intent("com.cashewteam.novatext.android.action.BIGBANG_OCR").apply {
    setPackage("com.cashewteam.novatext.android")
    type = "image/*"
    putExtra("ocr_image_uri", imageUri.toString())
    // 图片内触点，也是后续 BigBang 展开动画的锚点
    putExtra("boom_startx", 540)
    putExtra("boom_starty", 1200)
    // 可选：调用方包名，仅用于调试信息和后续上下文
    putExtra("caller_pkg", packageName)
    clipData = ClipData.newRawUri("bigbang_ocr_image", imageUri)
    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
}

if (intent.resolveActivity(packageManager) != null) {
    startActivity(intent)
}
```

### OCR 行为

- `ocr_image_uri`、`boom_startx`、`boom_starty` 是此接口必填参数
- 触点坐标使用图片所在屏幕的像素坐标；未传坐标时会退回屏幕中心，但无法保证最近文本选择和动画位置正确
- 此入口始终先显示范围选择 UI，不走悬浮球的自动最近段落 OCR
- Uri 无法读取、图片解码失败或没有识别到文本时，OCR 页会结束，不会打开空白 BigBang
- 识别语言只影响当前 OCR 会话；不会修改用户在 BigBang 设置中保存的默认语言

## 辅助模式调用

BigBang 注册了 `com.cashewteam.novatext.android.action.BIGBANG_ACCESSIBILITY`。调用方传入触点和自己的包名后，BigBang 会使用已启用的无障碍服务读取该应用的文本树，并按触点选择最近文本段落进入 BigBang。

```kotlin
val intent = Intent("com.cashewteam.novatext.android.action.BIGBANG_ACCESSIBILITY").apply {
    setPackage("com.cashewteam.novatext.android")
    // 必填：需要提取文本的宿主应用包名
    putExtra("caller_pkg", packageName)
    // 必填：用户在宿主应用中的触点，单位为屏幕像素
    putExtra("boom_startx", 540)
    putExtra("boom_starty", 1200)
}

if (intent.resolveActivity(packageManager) != null) {
    startActivity(intent)
}
```

### 无障碍模式 + 图片输入

无障碍模式可同时传入 `ocr_image_uri`。无障碍抓文仍是第一优先级；只有抓文失败且显式开启 OCR 回退时，才会使用传入图片在触点附近 OCR。这适合调用方已经持有截图、但希望优先复用原生文本树的场景。

```kotlin
val imageUri: Uri = /* 调用方可读取的 content:// 图片 */

val intent = Intent("com.cashewteam.novatext.android.action.BIGBANG_ACCESSIBILITY").apply {
    setPackage("com.cashewteam.novatext.android")
    putExtra("caller_pkg", packageName)
    putExtra("boom_startx", 540)
    putExtra("boom_starty", 1200)
    putExtra("ocr_image_uri", imageUri.toString())
    putExtra("extra_allow_accessibility_ocr_fallback", true)
    clipData = ClipData.newRawUri("bigbang_accessibility_image", imageUri)
    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
}
startActivity(intent)
```

- 图片必须为可授予 Nova Text 读取权限的 `content://` Uri
- 不传图片时，Nova Text 会按自身默认行为静默截图，作为 OCR 回退图源
- 不传 `extra_allow_accessibility_ocr_fallback` 或传 `false` 时，无障碍抓文失败即结束，不会 OCR

### 辅助模式行为与限制

- `caller_pkg`、`boom_startx`、`boom_starty` 是此接口必填参数。`caller_pkg` 必须是发起调用的宿主应用包名，不能填 BigBang 包名
- 用户必须先在系统设置中启用 Nova Text 无障碍服务；该接口不会代替用户申请或开启无障碍权限
- 无障碍服务会读取 `caller_pkg` 对应的活动窗口文本，并按触点选择最近段落；成功后会保留相邻段落，支持“炸了又炸”
- 无障碍窗口没有可访问文本时，本接口直接结束，不会自动切换到 OCR，避免在第三方调用中发生未预期的截图识别
- 调用前应确保宿主页面已经稳定显示；在页面切换、动画或窗口尚未获得无障碍焦点时调用，可能没有可读取节点

## 完整默认分流调用

BigBang 注册了 `com.cashewteam.novatext.android.action.BIGBANG_CAPTURE`。它复用悬浮球当前默认的完整流程，调用方提供自己的包名与触点，Nova Text 将按照该包名执行 OCR 白名单判定。

```kotlin
val intent = Intent("com.cashewteam.novatext.android.action.BIGBANG_CAPTURE").apply {
    setPackage("com.cashewteam.novatext.android")
    putExtra("caller_pkg", packageName)
    putExtra("boom_startx", 540)
    putExtra("boom_starty", 1200)
}

if (intent.resolveActivity(packageManager) != null) {
    startActivity(intent)
}
```

### 完整分流行为

- `caller_pkg`、`boom_startx`、`boom_starty` 均为必填；`caller_pkg` 用于白名单判定，必须是实际承载内容的宿主包名
- 白名单命中：Nova Text 截图后，对触点附近段落直接 OCR 并进入 BigBang
- 白名单未命中：Nova Text 先静默缓存截图，再从无障碍文本树按触点抓文；无文本时自动使用该缓存截图 OCR 回退
- 需要用户已开启 Nova Text 无障碍服务；该服务同时负责屏幕截图与文本树读取
- 此接口不接受调用方图片。调用方已持有图片时，使用上面的“无障碍模式 + 图片输入”或“图片 OCR 调用”接口

## 显式 Intent 调用

直接指定包名和类名，最直接但耦合度高（类名可能变化）：

```kotlin
val intent = Intent().apply {
    setClassName(
        "com.cashewteam.novatext.android",
        "com.cashewteam.novatext.android.BoomActivity"
    )
    putExtra(Intent.EXTRA_TEXT, "要分解的文本内容")
    putExtra("boom_startx", 540)
    putExtra("boom_starty", 1200)
    putExtra("extra_adjacent_text_before", "前一段落...")
    putExtra("extra_adjacent_text_after", "后一段落...")
}
startActivity(intent)
```

## 标准分享 Intent

通过 `ACTION_SEND` 走系统分享选择器，用户自行选择 BigBang：

```kotlin
val intent = Intent(Intent.ACTION_SEND).apply {
    type = "text/plain"
    putExtra(Intent.EXTRA_TEXT, "要分解的文本内容")
    putExtra("boom_startx", 540)
    putExtra("boom_starty", 1200)
    putExtra("extra_adjacent_text_before", "前一段落...")
    putExtra("extra_adjacent_text_after", "后一段落...")
}
startActivity(Intent.createChooser(intent, "分享到 BigBang"))
```

## Extra 键值说明

| Extra Key | 类型 | 必填 | 说明 |
|---|---|---|---|
| `Intent.EXTRA_TEXT` | String | 是 | 要分解的主文本 |
| `extra_selected_char_index` | int | 否 | 用户选中的字符在主文本中的索引（0-based），传入后自动选中该字符所在的分词 |
| `extra_adjacent_text_before` | String | 否 | 前一段落文本，支持多行（`\n` 分隔），每行一次"炸了又炸"上滑加载 |
| `extra_adjacent_text_after` | String | 否 | 后一段落文本，支持多行（`\n` 分隔），每行一次"炸了又炸"下滑加载 |
| `boom_startx` | int | 否 | BigBang 面板展开动画的 X 锚点 |
| `boom_starty` | int | 否 | BigBang 面板展开动画的 Y 锚点 |
| `extra_enable_adjacent_session` | boolean | 否 | 保留内部会话状态（内部使用，第三方无需设置） |
| `ocr_image_uri` | String | OCR 接口是 | 传入图片的 `content://` Uri；需要配合 `FLAG_GRANT_READ_URI_PERMISSION` 和 `clipData` 授予读取权限 |
| `caller_pkg` | String | 辅助模式接口是 | 需要提取无障碍文本的宿主包名；OCR 接口可选 |
| `extra_allow_accessibility_ocr_fallback` | boolean | 否 | 无障碍抓文失败时，允许使用传入图片或静默缓存截图 OCR 回退 |

## 行为说明

- 当 `extra_adjacent_text_before` 或 `extra_adjacent_text_after` 至少有一个非空时，BigBang 自动启用"炸了又炸"功能
- 当 `extra_selected_char_index` ≥ 0 时，BigBang 在分词完成后自动选中该字符所在的分词，同时滚动到该分词所在行
- `extra_selected_char_index` 为 0-based 字符索引，基于 `Intent.EXTRA_TEXT` 的原始文本计算
- 相邻文本按 `\n` 换行符逐行拆分为独立段落，每次上滑/下滑加载一行（短行可能合并为一次加载，与内部无障碍抓取行为一致）
- 相邻文本通过 `TextSessionCoordinator.replaceSession()` 注入，source 标记为 `"external"`
- 用户在 BigBang 界面上滑/下滑时，BoomChipPage 的拖拽手势触发 `peekAdjacentText()` → `loadAdjacent()`，与内部无障碍抓取的"炸了又炸"行为一致
- 如果不传相邻文本 extras，"炸了又炸"功能不可用（与之前行为一致）
- 第三方 OCR 与辅助模式接口使用稳定的自定义 Action；不要直接启动 `OcrLaunchActivity`、`BoomOcrActivity` 等实现类名
