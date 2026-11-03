# 异常统一迁移与兼容说明

本变更属于 `4.0.0-SNAPSHOT` 开发线，尚未发布或部署。需求基线为 [规格](../specs/summer-exception-unification-spec.md)和 [ADR 0035](../adr/0035-reuse-summer-exception-for-application-failures.md)。验收结果应以冻结源码、实际测试批次和制品摘要为准；相同 SNAPSHOT 文件名不能证明属于同一套构建。

## 应用调用

普通应用失败使用 `SummerException` 和明确的 `ErrorCode`，通过错误码表达分类；显式文字和原始 cause 可以同时携带。保留已有三个公共构造器的调用语义，新增文字入口采用命名工厂，避免给旧的 null 调用增加重载歧义。

```java
throw SummerException.withMessage(errorCode, "保留的业务消息", cause);
```

显式消息非 null 时按字面输出，包括空串、空白和占位符字符；null 沿用现有消息解析器、语言和默认文本。消息参数数组和 violations 列表保存结构快照，不承诺对其中任意对象进行深复制。cause 用于内部追踪，不进入错误信封。

只有实际携带固定字段明细的校验失败使用公共 `FieldValidationException`，它继承 `SummerException` 并复用同一份信息。类型名称含 Validation 不构成保留资格。字段明细采用公共类型：

```java
new FieldValidationException(List.of(
        new SummerException.Violation("name", "不能为空")));
```

不为状态、资源、权限、幂等或重试新增异常子类；不增加任意 data/details/context 属性。HTTP 状态由 MVC 的 typed `ErrorStatusResolver` 适配，异常本身不保存 HTTP 字段。JDK、Spring 和第三方异常仍按原生边界处理。仅对既有应用边界已经公开的原生业务拒绝，保留必要的窄转换及原 cause；未知技术错误使用安全兜底文字。

## Java 兼容边界

- 旧 Common 构造调用与新工厂分别有公共调用夹具；还将先按 M0 API 编译的 Common 客户端字节码直接运行于新 Common JAR，检查链接与语义。[旧客户端](../../scripts/acceptance/LegacySummerExceptionClient.java)和[新客户端](../../scripts/acceptance/UnifiedExceptionClient.java)可独立检查。
- 旧 `FieldValidationException$Violation` 二进制类已删除。引用该类的既有字节码会发生 `NoClassDefFoundError`，必须迁移并重新编译。
- `new FieldValidationException.Violation(...)` 形式的部分旧源码可因静态嵌套类型继承而重新编译，实际绑定到 `SummerException.Violation`。这不恢复旧二进制类，也不构成保留兼容别名。
- 显式 `import ...FieldValidationException.Violation` 要求旧 canonical 名称，重新编译会失败；应改为 `import ...SummerException.Violation`。不能笼统声称所有旧字段校验源码均失败或全部兼容。
- 已退出生产的领域异常类不提供临时兼容子类。外部源码、字节码或反射配置若引用这些类，须同步迁移；发布前按仓库版本政策处理公开 API 变化，不把 SNAPSHOT 构建当作兼容补丁版本发布。

## 升级与回退

升级时使用同一冻结构建的完整模块组合，核对每份 JAR 及可执行包内库的 SHA-256；不得混用新 Common 和旧调用模块。数据库结构、既有数据、接口路由与响应字段不是本变更的迁移对象。

回退使用完整、已验证的源码或制品检查点。当前已归档的 M2 检查点是基础层通过后的整套版本，**不是实施前的 M0 完整运行版本**。其 29 份 JAR 和可执行包应整体使用；回退演练另核健康、核心错误响应与精确自有测试资源清理。单个 API 客户端通过、JAR 存在或静态检查不等于回退验收。

本说明不授权推送、发布、部署或关闭父需求 Issue。最终工程验收记录另外说明实际运行范围、跳过/门控、独立审阅和剩余风险，不替代组织 CMMI 评估或人类签字。
