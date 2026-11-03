# Summer 4 异常统一：工程验收记录

**工程技术验收通过。** 实现提交 `f67d7617c482b5cca9359f34404fb22ae38b594f`；T00完成需求／集成与M4实际验收，T04完成独立Spec／M5技术质量验收。AC01–AC16技术验收闭合。本目录由后续仅增加验收文档的提交保存，原始产品源码、构建及资源与已测tree一致。

## 配置与范围

需求基线：[规格](../../specs/summer-exception-unification-spec.md)、[ADR 0035](../../adr/0035-reuse-summer-exception-for-application-failures.md)。父需求为 GitHub #173；子票 #174–#189 按原生依赖关系执行，父需求保持开放且正文不修改。

实际测试的产品 tree 为 `907687818954065d356964df2007eb108ddebef1`，实施起点 HEAD 为 `29d027cd68a786417d771749a83350d9779d3929`。候选的 228 个相关变更经过来源审核；现存用户分页改动与客户文件格式改动不进入候选，工作区原稿及实际 Git index 字节保留。实现提交的tree恰为上述已测tree；本目录是另一个docs-only提交的文档增量，不冒充新的产品测试批次。

保留 `SummerException` 与实际字段校验子类 `FieldValidationException`，退出 101 个原有专用类型。异常仅携带错误码、显式消息、消息参数、字段明细和 cause；状态由 MVC 适配，不增加任意信息容器。原生异常与协议、恢复及事务边界分别保留。

## 验证

|正式分组|tests / failures / errors / skipped|新鲜 suite|耗时|退出码|
|---|---|---|---|---|
|core-b02：全仓 test + assemble；原生依赖另组|886 / 0 / 0 / 0|208|1035.951秒|0|
|redis-b02：四个认证原生 Redis 测试类|48 / 0 / 0 / 0|4|66.545秒|0|
|mysql-b02：七个原生 MySQL 测试夹具|24 / 0 / 0 / 0|7|106.047秒|0|

三个分组属于同一产品 tree，均未复用旧 class 或结果，configured/after 输入校验通过、源码 delta 为空、integrity_error 为 null。219份 XML 没有跨分组 suite 重叠，共报告958次用例执行。AuthorizationCorrelationHttpTest 的两组参数化显示名各出现两次，只有956个不同的 classname#XML name 导航键；以 module/XML/testcase ordinal 保全全部执行，不把958写成不同方法数，也不覆盖同名参数用例。

最终归档覆盖14个制品模块的29份 JAR。13个内嵌框架库与各自归档库逐 SHA 相等，Sample 普通包与可执行包730个应用条目逐字节一致，40个迁移资源保留。最终 manifest SHA 为 `2119063e783efbbe6672de16fac7a05b04814f523a0c397463773f838b6baa56`。旧 Common 客户端不变字节码与新客户端在最终 JAR 上运行成功，字段类型的三种源码/二进制结果符合下表。

先实际启动最终完整可执行包，再启动已归档 M2 完整检查点，二者均通过 health 200/UP、customers 401/SUMMER_UNAUTHORIZED 和头体 requestId 一致性检查；各自进程已终止，随机自有 MySQL 库删除并验证不存在。M2 manifest SHA 为 `3c4cbf88bd039f28ed37dbdfa8a8e1867f3469b7158d0090a5b0c2dfb2e63d0a`。单独启动检查禁用 Redis 功能，只使用本包原生 MySQL；真实认证 Redis 由独立48项分组证明。七个 MySQL 夹具的自有库亦全部记录 claimed→removed-and-absence-verified，未扫描或删除未知库，未清空共享 Redis。

实际环境为 JDK21.0.12+8、Gradle9.0.0、固定 docker-desktop/local-base 的 MySQL9.7.1、Redis8.8.1；镜像摘要、Pod UID、命令、seal、输入、XML、客户端及制品摘要见最终 evidence.json。凭据只在所需 worker/子进程内存消费，不保存值或凭据摘要。

真实 SSE 并发测试在测试上下文使用 Hikari40 以容纳32个长连接及管理请求，验证该夹具下的限额和关闭后的连接槽释放；慢订阅者隔离由单独的服务级模拟测试验证。上述结果不表示生产默认连接池10已验证支持32个并发长连接。

## 追踪与度量

|指标|实际结果|
|---|---|
|需求设计／测试映射|14/14，100%|
|用户故事追踪|32/32，100%|
|迁移闭合|103/103，100%|
|受保护契约覆盖|489/489，100%；280项直接匹配断言＋209项明确批准等效证据|
|不合规自有异常残留|0|
|开放阻断缺陷|0|
|必需验证未执行项|0|

489项按具体错误码／有效状态／入口或明确共享协议边界定义，分为268个等效组，不是489次独立HTTP执行。原168个M0源入口、336项断言观察与23补充组保留各自含义，不机械相加。插件diagnostic的blank-path 400可达性未建立，排除该未确认导航；diagnostic 409和summary非ENABLED 409分别保存狭义组合证据，后者不与enable事务／状态语义合并，不声称其失败已单独经HTTP执行。

[双向矩阵](traceability-final.json)保存32US、14R、8D、12T、16AC、16票、103类型、194消费者、实现提交、489契约、实际XML执行及18缺陷之间的正反向查询。[契约目录](contracts-final.json)保留直接断言、等效裁决、guard和静态不变性来源。[矩阵校验](traceability-final-validation.json)的schema形状与外键检查均为0错误；校验使用所需关键字的标准库求值器与语义断言，不宣称使用未安装的外部metaschema引擎。

各分组分别报告，只有同一产品 tree、无重复的 XML 用例可以求并集。历史定向批次、模型测试和诊断探针不累加为全量。既有两个默认提前返回的外部身份链占位用例没有完整外部旅程信用，不用它们替代本次实际 BFF HTTP 契约证据。

