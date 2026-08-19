# Do not model a Maven parent in Gradle

Summer 4 删除旧 `summer-parent`，且不在 Gradle 中创建等价父模块。模块聚合由 `settings.gradle.kts` 管理，共享构建规则由根构建或未来的 `build-logic` convention plugins 管理，版本目录与 `summer-dependencies` 依赖平台分别承担内部版本声明和对外 BOM 约束。项目选择遵循 Gradle 的组合模型，而不是延续 Maven 继承结构，避免两套构建语义并存和旧依赖基线继续影响 Summer 4。
