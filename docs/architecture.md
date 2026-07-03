# Nova Text 架构文档

## 架构目标

项目当前采用的是“外层现代化，内核渐进替换”的路线：

- Compose 负责设置页、搜索页、OCR 页和 BigBang 外层浮层壳
- legacy Java 继续负责 BigBang 词块布局、选择、多选和部分原版交互
- Kotlin service / launcher / dispatcher 负责悬浮球、无障碍、OCR、启动编排
- 本地 `cppjieba` 和 ML Kit OCR 负责两条本地识别能力

目标不是一次性重写，而是在不破坏现有主链路的前提下逐步收口。

## 模块边界

### 1. `src/com/smartisanos/textboom/`

职责：

- legacy BigBang 内核
- 词块布局
- 选择、多选、搜索 / 分享 / 复制动作
- “炸了又炸”拖拽与段落追加的核心交互
- OCR 框选控件 `OcrSelectionView`
- 应用内设置存储 `BigBangSettings`

约束：

- 这里仍是 BigBang 交互语义的主事实来源
- 没有明确收益时，不要把现成可用的选择逻辑平移重写到 Compose

### 2. `app/src/main/kotlin/com/smartisanos/textboom/`

职责：

- 现代 Activity 壳
- Compose 页面和浮层外壳
- OCR 启动代理与识别编排
- Search Overlay 容器

关键类：

- `TextBoomSettingsActivity`
- `BoomActivity`
- `BoomSearchOverlayActivity`
- `BoomOcrActivity`
- `OcrLaunchActivity`
- `OverlayActivity`
- `OverlayPanelUi`
- `ManualOcrSourceStore`
- `MlKitOcrEngine`

### 3. `app/src/main/kotlin/com/smartisanos/textboom/service/`

职责：

- 悬浮球前台服务
- 无障碍服务
- 启动分流
- OCR / BigBang 的统一 launcher

关键类：

- `FloatingBallService`
- `NovaTextAccessibilityService`
- `BigBangCaptureDispatcher`
- `BoomActivityLauncher`
- `BoomOcrLauncher`
- `AccessibilityScreenshotCapture`
- `ForegroundAppResolver`

边界：

- 这里负责“决定走哪条链路”
- 这里不负责实现 BigBang 选词逻辑本身
- `ForegroundAppResolver` 只使用无障碍活跃窗口和最近事件缓存；无法解析包名时由分流层直接走 OCR

### 4. `app/src/main/kotlin/com/smartisanos/textboom/domain/capture/`

职责：

- 无障碍文本提取会话
- 最近段落窗口
- 上下文缓存与上一段 / 下一段扩展基础

关键类：

- `AccessibilityTextSession`
- `CaptureContracts`

边界：

- 这里只负责文本块和段落窗口语义
- 不负责 UI、Activity 拉起和 OCR
- 可点击 / 可聚焦节点如果通过 `contentDescription` 暴露更完整的卡片摘要，应作为优先文本块保留
- 已被这类完整卡片覆盖的子文本块不再参与最近块选择，避免播放量、时长等碎片抢中
- 纯数字、播放量等低信息量统计文本在最近块评分中降权
- “炸了又炸”拉取相邻段落时，如果相邻段少于 25 个非空白字符，会继续同方向累计；遇到长段、边界或 3 段短文本后一次性追加

### 5. `app/src/main/cpp/`

职责：

- `cppjieba` JNI
- 本地分词

边界：

- 只提供分词能力
- 不承担 UI 状态、页面逻辑或启动编排

## 当前主启动流程

### A. 设置页调试预览

`TextBoomSettingsActivity`
-> `BoomActivityLauncher.openText(...)`
-> `OcrLaunchActivity`
-> `OverlayActivity`
-> `BoomActivity`

说明：

- 这条链路不依赖悬浮球
- 主要用于检查分词、词块布局和 BigBang 操作栏

### B. 悬浮球无障碍文本链路

`FloatingBallService`
-> `BigBangCaptureDispatcher.captureAt(...)`
-> `BoomActivityLauncher.launchCapture(...)`
-> `OcrLaunchActivity`
-> `TextSessionCoordinator.runAccessibilityFirst(...)`
-> `OverlayActivity`
-> `BoomActivity`

说明：

- 用于 OCR 白名单外的默认文本提取路径
- 启动动画和 BigBang 入场动画都挂在这条统一链路上

