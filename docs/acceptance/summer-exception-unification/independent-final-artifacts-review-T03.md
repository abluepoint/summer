# T03 最终制品、API 与收到的运行证据独立复核

Standards：**0 项**。Spec：**0 项**。结论限于本报告所列固定输入与原始证据，不代签整体覆盖率、组织审批或发布。

本人不是所审制品工具/API夹具的作者。本轮只用 Python 标准库读取文件、JSON、ZIP、SHA 与 XML，未运行 Java、Gradle、数据库、启动/归档/验证主程序，也未修改产品源码、工具、旧结果、其他报告、HEAD、index 或 Issue；未使用子代理。动态行为结论来自原执行记录及执行逻辑的独立复核，未声称本轮重新联网执行。

## 固定版本及封印

- 最终产品 tree：`907687818954065d356964df2007eb108ddebef1`。
- 最终制品 manifest SHA-256：`2119063e783efbbe6672de16fac7a05b04814f523a0c397463773f838b6baa56`。
- 最终 fat JAR SHA-256：`2d0f3f95f220ea90bed603399f296961670ca5ab7692af23d0ceaad947d71a77`。
- 来源：`final-artifacts-b02/candidate-jars/manifest.json` → `20261103T084452Z-4e4e3f6f/seal.json` → `final-core-b02/candidate-summary.json`。manifest/init 的原字节摘要与 seal、before、after 全部相符；before 保存的 seal/overlay 与原件直接相等。seal 保留准备时的 `prepared-not-executed` 状态，实际执行依据是同目录 execution.claim、正式 summary 和新鲜结果，未凭该准备字段推定通过。
- 三批均核对独立 seal/claim/命令、完整标准 build 目录、前后输入见证、783 个 active inputs 当前逐 SHA 无漂移及四个 helper 摘要。core 同批命令包含 `test assemble`，禁止缓存/结果复用，归档来源严格位于该 seal 的 libs 中；原 JAR 修改时间均不早于 claim。

## 最终制品

独立核对 29 个互异归档 JAR、14 个 artifact module：原构建文件与归档的大小/SHA 全等，实际 libs 集合与 manifest 完全一致；逐个 ZIP CRC 通过、无重复条目。13 个非 sample 库与 fat JAR 中全部 `BOOT-INF/lib/summer-*` 名称集合相等，内嵌完整 JAR 字节摘要逐项一致，未混用 M2 模块。

