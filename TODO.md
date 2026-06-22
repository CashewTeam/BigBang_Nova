# TODO — 开发任务清单

> 状态：⬜ 待开始  🔄 进行中  ✅ 已完成  ❌ 已取消
> 文件路径相对于 `app/src/main/java/com/smartisanos/textboom/`，新增文件统一放入对应子包

---

## Phase 0：构建系统迁移 + 解除 Smartisan 依赖

### 0.1 Gradle 项目初始化
- ⬜ 创建 `build.gradle.kts`（项目根配置，AGP 8.x + Kotlin 2.x）
- ⬜ 创建 `settings.gradle.kts`（`include(":app")`）
- ⬜ 创建 `gradle.properties`（`android.useAndroidX=true`）
- ⬜ 创建 `gradle/libs.versions.toml`（Version Catalog）
- ⬜ 创建 `app/build.gradle.kts`：
  - `compileSdk = 34`, `minSdk = 29`, `targetSdk = 34`
  - `implementation files("libs/okhttp-2.7.5.jar")`、`okio-1.7.0.jar`（临时保留）
  - `csopensdk.jar` 标记 `// TODO: 替换为本地 OCR`
- ⬜ 执行 `gradle wrapper` 生成 Gradle Wrapper 文件
- ⬜ 创建 `app/proguard-rules.pro`（从 `proguard.flags` 迁移）
- ⬜ 创建 `.gitignore`（排除 `build/`、`.gradle/`、`local.properties`）

### 0.2 源码与资源迁移
- ⬜ 移动 `src/com/` → `app/src/main/java/com/`
- ⬜ 移动 `src/se/` → `app/src/main/java/se/`
- ⬜ 移动 `res/` → `app/src/main/res/`
- ⬜ 移动 `libs/` → `app/libs/`
- ⬜ 移动 `proguard.flags` → `app/proguard-rules.pro`
- ⬜ 移动 `LICENSE` 保留在项目根目录
- ⬜ 删除空的 `src/`、`res/` 目录
- ⬜ 删除 `Android.mk`

### 0.3 AndroidManifest.xml 改造
- ⬜ 移动 `AndroidManifest.xml` → `app/src/main/`
- ⬜ 更新 `package` → `com.smartisanos.textboom`（保持包名不变）
- ⬜ 移除 `<uses-sdk>`（改由 `build.gradle.kts` 管理）
- ⬜ 权限变更：
  - 移除 `READ_FRAME_BUFFER`（隐藏 API 权限）
  - 移除 `WRITE_SETTINGS`、`WRITE_SECURE_SETTINGS`（系统应用权限）
  - 添加 `SYSTEM_ALERT_WINDOW`
  - 添加 `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_SPECIAL_USE`
  - 添加 `BIND_ACCESSIBILITY_SERVICE`
- ⬜ `BoomApplication` 声明改为 `android:name=".BoomApplication"`
- ⬜ 注册悬浮球 Service 声明（`FloatingBallService`）
- ⬜ 注册无障碍 Service 声明（`BigBangAccessibilityService`）
- ⬜ 移除 Smartisan 主题引用 → 临时使用 `Theme.Material3.Light.NoActionBar`

### 0.4 逐个文件脱 Smartisan 耦合
- ⬜ `util/LogUtils.java`：`SystemProperties.get("ro.debuggable")` → `BuildConfig.DEBUG`
- ⬜ `Constant.java`：替换 `Settings.TEXT_BOOM_SEARCH_VALUE.TYPE_*` → 自定义 `int` 常量（保持值不变）
- ⬜ `SwipeSelectView.java`：移除 `SidebarUtils` 相关代码
  - 删除 `import smartisanos.util.SidebarUtils`
  - 删除 `mDragText`、`mStartDrag` Runnable、`mDragStarted` 字段
  - 删除 `onDetachedFromWindow()`、`initDrag()` 中 SidebarUtils 调用
- ⬜ `OptionsActivity.java`：
  - `smartisanos.widget.Title` → `androidx.appcompat.widget.Toolbar`
  - `IntentSmt` → `Intent`
  - 添加 `AppCompatActivity` 继承
- ⬜ `TextBoomSettingsActivity.java`：
  - 改为 `extends AppCompatActivity`
  - `SettingItemSwitch/Check/Text` → 暂用标准 `Switch`/`CheckBox`/`TextView`
  - `IntentSmt` → `Intent`
