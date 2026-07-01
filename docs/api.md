# Nova Text 接口与 API 文档

本文档只描述当前仓库中已经存在的内部契约，不描述未来目标接口。

## 1. Manifest 组件

### Activities

- `com.cashewteam.novatext.android.TextBoomSettingsActivity`
  - `exported=true`
  - Launcher Activity
- `com.cashewteam.novatext.android.OcrLaunchActivity`
  - 内部启动代理页
- `com.cashewteam.novatext.android.OverlayActivity`
  - 内部透明转发页
- `com.cashewteam.novatext.android.BoomActivity`
  - BigBang 承载页
- `com.cashewteam.novatext.android.BoomSearchOverlayActivity`
  - 搜索浮层页
- `com.cashewteam.novatext.android.BoomOcrActivity`
  - OCR 范围选择页
  - 也支持 `ACTION_SEND image/*`

### Services

- `com.cashewteam.novatext.android.service.FloatingBallService`
  - 前台悬浮球服务
- `com.cashewteam.novatext.android.service.NovaTextAccessibilityService`
  - 无障碍文本提取与截图服务

### Providers

- `com.cashewteam.novatext.android.TextBoomCallProvider`
  - authority: `com.cashewteam.novatext.android.call_method`
- `androidx.core.content.FileProvider`
  - authority: `${applicationId}.fileprovider`

## 2. 权限与系统能力

当前主链路涉及：

- `SYSTEM_ALERT_WINDOW`
- `BIND_ACCESSIBILITY_SERVICE`
- `FOREGROUND_SERVICE`
- `FOREGROUND_SERVICE_SPECIAL_USE`
- `POST_NOTIFICATIONS`

说明：

- 无障碍文本抓取与无障碍截图都依赖 `NovaTextAccessibilityService`
- 前台应用识别依赖无障碍活跃窗口和最近事件缓存，不再依赖使用情况访问权限
- 前台应用识别失败时，悬浮球链路会直接进入 OCR 截图识别

## 3. Activity Intent 契约

### 3.1 `BoomActivityLauncher.openText(...)`

调用方：

- 设置页调试入口
- OCR 识别结果
- 悬浮球无障碍文本链路
- 悬浮球白名单 OCR 链路

实际启动：

- `OcrLaunchActivity`

写入 extras：

- `Intent.EXTRA_TEXT`
- `boom_index = -1`
- `boom_startx`
- `boom_starty`
- `OcrLaunchActivity.EXTRA_CAPTURE_ACCESSIBILITY = false`
- `BoomActivity.EXTRA_DEBUG_PREVIEW_TEXT`（仅预览链路）
- `BoomActivity.EXTRA_MANUAL_OCR_SOURCE_TOKEN`（有可复用图片源时）

### 3.2 `BoomActivityLauncher.launchCapture(...)`

调用方：

- `BigBangCaptureDispatcher`

实际启动：

- `OcrLaunchActivity`

写入 extras：

- `boom_startx`
- `boom_starty`
- `OcrLaunchActivity.EXTRA_CAPTURE_ACCESSIBILITY = true`

说明：

- 手动重进 OCR 所需的 `manual_ocr_source_token` 不是这里直接传入
- 该 token 由 `OcrLaunchActivity` 在静默截图缓存成功后生成并继续透传

### 3.3 `BoomOcrLauncher.open(...)`

用途：

- 已有图片输入时进入 OCR

Activity 上下文：

- 直接启动 `BoomOcrActivity`

非 Activity 上下文：

- 先启动 `OcrLaunchActivity`

写入 extras：

- `BoomOcrActivity.EXTRA_OCR_IMAGE_URI`
- `boom_startx`
- `boom_starty`
- `boom_fullscreen`
- `boom_offsetx`
- `boom_offsety`
- `caller_pkg`
- `BoomActivity.EXTRA_MANUAL_OCR_SOURCE_TOKEN`

### 3.4 `BoomOcrLauncher.launchCapture(...)`

用途：

