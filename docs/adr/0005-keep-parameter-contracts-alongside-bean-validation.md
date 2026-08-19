# Keep parameter contracts alongside Bean Validation

Summer MVC 将 Field/Style 作为长期正式的参数契约能力，而不是旧版过渡兼容层。参数契约负责查询、表单及动态请求参数的声明、聚合校验和类型化导出，Jakarta Bean Validation 继续负责 DTO 与 JSON 请求体；项目接受两套校验入口并存的学习成本，以避免用 DTO 模型强行表达动态参数结构，也不恢复旧 JSON Schema、全局 Context 或静态应用上下文链路。
