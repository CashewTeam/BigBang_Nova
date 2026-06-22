# BigBang Nova

**BigBang Nova** 是经典 Smartisan OS「大爆炸」功能的 Android 原生迁移项目。

目标是将屏幕上的任意文本「炸开」为独立的词芯片（word chips），支持滑动多选后进行搜索、查词典、复制、分享等操作，并逐步替换 Smartisan OS 私有依赖，迁移到标准 Android 能力。

## 当前状态

当前仓库**不是** README 下方“目标架构”所描述的完成态，现状如下：

- 代码主体仍是老式 Android Views + Java 工程
- 已补入 Gradle 构建脚手架，并已在本机通过 `./gradlew assembleDebug`
- 设置页已加入预制文本调试和 BigBang 页面预览入口
- 分词主路径仍依赖远程 HTTP 接口，离线本地分词留在后续阶段
- OCR 入口已在 P0 暂时降级为不可用提示，旧 CamScanner 代码仍保留在源码骨架中
- Smartisan 私有类、私有设置项、私有动画资源仍大量存在

因此，本仓库目前更适合被理解为：

- 一个可用于迁移的 Smartisan 旧实现基线
- 一个正在进行中的 Android 标准化改造项目

## 特性

- **文本炸词**：将选中文本分词为独立词块，配合爆炸动画展示
- **滑动多选**：手指滑动即可精确多选词块，支持行内/跨行选取
- **本地分词**：计划替换为 [cppjieba](https://github.com/yanyiwu/cppjieba) 离线中文分词引擎
- **本地 OCR**：计划替换为开源 OCR 引擎（待实现）
- **悬浮球触发**：计划复用 NovaText 的悬浮球能力
- **无障碍模式**：计划通过 AccessibilityService 直接从界面提取文本
- **多引擎搜索与词典**：支持多种搜索引擎和在线词典
- **现代 UI**：后续逐步迁移到 Kotlin + Compose

## 与原始版本的区别

| 特性 | 原始 BigBang (Smartisan OS) | BigBang Nova |
|------|---------------------------|--------------|
| 目标平台 | Smartisan OS 定制 ROM | Android 10+ 通用设备 |
| 构建系统 | Android.mk (AOSP) | 迁移中，目标为 Gradle (AGP) |
| 分词引擎 | 远程 HTTP API | 迁移中，目标为本地 cppjieba (C++ JNI) |
| OCR 引擎 | CamScanner SDK (商业) | 迁移中，目标为本地开源 OCR 引擎 |
| UI 框架 | Android Views (Java) | 迁移中，目标为 Jetpack Compose (Kotlin) |
| 悬浮球 | 系统级 Sidebar 集成 | 迁移中，目标为独立悬浮窗 Service |
| 文本提取 | 仅限 Smartisan 文本框 | 迁移中，目标为无障碍服务通用提取 |
| 隐私 | 文本上传至服务器 | 目标为全离线，数据不出设备 |

## 目标架构

```
┌──────────────────────────────────────────────────────┐
│                  UI Layer (Compose)                    │
│  BigBangScreen / SettingsScreen / SearchSheet          │
├──────────────────────────────────────────────────────┤
│              ViewModel Layer (Kotlin)                  │
│  BigBangViewModel / SettingsViewModel                  │
├──────────────────────────────────────────────────────┤
│               Domain Layer (Kotlin)                    │
│  TextTokenizer / TextCaptureEngine / ActionHandler     │
├──────────────────────┬───────────────────────────────┤
│  Native Layer (JNI)  │   System Services              │
│  cppjieba (C++)      │   FloatingBallService          │
│  OCR Engine (C++)    │   AccessibilityService         │
│                      │   MediaProjection              │
└──────────────────────┴───────────────────────────────┘
```

上图描述的是**目标架构**，不是当前仓库的实际目录和实现状态。

## 当前仓库结构

```text
BigBang_Nova/
├── app/                               # Gradle 应用模块
├── src/com/smartisanos/textboom/      # 旧版核心业务逻辑（Java）
├── src/se/emilsjolander/              # 内嵌 StickyListHeaders 库
├── src/smartisanos/                   # 兼容性 stub（过渡期）
├── res/                               # 旧版布局、资源、多语言
├── libs/                              # 旧版 jar 依赖（okhttp/okio/csopensdk 等）
├── AndroidManifest.xml
├── Android.mk
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
└── DEVELOPMENT_PLAN.md                # 当前迁移计划
```

## 目标技术栈

| 层 | 目标技术 |
|----|---------|
| 语言 | Kotlin + C++17 |
| UI | Jetpack Compose + Material 3 |
| 架构 | 分层架构 / ViewModel |
| 构建 | Gradle + AGP |
| 分词 | cppjieba (via JNI/CMake) |
| OCR | PaddleOCR Lite / Tesseract（待定） |
| 网络 | 仅保留搜索/词典访问所需能力 |
| 测试 | 单元测试 + UI 测试 + 集成验证 |

## 快速开始

### 当前可用入口

- 阅读迁移计划：[DEVELOPMENT_PLAN.md](./DEVELOPMENT_PLAN.md)
- 查看现有工程入口：`Android.mk`、`AndroidManifest.xml`
- 查看核心逻辑：`src/com/smartisanos/textboom/`

### 说明

当前仓库已补入 Gradle 构建脚手架，但还没有完成这台机器上的同步/构建验证，因此 README 先不写成“已验证可执行”的状态。

## 权限说明

| 权限 | 用途 |
|------|------|
| `SYSTEM_ALERT_WINDOW` | 目标态：悬浮球显示 |
| `BIND_ACCESSIBILITY_SERVICE` | 目标态：从界面提取文本 |
| `FOREGROUND_SERVICE` | 目标态：悬浮球后台运行 |
| `INTERNET` | 搜索 / 词典查询 |
| `READ/WRITE_EXTERNAL_STORAGE` | 旧版 OCR / 文件读写路径 |

## 开发计划

详见 [DEVELOPMENT_PLAN.md](./DEVELOPMENT_PLAN.md)

## 致谢

- [cppjieba](https://github.com/yanyiwu/cppjieba) - 结巴中文分词 C++ 版本
- [Smartisan OS](https://www.smartisan.com) - 原始 BigBang 功能创意来源
- [NovaText](https://github.com/con11/NovaText) - Flutter 先行版本（悬浮球/无障碍组件复用）

## License

Apache License 2.0
