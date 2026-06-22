# 开发计划 (Development Plan)

## 总体思路

以现有 BigBang_Nova 项目为起点，**逐步替换** Smartisan OS 依赖为现代 Android 标准 API，将远程服务迁移为本地算法，同时从 NovaText 移植悬浮球/无障碍等已验证模块。不做大爆炸式重写，每一步都保持项目可编译可运行。

迁移顺序遵循两个原则：

1. 先打通当前主路径，再做技术栈升级。
2. 先收口系统耦合点，再替换 UI 和大模块。

## 基线代码状态

```
BigBang_Nova/                          ← 当前改造起点
├── src/com/smartisanos/textboom/      ← 20 个 Java 文件（核心业务逻辑）
├── src/se/emilsjolander/              ← 内嵌 StickyListHeaders 库
├── libs/                              ← csopensdk.jar, okhttp, okio
├── res/                               ← 布局/颜色/多语言资源
├── Android.mk                         ← AOSP 构建，→ 需迁移为 Gradle
└── tests/                             ← 空目录
```

**Smartisan OS 耦合点（需替换）：**

| 文件 | 依赖 | 替换方案 |
|------|------|---------|
| `BoomSearchActivity.java` | extends `smartisanos.app.SearchActivity` + `Settings.TEXT_BOOM_SEARCH_VALUE.*` | 改为标准 `AppCompatActivity` + 应用内搜索配置 |
| `SwipeSelectView.java` | `smartisanos.util.SidebarUtils` | 直接移除 sidebar 拖拽逻辑 |
| `OptionsActivity.java` | `smartisanos.widget.Title`, `IntentSmt` | 改为标准 Toolbar / Compose |
| `TextBoomSettingsActivity.java` | `SettingItem*`, `Title`, `IntentSmt`, `Settings.Global.*` | 改为应用内设置页 |
| `BoomOcrActivity.java` | `android.view.SurfaceControl` (hidden API) + CamScanner SDK | 截图与 OCR 解耦，改为服务/引擎驱动 |
| `LogUtils.java` | `android.os.SystemProperties` (hidden API) | `BuildConfig.DEBUG` |
| `Constant.java` | 远程分词地址、旧 OCR/字典常量 | 本地配置常量 / 删除废弃常量 |
| `BoomActionHandler.java` | `Settings.Global.TEXT_BOOM_SEARCH_METHOD` | 改为应用内搜索偏好读取 |
| `OptionsInfo.java` | `Settings.System/Secure/Global` 直写 | 改为应用内存储适配层 |

**额外基线问题（计划中需显式处理）：**

- 当前 README 描述的是目标态，不是仓库现状。
- 当前主路径 `BoomActivity` 仍然依赖远程分词接口，无网会直接退出。
- OCR 停止控制通过 `TextBoomCallProvider` 反向驱动 `BoomOcrActivity`，后续要决定保留或删除这条跨进程路径。

**NovaText 可移植模块：**

| 源文件 | 目标位置 | 功能 |
|--------|---------|------|
| `FloatingBallService.kt` | `service/` | 悬浮球拖拽定位 |
| `NovaTextAccessibilityService.kt` | `service/` | 无障碍文本提取 |
| `AccessibilityTextSession.kt` | `domain/capture/` | 无障碍树遍历提取引擎 |
| `CaptureContracts.kt` | `domain/capture/` | 文本捕获数据类 |
| `MediaProjectionStore.kt` | `data/` | 截屏权限持久化 |
| `BigBangPreferences.kt` | `data/` | SharedPreferences 工具 |
| `AppDebugPreferences.kt` | `data/` | 调试开关管理 |
| `NovaTextLogger.kt` | `util/` | 条件日志封装 |

---

## Phase 0：构建系统迁移 + 建立最小可编译基线（已完成）

**目标：让现有代码在标准 Android SDK 上通过 Gradle 编译，可安装运行，并为后续解耦留出稳定落点；同时让设置页承担基础开发调试入口职责。**

**阶段结论：**

- 构建链已验证通过，`./gradlew assembleDebug` 成功。
- 设置页已具备预制文本调试和 BigBang 页面预览。
- 当前仍保留 root 目录源码布局，通过 `sourceSets` 兼容接入；目录重排不再作为 P0 退出门槛。

