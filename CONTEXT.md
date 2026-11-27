# Summer 4 Framework

Summer 4 是面向企业应用的可复用基础框架产品线。本文件定义项目讨论、设计和文档中使用的统一语言。

这里的词汇描述已选定的架构语义，不代表代码已全部实现。Summer UI 与 Customer username 来自 ADR 0014–0016；当前迁移状态见 [现状与目标差异](docs/architecture/现状与目标差异.md)。

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
Summer MVC 中覆盖查询/表单参数、JSON body 和路径变量的长期传输字段契约，用于确定来源、presence、nullability、Style 规则和未知字段策略，并产生经过验证的类型化值。每个字段的契约必须明确、完整，来源由框架按实际请求位置自动推断。
_Avoid_: 旧版 Field 兼容、JSON Schema 校验、DTO 校验替代品、隐式来源优先级

**Style**:
参数契约中由应用显式定义、由字段按名选用的可复用参数语义，集中定义类型、格式及长度、范围等业务约束，负责验证原始参数并将其导出为约定类型；名称由应用决定。
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

**Request log snapshot**:
为记录一次 HTTP 请求及其处理结果而生成的数据副本，可独立于业务请求数据进行脱敏与限长后交给日志能力输出。
_Avoid_: 原始请求流、业务请求体、业务审计记录

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

**Summer UI**:
面向 Summer Sample 后端能力的独立管理端前端，用于以中后台界面呈现并操作后端业务资源；它不是 Summer 4 框架能力，也不等同于 Summer Sample 后端本身。
_Avoid_: Summer 4 UI framework、Summer MVC 前端实现、summer-sample-arco-pro 的副本

**Customer**:
Summer Sample 中由不可变 `username` 稳定标识的客户业务资源；`customerId` 是内部数据库标识，`email` 是联系方式，`displayName` 是展示名称，`active` 表示启用状态。
_Avoid_: 以 email 作为资源身份、以 customerId 作为公开路由标识、把停用等同于删除

**Customer username**:
Customer 存续期间不可变且全局唯一的业务标识，长度为 3–64，只允许小写 ASCII 字母、数字、点、下划线和连字符，并且必须以字母或数字开头和结尾；Customer 被永久删除后，该 username 可以重新使用。
_Avoid_: 登录凭据、可变昵称、邮箱别名、大小写不敏感的自由文本

**Customer email**:
Customer 的必填、全局唯一联系方式，使用标准邮箱格式并允许修改；修改 email 不改变 Customer 的 username 或资源路由。
_Avoid_: 资源身份、可重复联系方式、修改邮箱即创建新 Customer

**Customer display name**:
Customer 的必填、非空白、最长 200 个字符的展示名称；创建时必须提供，更新时可省略表示不修改，显式 null 不被接受。
_Avoid_: 用 null 表示清空、把空白当作有效名称、把 displayName 当作资源身份

**Authenticated principal**:
Summer UI 中通过 Spring Security 身份提供商完成认证并访问管理端 API 的安全主体；其概念、生命周期和权限语义遵循 Spring Security，不与 Customer 业务资源建立本次范围内的绑定关系。
_Avoid_: Operator 作为额外领域实体、Customer username 作为登录凭据、因 Customer 生命周期改变认证主体

**Customer activation state**:
Customer 的可恢复业务状态；新建 Customer 默认为启用，`active = false` 表示停用但保留记录且允许重新启用，不影响 Spring Security 的 authenticated principal。
_Avoid_: 创建时指定初始停用、软删除标记、登录账号状态、不可恢复终态

**Customer deletion**:
永久移除 Customer 业务记录且不可恢复的生命周期操作，与可恢复的停用明确区分。
_Avoid_: 将 active 设为 false、可撤销停用、认证主体删除

**Business dictionary（业务字典）**:
平台统一维护和发布的词表，字典编码在全平台唯一，用于业务字段的显示名称转换和选项选择。接口目录通过「服务名称」字典的稳定项值关联服务。字典管理和字典组管理均由平台域授权，选择活动租户后仍按平台菜单资格展示。
_Avoid_: 将单个选项称为字典

**Dictionary item（字典项）**:
业务字典中以字符串值作为业务引用的具体取值，包含值、显示名称和说明；一经发布，其值保持不变。
_Avoid_: 将显示名称当作值、将字典项称为字典组

**Dictionary group（字典组）**:
同一业务字典内字典项的命名子集；同一字典项可属于多个组，各组具有独立于字典默认顺序的组内顺序。以所属字典内唯一的稳定编码标识，组名是可变的显示名称。
_Avoid_: 多个字典的分类、跨字典的字典项集合

**Dictionary linkage（字典联动）**:
根据上游字典的已选值选择下游字典组，从而确定下游可选范围的业务关系。
_Avoid_: 字典项父子树、字典组层级

**Published dictionary version（字典发布版本）**:
单个业务字典一次发布形成的完整内容，包含字典项及字典默认排序、全部字典组及其成员和组内排序。发布后该版本的内容固定。
_Avoid_: 字典组独立发布版本、由不同版本拼装的字典内容

**Dictionary item deactivation（字典项停用）**:
字典项退出可新选范围、同时保留已有业务值的显示名称转换能力的状态。
_Avoid_: 删除字典项、停用字典本身