## 兼容与发布影响

|项目|实际影响与处理|
|---|---|
|旧 Common 三构造器|保留原调用含义；旧 API 编译客户端须以原字节码在最终 Common JAR 上实际运行。新增显式文字使用命名工厂，避免抢占旧调用。|
|字段明细二进制|旧 `FieldValidationException$Violation` 已删除；旧字节码预期发生 `NoClassDefFoundError`，调用方须迁移并重新编译。|
|字段明细源码|直接限定 `FieldValidationException.Violation` 的部分源码可因静态嵌套类型继承重编译；旧 canonical import 会编译失败，应导入 `SummerException.Violation`。两种源码结果分别验证。|
|101 个领域类型|原定义均为包可见；仍属实际类型删除，不承诺反射或同包非标准消费者兼容，不保留过渡子类。|
|手工装配 CachedBodyFilter|旧默认、int、mapper 构造器签名保留；未配置 MVC resolver 的超限分支现在以 SummerException 为 cause 抛 ServletException 并终止链，旧 ObjectMapper 不再直接渲染错误 JSON。手工装配者迁移至 `CachedBodyFilter.withExceptionResolver(int, HandlerExceptionResolver)`。框架自动配置已使用该入口，保留原 413 JSON 契约。|
|HTTP 与协议|已有 JSON 状态/错误码/文字/requestId、权限、CSRF/OAuth、Raw/Range/SSE/WebSocket/提交后边界按独立契约分别验收；不把共享渲染代表用例称为每条路由均实际执行。|
|版本与回退|仍为 `4.0.0-SNAPSHOT`，未推送、发布或部署。升级与回退以整套归档制品为单位，禁止新 Common 与旧调用模块混配。M2 是基础层已验证检查点，不是实施前 M0 的完整运行版本。|

## 缺陷与过程

保留原失败批及复测链。core-b01 的实际结果为 866/1/0/0，唯一失败是 Modulith 架构门禁：定制输出目录不符合 ArchUnit 默认测试目录识别，误导入 255 个测试类；实际探针的 18 条违规与正式失败逐条一致。恢复标准 build/classes 目录布局，仅修改验证工具，不改变产品模块规则或跳过测试；T03 独立复核通过后用全新 seal 完整重跑。生产类对照的零违规只用于归因，不替代正式门禁。

18项缺陷保留初始失败及整改／复测链；T00逐项复核关闭，T04按实际执行ID或工具负例与非作者报告独立确认无剩余阻断。代码、测试与验证工具的作者排除和独立来源分别保留。

额外执行的 `git diff-tree --check` 退出2，报告13处文件末尾空行，没有其他诊断。独立复核确认它们来自精确删除末尾异常类时保留的非目标原字节，且无仓库强制禁止规则；本次技术验收接受这一非阻断格式取舍。该检查不标为通过，不豁免其他差异诊断，也不为此改动已冻结产品。

[变更记录](change-records.json)保留CR-EX-0001：按真实调用给任务10补任务08／11原生依赖，不改变范围或响应契约；旧43条依赖关系及阶段出口保留于本地原始记录。类型删除在01–14迁移出口后执行，再进行本次固定候选集成。

|阶段|实际出口与责任|
|---|---|
|M0|T00固定真实HEAD／源码差异、103类型及194消费记录；现存用户改动分别保留。|
|M1／M2|基础契约与HTTP边界实际集成；200项基础验证和完整29JAR检查点形成M2，明确不是M0。|
|M3|分域迁移分别交接与非作者复核；15在此前置出口后删除101类型并实际118项定向验证。|
|M4|T00在同一产品tree运行core／Redis／MySQL958次执行，完成API、同版制品及后置整体M2回退。|
|M5|T04独立Spec／质量接受，T00需求／集成接受；AC01–AC16实际技术验收、七项度量和风险记录闭合。|

[规范审阅](final-standards-review.md)、[Spec／质量验收](final-spec-quality-review-T04.md)、[制品／API／回退独立复核](independent-final-artifacts-review-T03.md)与[T00实际角色验收](T00-integration-acceptance.json)分别保存作者排除和实际执行者；新增阻断问题为0。文档落位另经非作者精确复核，最终提交与工单关闭回执写入[子票189](https://github.com/abluepoint/summer/issues/189)。

回退启动使用各自新建的自有测试库，验证完整归档集合及核心错误契约，不宣称已对同一生产业务数据库完成降级恢复。公共Field二进制／canonical import、手工Filter装配、测试池容量、两个默认门控占位、13处EOF格式取舍及SNAPSHOT发布边界均按上文接受并保留。

T00 负责本次需求/集成技术验收与 M4，T04 负责非作者技术质量复核与 M5；作者范围由其他聊天的独立记录覆盖。此处的实际工程角色记录不是组织级 CMMI 认证、正式发布批准或人类签字。组织模型版本、成熟度目标与组织级正式验收保持未指定。

## 证据保存

[证据索引](evidence.json)与[文件摘要](manifest.json)绑定实际版本、命令、输入、环境、测试和制品；[缺陷台账](defects.json)及T04等冻结质量记录保持原字节；T05报告仅重定位相对链接，原件摘要和逐链接映射保存在文件摘要中。测试原XML、seal、原始失败、完整JAR和各自工具继续保存在本地项目 `.scratch/exception-unification/execution/`，未推送或发布；本目录摘要不等于远程已托管这些大制品。部分原始记录的remaining字段是采集时点快照，当前完成状态以本记录、两角色实际验收和最终矩阵为准。

本工程验收按规格执行项目级需求、评审、配置、风险、验证及度量记录，不代表组织CMMI成熟度认证或正式发布批准。
