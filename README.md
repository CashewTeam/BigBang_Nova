# BigBang Nova

**BigBang Nova** 是经典 Smartisan OS「大爆炸」功能的现代化 Android 原生重构版本。

将屏幕上的任意文本「炸开」为独立的词芯片（word chips），支持滑动多选后进行搜索、查词典、复制、分享等操作。完全本地化运行，无需网络请求，保护用户隐私。

## 特性

- **文本炸词**：将选中文本分词为独立词块，配合爆炸动画展示
- **滑动多选**：手指滑动即可精确多选词块，支持行内/跨行选取
- **本地分词**：基于 [cppjieba](https://github.com/yanyiwu/cppjieba) 的离线中文分词引擎，无需网络
- **本地 OCR**：离线 OCR 识别图片/截屏中的文字（待实现）
- **悬浮球触发**：通过悬浮球拖拽定位，一键提取目标区域文字
- **无障碍模式**：通过 AccessibilityService 直接从界面提取文本
- **多引擎搜索与词典**：支持多种搜索引擎和在线词典
- **Material You 设计**：基于 Jetpack Compose 的现代 UI，支持动态取色

## 与原始版本的区别

| 特性 | 原始 BigBang (Smartisan OS) | BigBang Nova |
|------|---------------------------|--------------|
| 目标平台 | Smartisan OS 定制 ROM | Android 10+ 通用设备 |
| 构建系统 | Android.mk (AOSP) | Gradle (AGP) |
| 分词引擎 | 远程 HTTP API | 本地 cppjieba (C++ JNI) |
| OCR 引擎 | CamScanner SDK (商业) | 本地开源 OCR 引擎 |
| UI 框架 | Android Views (Java) | Jetpack Compose (Kotlin) |
| 悬浮球 | 系统级 Sidebar 集成 | 独立悬浮窗 Service |
| 文本提取 | 仅限 Smartisan 文本框 | 无障碍服务通用提取 |
| 隐私 | 文本上传至服务器 | 全离线，数据不出设备 |

## 技术架构

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

## 技术栈

| 层 | 技术 |
|----|------|
| 语言 | Kotlin 2.x + C++17 |
| UI | Jetpack Compose + Material 3 |
| 架构 | MVVM + Flow |
| 构建 | Gradle 8.x + AGP 8.x |
| 分词 | cppjieba v5.6 (via JNI/CMake) |
| OCR | PaddleOCR Lite / Tesseract (待定) |
| 网络 | OkHttp 4.x (仅搜索/词典) |
| 测试 | JUnit 5 + MockK + Compose Testing |

## 项目结构

```
BigBang_Nova/
├── app/                          # 主应用模块
│   ├── src/main/
│   │   ├── java/com/novatext/bigbang/
│   │   │   ├── ui/               # Compose UI
│   │   │   ├── viewmodel/        # ViewModels
│   │   │   ├── domain/           # 业务逻辑
│   │   │   ├── data/             # 数据层
│   │   │   ├── service/          # Android Services
│   │   │   └── di/               # 依赖注入
│   │   └── res/                  # 资源文件
│   └── build.gradle.kts
├── jni/                          # C++ 原生模块
│   ├── cppjieba/                 # cppjieba 源码 + 头文件
│   ├── dict/                     # 分词词典
│   ├── jni_bridge.cpp            # JNI 桥接
│   └── CMakeLists.txt
├── build.gradle.kts              # 根构建配置
├── settings.gradle.kts
└── gradle.properties
```

## 快速开始

### 环境要求

- Android Studio Hedgehog (2023.1.1) 或更高
- Android SDK 34+
- NDK 26+ (用于 cppjieba 编译)
- Kotlin 2.0+

### 构建

```bash
# 克隆项目
git clone <repo-url>
cd BigBang_Nova

# 构建调试版
./gradlew assembleDebug

# 运行测试
./gradlew test

# 构建发布版
./gradlew assembleRelease
```

## 权限说明

| 权限 | 用途 |
|------|------|
| `SYSTEM_ALERT_WINDOW` | 悬浮球显示 |
| `BIND_ACCESSIBILITY_SERVICE` | 从界面提取文本 |
| `FOREGROUND_SERVICE` | 悬浮球后台运行 |
| `INTERNET` | 搜索 / 词典查询 |
| `READ/WRITE_EXTERNAL_STORAGE` | 词典文件 / OCR 截图 |

## 开发计划

详见 [DEVELOPMENT_PLAN.md](./DEVELOPMENT_PLAN.md)

## 任务清单

详见 [TODO.md](./TODO.md)

## 致谢

- [cppjieba](https://github.com/yanyiwu/cppjieba) - 结巴中文分词 C++ 版本
- [Smartisan OS](https://www.smartisan.com) - 原始 BigBang 功能创意来源
- [NovaText](https://github.com/con11/NovaText) - Flutter 先行版本（悬浮球/无障碍组件复用）

## License

Apache License 2.0