- ⬜ `BoomSearchActivity.java`：
  - `extends smartisanos.app.SearchActivity` → `extends AppCompatActivity`
  - 自己实现搜索框 + WebView（参考原 SearchActivity 行为模拟）
  - 处理原 `SearchActivity` 的回调方法（如 `onQueryTextChange`、`onQueryTextSubmit`）
- ⬜ `BoomOcrActivity.java`：
  - 注释/移除 `SurfaceControl.screenshot()` 调用
  - 标记 `// TODO: Phase 3 替换为 MediaProjection/AccessibilityService`
  - 保留其余 OCR 流程代码骨架
- ⬜ `TextBoomCallProvider.java`：
  - 评估是否仍需要跨进程调用（OCR 停止操作）
  - 若需跨进程，改为 AIDL；否则标 `@Deprecated` 移除

### 0.5 资源修复
- ⬜ `res/values/styles.xml`：Smartisan 主题 → `Theme.Material3.Light.NoActionBar`
- ⬜ `res/layout/`：布局中 Smartisan 控件引用 → 标准 Android 控件
- ⬜ `res/values/strings.xml`：`app_label` → 应用名
- ⬜ `res/values/colors.xml`：确认颜色值不再依赖 Smartisan 框架
- ⬜ 删除 `res/values-smartisan*/`（如有）

### 0.6 验证
- ⬜ `./gradlew assembleDebug` 编译通过
- ⬜ APK 可在 Android 10+ 真机安装运行
- ⬜ 基本 Activity 启动不崩溃（先确保骨架可以运行）

---

## Phase 1：移植 NovaText 悬浮球 + 无障碍文本提取

### 1.1 移植悬浮球
- ⬜ 从 NovaText 复制 `FloatingBallService.kt` → 新建 `service/FloatingBallService.kt`
- ⬜ 移除 `NovaTextEventBridge` / `EventChannel` 引用
- ⬜ 改为通过 `LocalBroadcastManager` 或 `Intent` 发送坐标/文本
  ```kotlin
  // 替代原来的 EventChannel 推送
  val intent = Intent(ACTION_TRIGGER_BIGBANG).apply {
      putExtra(EXTRA_TOUCH_X, sampleX)
      putExtra(EXTRA_TOUCH_Y, sampleY)
      putExtra(EXTRA_TEXT_BLOCKS, captureResult.toJson())
  }
  LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
  ```
- ⬜ 修复包名引用（从 `com.novatext.nova_text` → `com.smartisanos.textboom`）
- ⬜ 迁移所需的资源文件（悬浮球图标 drawable、notification icon 等）
- ⬜ 在 `AndroidManifest.xml` 中注册 Service + 通知渠道

### 1.2 移植无障碍提取引擎
- ⬜ 复制 `NovaTextAccessibilityService.kt` → `service/BigBangAccessibilityService.kt`
- 内容：`AccessibilityTextSession.kt` 整个文件（含 `AccessibilityTextSessionRunner`、`TextSessionCoordinator`、`ParagraphWindow`）
- ⬜ 移到 `domain/capture/` 子包
- ⬜ `CaptureContracts.kt`：移到 `domain/capture/` 子包
- ⬜ 移除所有 Flutter EventChannel / MethodChannel 引用
- ⬜ 文本提取结果通过 Broadcast/Intent 发送给 BoomActivity
- ⬜ 创建 `res/xml/bigbang_accessibility_service.xml`：
  ```xml
  <accessibility-service
      android:accessibilityEventTypes="typeWindowContentChanged|typeWindowStateChanged"
      android:canRetrieveWindowContent="true"
      android:canTakeScreenshot="true"
      android:notificationTimeout="100" />
  ```
- ⬜ 在 Manifest 中注册 AccessibilityService

### 1.3 移植支持类
- ⬜ `MediaProjectionStore.kt` → `data/MediaProjectionStore.kt`
- ⬜ `BigBangPreferences.kt` → `data/BigBangPreferences.kt`
- ⬜ `AppDebugPreferences.kt` → `data/AppDebugPreferences.kt`
- ⬜ `NovaTextLogger.kt` → `util/NovaTextLogger.kt`
- ⬜ 统一修复所有 import 包名引用

### 1.4 连接悬浮球 → BigBang 主流程
- ⬜ 修改 `BoomActivity.onCreate()`：
  - 当前从 `Intent.EXTRA_TEXT` 接收文本
  - 新增从 Broadcast 接收悬浮球捕获的文本