**Effective dictionary version（字典生效版本）**:
当前选定用于业务消费的字典发布版本，回滚后可指向较早发布的版本。
_Avoid_: 始终等同于最大版本号、草稿版本、启用项集合

**Dictionary draft revision（字典草稿修订号）**:
区分同一字典草稿先后修改的修订标识，与字典发布版本号分开。
_Avoid_: 字典生效版本、已发布版本号

**平台管理员**

已有个人账号加入平台运营名单后取得的身份。平台角色决定可执行的操作，移除最后一个角色仍保留名单资格。移出名单时同时解除平台角色、用户组归属、平台部门归属和负责人职务，个人账号及租户成员关系独立保留。

**平台用户组**

平台管理中可命名、填写说明并绑定多个平台角色的分组。平台管理员管理的“管理员”页签展示每人的所属用户组，可分配多个用户组及个人角色。“用户组”页签按组管理成员。“角色”页签按平台角色查看、分配和移除直接授权的管理员。管理员继承所分配用户组的有效平台角色。撤销某组归属或解绑角色时，个人直接授权和其他组的授权独立保留。

**平台部门**

平台运营机构树的节点。管理员可以暂未分配部门，也可以在一个主部门之外兼任其他部门。每个部门最多一名负责人，由直接成员担任。平台组织的查看、结构维护和人员调配分别授权，部门范围支持全部平台部门、本人平台主部门及下级、指定平台部门及下级。

**Permission（权限）**:
系统中可独立新建和管理、用于建立授权关系的对象，按控制对象分为接口权限、菜单权限和数据权限。权限编码属于权限，接口资源不携带权限码。
权限定义仅由平台维护；租户角色从平台权限目录选择权限，租户只管理本域角色及人员授权。已有租户授权按原域保留兼容读取，只能保留或移除，不隐式转换为平台授权，见 [ADR 0043](docs/adr/0043-platform-only-permission-management.md)。
_Avoid_: 将接口本身等同于权限、将权限组作为授权对象

**Permission group（权限组）**:
支持多级父子结构、可混合包含接口权限、菜单权限和数据权限的分类集合。每项权限直接归属且只归属一个权限组，授权关系仍关联具体权限。
_Avoid_: 角色、直接授权对象、父组隐式授予子组权限

**Permission access mode（权限访问方式）**:
权限中区分公开访问、登录访问和授权访问的属性，用于表达请求者满足该权限访问条件的方式。
_Avoid_: 接口资源的访问方式、接口权限/菜单权限/数据权限等权限类型

**Interface resource（接口资源）**:
独立于权限、作为接口权限关联目标的可调用接口对象。平台统一维护接口资源清单及其历史版本，各租户共用同一份目录。
_Avoid_: 权限本身、接口携带的权限码

**Interface permission（接口权限）**:
控制接口操作是否可执行的权限，与菜单权限分别授权；一项接口权限可关联多个接口，同一接口也可被多个接口权限关联。
_Avoid_: 菜单可见即允许调用接口、接口自身的权限码

**Menu permission（菜单权限）**:
控制功能菜单入口访问、与菜单采用多对多关联的权限；授予菜单权限不会自动授予接口权限。
_Avoid_: 接口调用凭据、隐含接口授权

**Menu manifest（菜单清单）**:
平台统一发布菜单及其所属客户端。菜单通过稳定编码关联平台字典 `menu-client`，历史菜单归入 `web`（Web 管理端）。平台布局在各租户共用，菜单可见性仍由当前身份的菜单授权决定。

**Menu directory（菜单目录）**:
用于组织下级菜单的导航项；仅作为目录展示不代表用户具有该节点对应页面的访问资格。
_Avoid_: 父页面授权、子菜单向父页面继承权限

**Data permission（数据权限）**:
由一个或多个接口权限和一个预设记录范围构成。管理员可按权限组选取接口权限。已接入数据过滤的业务操作按实际命中的接口计算范围，历史授权在重新配置时替换。
_Avoid_: 接口调用许可、将可查询范围自动视为可修改范围

**Self data scope（本人范围）**:
根据记录归属字段确定的范围，例如申请人是当前用户的申请单。
_Avoid_: 一律按创建人判定、在每项权限中配置归属字段

**Department data scope（本部门范围）**:
由当前主体在当前租户内全部有效任职部门确定的记录范围，不受当前选中部门的切换限制。
_Avoid_: 仅当前选中部门、跨租户任职部门集合

**Department tree data scope（本部门及下级范围）**:
由本部门范围所对应的全部有效任职部门及其下级部门共同确定的记录范围，仍限于当前租户。
_Avoid_: 仅当前选中部门的子树

**Specified-department data scope（指定部门范围）**:
由权限配置在当前租户内明确选定一个或多个部门所确定的记录范围，不自动包含所选部门的下级部门。
_Avoid_: 只能选择一个部门、默认等同于主体任职部门、选择父部门即自动授权整棵子树

**Role（角色）**:
用于向用户授予权限的权限集合；需要明确授权的权限通过角色授予，用户与角色建立关联后获得相应权限。
_Avoid_: 仅用于分类的权限组、用户直接授权或显式拒绝清单
