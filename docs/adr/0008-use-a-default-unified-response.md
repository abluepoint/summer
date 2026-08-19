# Use a default unified response

Summer Common 拥有统一响应的数据契约，Summer MVC 将该契约作为应用 JSON 端点的长期默认包装，Summer Security 复用同一错误结构而不维护重复响应类型。项目接受框架对 Controller 返回值施加默认语义的约束，以换取一致客户端体验；OAuth2/OIDC、Actuator 等协议端点以及文件、字节流和 Range 等资源响应自动绕过，应用端点通过 `@RawResponse` 显式绕过。原先仅用于跨模块避免二次包装的 `UnwrappedResponse` 标记不再保留。
