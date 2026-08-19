# Curmerce Web

Curmerce 的用户商城前端，当前覆盖登录、注册和收货地址管理的基础链路。

## 参考与边界

页面布局和组件职责参考 Mercur storefront 固定 revision `1c667f77a24dc324d9bb6e057d4334d580d16db9`，但本工程不复用 Mercur 的 Medusa API、支付逻辑或业务类型。请求、类型、认证和错误处理均按 Curmerce 的 Spring Boot `/app-api` 接口实现。

固定 revision 的源码通过 `reference-submodules/mercur` 的 Git 对象恢复验证；为避免覆盖参考子模块当前本地状态，没有修改其工作树或根仓库子模块指针。

## 本地运行

```bash
npm install
cp .env.example .env.local
npm run dev
```

默认访问 `http://127.0.0.1:3000`，后端默认地址为 `http://127.0.0.1:48080`。