### C. 悬浮球白名单 OCR 链路

`FloatingBallService`
-> `BigBangCaptureDispatcher.captureAt(...)`
-> `AccessibilityScreenshotCapture.captureToOcr(...)`
-> `ManualOcrSourceStore`
-> `BoomOcrLauncher.launchCapture(...)`
-> `OcrLaunchActivity`
-> `MlKitOcrEngine.recognize(...)`
-> 段落级结果合并
-> 取最近段落
-> `OverlayActivity`
-> `BoomActivity`

说明：

- 当前不会进入范围选择页
- 这条链路先在 `BigBangCaptureDispatcher` 截图并写入内存缓存，再启动 `OcrLaunchActivity`
- `OcrLaunchActivity` 只负责启动动画、读取缓存图做 OCR，并打开 BigBang 启动门槛
- 最近文本选择统一收口在 `MlKitOcrEngine`
- OCR 结果先按 ML Kit `TextBlock` 取段落
- 自带多行文本的 `TextBlock` 清洗换行后直接输出，不再参与后续合并
- 只有剩余单行 `TextBlock` 再按近似高度做横向合并，不使用左右距离阈值
- 同一段落输出不保留内部换行符；不同段落之间才保留段落分隔

### D. 图片分享 / 设置页图片调试 OCR 链路

图片输入
-> `BoomOcrLauncher.open(...)`
-> `BoomOcrActivity`
-> 范围选择
-> `MlKitOcrEngine.recognize(...)`
-> `BoomActivityLauncher.openText(...)`
-> `OcrLaunchActivity`
-> `OverlayActivity`
-> `BoomActivity`

说明：

- 这条链路保留范围选择页
- 主要用于图片调试、系统分享和手动框选场景

### E. BigBang 内重进 OCR / 临时语言切换

`BoomActivity`
-> 读取 `ManualOcrSourceStore`
-> 左下角 OCR 按钮：`BoomOcrLauncher.open(...)`
-> `BoomOcrActivity`
-> 范围选择
-> `MlKitOcrEngine.recognize(...)`
-> `BoomActivityLauncher.openText(...)`

或：

`BoomActivity`
-> 右下角语言按钮
-> `BoomOcrLauncher.replayWithLanguage(...)`
-> `OcrLaunchActivity`
-> 复用已有图片源 / 最近段落规则
-> `BoomActivityLauncher.openText(...)`

说明：

- 手动图片 OCR 会复用上一次框选范围重跑
- 悬浮球白名单 OCR 会复用原截图和最近段落提取规则重跑
- 语言切换只影响当前 OCR 会话，不修改设置页默认 OCR 语言

## 页面职责边界

### `TextBoomSettingsActivity`

- 应用唯一设置入口
- 负责权限状态展示、悬浮球控制、搜索源配置、OCR 语言和白名单配置
- 承担开发调试入口

### `BoomActivity`

- 当前 BigBang 真实承载页
- Compose 外层只负责浮层外观和入场动画
- 词块内容仍交给 `BoomChipPage`
- 外壳底栏负责重新 OCR 与 OCR 临时语言切换入口

### `BoomSearchOverlayActivity`

- 搜索 / 词典 / 百科浮层页
- WebView 与底部站点切换都在这里收口
- 浏览器操作栏已补前进和刷新

### `BoomOcrActivity`

- 范围选择 OCR 专用页
- 只服务于“需要手动框选”的 OCR 场景

### `OcrLaunchActivity`

- 通用启动代理页
- 负责炸开动画、无障碍文本抓取、白名单 OCR 的缓存图直接识别
- 白名单 OCR 模式下使用 dispatcher 已缓存的截图；识别成功后打开 BigBang 启动门槛

## 配置边界

统一配置入口只有一个：

- `BigBangSettings`

约束：

- 新的持久化配置优先加到 `BigBangSettings`
- 不再新增散落的 `SharedPreferences` key 读取点

## 当前迁移边界

允许继续保留：

- legacy Java BigBang 内核
- `archive/legacy-ui/` 历史代码
- `sourceSets` 兼容 legacy `src/` / `res/`

不建议随手做：

- 把 BigBang 内核整体重写成 Compose
- 引入新的架构层包裹现有 dispatcher / launcher
- 新开一套平行启动链路绕过现有 launcher

当前更重要的是保持启动链路、OCR 链路和 BigBang 内核只有一套真实主路径。