- ⬜ 修改 `BoomApplication`：
  - 初始化时注册 BroadcastReceiver 监听悬浮球事件
  - 或改为 ViewModel/Repository 驱动
- ⬜ 当悬浮球拖拽释放且无障碍文本非空 → 直接启动 BoomActivity 并分词

### 1.5 验证
- ⬜ 授予悬浮窗权限后悬浮球显示
- ⬜ 拖拽悬浮球可移动、释放有触觉反馈
- ⬜ 授予无障碍权限后可提取文本
- ⬜ 提取的文本传入 BoomActivity 正确分词展示

---

## Phase 2：本地分词引擎替换远程 API

### 2.1 cppjieba NDK 集成
- ⬜ 创建 `app/src/main/cpp/jni_bridge.cpp`
- ⬜ 复制 cppjieba 头文件到 `app/src/main/cpp/cppjieba/`
- ⬜ 创建 `app/src/main/cpp/CMakeLists.txt`：
  ```cmake
  cmake_minimum_required(VERSION 3.22.1)
  project("bigbang_jni")
  set(CMAKE_CXX_STANDARD 17)
  add_library(bigbang_jni SHARED jni_bridge.cpp)
  target_include_directories(bigbang_jni PRIVATE ${CMAKE_SOURCE_DIR})
  find_library(log-lib log)
  target_link_libraries(bigbang_jni ${log-lib})
  ```
- ⬜ 在 `app/build.gradle.kts` 中配置：
  ```kotlin
  android {
      defaultConfig {
          ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64") }
      }
      externalNativeBuild {
          cmake {
              path = file("src/main/cpp/CMakeLists.txt")
          }
      }
  }
  ```

### 2.2 JNI 桥接层实现
- ⬜ `jni_bridge.cpp` 实现：
  - `jstring JNICALL nativeInit(JNIEnv*, jobject, jstring dictPath)`
  - `jstring JNICALL nativeCut(JNIEnv*, jobject, jstring text)` — 返回 JSON `[{"word":"...","offset":n},...]`
  - `jstring JNICALL nativeTag(JNIEnv*, jobject, jstring text)` — 词性标注
  - `void JNICALL nativeInsertUserWord(JNIEnv*, jobject, jstring word, jstring tag, jint freq)`
  - `void JNICALL nativeDestroy(JNIEnv*, jobject)`
- ⬜ 使用 `std::mutex` 保护分词实例的线程安全
- ⬜ 返回 JSON 格式**复用现有 `OkHttpClientManager` 返回的 JSON 结构**，减少对 `BoomWordsLayout` 的改动

### 2.3 Kotlin 封装层
- ⬜ 创建 `domain/tokenizer/JiebaTokenizer.kt`（Kotlin 接口封装）
  ```kotlin
  class JiebaTokenizer(private val dictDir: File) {
      private external fun nativeInit(dictPath: String): Boolean
      private external fun nativeCut(text: String): String  // 返回 JSON
      private external fun nativeDestroy()
      
      fun tokenize(text: String): TokenizeResult { ... }
  }
  ```
- ⬜ 创建 `domain/tokenizer/Tokenizer.kt`（接口，方便测试 mock）
- ⬜ 创建 `domain/tokenizer/TokenizeResult.kt`（数据类）

### 2.4 词典资源管理
- ⬜ 复制 cppjieba 词典到 `app/src/main/assets/dict/`
  - `jieba.dict.utf8`（主词典，~5MB）
  - `hmm_model.utf8`（HMM 模型）
  - `idf.utf8`（IDF 词典）
  - `stop_words.utf8`（停用词）
  - `user.dict.utf8`（用户词典模板）
- ⬜ 创建 `data/DictManager.kt`：
  - 首次启动从 `assets/dict/` 解压到 `filesDir/dict/`
  - 版本检测：`dict_version.txt` 版本号变化时重新解压
  - 用户词典读写在 `filesDir/dict/user.dict.utf8`

### 2.5 替换调用点（核心改造）
- ⬜ **`BoomApplication.kt`**（或创建新的初始化类）：
  - `onCreate()` 中调用 `DictManager.init(assets)` 解压词典
  - `JiebaTokenizer.init(dictDir)` 初始化分词器
  - 提供 singleton 访问 `JiebaTokenizer.getInstance()`