plain JAR 的 730 个非目录、非 manifest 应用条目逐字节等于 fat JAR；其中 40 个 SQL 迁移资源。唯一明确的根资源映射为 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`，其余应用条目映射到 `BOOT-INF/classes/`。14 个 sources JAR 中的全部 483 份 Java 源码逐项等于封印候选的 candidate_inputs 摘要。独立计算与 `final-artifacts-verified-b02.json` 全部字段相等。

## 原始正式 XML

| 批次 | tests | failures | errors | skipped | suite |
| --- | ---: | ---: | ---: | ---: | ---: |
| final-core-b02 | 886 | 0 | 0 | 0 | 208 |
| final-redis-b02 | 48 | 0 | 0 | 0 | 4 |
| final-mysql-b02 | 24 | 0 | 0 | 0 | 7 |

三个正式批次 exit 0、configured/after attestation 均 true、integrity_error 均 null。逐份归档 XML 与相应唯一 build 中的原始 XML 摘要相等，原件时间不早于各自 claim；解析 suite 属性、每个 testcase 与失败/错误/跳过节点，结果与 candidate-results 逐行及 summary 一致。合计 958 次 testcase 执行、219 个互异 suite；display key 956，不能把执行次数当成唯一路由/情景覆盖率。原 core-b01 的失败不被本报告覆盖或改写。

7 份原生 MySQL 资源证据逐 SHA 核对，owner 与文件 stem 对应，均含 claimed 后的 `removed-and-absence-verified` 终态；它们属于正式 MySQL seal 的 runtime。这里只复核原始清理证据，没有重新连接数据库或查询现存 schema。Redis 48 是独立正式分组，不能由下面禁用 Redis 的启动检查替代。

## API 与兼容性

`final-clients-b02/result.json` 与 `commands.json` 的 11 条原始命令逐项相等，9 条 exit 0、2 条预期 exit 1；均固定 Gradle JDK 21.0.12，所有保存 class 的 major 为 65。五份 M0 API 源码逐字节计算 Git blob SHA-1，与 pre-implementation-blobs 中五个固定对象 ID 全等，不运行 Git 或 Java 取得结论。

1. **旧 Common 客户端字节码通过。** 三个既有构造契约客户端仅对 M0 API 编译，执行 classpath 为旧客户端目录与最终归档 Common；没有旧 API classes。保存的客户端 class 还与 M2 验证时客户端 class 逐字节相同，最终执行 exit 0。
2. **新公共客户端通过。** 仅使用最终归档 Common/MVC 编译和执行；覆盖显式消息/cause、参数双向防御复制、空/无显式消息、共享 violations 防御复制及 Field 继承关系。源码断言、两条原始成功命令和零诊断相符。
3. **旧 Field 嵌套类型字节码不兼容。** 对 M0 API 编译后，用最终 Common/MVC 执行 exit 1，原始诊断是 `NoClassDefFoundError: ...FieldValidationException$Violation`。旧 class 确含该 owner，最终 MVC JAR 确无该 class。
4. **同一直接限定旧源码可以重编译并执行。** `FieldValidationException.Violation` 通过继承找到共享类型，新 class 指向 `SummerException$Violation`，该 class 实际存在于最终 Common JAR；编译/执行均 exit 0。
5. **旧 canonical import 源码不兼容。** 对 M0 API 编译 exit 0；对最终库编译 exit 1，原始 javac 诊断为 `import requires canonical name for Violation`，不是泛称所有旧 Field 源码都失败。

上述预期不兼容与已接受的公开 API 兼容说明一致，本范围不另列为新缺陷。五份 M0 API 夹具不代表完整 M0 运行制品或 M0 回退。

## 收到完成证据后追加的最终启动与 M2 整套回退

首次核对时 M2 b03 仍在运行，JSON review_phases 保留当时待验收状态及追加前报告摘要；收到真实完成通知后才补本节。

最终 `final-startup-b02/result.json` 绑定上述最终 tree/manifest/fat SHA；归档完整集合直接 `java -jar` 启动，原结果记录原生 MySQL、自有随机 schema 创建确认及删除、whole_set/passed 为 true。health 为 200/UP；受保护 customers 为 401/SUMMER_UNAUTHORIZED，requestId 为 artifact-contract。独立回读启动逻辑确认同时断言响应 body/header requestId；原结果仅保存合并后的值。自有 PID 收尾 exit 143。

随后 `m2-rollback-startup-b03/result.json` 与 `final-whole-set-rollback.json` 指向旧整套 M2：tree `a064716563acb9bc62c72876cf15a3922bcb8d89`，manifest SHA `3c4cbf88bd039f28ed37dbdfa8a8e1867f3469b7158d0090a5b0c2dfb2e63d0a`，fat SHA `4c1436db7f3a10a39a22835c95dabd32c960e3df6ed29dc315ab1e95516ada54`。独立重新检查旧 29 JAR/14 module 的归档 SHA、CRC、无重复条目，13 个内嵌库与旧集合完整字节相等，810 个旧应用条目与 40 个迁移资源一致。此旧版本的 810 不与最终版本 730 混用。

M2 原结果实际 passed，health200/UP、customers401/SUMMER_UNAUTHORIZED/requestId 均相同；使用与最终启动不同的自有 schema/PID，schema 删除确认、自有进程 exit143。文件时间显示最终启动完成 result 保存先于 M2 驱动提取，后者先于 M2 最终 result 保存，与整套链及实际同步启动逻辑吻合；没有以早先 M2 b02 代替此后置回退。

该回退范围是**最终启动之后运行完整归档 M2 基础集合**。不是完整实施前 M0；两次独立启动使用各自新建库，不证明对最终生产数据库做降级恢复。两次 standalone 都明确禁用 Redis 特性，只证明该配置下启动/401/清理；正式最终 Redis48 证据单列。组织或发布批准仍为 false。

## 审阅输入与边界

机器证据见同目录 `independent-final-artifacts-review-T03.json`：保存 357 份读取输入 SHA、三个 seal/XML 核算、五个 M0 blob、API class/诊断、7 份原生清理证据、启动/回退摘要及时间链。收尾再次逐 SHA 比较，delta 为空；产品活动输入仍由 seal 的 783 项逐项核验。

实际 Git HEAD 仍为 `29d027cd68a786417d771749a83350d9779d3929`，与未提交候选 tree 分开记录；真实 index 1450 项，原字节等于 `01/index-file-post-test.snapshot`。本报告不承接整体路由分母、最终 AC16/组织人类签字、提交推送或发布许可。