- 悬浮球白名单 OCR 链路的“先前台、再截图”入口
- 命中 OCR 白名单时只能用这个入口先启动 `OcrLaunchActivity`

实际启动：

- `OcrLaunchActivity`

写入 extras：

- `BoomOcrLauncher.EXTRA_CAPTURE_OCR_SCREENSHOT = true`
- `boom_startx`
- `boom_starty`
- `boom_fullscreen`
- `boom_offsetx`
- `boom_offsety`
- `caller_pkg`

约束：

- `BigBangCaptureDispatcher` 不直接截图后再启动 Activity
- `OcrLaunchActivity` 收到 `EXTRA_CAPTURE_OCR_SCREENSHOT = true` 后，在自身窗口内调用 `AccessibilityScreenshotCapture.captureToOcr(...)`
- OCR 成功拿到最近段落后，由 `OcrLaunchActivity` 继续拉起 `OverlayActivity -> BoomActivity`

### 3.5 `BoomOcrLauncher.replayWithLanguage(...)`

用途：

- BigBang 内 OCR 结果的临时语言切换重跑

实际启动：

- `OcrLaunchActivity`

写入 extras：

- `BoomActivity.EXTRA_MANUAL_OCR_SOURCE_TOKEN`
- `boom_startx`
- `boom_starty`
- `OcrLaunchActivity.EXTRA_REPLAY_OCR_MODE`
- `OcrLaunchActivity.EXTRA_REPLAY_MODE`

### 3.6 `OcrLaunchActivity` 内部开关

当前内部 extra：

- `EXTRA_CAPTURE_ACCESSIBILITY`
  - 含义：进入无障碍文本抓取链路
- `EXTRA_SKIP_LEGACY_FADE_IN`
  - 含义：BigBang 已走外层入场动画，内部 legacy fade-in 跳过
- `BoomOcrLauncher.EXTRA_CAPTURE_OCR_SCREENSHOT`
  - 含义：进入无障碍截图 + 全屏 OCR 链路
- `EXTRA_REPLAY_OCR_MODE`
  - 含义：本次重跑 OCR 使用的临时语言
- `EXTRA_REPLAY_MODE`
  - 含义：本次重跑 OCR 使用的复用方式
  - 当前值：
    - `nearest_paragraph`
    - `selection_rect`

## 4. OCR 输入契约

### `BoomOcrActivity`

支持两类输入：

1. `ACTION_SEND image/*`
   - 读取 `Intent.EXTRA_STREAM`
2. 内部 extra
   - `EXTRA_OCR_IMAGE_URI = "ocr_image_uri"`

可选辅助 extra：

- `boom_startx`
- `boom_starty`
- `boom_fullscreen`
- `boom_offsetx`
- `boom_offsety`
- `caller_pkg`

说明：

- `caller_pkg` 和 offset 用于截图后图像裁切修正
- 范围选择页只用于手动框选 OCR，不用于悬浮球白名单 OCR 直接识别链路
- BigBang 内左下角 OCR 按钮会重进这条范围选择链路

## 5. 悬浮球 Service 入口

类：

- `FloatingBallService`

公开 action：

- `ACTION_START = "com.cashewteam.novatext.android.action.START_FLOATING_BALL"`
- `ACTION_STOP = "com.cashewteam.novatext.android.action.STOP_FLOATING_BALL"`
- `ACTION_RESET_POSITION = "com.cashewteam.novatext.android.action.RESET_FLOATING_BALL"`

公开方法：

- `start(context)`
- `stop(context)`
- `resetPosition(context)`
- `refreshAppearance(context)`
- `isActive()`
- `getActiveStateFlow()`

内部截图辅助：

- `hideForScreenshot()`
- `restoreAfterScreenshot()`

说明：

- 悬浮球当前采用左右贴边胶囊样式
- 拖动松手后自动吸附左右边缘
- 横屏下仍保持贴边，不停在屏幕中间

## 6. Provider 契约

### `TextBoomCallProvider`

authority：