- ⬜ **`OkHttpClientManager.java`** → **`network/JiebaTokenizeProvider.java`**：
  - 删除 `OkHttpClient`、HTTP POST 逻辑
  - 改为调用 `JiebaTokenizer.tokenize()`
  - 保持返回类型不变（JSON 字符串），对调用者透明
- ⬜ **`BoomActivity.java`** 中：
  - `doBoomParse()` / 网络回调 → 改为同步/异步调用本地分词器
  - 原 HTTP 回调的 `onSuccess`/`onFailure` → 直接改为 `CoroutineScope` 或 `AsyncTask` 执行分词
- ⬜ **`BoomWordsLayout.java`**：保持不变（仍解析 JSON 格式的分词结果）
- ⬜ **`Constant.java`**：删除 `SEGMENT_URL`，删除 `SEGMENT_API_KEY`

### 2.6 测试
- ⬜ `JiebaTokenizerTest`：验证正确分词
- ⬜ 对比原 HTTP API 返回结果 vs 本地分词结果，确保格式兼容
- ⬜ 性能测试：1000 字中文文本分词 < 100ms
- ⬜ 边界测试：空文本、纯英文、emoji、生僻字

---

## Phase 3：本地 OCR 引擎替换 CamScanner SDK

### 3.1 OCR 引擎引入
- ⬜ 评估并确认 OCR 引擎（推荐 PaddleOCR Lite 或 Google ML Kit）
- ⬜ 添加引擎依赖到 `app/build.gradle.kts`
- ⬜ 模型文件放入 `app/src/main/assets/ocr/`
- ⬜ 实现 `OcrEngine` 接口（`domain/ocr/OcrEngine.kt`）
  ```kotlin
  interface OcrEngine {
      suspend fun recognize(bitmap: Bitmap): OcrResult
      fun release()
  }
  ```

### 3.2 截图方案替换
- ⬜ Android 11+：使用 `AccessibilityService.takeScreenshot()` 获取屏幕截图
  - 在 `BigBangAccessibilityService` 中实现 `onTakeScreenshot` 回调
  - 或无回调直接使用 `takeScreenshot(displayId, executor, callback)`
- ⬜ Android 10：使用 `MediaProjection` + `ImageReader`：
  - 请求 `MediaProjectionManager.createScreenCaptureIntent()` 权限
  - 创建 `VirtualDisplay` → `ImageReader` → 获取 `Bitmap`
  - 权限通过 `MediaProjectionStore` 持久化
- ⬜ 创建 `CaptureScreenUseCase` 封装两种实现

### 3.3 BoomOcrActivity 改造
- ⬜ 重写 `BoomOcrActivity.java` → `BoomOcrActivity.kt`（或保留 Java 简化修改）
- ⬜ 移除 `CSOpenAPI` / `csopensdk.jar` 所有引用
- ⬜ 移除 `SurfaceControl.screenshot()` 调用
- ⬜ 新流程：截屏 → `OcrEngine.recognize(bitmap)` → 获取文本 → 启动 `BoomActivity`
- ⬜ OCR 加载动画（替换原 CamScanner 圈圈动画）

### 3.4 清理 CamScanner
- ⬜ 删除 `libs/csopensdk.jar`
- ⬜ 删除 `BoomOcrActivity.java` 中 CamScanner 初始化/回调代码
- ⬜ 删除 `Constant.java` 中 CamScanner 相关常量
- ⬜ 从 `app/build.gradle.kts` 中移除 csopensdk 依赖

---

## Phase 4：UI 现代化改造（增量 Compose 迁移）

### 4.1 Compose 基础设施
- ⬜ `app/build.gradle.kts` 添加 Compose BOM + Material 3 依赖
- ⬜ 创建 `ui/theme/Theme.kt`、`Color.kt`、`Type.kt`
- ⬜ 创建 `ui/theme/BigBangTheme.kt`（动态取色 + 支持暗色模式）

### 4.2 逐页迁移

#### 4.2a 设置页 → Compose（第一优先，改动最小）
- ⬜ 创建 `ui/settings/SettingsScreen.kt`
- ⬜ 创建 `ui/settings/SettingsViewModel.kt`
- ⬜ `TextBoomSettingsActivity.kt`：`setContent { SettingsScreen() }`
- ⬜ 实现所有原有设置项：
  - BigBang 总开关 / OCR 开关
  - 默认搜索引擎 / 词典
  - 触发区域设置
  - 用户词典管理
- ⬜ 删除 `OptionsActivity.java`（合并到 SettingsScreen）
- ⬜ `DataStore` 替换原有 `Settings.Global/Secure`（原系统设置存储）

