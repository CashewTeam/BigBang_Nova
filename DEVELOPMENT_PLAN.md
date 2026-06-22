# 开发计划 (Development Plan)

## 总体思路

以现有 BigBang_Nova 项目为起点，**逐步替换** Smartisan OS 依赖为现代 Android 标准 API，将远程服务迁移为本地算法，同时从 NovaText 移植悬浮球/无障碍等已验证模块。不做大爆炸式重写，每一步都保持项目可编译可运行。

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
| `BoomSearchActivity.java` | extends `smartisanos.app.SearchActivity` | 改为标准 `AppCompatActivity` + WebView |
| `SwipeSelectView.java` | `smartisanos.util.SidebarUtils` | 直接移除 sidebar 拖拽逻辑 |
| `OptionsActivity.java` | `smartisanos.widget.Title`, `IntentSmt` | 改为标准 Toolbar / Compose |
| `TextBoomSettingsActivity.java` | `SettingItem*`, `Title`, `IntentSmt` | 改为 Compose 设置页 |
| `BoomOcrActivity.java` | `android.view.SurfaceControl` (hidden API) | MediaProjection / AccessibilityService |
| `LogUtils.java` | `android.os.SystemProperties` (hidden API) | `BuildConfig.DEBUG` |
| `Constant.java` | `Settings.TEXT_BOOM_SEARCH_VALUE.*` (Smartisan Settings keys) | DataStore / SharedPreferences |

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

## Phase 0：构建系统迁移 + 移除 Smartisan 依赖（预计 2 周）

**目标：让现有代码在标准 Android SDK 上通过 Gradle 编译，可安装运行。**

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
- [ ] 清理 Smartisan 主题资源引用（`@style/BaseStyle` 等 → Material 主题）

### 0.3 逐个文件解除 Smartisan 耦合
- [ ] **LogUtils.java**：`SystemProperties.get("ro.debuggable")` → `BuildConfig.DEBUG`
- [ ] **Constant.java**：`Settings.TEXT_BOOM_SEARCH_VALUE.TYPE_*` → 自定义常量
- [ ] **OptionsInfo.java**：移除 `IntentSmt` 相关逻辑
- [ ] **SwipeSelectView.java**：移除 `SidebarUtils` 相关代码（sidebar 拖拽）
- [ ] **OptionsActivity.java**：`smartisanos.widget.Title` → Material Toolbar / 暂用标准 ActionBar
- [ ] **BoomSearchActivity.java**：`extends SearchActivity` → `extends AppCompatActivity`，自行实现搜索框 WebView
- [ ] **TextBoomSettingsActivity.java**：Smartisan 控件 → 标准 Android PreferenceFragment / 暂时简化
- [ ] **BoomOcrActivity.java**：`SurfaceControl.screenshot()` → 临时禁用截图功能，标记 TODO 等 Phase 3
- [ ] **TextBoomCallProvider.java**：评估是否仍需跨进程调用，可能可直接移除

### 0.4 验证
- [ ] `./gradlew assembleDebug` 通过
- [ ] 在 Android 10+ 真机上安装运行不崩溃
- [ ] 废弃远程 HTTP API 前，先标记 `@Deprecated` 保留调用路径（等 Phase 2 切换）

---

## Phase 1：移植 NovaText 悬浮球 + 无障碍文本输入（预计 2 周）

**目标：用悬浮球机制替换原有的 Smartisan 系统内触发方式，建立文本捕获新入口。**

### 1.1 移植悬浮球服务
- [ ] 从 NovaText 复制 `FloatingBallService.kt` → `app/src/main/java/com/smartisanos/textboom/service/`
- [ ] 移除 Flutter `EventChannel` / `NovaTextEventBridge` 依赖，改用：
  - 通过 `Intent` / `BroadcastReceiver` 通知主界面触发 BigBang
  - 或使用 `SharedFlow`（如引入 kotlinx.coroutines）
- [ ] 适配当前项目的包名和资源引用
- [ ] 在 `AndroidManifest.xml` 注册 Service（`FOREGROUND_SERVICE` + `SYSTEM_ALERT_WINDOW`）
- [ ] 实现悬浮球 → 启动 BigBang 的流程：
  - 拖拽释放 → 计算坐标 → 传递坐标和文本到 `BoomActivity`

### 1.2 移植无障碍文本提取
- [ ] 复制 `NovaTextAccessibilityService.kt` → `service/`
- [ ] 复制 `AccessibilityTextSession.kt`（含 `TextSessionCoordinator`、`ParagraphWindow`）→ `domain/capture/`
- [ ] 复制 `CaptureContracts.kt` → `domain/capture/`
- [ ] 移除 Flutter 依赖引用
- [ ] 配置 `res/xml/accessibility_service_config.xml`
- [ ] 悬浮球拖拽释放后调用 `AccessibilityTextSessionRunner.captureWindow(request)`
- [ ] 将提取的文本传入 `BoomActivity`（替代原有的 `Intent.EXTRA_TEXT` 被动接收）

