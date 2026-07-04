# AGENTS.md

本文件定义 Nova Text 仓库内 AI 代理的项目级边界。优先级低于系统 / 开发者消息，高于普通仓库习惯。

## 项目目标

- 这是 Smartisan BigBang 的 Android 原生迁移与现代化项目
- 当前阶段目标是补主链路、补 feature、补兼容性
- 不是一次性重写，不是大规模架构翻修

## 代码边界

### 1. 保留双栈结构

- `src/com/smartisanos/textboom/` 是当前 BigBang 内核与部分 OCR 控件的事实来源
- `app/src/main/kotlin/com/smartisanos/textboom/` 负责现代 Activity、Compose 壳、service、launcher、OCR 编排
- 不要为了“纯 Kotlin / 纯 Compose”目标，主动重写已经稳定可用的 legacy BigBang 内核

### 2. 启动链路统一

- 文本进入 BigBang：优先走 `BoomActivityLauncher`
- OCR 进入范围选择页：优先走 `BoomOcrLauncher`
- 不要在新代码里直接从 service 或任意页面随手 `startActivity(BoomActivity)` / `startActivity(BoomOcrActivity)`
- 悬浮球与后台场景需要复用现有代理页 `OcrLaunchActivity` / `OverlayActivity`
- 悬浮球主链路统一时序：
  - 先隐藏悬浮球
  - 再截图
  - 再显示外部 `anim_loop` overlay
  - 最后进入无障碍抓文或 OCR
- 悬浮球白名单 OCR 由 `BigBangCaptureDispatcher` 先截图写入内存缓存，再启动 `OcrLaunchActivity` 做 OCR；不要在代理页内再重复首张截图
- 悬浮球无障碍文本链路优先在 `BigBangCaptureDispatcher` 内完成静默截图缓存和抓文，再走 `BoomActivityLauncher.openText(...)`
- 如果外层服务已经显示 loop 动画，继续通过 `EXTRA_EXTERNAL_LAUNCH_LOOP` 复用，不要在 `OcrLaunchActivity` 内再播第二套 loop

### 3. 配置统一

- 新增持久化配置统一放到 `BigBangSettings`
- 不要新增散落的 `SharedPreferences` 读取点
- 设置页展示状态优先复用已有状态源，不额外轮询

### 4. OCR 边界

- OCR 引擎统一收口在 `MlKitOcrEngine`
- 图片调试 / 分享入口保留范围选择页
- 悬浮球白名单 OCR 走直接识别链路时，优先在 `MlKitOcrEngine` 内补最近文本块 / 段落逻辑
- 悬浮球白名单 OCR 的截图由 `BigBangCaptureDispatcher` 通过 `AccessibilityScreenshotCapture` 发起；截图成功后启动 `OcrLaunchActivity` 打开 BigBang 启动门槛，避免 OCR 成功但窗口不拉起
- 无障碍文本链路的手动重进 OCR 复用静默缓存截图，不再为这条路径单独造第二套图源
- OCR 图片源统一走 `ManualOcrSourceStore`
  - 只保留一个活动 token
  - 优先复用内存 `cachedBitmap`
  - 不再把主链路截图缓存写回磁盘
- 不要为同一类 OCR 场景再造第二套解码、裁切、最近块选择实现

### 5. 无障碍与截图边界

- 文本提取主入口统一走 `BigBangCaptureDispatcher`
- 无障碍文本抓取逻辑在 `domain/capture/`
- 无障碍截图逻辑在 `AccessibilityScreenshotCapture`
- 前台应用识别统一依赖无障碍活跃窗口和最近事件缓存，不再改回使用情况访问权限方案
- Android 11+ 走 `AccessibilityService.takeScreenshot()`
- Android 7-10 走 Shizuku 截图回退；不要把这段再改回 MediaProjection 主路径
- 不要把分流判断散落到 `FloatingBallService`、页面和 helper 多处复制

### 6. 动画与悬浮球可见性边界

- 悬浮球触发后的 loop 动画统一用外部 `anim_loop` overlay，不再新增分支动画实现
- loop 动画坐标始终跟随悬浮球实际采样点 / OCR 触点，不要在不同入口各自补坐标偏移
- `FloatingBallService.notifyBigBangShellShown()` 是启动成功收口点：
  - 负责关闭 loop 动画
  - 负责解除 launch suppression
- 搜索页打开时允许悬浮球继续显示；不要再把搜索页并入“统一隐藏悬浮球”的黑名单
- BigBang / OCR / 代理页在前台时继续压住悬浮球，避免遮挡和误触

### 7. 文档边界

- `README.md`：产品总览、快速使用、文档索引
- `docs/development-plan.md`：当前状态和路线图
- `docs/architecture.md`：模块边界和启动流程
- `docs/api.md`：组件契约与内部 API
- 更新其中一份文档时，避免把其它文档的职责内容复制进去

## 修改原则

- 先读真实代码，再改文档或实现
- Bug 修复优先找共享根因，不补路径特判
- 不要加未被请求的新抽象、新层级或“以后可能用到”的开关
- 不要新增掩盖真实问题的兜底策略
- 不要为了修链路问题把截图、动画、图源缓存拆成每个入口各自维护的一套逻辑
- 旧代码归档优先放 `archive/legacy-ui/`，不要混回主链路

## 验证要求

- 改 Kotlin / Java / Manifest / 资源后，至少运行：
  - `bash ./gradlew assembleDebug`
- 只改文档时，不改版本号
- 中大型新功能：按 `X.Y.Z` 里的 `Y + 1`
- Bug 修复和小功能更新：按 `X.Y.Z` 里的 `Z + 1`

## 禁止事项

- 未经明确需求，不要：
  - 全量 Kotlin 化 legacy 内核
  - 全量 Compose 重写 BigBang 词块布局
  - 新建平行启动链路
  - 引入新的架构框架或状态管理框架
