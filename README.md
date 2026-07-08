<p align="center">
  <a href="screenshots/1.jpg"><img src="screenshots/1.jpg" alt="Screenshot 1" width="18%" /></a>
  <a href="screenshots/2.jpg"><img src="screenshots/2.jpg" alt="Screenshot 2" width="18%" /></a>
  <a href="screenshots/3.jpg"><img src="screenshots/3.jpg" alt="Screenshot 3" width="18%" /></a>
  <a href="screenshots/4.jpg"><img src="screenshots/4.jpg" alt="Screenshot 4" width="18%" /></a>
  <a href="screenshots/5.jpg"><img src="screenshots/5.jpg" alt="Screenshot 5" width="18%" /></a>
</p>

# Nova Text

Nova Text 是经典 Smartisan OS「大爆炸」功能的 Android 原生迁移与现代化项目。

## 🔨更多 Smartisan 相关开源项目

- [awesome-smartisanOS](https://github.com/CashewTeam/awesome-smartisanOS) — 收集与 SmartisanOS（锤子科技操作系统）相关的优质 GitHub 项目、工具、资源和文章汇总。包括本项目 Nova Text、TNT Anywhere、锤子音乐、锤子桌面移植、HandShaker 维护版、足迹壁纸收藏等数十个项目，面向 SmartisanOS 生态的开发者、爱好者和用户。

### 🎨 UI 与系统特性
基于 **Jetpack Compose** 重构大部分 UI，适配高版本 Android 特性：**深色模式**、**自适应图标**、**多窗口支持**。

### 📝 文本获取与处理
支持**无障碍权限直接提取文本**，继承经典「**炸了又炸**」交互（上下拖拽追加相邻段落），集成**搜索/词典/百科**引擎，默认接入 DuckDuckGo 与萌娘百科。

### 🔍 离线 OCR 识别
基于**无障碍截图 / Shizuku 截屏** + **Google ML Kit** 实现纯离线 OCR，支持中文、日语、韩语、英语。自动命中触点附近的文本块，支持**自定义 OCR 白名单**，可在识别后**重新选择识别范围或切换识别语言**。

### ⚡ 悬浮球交互
**拖拽触发**，松手自动贴边；**锁定状态下双击移动位置**；支持自定义**大小/透明度/自动隐藏**；特有**单手优化**模式，可调节锁定高度与触发角度阈值。

## 快速使用

### 📲 下载与安装

- **GitHub Releases**：<https://github.com/CashewTeam/BigBang_NovaText/releases/latest>
- **夸克网盘**：<https://pan.quark.cn/s/b272e9416cab>

### 🔐 授权

首次使用需要授予两项权限：

1. **悬浮窗权限** — 打开设置页后点击"悬浮窗权限"检查状态，按提示跳转系统设置授予
2. **无障碍服务** — 在系统设置 → 无障碍 → 已安装应用中找到 `NovaTextAccessibilityService` 并开启
   > 无障碍权限是获取前台文本内容的核心通道，不开启则无法提取文字

授予后返回设置页，两项状态均应显示为已开启。

**澎湃 OS** 或其他定制系统需要打开"允许后台弹出页面"权限

**应用锁、隐私模式、安全模式**等定制系统功能可能会影响文字和图片截取

### 🎈 使用悬浮球

1. 在设置页点击**启动悬浮球**
2. 屏幕上出现一个半透明的小圆球，将其**拖拽到目标文字区域**上方
3. 松手后悬浮球隐藏，短暂加载动画后弹出 BigBang 浮层，文字已被「炸开」
4. 在 BigBang 中：
   - **点选**单个词块复制
   - **左右滑动**批量词块选择
   - **上滑/下滑**触发「炸了又炸」，拉取相邻段落
   - 支持**搜索 / 词典 / 百科**查询选中文字以及分享和复制
> **双击悬浮球**可以在不开启大爆炸的情况下移动悬浮球位置

> **隐藏悬浮球**功能只是**视觉上**将悬浮球隐藏为了小蓝条，不改变实际交互行为，请从小蓝条外的空白区域开始拖拽，防止和安卓系统返回手势冲突

> **进阶提示**：若目标区域是 OCR 白名单中的应用（如 QQ），会自动走全屏 OCR 识别而非无障碍文本提取，适合图片类或受限应用场景。

*当前版本还未支持大爆炸编辑模式

### ⚙️ 自定义设置项

在设置页中可调整：

- **悬浮球外观** — 大小与透明度，适应不同屏幕和使用习惯
- **悬浮球交互** — 锁定高度、单手优化模式、单手触发角度阈值
- **OCR 设置** — 识别语言（中文/日语/韩语/英语）、白名单应用列表
- **搜索源** — 默认搜索引擎（DuckDuckGo / 萌娘百科等）和词典源
- **调试工具** — 切换预制文本预览 BigBang、开启识别调试日志、选择图片进入 OCR 调试

各选项均有即时效果，无需重启应用。

## Android 版本支持策略

- Android 11+：当前主维护目标，优先保证完整功能适配与稳定性
- Android 7-10：当前仅作二级支持
  - 允许继续编译和尝试运行
  - 不保证所有功能可用
  - 不保证不同机型上的稳定性与一致性

## 当前进度

已完成：

- Gradle 构建已打通，继续兼容 legacy `src/` / `res/` 目录
- `cppjieba` JNI 已替代远程分词主路径，并在启动后后台预热
- 设置页已重构为 Compose，支持深色模式、调试入口、悬浮球配置、OCR 白名单配置
- 设置页已补悬浮球锁定高度、单手优化、单手角度阈值、识别调试日志和 Android 7-10 Shizuku 状态
- BigBang 页面已接入 Compose 外层浮层壳，内部词块选择与多选逻辑仍复用 legacy Java
- 搜索页已改为 Compose + WebView 浮层页
- OCR 已切到离线 ML Kit V2，支持中文 / 日语 / 韩语 / 英语
- 设置页图片调试入口与系统图片分享入口可进入 OCR 范围选择页
- 悬浮球白名单 OCR 链路已接通：截图后直接全屏 OCR，并按触点命中最近文本块进入 BigBang
- 悬浮球无障碍链路已补静默截图缓存，供 BigBang 内手动重进 OCR 复用
- “炸了又炸”已接通，支持上下拖拽拉取相邻段落，并对连续短段落做批量追加
- BigBang 外壳已支持重新 OCR 识别，以及 OCR 结果的临时语言切换重跑
- 搜索页已扩展 DuckDuckGo、萌娘百科，浏览器操作栏已补前进和刷新

仍在进行：

- 编辑模式仍是 placeholder，尚未接回可用交互
- 横屏与平板适配尚未系统收口
- 多机型、多 Android 版本下的实机兼容性验证和 Debug 仍需持续推进
- OCR 最近段落命中、段落合并和复杂页面提取规则仍会继续打磨，但不再是“链路未打通”状态

## 源码目录参考

- `src/com/smartisanos/textboom/`：legacy Java BigBang 内核、词块布局、多选逻辑
- `app/src/main/kotlin/com/smartisanos/textboom/`：Compose 页面、Activity、Service、OCR、启动编排
- `app/src/main/kotlin/com/smartisanos/textboom/domain/capture/`：无障碍文本提取会话与最近段落窗口
- `app/src/main/cpp/`：`cppjieba` JNI
- `archive/legacy-ui/`：已归档的旧设置页 / 旧搜索页代码，不再主链路编译
- 通过源码构建：

```bash
bash ./gradlew assembleDebug
```

APK 输出路径：`app/build/outputs/apk/debug/app-debug.apk`

## 文档

- [文档索引](./docs/README.md)
- [开发计划](./docs/development-plan.md)
- [架构文档](./docs/architecture.md)
- [接口与 API 文档](./docs/api.md)

## 致谢

- [cppjieba](https://github.com/yanyiwu/cppjieba)
- [BigBang](https://github.com/SmartisanTech/packages_apps_BigBang)


## License / 许可证说明

Nova Text 作为一个整体，以 **GNU General Public License v3.0（GPLv3）** 协议分发。完整协议文本见 [`LICENSE`](./LICENSE)。

本项目是基于 SmartisanTech 开源的 BigBang / BigBoom 应用继续开发的社区分支与现代化改造版本。原始项目基于 **Apache License 2.0** 发布，因此本仓库中来自原始 SmartisanTech BigBang / BigBoom 项目的代码、资源、版权声明与归属信息，仍然保留其原有的 Apache License 2.0 授权与声明。Apache License 2.0 协议文本见 [`LICENSE-Apache 2.0`](./LICENSE-Apache%202.0)。

本仓库的许可结构可以理解为：

* 本分支作为整体，包括 Nova Text 新增代码、重构代码、集成逻辑、现代化适配、构建系统调整和项目特定修改，按 **GPLv3** 分发。
* 来自原始 SmartisanTech BigBang / BigBoom 项目的部分，仍保留原始 **Apache License 2.0** 的版权声明、归属声明和许可要求。
* 基于原始项目修改过的文件，可能同时包含原始作者版权声明和本项目修改声明。
* 第三方库、依赖、字体、模型、词典、图标或其他资源，如果各自带有独立许可证，则仍遵循其各自的许可证条款。
* Apache License 2.0 与 GPLv3 在该方向上兼容：Apache-2.0 代码可以被纳入 GPLv3 项目中；但本分支中受 GPLv3 约束的新增代码和修改代码，不能在没有额外授权的情况下重新以 Apache-2.0 协议并入原始项目。

本项目是独立的社区分支，不隶属于 Smartisan / SmartisanTech，也未获得 Smartisan / SmartisanTech 的官方背书或赞助。Smartisan、BigBang、BigBoom、锤子科技、Smartisan OS 等名称可能是其各自权利人的商标或产品名称，仅用于说明项目来源与兼容背景。

