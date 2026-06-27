# Nova Text

**Nova Text** 是经典 Smartisan OS「大爆炸」功能的 Android 原生迁移和现代化、本地化改造项目。

当前目标不是重新发明一套新产品，而是在保留原版交互气质的前提下，把旧 Smartisan 代码逐步改造成一套可在通用 Android 设备上运行、可继续维护、可继续补功能的本地化实现。

## 当前状态

截至当前仓库状态，项目已经不再是“只能编译骨架”的阶段，已经具备可调试、可预览、可本地分词、可通过悬浮球触发的主链路基础。

**已完成的核心能力**

- Gradle 工程已接通，保留 legacy `src/` / `res/` 目录，通过 `sourceSets` 兼容构建
- 本地 `cppjieba` 分词已接入，`BoomActivity` 不再依赖远程分词 HTTP 接口
- App 启动后会异步预热 jieba，引擎状态会在设置页显示
- 设置页已重构为 Jetpack Compose，并支持深色模式
- 设置页已具备开发调试入口
  - 预制文本切换
  - 自定义调试文本输入
  - BigBang 预览入口
  - 搜索方式 / 词典方式配置
- 大爆炸主界面外层已接入 Compose 浮层壳，支持深浅色和多窗口全屏适配
- 搜索页已改为 Compose + 原生 WebView 浮层页，直接叠加在大爆炸界面之上
- 悬浮球服务、无障碍服务、基础文本捕获分发链路已接入
- BigBang 拉起链路已改为 `OverlayActivity -> BoomActivity`，避免从三方 App 返回到设置页

**当前仍未完成或仍属过渡态的部分**

- OCR 仍未迁移完成，旧 `BoomOcrActivity` 中的 CamScanner 路径目前处于禁用状态
- 大爆炸原版 feature 还未补齐，尤其是“炸了又炸”、编辑模式等
- 无障碍文本提取链路已接通，但三方 App 兼容性和命中率还需要继续打磨
- 搜索页和设置页已现代化，但内部大爆炸词块布局仍主要沿用 legacy Java/View 实现
- 代码库仍处于 Java legacy + Kotlin/Compose 并存阶段，不是最终架构

## 当前可用功能

- 设置页权限检查与悬浮球控制
- 设置页预制文本调试
- 设置页 BigBang 页面预览
- 本地 jieba 分词与预热状态显示
- 大爆炸选词、滑动多选、搜索 / 词典 / 分享 / 复制等基础操作
- 搜索 / 词典 / 百科浮层页
- 悬浮球触发文本捕获主链路

## 使用说明

### 1. 直接体验当前界面

安装调试包后，启动 `TextBoomSettingsActivity`。

在设置页中可以：

- 打开悬浮窗权限
- 打开无障碍服务设置
- 启动 / 停止悬浮球
- 选择预制调试文本
- 手动输入调试文本
- 点击“预览大爆炸”直接进入 BigBang 页面

这条预览链路不依赖悬浮球，适合单独检查：

- 分词是否正常
- 词块布局是否正常
- 选中态和按钮显示是否正常
- 搜索浮层是否能正常打开

### 2. 悬浮球链路

完成以下步骤后可体验悬浮球主链路：

1. 在设置页授予悬浮窗权限
2. 在系统设置中启用无障碍服务 `NovaTextAccessibilityService`
3. 回到设置页启动悬浮球
4. 将悬浮球拖到目标文本区域附近释放

当前流程会优先尝试走无障碍文本捕获；捕获成功后会直接拉起 BigBang。

### 3. 搜索页

在 BigBang 中选中文本后，可进入搜索浮层页。

当前搜索页支持：

- Web 搜索
- 在线词典
- 百科查询
- 浮层内返回 / 前进
- 切换默认搜索源
- 在外部浏览器中打开当前页面

## 开发环境

- Android Studio / IntelliJ with Android plugin
- Android SDK 34
- minSdk 29
- NDK + CMake（用于 `cppjieba` JNI）

## 构建

```bash
bash ./gradlew assembleDebug
```

当前工程仍保留 legacy 根目录源码布局：

- `src/com/smartisanos/textboom/`：旧 Java 主逻辑
- `app/src/main/kotlin/com/smartisanos/textboom/`：新增 Kotlin / Compose / Service / overlay 逻辑
- `res/`：legacy 资源
- `app/src/main/cpp/`：本地 `cppjieba` JNI

## 当前代码结构

```text
BigBang_Nova/
├── app/
│   └── src/main/
│       ├── cpp/                           # cppjieba JNI / CMake
│       └── kotlin/com/smartisanos/textboom/
│           ├── BoomActivity.kt            # Compose 外层壳 + legacy BigBang 内核接入
│           ├── BoomSearchOverlayActivity.kt
│           ├── TextBoomSettingsActivity.kt
│           ├── OverlayActivity.kt
│           ├── OverlayPanelUi.kt
│           ├── service/                   # 悬浮球 / 无障碍 / 捕获分发
│           ├── domain/capture/            # 捕获会话数据结构
│           └── data/                      # 新增偏好与存储
├── src/com/smartisanos/textboom/          # legacy Java BigBang 主逻辑
├── src/smartisanos/                       # 过渡期兼容 stub
├── res/                                   # legacy 资源
├── libs/                                  # 旧 jar 依赖（含 csopensdk）
├── AndroidManifest.xml
├── Android.mk
├── README.md
└── DEVELOPMENT_PLAN.md
```

## 当前迁移策略

这个项目当前采用的是**外层现代化、内核逐步替换**的路线：

- 外层页面、权限入口、设置、浮层 UI 优先迁到 Kotlin + Compose
- 词块布局、选中逻辑、部分动画复用 legacy Java 保留大爆炸原汁原味的交互体验
- 在每一轮功能迁移中，优先保证主链路可运行，再补齐原版 feature

这样做的目的很直接：

- 避免一次性重写所有 BigBang 交互导致回归不可控
- 让设置页、搜索页、悬浮球链路先具备可调试性
- 把精力先放在 OCR、本地化和 feature 补齐上

## 下一阶段重点

下一阶段开发重点已经收敛为三条：

1. 接入 Android 原生 ML Kit OCR 识别
2. 继续补齐原版大爆炸功能 feature，例如“炸了又炸”和编辑模式
3. 做三方应用适配与性能调优

详细计划见：[DEVELOPMENT_PLAN.md](./DEVELOPMENT_PLAN.md)

## 已知限制

- OCR 入口目前不可用，旧 CamScanner 代码仅作为迁移残留保留
- 搜索页中个别站点在浮层 WebView 中仍存在兼容性问题
- 大爆炸内部仍有部分 legacy View 交互与视觉细节待继续对齐原版
- 多厂商 ROM 下的无障碍文本提取稳定性还没有完整收敛

## 致谢

- [cppjieba](https://github.com/yanyiwu/cppjieba) 本地分词算法
- [BigBang](https://github.com/SmartisanTech/packages_apps_BigBang) Smartisan OS 大爆炸原始开源代码

## License

Apache License 2.0
