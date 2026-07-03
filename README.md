<p align="center">
  <a href="screenshots/1.jpg"><img src="screenshots/1.jpg" alt="Screenshot 1" width="18%" /></a>
  <a href="screenshots/2.jpg"><img src="screenshots/2.jpg" alt="Screenshot 2" width="18%" /></a>
  <a href="screenshots/3.jpg"><img src="screenshots/3.jpg" alt="Screenshot 3" width="18%" /></a>
  <a href="screenshots/4.jpg"><img src="screenshots/4.jpg" alt="Screenshot 4" width="18%" /></a>
  <a href="screenshots/5.jpg"><img src="screenshots/5.jpg" alt="Screenshot 5" width="18%" /></a>
</p>

# Nova Text

Nova Text 是经典 Smartisan OS「大爆炸」功能的 Android 原生迁移与现代化项目。

当前仓库不是概念验证阶段，已经具备完整的本地化主链路：

- 本地 `cppjieba` 分词
- Compose 设置页
- BigBang 浮层壳 + legacy 词块内核
- Compose 搜索浮层 + WebView
- 悬浮球 + 无障碍文本提取
- ML Kit OCR V2 离线识别
- OCR 白名单分流

## 当前进度

已完成：

- Gradle 构建已打通，继续兼容 legacy `src/` / `res/` 目录
- `cppjieba` JNI 已替代远程分词主路径，并在启动后后台预热
- 设置页已重构为 Compose，支持深色模式、调试入口、悬浮球配置、OCR 白名单配置
- BigBang 页面已接入 Compose 外层浮层壳，内部词块选择与多选逻辑仍复用 legacy Java
- 搜索页已改为 Compose + WebView 浮层页
- OCR 已切到离线 ML Kit V2，支持中文 / 日语 / 韩语 / 英语
- 设置页图片调试入口与系统图片分享入口可进入 OCR 范围选择页
- 悬浮球白名单 OCR 链路已接通：截图后直接全屏 OCR，并按触点命中最近文本块进入 BigBang
- “炸了又炸”已接通，支持上下拖拽拉取相邻段落，并对连续短段落做批量追加
- BigBang 外壳已支持重新 OCR 识别，以及 OCR 结果的临时语言切换重跑
- 搜索页已扩展 DuckDuckGo、萌娘百科，浏览器操作栏已补前进和刷新

仍在进行：

- 编辑模式仍是 placeholder，尚未接回可用交互
- 横屏与平板适配尚未系统收口
- 多机型、多 Android 版本下的实机兼容性验证和 Debug 仍需持续推进
- OCR 最近段落命中、段落合并和复杂页面提取规则仍会继续打磨，但不再是“链路未打通”状态

## 快速使用

### 设置页

启动 `TextBoomSettingsActivity` 后可直接：

- 检查悬浮窗 / 无障碍状态
- 启动和停止悬浮球
- 调整悬浮球大小与透明度
- 切换预制调试文本并预览 BigBang
- 配置搜索源、词典源、OCR 语言和 OCR 白名单
- 选择图片进入 OCR 调试

### 悬浮球主链路

1. 授予悬浮窗权限
2. 启用 `NovaTextAccessibilityService`
3. 在设置页启动悬浮球
4. 将悬浮球拖到目标区域后松手

当前分两条路径：

- 白名单外：优先走无障碍文本提取，再进入 BigBang
- 白名单内：优先走无障碍截图 + 全屏 OCR，再按触点命中最近文本块进入 BigBang
- 白名单 OCR 的启动顺序固定为：先启动透明代理页，再由代理页截图和识别；不要改成后台 service 截图后再拉 Activity
- 前台应用识别依赖无障碍活跃窗口和最近事件缓存；仍无法识别时直接走 OCR
- 悬浮球拖动松手后会自动贴到屏幕左侧或右侧，横屏下也不会停在屏幕中间
- 进入 BigBang 后可继续上滑 / 下滑触发“炸了又炸”，并可从底栏重进 OCR 或临时切换 OCR 语言

### OCR 调试 / 分享链路

这两条入口保留手动范围选择页：

1. 图片输入
2. 范围选择
3. 离线 OCR
4. BigBang

说明：

- OCR 结果进入 BigBang 后，左下角可重进 OCR 范围选择
- OCR 来源的 BigBang 右下角可临时切换识别语言，并立即重跑 OCR
- 这类临时切换不会修改设置页里的默认 OCR 语言

## 构建

```bash
bash ./gradlew assembleDebug
```

当前主要源码目录：

- `src/com/smartisanos/textboom/`：legacy Java BigBang 内核、词块布局、多选逻辑
- `app/src/main/kotlin/com/smartisanos/textboom/`：Compose 页面、Activity、Service、OCR、启动编排
- `app/src/main/kotlin/com/smartisanos/textboom/domain/capture/`：无障碍文本提取会话与最近段落窗口
- `app/src/main/cpp/`：`cppjieba` JNI
- `archive/legacy-ui/`：已归档的旧设置页 / 旧搜索页代码，不再主链路编译

## 文档

- [文档索引](./docs/README.md)
- [开发计划](./docs/development-plan.md)
- [架构文档](./docs/architecture.md)
- [接口与 API 文档](./docs/api.md)

## 致谢

- [cppjieba](https://github.com/yanyiwu/cppjieba)
- [BigBang](https://github.com/SmartisanTech/packages_apps_BigBang)

## License

GNU General Public License v3.0