#### 4.2b 搜索/词典页 → Compose
- ⬜ 创建 `ui/search/SearchSheet.kt`（BottomSheet）
- ⬜ WebView 集成
- ⬜ 搜索引擎/词典 Tab 切换
- ⬜ `BoomSearchActivity` 改为以 Compose 承载此 Sheet

#### 4.2c BigBang 主页面 → Compose
- ⬜ 创建 `ui/bigbang/BigBangScreen.kt`
- ⬜ 创建 `ui/bigbang/BigBangViewModel.kt`
- ⬜ 创建 `ui/bigbang/BoomTextCanvas.kt`（词芯片布局 Compose 实现）
- ⬜ 创建 `ui/bigbang/BoomAnimation.kt`（爆炸入场/退出动画）
- ⬜ 词芯片渲染：`FlowRow` / 自定义 `Layout` 实现 `BoomWordsLayout.java` 同等逻辑
- ⬜ 操作栏：`BoomActionBar.kt`
- ⬜ `BoomActivity.java` → `BoomActivity.kt` + `setContent { BigBangScreen() }`
- ⬜ 保留 `BoomWordsLayout.java` 的纯计算逻辑（可转为 Kotlin object 工具函数）

### 4.3 保留与复用
- ⬜ 以下 Java 文件**保留其逻辑**，仅在 Compose 中调用或转为 Kotlin 工具类：
  - `BoomWordsLayout.java`（排版计算）
  - `BoomAnimator.java`（插值器常量/算法）
  - `BoomActionHandler.java`（操作逻辑）→ 抽取到 ViewModel
  - `CubicInInterpolator.java`、`SineInoutInterpolator.java`（插值器）→ 转为 Compose `Easing`
- ⬜ 以下文件可随 Activity 迁移而删除：
  - `CustomScrollView.java`
  - `ImageSpanAlignCenter.java`

---

## Phase 5：清理、测试与发布

### 5.1 死代码清理
- ⬜ 删除 Smartisan 废弃资源文件
- ⬜ 删除 `libs/okhttp-2.7.5.jar`、`libs/okio-1.7.0.jar`（如已无引用）
- ⬜ 删除 `libs/csopensdk.jar`
- ⬜ 删除 `libs/android-support-v4.jar`（改用 AndroidX）
- ⬜ 清理 `AndroidManifest.xml` 中无用权限/声明
- ⬜ 全量检查无 Smartisan import 残留
- ⬜ 整理包结构，删除空目录

### 5.2 测试补充
- ⬜ `domain/tokenizer/` 单元测试：
  - `JiebaTokenizerTest`：分词准确性、性能
  - `DictManagerTest`：词典加载/版本更新
- ⬜ `domain/capture/` 单元测试：
  - `TextSessionCoordinatorTest`：段落扩展逻辑
  - `AccessibilityTextSessionRunnerTest`：文本块提取排序
- ⬜ `ui/bigbang/` UI 测试：
  - `BigBangScreenTest`：词芯片渲染
  - `BoomSelectionGesture`：滑动选择
- ⬜ `ui/settings/` UI 测试：
  - `SettingsScreenTest`：设置项保存/读取

### 5.3 兼容性验证
- ⬜ Android 10 (API 29) 真机
- ⬜ Android 11 (API 30) 真机
- ⬜ Android 12/12L (API 31/32) 真机
- ⬜ Android 13 (API 33) 真机
- ⬜ Android 14 (API 34) 真机
- ⬜ Android 15 (API 35) 模拟器

### 5.4 性能检查
- ⬜ 词典初始化耗时（目标 < 2s）
- ⬜ 分词吞吐（1000 字 < 100ms）
- ⬜ 悬浮球内存占用
- ⬜ LeakCanary 接入，检查内存泄漏
- ⬜ 启动时间基线测量

### 5.5 发布准备
- ⬜ 应用签名配置
- ⬜ ProGuard/R8 规则完善
- ⬜ README 更新
- ⬜ Release Notes 编写

---

## 持续迭代（v1.1+）

- ⬜ 暗色模式完善
- ⬜ 平板 / 折叠屏布局适配
- ⬜ 更多搜索引擎/词典自定义
- ⬜ 离线词典（本地词库）
- ⬜ App Shortcuts / 桌面小组件
- ⬜ 无障碍文本提取性能优化（增量更新 vs 全量遍历）