### 0.1 Gradle 项目初始化
- [ ] 创建 `build.gradle.kts`（根）、`settings.gradle.kts`、`gradle.properties`
- [ ] 创建 `app/build.gradle.kts`，配置 `compileSdk 34 / minSdk 29 / targetSdk 34`
- [ ] 将 `libs/*.jar` 作为 `implementation files()` 引入（okhttp、okio 保留；csopensdk 标记待移除）
- [ ] 添加 Gradle Wrapper
- [ ] 添加 `gradle/libs.versions.toml` 管理依赖版本
- [ ] 迁移 `AndroidManifest.xml` 到 `app/src/main/`，更新权限声明
  - 移除 `READ_FRAME_BUFFER`（隐藏权限，替换为 MediaProjection）
  - 移除 `WRITE_SETTINGS`、`WRITE_SECURE_SETTINGS`（Smartisan 系统应用权限）
  - 添加 `SYSTEM_ALERT_WINDOW`（悬浮球）、`BIND_ACCESSIBILITY_SERVICE`

### 0.2 源代码目录重组
- [ ] 将 `src/` 下代码移动到 `app/src/main/java/`
- [ ] 将 `res/` 移动到 `app/src/main/res/`
- [ ] **保持现有包名** `com.smartisanos.textboom`（避免一次性全局改名，后续逐步迁移）
- [ ] 先保留 View 体系和现有页面结构，只清理必需的 Smartisan 主题资源引用（`@style/BaseStyle` 等）

### 0.3 建立应用内配置抽象层
- [ ] 新建 `BigBangSettings`（SharedPreferences 或 DataStore 二选一）
- [ ] 将以下系统耦合配置收口到 `BigBangSettings`
  - 搜索引擎默认值
  - 词典默认值
  - BigBang 开关
  - OCR 开关
  - 触发区域配置
  - 调试预制文本
  - 调试预览参数
- [ ] 定义应用内常量，替代 `Settings.TEXT_BOOM_SEARCH_VALUE.*` 和 `Settings.Global.TEXT_BOOM_*`

### 0.4 逐个文件解除 Smartisan 耦合
- [ ] **LogUtils.java**：`SystemProperties.get("ro.debuggable")` → `BuildConfig.DEBUG`
- [ ] **Constant.java**：删除远程分词 URL 与不再需要的旧平台常量
- [ ] **OptionsInfo.java**：去掉对 `Settings.System/Secure/Global` 的直接写入
- [ ] **SwipeSelectView.java**：移除 `SidebarUtils` 相关代码（sidebar 拖拽）
- [ ] **OptionsActivity.java**：`smartisanos.widget.Title`、`IntentSmt` → 标准 Toolbar / Activity 动画可先删
- [ ] **BoomSearchActivity.java**：`extends SearchActivity` → `extends AppCompatActivity`，先保留 WebView 搜索页
- [ ] **TextBoomSettingsActivity.java**：先改为标准 View/Preference 页面，读取 `BigBangSettings`
- [ ] **TextBoomSettingsActivity.java**：增加类似 Flutter 版的开发调试区
  - 预制文本调试入口：可选择或输入测试文本，直接拉起 `BoomActivity`
  - BigBang 页面预览入口：不依赖悬浮球/无障碍，直接预览炸词页面
- [ ] **BoomActionHandler.java**：搜索/词典默认值读取改接 `BigBangSettings`
- [ ] **BoomActivity.java**：支持调试预览模式输入，便于设置页直接打开预览
- [ ] **BoomOcrActivity.java**：Phase 0 只保证可编译；截图能力可临时关闭，不在这里引入新截图方案
- [ ] **TextBoomCallProvider.java**：确认是否有外部调用方；若无则删除，若有则记录替代方案到 Phase 3

### 0.5 验证
- [ ] `./gradlew assembleDebug` 通过
- [ ] 在 Android 10+ 真机上安装运行不崩溃
- [ ] 设置页、搜索页、主页面能进入，不因 Smartisan 类缺失直接崩溃
- [ ] 设置页可直接触发预制文本调试
- [ ] 设置页可直接打开 BigBang 页面预览，用于排查布局、分词、选中态问题

---

## Phase 1：本地分词最小闭环（预计 2 周）

**目标：先切掉远程分词依赖，让 BigBang 主流程在离线条件下可工作。**

