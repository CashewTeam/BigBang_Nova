# 第三方应用调用

## 调用方式总览

| 方式 | 是否弹选择器 | 推荐度 | 说明 |
|---|---|---|---|
| 自定义 Action | 否 | 推荐 | 直接打开 BigBang，不依赖类名 |
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

## 行为说明

- 当 `extra_adjacent_text_before` 或 `extra_adjacent_text_after` 至少有一个非空时，BigBang 自动启用"炸了又炸"功能
- 当 `extra_selected_char_index` ≥ 0 时，BigBang 在分词完成后自动选中该字符所在的分词，同时滚动到该分词所在行
- `extra_selected_char_index` 为 0-based 字符索引，基于 `Intent.EXTRA_TEXT` 的原始文本计算
- 相邻文本按 `\n` 换行符逐行拆分为独立段落，每次上滑/下滑加载一行（短行可能合并为一次加载，与内部无障碍抓取行为一致）
- 相邻文本通过 `TextSessionCoordinator.replaceSession()` 注入，source 标记为 `"external"`
- 用户在 BigBang 界面上滑/下滑时，BoomChipPage 的拖拽手势触发 `peekAdjacentText()` → `loadAdjacent()`，与内部无障碍抓取的"炸了又炸"行为一致
- 如果不传相邻文本 extras，"炸了又炸"功能不可用（与之前行为一致）
