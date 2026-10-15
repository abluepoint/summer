# 必填请求头错误响应修复

T26独立验收初始化时，成员创建请求缺少Idempotency-Key，真实HTTP返回500。补头后同一有效请求创建成功；这暴露了Summer MVC共享错误映射问题，不属于任务调度缺陷。

MissingRequestHeaderException落入通用Exception分支，服务端把客户端缺失头当内部错误。新增窄化异常处理：返回400、SUMMER_INVALID_ARGUMENT和以头名定位的字段错误，保留requestId；不把异常原文或请求头值写入响应。后端授权和业务逻辑保持原样。

定向MockMvc绑定入口先取得 **2/1/0/0**有效红灯，修复后 **2/0/0/0**、退出0。最初编译阶段0项失败独立保留：jsonPath匹配器需要模块未引入的Hamcrest，改用已有Jackson/AssertJ断言，不增加依赖。

新冻结候选e1a0380ffd187f95c4e517a92412f23d429c828647b7b4f5e4a498b856f4057d的534个生产输入在构建前后一致，迁移资源匹配。实际MySQL/独立Redis/PKCE复验 **2/0/0/0**、退出0：缺头返回400且SUMMER错误、字段名、响应头/body requestId正确；补头后同一请求201。没有重跑T26已绿矩阵。

原两批真实500、测试红灯、修复绿灯、候选及实际结果均保存在.git/summer4-closeout-20261015/mvc-header-fix与t26-live。公开结果见 [required-header-contract-result.json](required-header-contract-result.json)。自有schema、账号、Redis容器、JVM和端口已核验清理，敏感日志检测零命中。

复现脚本scripts/acceptance/required-headers/run.py复用T26的隔离环境。使用SUMMER_TASK_CONFIGURATION_EVIDENCE_DIR指定新目录，SUMMER_TASK_CONFIGURATION_CANDIDATE及其SHA256指定不可变候选；凭据仅在内存、环境或stdin，不写命令参数。新增MockMvc测试直接覆盖Spring缺失头绑定及已有处理器入口，不以调用新方法的镜像测试替代HTTP行为。