### 1.1 cppjieba NDK 集成
- [ ] 创建 `app/src/main/cpp/` 目录结构
- [ ] 复制 cppjieba 头文件到 `app/src/main/cpp/cppjieba/`
- [ ] 创建 `CMakeLists.txt`，配置交叉编译（arm64-v8a, armeabi-v7a, x86_64）
- [ ] 编写 `jni_bridge.cpp`：
  - `init(dictPath)` — 加载词典
  - `cut(text)` — MixSegment 模式分词，直接返回 offsets 或词项列表
  - `destroy()` — 释放
- [ ] 编写 Kotlin 封装类 `CppJiebaTokenizer`

### 1.2 词典资源
- [ ] 将 cppjieba 词典放入 `app/src/main/assets/dict/`
- [ ] 创建 `DictManager`：首次启动解压到 `filesDir/dict/`
- [ ] 版本标记（`dict_version.txt`）

### 1.3 替换调用点
- [ ] **BoomActivity.java**：去掉“无网即退出”逻辑，改为直接走本地分词
- [ ] **BoomActivity.java**：HTTP POST 调用点 → `CppJiebaTokenizer.cut()`
- [ ] 保持 `BoomWordsLayout` 输入契约稳定，但不再保留网络 JSON 作为正式边界
- [ ] 删除分词相关 OkHttp / Okio 依赖和代码
- [ ] 删除 `SEGMENT_URL` 常量

### 1.4 测试
- [ ] JNI 函数单元测试（分词正确性）
- [ ] 性能基准：1000 字 < 100ms
- [ ] 边界测试：空文本、英文、emoji、超长文本

---

## Phase 2：移植 NovaText 悬浮球 + 无障碍文本输入（预计 2 周）

**目标：在离线分词已经可用的前提下，用悬浮球和无障碍建立新的文本捕获入口。**

### 2.1 移植悬浮球服务
- [ ] 从 NovaText 复制 `FloatingBallService.kt` → `app/src/main/java/com/smartisanos/textboom/service/`
- [ ] 移除 Flutter `EventChannel` / `NovaTextEventBridge` 依赖
- [ ] 使用明确的应用内触发通道
  - `Intent`
  - `BroadcastReceiver`
  - 或明确封装的进程内事件总线
- [ ] 适配当前项目的包名和资源引用
- [ ] 在 `AndroidManifest.xml` 注册 Service（`FOREGROUND_SERVICE` + `SYSTEM_ALERT_WINDOW`）
- [ ] 实现悬浮球 → 启动 BigBang 的流程

### 2.2 移植无障碍文本提取
- [ ] 复制 `NovaTextAccessibilityService.kt` → `service/`
- [ ] 复制 `AccessibilityTextSession.kt`（含 `TextSessionCoordinator`、`ParagraphWindow`）→ `domain/capture/`
- [ ] 复制 `CaptureContracts.kt` → `domain/capture/`
- [ ] 移除 Flutter 依赖引用
- [ ] 配置 `res/xml/accessibility_service_config.xml`
- [ ] 悬浮球拖拽释放后调用文本捕获流程
- [ ] 将提取的文本传入 `BoomActivity`

### 2.3 移植支持类
- [ ] 复制 `MediaProjectionStore.kt` → `data/`
- [ ] 复制 `BigBangPreferences.kt` → `data/`
- [ ] 复制 `NovaTextLogger.kt` → `util/`
- [ ] 统一包名引用

### 2.4 验证
- [ ] 悬浮球可显示、拖拽、释放
- [ ] 无障碍服务可提取屏幕文本
- [ ] 文本正确传入 BoomActivity 并完成本地分词展示

---

## Phase 3：本地 OCR 引擎替换 CamScanner SDK（预计 3 周）

**目标：用开源 OCR 引擎替换 CamScanner。**

### 3.1 OCR 引擎集成
- [ ] 评估 PaddleOCR Lite / Tesseract 5 在 Android 上的集成方式
- [ ] 集成选定引擎（NDK 编译或 AAR 引入）
- [ ] 实现 `OcrEngine` 接口

