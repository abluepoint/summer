# Summer 4 Framework

Summer 4 是面向企业应用的可复用基础框架产品线。本文件定义项目讨论、设计和文档中使用的统一语言。

## Language

**Summer 4**:
面向企业应用提供通用基础能力的可复用框架产品线，不代表某个具体业务系统。
_Avoid_: 迁移项目、兼容层、业务应用平台

**Summer Sample**:
用于展示和验证 Summer 4 能力的参考应用，不属于框架本身的产品能力边界。
_Avoid_: summer-data-jpa-test、产品应用、测试模块

**Summer MVC**:
Summer 4 中统一 HTTP 请求、参数、校验、错误与响应语义的框架能力。
_Avoid_: Web capability、summer-web

**Parameter contract**:
Summer MVC 中覆盖查询/表单参数、JSON body 和路径变量的长期传输字段契约，用于声明来源、presence、nullability、Style 规则和未知字段策略，并产生经过验证的类型化值。
_Avoid_: 旧版 Field 兼容、JSON Schema 校验、DTO 校验替代品、隐式来源优先级

**Style**:
参数契约中可复用的参数语义，负责验证原始参数并将其导出为约定类型。
_Avoid_: 单纯校验器、单纯类型转换器

**Unified response**:
由 Summer Common 定义、由 Summer MVC 应用于应用 JSON 端点的默认响应契约；协议端点、资源传输和显式声明的原始响应不属于该契约。
_Avoid_: 全局无条件包装、协议响应格式

**Raw response**:
应用端点显式选择不使用统一响应、直接保留原始响应语义的结果。
_Avoid_: UnwrappedResponse 标记、默认响应模式

**Request correlation**:
Summer MVC 为一次请求维护稳定 requestId，并通过请求属性、响应头和日志上下文关联该请求的处理记录。
_Avoid_: SummerContext、全局请求数据袋、业务上下文

**Framework error**:
由 Summer 4 定义的跨能力失败，使用保留的 `SUMMER_` 命名空间和语义化错误码；业务应用必须使用自己的命名空间。
_Avoid_: `SUMMER-` 前缀、HTTP 数字后缀、业务错误占用 `SUMMER_`、通过错误码字符串猜测传输状态

**Error category**:
由框架错误和业务错误共同声明的传输无关失败类别，供 MVC 等适配层映射为具体协议状态。
_Avoid_: HTTP 状态码、错误码字符串规则、业务命名空间

**Capability module**:
承载一组内聚、可复用框架能力并定义其稳定边界的模块。
_Avoid_: Core module、领域模块、业务模块

**Foundation capability module**:
只承载多个能力共同需要的稳定契约，并作为其他能力模块依赖基础的能力模块；Summer Common 属于此类。
_Avoid_: 通用工具模块、Common 工具箱、共享实现模块

**Autoconfiguration module**:
将一个能力模块按条件装配进应用的模块，不拥有该能力本身。
_Avoid_: Starter、能力实现模块

**Starter**:
单项能力的依赖接入入口，只表达该能力所需的依赖组合。
_Avoid_: 能力模块、自动配置实现

**Aggregate starter**:
组合多个 Starter 的依赖入口，用于一次接入一组 Summer 4 能力。
_Avoid_: 聚合能力模块、业务应用

**Dependency platform**:
集中发布 Summer 4 及其基础技术版本约束的依赖入口；`summer-dependencies` 是唯一依赖平台。
_Avoid_: summer-parent、Gradle 父模块、Starter

**Summer Security**:
Summer 4 的可选安全能力，覆盖资源保护、OIDC 集成以及可独立启用的授权服务。
_Avoid_: 默认身份平台、仅限资源服务的适配层

**Authorization Server capability**:
Summer Security 中负责客户端授权和令牌签发的可选能力；生产使用时必须连接持久化身份、客户端与密钥设施。
_Avoid_: Demo 登录服务、默认开启的身份中心

**SQL template**:
Summer Data JPA 中用于表达和渲染复杂读取查询的一等查询契约，与 Repository 持久化模型并存。
_Avoid_: ORM 替代品、写模型脚本、临时 SQL 片段

**Result projection adapter**:
SQL template 内部将查询结果适配为标量、Map、Record/构造器 DTO、Spring Data 接口投影或原始 Tuple 的窄化组件，并显式将列别名规范化为 camelCase。
_Avoid_: 通用 TupleMapper、独立对象映射框架、隐式别名规则
