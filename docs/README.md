# 文档索引

本目录只保留当前主线文档，尽量避免功能重叠。

## 文档分工

- [development-plan.md](./development-plan.md)
  - 只写当前开发状态、下一阶段目标、里程碑和未完成项
  - 不展开实现细节，不重复接口定义
- [architecture.md](./architecture.md)
  - 只写模块边界、组件职责、启动流程、动画时序和迁移约束
  - 不写具体待办清单
- [api.md](./api.md)
  - 只写当前代码中的组件契约、Intent extra、Provider 方法、Service 入口、设置键和截图/OCR provider 选择
- [third-party-integration.md](./third-party-integration.md)
  - 面向第三方应用的调用方式、Intent 示例、参数说明和行为限制
  - 不写产品路线和大段架构讨论

## 阅读顺序

1. 先看 [README.md](../README.md) 了解当前能力和使用方式
2. 再看 [architecture.md](./architecture.md) 了解代码分层和启动流程
3. 然后看 [api.md](./api.md) 查具体契约
4. 最后看 [development-plan.md](./development-plan.md) 了解后续工作
