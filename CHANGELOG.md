## 更新日志

### [Nova Text 1.16.1]

#### 新增功能

- 实验性触控监听迁入 Nova Text 主应用，设置页可以直接配置触发方式和相关参数。
- 新增压感、接触面积、单指长按、双指单击和三指单击等触发方式，并支持观察三类传感器触发时长。
- OCR 范围选择页新增二维码和常见一维条形码识别。
- 普通文本二维码可直接进入大爆炸分词，网页链接可选择浏览器打开，应用链接会拉起对应应用。
- WiFi 二维码支持查看和复制 SSID、密码或完整网络信息；微信支付二维码会特殊提示要求打开微信。
- 无法直接打开的链接会显示原文，可复制或交给大爆炸处理。

#### 体验优化

- 编辑模式新增进入和退出动画，切换编辑状态更自然。
- 优化了炸了又炸的文本提示视觉

#### 问题修复
- 修复安卓 11 系统状态栏被显示为黑色的问题。

### [Extra 0.2.2]

#### 新增功能

- 升级到 LibXposed API 101，提高扩展模块与新版本 Xposed 环境的兼容性。
- 支持通过 Extra 模块自动开启 Nova Text 无障碍服务，减少首次配置步骤。
- 实验性免 Root 触控事件监听已迁移至主 App

**Full Changelog**: https://github.com/CashewTeam/BigBang_NovaText/compare/Beta_1.15.2...Beta_1.16.1

### [Beta 1.15.2](https://github.com/CashewTeam/BigBang_NovaText/releases/tag/Beta_1.15.2)

#### 编辑模式优化
- 编辑模式 IME 可用区域适配
- 修复大爆炸 emoji 文本输入
- 微信输入法长按删除适配
- 修复词块圆角缩放问题
- 修复撤销重做按钮视觉替换错误


### [Beta 1.15.1](https://github.com/CashewTeam/BigBang_NovaText/releases/tag/Beta_1.15.1)

#### 编辑模式优化

- 优化编辑模式光标拖拽映射、自动滚动和松手后的归位动画。
- 增加光标所在位置的词块让位动画，改善行首、行中和行尾插入体验。
- 修复 Release 版本中文词块尺寸异常及文字渲染丢失问题。
- 修复首次进入编辑模式时段落尾部光标定位与手动定位不一致的问题。

### [Beta 1.15.0](https://github.com/CashewTeam/BigBang_NovaText/releases/tag/Beta_1.15.0)
#### 重大功能更新 大爆炸编辑模式
- 支持了大爆炸编辑模式，支持词块编辑、删除、文本输入、复制、符号输入

#### 优化与修复
- 添加了复制按钮的动画。
- 更新了新版的工具栏图标资源。
- 全屏截图现在始终以原始像素识别，提高首次OCR识别概率。
**Full Changelog**: https://github.com/CashewTeam/BigBang_NovaText/compare/Beta_1.14.29...Beta_1.15.0

### [Beta 1.14.29](https://github.com/CashewTeam/BigBang_NovaText/commits/smartisan-m-onestep_bigboom/)

#### 新增功能

- 新增 Nova Text Extra 触控扩展模块，提供 Xposed 与 Android 13+ 实验性无障碍两种触发方式。
- 新增压感、椭圆接触面积、单指长按、双指单击和三指单击等触发选项；不同方式会在设置页标明适用范围。
- Extra 模块新增输入法忽略策略，输入时不会意外触发 Nova Text。
触控信息获取的实现由 @EX3124 提供 https://github.com/CashewTeam/BigBang_NovaText/pull/22

#### 体验优化

- 默认识别流程的启动动画统一由 OCR 代理页按实际触点播放，动画位置和识别位置更一致。
- 悬浮球最大尺寸提高至 150%，方便大屏和视力辅助场景使用 https://github.com/CashewTeam/BigBang_NovaText/issues/14#issuecomment-4945160395
#### 问题修复

- 修复设置页在横屏下没有正确居中的问题。
#### 仓库更新
- 自动构建现在支持同时生成 Extra 模块。
- 补充触控触发接入说明和编辑模式开发计划，方便尝鲜和后续功能迭代。

### Beta 1.14.26

#### 问题修复

- 修复部分定制系统和动态页面中，悬浮球截图成功后 OCR 代理页未显示的问题。现在会在启动前清理旧的等待状态；仅当代理页未进入前台时自动重试一次，不影响正常识别流程。[Issue #21](https://github.com/CashewTeam/BigBang_NovaText/issues/21)

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