### 3.2 截图方案迁移
- [ ] 将“截图”从 `BoomOcrActivity` 中抽离为独立能力接口 `ScreenshotProvider`
- [ ] Android 11+：由 `AccessibilityService.takeScreenshot()` 所在服务侧实现截图
- [ ] Android 10：由 `MediaProjection` + `ImageReader` 实现截图
- [ ] Activity 只负责接收截图结果、展示状态和串联 OCR
- [ ] 截图 → Bitmap → OCR → 文本 → 分词

### 3.3 OCR 流程改造
- [ ] 重写 `BoomOcrActivity` 为 Kotlin（或保留 Java 但简化）
- [ ] 移除 CamScanner SDK（`csopensdk.jar`、`CSOpenAPI` 导入）
- [ ] 删除或替换 `TextBoomCallProvider` 这类仅为旧 OCR 流程服务的控制路径
- [ ] 连接 OCR 结果到 BigBang 显示管道

---

## Phase 4：UI 现代化改造（预计 3 周）

**目标：在核心功能闭环稳定后，再逐步用 Kotlin + Compose 替换旧 View UI。**

### 4.1 增量替换策略（逐页迁移，新旧共存）
- [ ] Step 1：设置页 → Compose（`TextBoomSettingsActivity` + `OptionsActivity` → `SettingsScreen`）
- [ ] Step 2：搜索/词典页 → Compose（`BoomSearchActivity` → `SearchScreen` / `SearchSheet`）
- [ ] Step 3：BigBang 主页面 → Compose（优先保留现有词布局/选择逻辑，替换外层 UI）
- [ ] Step 4：OCR 页面 → Compose

### 4.2 Compose 基础设施
- [ ] 添加 Compose BOM / Material 3 依赖
- [ ] 创建 `Theme.kt` / `Color.kt` / `Type.kt`
- [ ] 配置动态取色

### 4.3 保留的逻辑层
- [ ] `BoomWordsLayout.java`：词芯片排版计算逻辑，保持 Java 或转为 Kotlin util
- [ ] `BoomAnimator.java`：动画参数/插值计算，转为 Compose Animation 等价实现
- [ ] `BoomActionHandler.java`：操作逻辑，抽取为 ViewModel

### 4.4 验证
- [ ] 新 UI 功能完整度与原版一致
- [ ] 动画流畅度不降级

---

## Phase 5：清理与测试（预计 2 周）

### 5.1 死代码清理
- [ ] 删除 Smartisan 废弃资源（主题、控件样式）
- [ ] 删除 `libs/csopensdk.jar`
- [ ] 删除 OkHttp/Okio jar（如已不再使用）
- [ ] 清理无用权限、常量、import

### 5.2 测试补充
- [ ] 分词引擎单元测试
- [ ] ViewModel 单元测试
- [ ] Compose UI 测试（关键页面）
- [ ] 无障碍服务集成测试

### 5.3 兼容性
- [ ] Android 10 ~ 15 真机测试
- [ ] 主流厂商 ROM 适配验证

---

## 里程碑

| 里程碑 | 预计日期 | 交付物 |
|--------|---------|--------|
| M0 | Week 2 | Gradle 项目可编译运行，主要 Smartisan 编译阻塞已去除，设置页具备基础调试与预览入口 |
| M1 | Week 4 | 本地分词引擎运行，主页面离线可用 |
| M2 | Week 6 | 悬浮球 + 无障碍文本提取可工作 |
| M3 | Week 10 | 本地 OCR 运行，切掉 CamScanner SDK |
| M4 | Week 13 | Compose UI 主要页面完成 |
| M5 | Week 15 | 测试完成，v1.0 |

---

## 风险与应对

| 风险 | 影响 | 应对措施 |
|------|------|---------|
| Smartisan `SearchActivity` 父类功能太多，替换工作量大 | 中 | 先做最小替换（标准 Activity + WebView），后续迭代增强 |
| `SurfaceControl.screenshot()` 无标准 API 等价替换 | 中 | 不直接在 Activity 中硬替换，改为服务侧截图 + Activity 消费结果 |
| cppjieba NDK 编译兼容性问题 | 低 | 提前在 CI 覆盖多 ABI，并尽早在 Phase 1 验证 |
| 无障碍服务被系统杀死 | 中 | 前台 Service + 引导用户设电池白名单 |
| README/计划与仓库现状不一致导致执行偏差 | 中 | 文档明确区分“当前状态”和“目标状态”，里程碑只写可验收交付物 |