### 1.3 移植支持类
- [ ] 复制 `MediaProjectionStore.kt` → `data/`
- [ ] 复制 `BigBangPreferences.kt` → `data/`
- [ ] 复制 `NovaTextLogger.kt` → `util/`
- [ ] 统一包名引用

### 1.4 验证
- [ ] 悬浮球可显示、拖拽、释放
- [ ] 无障碍服务可提取屏幕文本
- [ ] 文本正确传入 BoomActivity 进行分词展示

---

## Phase 2：本地分词引擎替换远程 API（预计 3 周）

**目标：用 cppjieba JNI 替换 OkHttp 远程分词请求。**

### 2.1 cppjieba NDK 集成
- [ ] 创建 `app/src/main/cpp/` 目录结构
- [ ] 复制 cppjieba 头文件到 `app/src/main/cpp/cppjieba/`
- [ ] 创建 `CMakeLists.txt`，配置交叉编译（arm64-v8a, armeabi-v7a, x86_64）
- [ ] 编写 `jni_bridge.cpp`：
  - `init(dictPath)` — 加载词典
  - `cut(text)` — MixSegment 模式分词，返回 `[{"word":"...","offset":n}, ...]`（JSON 格式，复用现有 `OkHttpClientManager` 的返回结构，减少上层改动）
  - `destroy()` — 释放
- [ ] 编写 Kotlin 封装类 `CppJiebaTokenizer`

### 2.2 词典资源
- [ ] 将 cppjieba 词典放入 `app/src/main/assets/dict/`
- [ ] 创建 `DictManager`：首次启动解压到 `filesDir/dict/`
- [ ] 版本标记（`dict_version.txt`）

### 2.3 替换调用点
- [ ] **OkHttpClientManager.java** / **BoomActivity.java** 中的 HTTP POST 调用点 → `CppJiebaTokenizer.cut()`
- [ ] 保持 `BoomWordsLayout` 的输入格式不变（`Word[]` 或 JSON 解析结果），只替换数据来源
- [ ] 删除 OkHttp / Okio 相关网络依赖和代码
- [ ] 删除 `SEGMENT_URL` 常量

### 2.4 测试
- [ ] JNI 函数单元测试（分词正确性）
- [ ] 性能基准：1000 字 < 100ms
- [ ] 边界测试：空文本、英文、emoji、超长文本

---

## Phase 3：本地 OCR 引擎替换 CamScanner SDK（预计 3 周）

**目标：用开源 OCR 引擎替换 CamScanner。**

### 3.1 OCR 引擎集成
- [ ] 评估 PaddleOCR Lite / Tesseract 5 在 Android 上的集成方式
- [ ] 集成选定引擎（NDK 编译或 AAR 引入）
- [ ] 实现 `OcrEngine` 接口

### 3.2 截图方案迁移
- [ ] **BoomOcrActivity.java**：`SurfaceControl.screenshot()` → 
  - Android 11+：`AccessibilityService.takeScreenshot()`
  - Android 10：`MediaProjection` + `ImageReader`
- [ ] 截图 → Bitmap → OCR → 文本 → 分词

### 3.3 OCR 流程改造
- [ ] 重写 `BoomOcrActivity` 为 Kotlin（或保留 Java 但简化）
- [ ] 移除 CamScanner SDK（`csopensdk.jar`、`CSOpenAPI` 导入）
- [ ] 连接 OCR 结果到 BigBang 显示管道

---

## Phase 4：UI 现代化改造（预计 3 周）

**目标：逐步用 Kotlin + Compose 替换 Smartisan 风格 UI。**

### 4.1 增量替换策略（逐页迁移，新旧共存）
- [ ] Step 1：设置页 → Compose（`TextBoomSettingsActivity` + `OptionsActivity` → `SettingsScreen`）
- [ ] Step 2：搜索/词典页 → Compose（`BoomSearchActivity` → `SearchSheet` / `SearchActivity`）
- [ ] Step 3：BigBang 主页面 → Compose（保留 `BoomWordsLayout` 布局逻辑，UI 层替换）
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
| M0 | Week 2 | Gradle 项目可编译运行，Smartisan 依赖全部去除 |
| M1 | Week 4 | 悬浮球 + 无障碍文本提取可工作 |
| M2 | Week 7 | 本地分词引擎运行，切掉远程 API |
| M3 | Week 10 | 本地 OCR 运行，切掉 CamScanner SDK |
| M4 | Week 13 | Compose UI 主要页面完成 |
| M5 | Week 15 | 测试完成，v1.0 |

---

## 风险与应对

| 风险 | 影响 | 应对措施 |
|------|------|---------|
| Smartisan `SearchActivity` 父类功能太多，替换工作量大 | 中 | 先做最小替换（标准 Activity + WebView），后续迭代增强 |
| `SurfaceControl.screenshot()` 无标准 API 等价替换 | 中 | MediaProjection 需用户授权，体验差一步；可优先用 Accessibility 截图 |
| cppjieba NDK 编译兼容性问题 | 低 | 提前在 CI 覆盖多 ABI；备选纯 Kotlin 分词器 |
| 无障碍服务被系统杀死 | 中 | 前台 Service + 引导用户设电池白名单 |