- `com.cashewteam.novatext.android.call_method`

method：

- `stop_ocr`

行为：

- 将 `BoomOcrActivity.sBoomCancel = true`
- 若 OCR Activity 存在，则回调其 `cancelOcr()`

说明：

- 这是 legacy 残留耦合，目前仍在使用
- 新链路不要绕过它再造一套 OCR 取消协议

## 7. 设置存储契约

统一入口：

- `src/com/smartisanos/textboom/data/BigBangSettings.java`

当前核心 key：

- `web_search_type`
- `dict_search_type`
- `wiki_search_type`
- `big_bang_enabled`
- `ocr_enabled`
- `trigger_area`
- `debug_preset_text`
- `debug_preview_text`
- `debug_skip_accessibility`
- `ocr_recognizer_mode`
- `ocr_whitelist_packages`
- `floating_ball_size_percent`
- `floating_ball_active_alpha_percent`
- `floating_ball_idle_alpha_percent`

OCR 语言枚举：

- `chinese`
- `japanese`
- `korean`
- `latin`

OCR 白名单默认值：

- `com.tencent.mm`
- `com.tencent.mobileqq`

## 8. 识别能力边界

### `AccessibilityTextSession`

当前职责：

- 从 `NovaTextAccessibilityService.rootInActiveWindow` 遍历无障碍节点
- 生成原始文本块、段落窗口和最近段落
- 缓存上一段 / 下一段，供“炸了又炸”使用

说明：

- 普通父容器如果只是重复子节点文本，会被过滤，避免重复文本块
- 可点击 / 可聚焦且 `contentDescription` 信息更完整的卡片节点会保留
- 被完整卡片节点覆盖的子文本块会从候选中移除，避免统计数字、时长、按钮碎片抢中
- 纯数字、播放量等低信息量统计文本会在最近块评分中降权
- `peekAdjacentText(...)` 与 `loadAdjacent(...)` 共用相邻段落批量规则
- 相邻段落少于 25 个非空白字符时继续同方向累计，遇到长段、边界或累计 3 段短文本后停止
- 多段短文本一次性用段落分隔输出，避免用户反复触发“炸了又炸”

### `ManualOcrSourceStore`

当前职责：

- 保存当前 BigBang 会话可复用的 OCR 图片源
- 为“重新 OCR”与“临时语言切换”提供统一图片输入

当前状态字段：

- `token`
- `imageUri`
- `touchX / touchY`
- `callerPackage`
- `fullscreen`
- `offsetX / offsetY`
- `sourceTag`
- `replayMode`
- `selectionRect`
- `ocrMode`

说明：

- 这是进程内临时状态，不做持久化
- 无图像来源的纯文本 BigBang 会话不会写入这里
- 临时语言切换只改当前 source 的 `ocrMode`，不写回默认设置

### `MlKitOcrEngine`

当前职责：

- `decodeBitmap(...)`
- `recognize(...)`
- `prepareBitmap(...)`
- `findNearestTextBlock(...)`
- `findParagraphs(...)`
- `buildParagraphText(...)`

说明：

- OCR 结构化输出统一在这里转换成 BigBang 可用文本
- 先按 ML Kit `TextBlock` 取原始段落块
- 原始 `TextBlock` 如果自带多行文本，清洗内部换行后直接作为最终段落输出，不再参与二次合并
- 只有单行 `TextBlock` 才进入近似高度横向合并，不设置左右距离阈值，避免同一行碎块因为间距大而拆散
- 同一段落输出必须压成一行，不保留内部换行符
- 多个段落的最终文本由 `buildParagraphText(...)` 用段落分隔连接
- 悬浮球白名单 OCR、图片调试 OCR、图片分享 OCR 都应复用这套段落合并规则

### `CppJiebaTokenizer`

当前职责：

- 本地分词
- 懒加载初始化
- 启动后后台预热

说明：

- BigBang 文本分词统一依赖它
- 设置页只读 `JiebaWarmUpTracker`，不要为了读状态直接初始化 tokenizer
