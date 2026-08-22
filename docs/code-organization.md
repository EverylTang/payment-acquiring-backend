# 代码分层约定

## 后端

每个服务按职责分层，包路径固定为：

```text
<service>/service/controller   HTTP Controller、请求响应 DTO
<service>/service/service      应用服务、定时任务、消息消费者
<service>/service/mapper       MyBatis-Plus Entity、Mapper、Repository
<service>/service/domain       领域对象和状态机
<service>/service/config       配置属性
<service>/service/security     认证与授权
```

Controller 只负责协议解析和响应编排，业务规则放在 `service`，数据库访问只能通过 `mapper`。Gateway 保持 `filter` 层，因为它只处理网关过滤器。

## 前端

前端业务页面按模块放在 `payment-acquiring-frontend/src/modules/`：

```text
modules/auth
modules/merchant
modules/product
modules/permission
modules/user
modules/refund
modules/operations
```

`App.vue` 只负责应用壳、菜单和模块切换；共享请求和认证逻辑仍放在 `src/api.ts`、`src/auth.ts`。新增业务页面必须进入对应模块目录，模块间不得通过相对路径互相引用页面组件。
