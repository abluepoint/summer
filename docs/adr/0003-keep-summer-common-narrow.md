# Keep Summer Common narrow

`summer-common` 只承载跨能力稳定契约，例如上下文、错误、消息和响应标记，不接收 MVC、Data JPA、Security 实现或无明确归属的通用工具。项目选择牺牲把共享代码集中存放的便利，以保持基础依赖稳定、避免循环依赖，并防止所有能力通过 Common 形成隐性耦合。
