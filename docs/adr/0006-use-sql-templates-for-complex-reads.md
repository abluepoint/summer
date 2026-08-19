# Use SQL templates for complex reads

Summer Data JPA 将 Freemarker SQL template 保留为长期一等查询能力，用于复杂查询和读模型；实体写入、简单 CRUD 与聚合持久化继续优先使用 Repository。项目接受 ORM 与显式 SQL 双轨带来的学习、维护和数据库适配成本，以换取复杂读取逻辑的可控 SQL、动态组合能力和清晰性能边界，同时不把模板 SQL 扩张为完整 ORM 的替代品。
