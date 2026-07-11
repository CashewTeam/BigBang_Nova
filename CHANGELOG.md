## 更新日志

### [Beta 1.14.25](https://github.com/CashewTeam/BigBang_NovaText/releases/tag/Beta_1.14.25)

更新日期：2026-07-08 至 2026-07-12

#### 新增功能

- 新增 Android 通用文本分享入口，可以从其他应用直接把文字交给 Nova Text 处理。[Issue #17](https://github.com/CashewTeam/BigBang_NovaText/issues/17)
- 新增桌面快捷方式，支持快速进入截图 OCR 或处理剪贴板文本。
- 新增系统快捷设置入口，可以快速开关悬浮球，也可以直接进入截图 OCR。
- 新增第三方应用调用接口，并补充调用说明，方便其他应用或模块接入 Nova Text。[PR #13](https://github.com/CashewTeam/BigBang_NovaText/pull/13)

#### 设置页布局重构优化

- 重新整理设置页分类和入口，常用功能更容易找到。
- 新增启动向导权限配置面板。
- 新增自定义搜索源：可以自己填写网址、名称和图标，让搜索、词典和百科更符合个人习惯。[Issue #12](https://github.com/CashewTeam/BigBang_NovaText/issues/12)
- 新增悬浮球左右侧锁定、BigBang 行距调整和截图 OCR 启动延迟设置。
- Android 7–10 新增 MediaProjection 截图方式，同时保留 Shizuku 选择，并将截图源切换集中到设置页。
- Android 13+ 增加通知权限状态提示和启动引导。
- 识别追踪日志统一跟随“调试模式”，设置页不再保留容易混淆的独立日志开关。
- 针对定制系统添加后台弹出页面权限申请入口

#### 体验优化

- 悬浮球可以锁定在屏幕左右侧，普通拖动时不会意外改变停靠位置；双击移动仍可重新选择一侧。
- 大爆炸 UI 界面的顶部浮动按钮添加限位。[Issue #20](https://github.com/CashewTeam/BigBang_NovaText/issues/20)
- 搜索页和 OCR 启动流程优化了动画衔接、返回行为和启动时机。
- Android 7–10 截图流程会及时释放系统资源，减少截图后投屏会话残留。

#### 问题修复

- 修复选词时手指向下拖动无法继续滚动的问题。[Issue #19](https://github.com/CashewTeam/BigBang_NovaText/issues/19)
- 修复拉取上下段文本后范围选择背景框消失的问题。
- 修复截图 OCR 动画出现过早或过晚的问题。
- 修复旧版 Android 和非全面屏设备上 Smartisan 风格开关错位，以及轻点被误判为拖动的问题。

#### 社区贡献

- 感谢 [w0scan](https://github.com/w0scan) 提交第三方调用与自动构建相关改进。[PR #13](https://github.com/CashewTeam/BigBang_NovaText/pull/13)
- 感谢 [SkyShadowHero](https://github.com/SkyShadowHero) 提交 Smartisan 风格开关改进。[PR #11](https://github.com/CashewTeam/BigBang_NovaText/pull/11)
- 感谢 [yffengdong](https://github.com/yffengdong) 提交文本处理、搜索和选词体验反馈。[Issue #12](https://github.com/CashewTeam/BigBang_NovaText/issues/12)、[Issue #17](https://github.com/CashewTeam/BigBang_NovaText/issues/17)、[Issue #19](https://github.com/CashewTeam/BigBang_NovaText/issues/19)
- 感谢 [gstar110](https://github.com/gstar110) 提交顶部边缘选词问题反馈。[Issue #20](https://github.com/CashewTeam/BigBang_NovaText/issues/20)


### [Alpha 1.13.25](https://github.com/CashewTeam/BigBang_NovaText/releases/tag/Alpha_1.13.25)

#### 优化

- 支持 Android 7–9 安装。
- 优化非自适应图标与自适应图标的切换。
- 新增横屏安全区设置，让平板上的悬浮球位置更合适。
- 百度翻译改为有道翻译。

#### 问题修复

- 改善横屏下悬浮球隐藏、经典样式和全屏 BigBang 的显示效果。
- 修复横屏 OCR 页面无法打开、旋转后范围选择器不刷新和旋转崩溃问题。
- 修复悬浮球点击后不进入静置倒计时、搜索页启动状态异常的问题。
- 修复“炸了又炸”拉取上下段文本后选中状态或已拉取内容丢失的问题。
- 修复全选已有内容时范围选择框消失的问题。

### [Alpna 1.12.26](https://github.com/CashewTeam/BigBang_NovaText/releases/tag/Alpna_1.12.26)

#### 新增功能

- 新增二次切词“菜刀”功能。
- 支持将悬浮球隐藏为小蓝条，减少对画面的遮挡。
- 新增关于页面，集中展示项目说明和相关链接。
- Android 10 支持通过 Shizuku 进行后台静默截图。

#### 体验优化

- 无障碍识别失败时自动回退到 OCR 识别。
- 统一无障碍和 OCR 的启动流程。
- 优化 BigBang 启动动画，加入更自然的缩放效果。

#### 问题修复

- 修复英文分词结果将空格带入搜索网址的问题。
- 修复无障碍链路中 BigBang 启动动画不显示的问题。
- 修复不同屏幕尺寸下 BigBang 和搜索栏图标位置偏移的问题。
- 修复 Android 11 退出动画方向异常、关闭应用后无法重新启动悬浮球的问题。
- 修复 BigBang 结束后悬浮球不会自动进入静置状态的问题。

### [Alpha 1.11.36](https://github.com/CashewTeam/BigBang_NovaText/releases/tag/Alpha_1.11.36)

#### 首次发布

- 首次发布 Nova Text Alpha 版本。
- 支持 Android 11（坚果 R2）和 Android 15（澎湃 OS3，Redmi Note 12 Turbo）测试使用。

#### 已知情况

- Android 11 的 BigBang 退出动画方向异常。
- 不同屏幕尺寸下，炸开动画和搜索栏图标可能出现位置偏移。
- Android 10 暂不支持无障碍截图，后续评估 MediaProjection 截图支持。
