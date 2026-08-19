# Keep Automator outside the framework repository

`summer-automator` 是独立的代码生成工具，不属于 Summer 4 的运行时框架、自动配置、Starter、依赖平台或参考应用，因此从本仓库活动分支移除且不纳入 Gradle 构建。若该工具继续发展，应作为独立产品和独立仓库维护；项目依赖 Git 历史保存旧实现，不在 Summer 4 中建立 legacy 或 archive 代码目录，以保持产品边界和依赖扫描结果清晰。
