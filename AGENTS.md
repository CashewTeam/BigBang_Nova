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
- 悬浮球白名单 OCR 必须先启动 `OcrLaunchActivity`，再由代理页内部调用无障碍截图和 OCR；不要改成“service 先截图，截图成功后再拉 Activity”，这会被部分系统后台启动限制截断

### 3. 配置统一

- 新增持久化配置统一放到 `BigBangSettings`
- 不要新增散落的 `SharedPreferences` 读取点
- 设置页展示状态优先复用已有状态源，不额外轮询

### 4. OCR 边界

- OCR 引擎统一收口在 `MlKitOcrEngine`
- 图片调试 / 分享入口保留范围选择页
- 悬浮球白名单 OCR 走直接识别链路时，优先在 `MlKitOcrEngine` 内补最近文本块 / 段落逻辑
- 悬浮球白名单 OCR 的截图由 `OcrLaunchActivity` 持有前台窗口后发起；截图成功后需要打开 BigBang 启动门槛，避免 OCR 成功但窗口不拉起
- 不要为同一类 OCR 场景再造第二套解码、裁切、最近块选择实现

### 5. 无障碍与截图边界

- 文本提取主入口统一走 `BigBangCaptureDispatcher`
- 无障碍文本抓取逻辑在 `domain/capture/`
- 无障碍截图逻辑在 `AccessibilityScreenshotCapture`
- 不要把分流判断散落到 `FloatingBallService`、页面和 helper 多处复制

### 6. 文档边界

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
