# 数据持久层约定

项目所有服务统一使用 MyBatis-Plus 访问 MySQL，禁止在业务代码中引入 Spring JDBC、`JdbcClient`、`JdbcTemplate` 或直接使用 JDBC API。

## 现有实现

- Trade/Fund 核心领域使用 `BaseMapper`、实体类和 XML/注解 Mapper。
- Platform 管理接口和 Fund 对账查询通过 `MybatisPlusClient` 调用 MyBatis-Plus `SqlRunner`，用于承接复杂查询和动态条件。
- 数据库事务使用 Spring `@Transactional`，事务边界内仍必须通过 MyBatis-Plus Mapper 或 `MybatisPlusClient` 访问数据库。

## 新增代码规则

1. 新表必须创建对应 `@TableName` 实体和 `BaseMapper`。
2. 单表增删改查优先使用 `LambdaQueryWrapper`、`LambdaUpdateWrapper` 和 Mapper 方法。
3. 多表、聚合或数据库特性 SQL 放入 Mapper XML/注解，不得在 Controller 中拼接 JDBC SQL。
4. 分页使用 MyBatis-Plus `IPage`/`Page`，并统一应用数据权限过滤。
5. 新增依赖和代码提交前检查不得出现 `org.springframework.jdbc`、`JdbcClient`、`JdbcTemplate`、`DriverManager` 等引用。
