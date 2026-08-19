# Curmerce 订单与退款契约

本文记录当前模块化单体版本的订单、支付、履约和基础退款接口契约。接口控制器使用 `/commerce/...` 路径；实际部署时仍需叠加网关或应用配置中的 API 前缀（例如 `/app-api`、`/admin-api`）。金额单位为分，时间使用服务端 `Asia/Shanghai` 时区返回的日期时间。

## 1. 认证与数据归属

买家接口使用当前登录用户，不接受客户端传入 `memberUserId`。管理后台接口使用管理员 Token，并通过 `tenant-id` 选择租户；商家自助接口使用当前登录商家 Owner，不接受 `merchantId` 或 `storeId`。

常见请求头：

```text
Authorization: Bearer <token>
tenant-id: <tenant-id>       # 管理后台请求
Idempotency-Key: <key>       # 创建订单请求，8-64 位
```

订单、支付、退款详情都必须通过服务层的买家、商家/店铺或管理员范围校验。不存在和无权访问的订单/退款统一返回对应的“未找到”错误，避免泄露跨用户数据。

订单创建会先锁定当前买家已选购物车行，再二次读取幂等记录。这样同一买家同一幂等键的并发重试会在首个事务提交后返回已创建订单，而不是把数据库唯一键冲突暴露给客户端。

## 2. 买家订单与支付接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/commerce/order/create` | 从当前买家已选购物车项创建订单；同一买家复用相同 `Idempotency-Key` 返回原订单 |
| GET | `/commerce/order/page?status={status}` | 查询当前买家订单，可按订单主状态分页筛选 |
| GET | `/commerce/order/get?id={id}` | 查询当前买家订单详情，返回商品与收货地址快照、支付摘要、物流信息和退款摘要 |
| POST | `/commerce/payment/create` | 为当前买家的待支付订单创建或重放模拟支付单 |
| POST | `/commerce/payment/simulate-callback` | 模拟支付渠道回调；当前接口为演示用途，回调请求携带支付单号、回调号和支付金额 |
| PUT | `/commerce/order/cancel` | 取消当前买家的待支付订单，并恢复订单快照库存 |
| PUT | `/commerce/order/confirm-receipt` | 当前买家确认已发货订单收货 |

订单分页响应的摘要字段至少包括 `id`、`orderNo`、`status`、`refundStatus`、金额、商品数量和创建/完成时间。订单详情额外包括 `paymentNo`、`paymentStatus`、`paymentAmount`、`paidTime`、商品快照、收货地址快照、物流字段和退款摘要。

## 3. 买家退款接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/commerce/refund/apply` | 当前买家为本人可退款订单申请退款；申请幂等，首次申请进入 `REQUESTED` |
| GET | `/commerce/refund/page?status={status}` | 查询当前买家自己的退款记录，可按退款状态和订单号筛选 |
| GET | `/commerce/refund/get?id={id}` | 查询当前买家的退款详情 |

退款申请不会直接把退款标记为成功。订单主状态（例如已发货、已完成）和订单售后 `refundStatus` 独立维护；一个订单最多保留一条有效退款记录。

## 4. 商家和管理员退款接口

管理员路径在 `/admin-api` 前缀下：

| 方法 | 路径 | 权限 | 说明 |
| --- | --- | --- | --- |
| GET | `/commerce/refund/page` | `commerce:refund:query` | 查询全租户退款分页 |
| GET | `/commerce/refund/get?id={id}` | `commerce:refund:query` | 查询退款详情 |
| PUT | `/commerce/refund/approve` | `commerce:refund:audit` | 审核通过，进入渠道处理中 |
| PUT | `/commerce/refund/reject` | `commerce:refund:audit` | 驳回退款；驳回备注必填 |
| POST | `/commerce/refund/simulate-callback` | `commerce:refund:callback` | 模拟退款渠道回调 |
| GET | `/commerce/refund/page-own` | `commerce:refund:self-query` | 仅查询当前商家/店铺的退款 |
| GET | `/commerce/refund/get-own?id={id}` | `commerce:refund:self-query` | 查询当前商家/店铺退款详情 |
| PUT | `/commerce/refund/approve-own` | `commerce:refund:self-audit` | 当前商家审核自己店铺的退款 |
| PUT | `/commerce/refund/reject-own` | `commerce:refund:self-audit` | 当前商家驳回自己店铺的退款 |

商家审核记录使用当前登录用户 ID 写入 `reviewerUserId`，而不是由客户端传入。商家查询和审核均通过订单的 `merchantId + storeId` 所有权校验，跨商家请求按“退款不存在”处理。

## 5. 状态定义

### 5.1 订单主状态

| 值 | 名称 | 允许的主要动作 |
| ---: | --- | --- |
| 10 | `PENDING_PAYMENT` 待支付 | 创建支付、买家取消 |
| 20 | `PAID_PENDING_SHIPMENT` 已支付待发货 | 商家发货、买家申请退款 |
| 30 | `SHIPPED` 已发货 | 买家确认收货、买家申请退款 |
| 40 | `COMPLETED` 已完成 | 买家申请基础退款（按当前 MVP 规则） |
| 50 | `CANCELED` 已取消 | 不允许支付、发货、确认收货或再次取消 |

订单创建时扣减库存并保存商品/地址快照。取消只允许状态 10，并在同一事务中恢复快照库存、取消待支付单和更新订单状态。发货只允许状态 20，重复或跨店铺发货会被拒绝。存在申请中、已通过或已成功的退款（`refundStatus` 为 10/20/30）时，发货和确认收货都会被拒绝，避免订单主状态与售后结果互相漂移；发货时间、完成时间、支付时间和退款处理时间统一按数据库秒精度写入。

### 5.2 支付状态

| 值 | 名称 | 转换 |
| ---: | --- | --- |
| 10 | `INITIATED` 待支付 | 创建支付单 → 20；订单取消 → 30 |
| 20 | `SUCCESS` 支付成功 | 仅允许相同回调内容重放 |
| 30 | `CANCELED` 已取消 | 终态，不接受支付回调 |

支付回调必须匹配金额。相同 `callbackId + paidAmount` 的成功回调幂等返回；不同回调号、金额不一致或已取消支付单均拒绝。

### 5.3 退款状态与售后状态

| 值 | 名称 | 转换 |
| ---: | --- | --- |
| 0 | `NONE` 无售后 | 无退款记录 |
| 10 | `REQUESTED` 退款申请中 | 买家申请 → 20 或 40 |
| 20 | `APPROVED` 退款通过/处理中 | 管理员/商家通过 → 30 或 50 |
| 30 | `SUCCESS` 退款成功 | 成功退款回调 |
| 40 | `REJECTED` 退款拒绝 | 管理员/商家驳回 |
| 50 | `FAILED` 退款失败 | 失败退款回调 |

状态转换图：

```text
NONE ──买家申请──> REQUESTED ──审核通过──> APPROVED ──成功回调──> SUCCESS
                              │                         └─失败回调──> FAILED
                              └──审核驳回──> REJECTED
```

退款回调只接受 `APPROVED`。相同回调内容重放安全幂等；同一退款号的回调内容冲突会拒绝。成功或失败回调会同步订单的 `refundStatus`，但不会覆盖订单主交易状态。

当前 MVP 采用整单退款：待发货订单（`PAID_PENDING_SHIPMENT`）退款成功时，在同一个本地事务内恢复订单中每个 SKU 的预扣库存；库存恢复任一条失败，退款状态、订单售后状态和 Outbox 事件都会随事务回滚。已发货或已完成订单退款成功不恢复库存，因为商品已经进入履约流程。退款申请中、审核通过但尚未成功、退款被驳回或退款失败均不恢复库存。重复的成功回调在终态检查后直接幂等返回，不会再次恢复库存。当前不支持部分退款。

## 6. 数据库与迁移说明

`20260818-11-refund-workflow.sql` 增加订单售后状态、退款审核人/时间/备注、回调标识/结果及必要的索引和检查约束。迁移脚本先输出现有状态分布，并对旧版已经结束但缺少审核/回调元数据的记录写入明确的历史占位值（审核人 `0`、`legacy-refund-<id>` 回调号），同时按每笔历史退款回填订单的 `refundStatus`，以避免历史订单详情显示为“无售后”。这些占位值只表示历史来源未知，不应由新业务写入。

`20260819-12-commerce-outbox.sql` 增加事务性 Outbox 事件表 `commerce_outbox_event`（`event_type + event_key` 唯一、发布扫描索引、状态与尝试次数检查约束、JSON 负载校验）和对账台账表 `commerce_reconciliation_issue`（问题类型/作用域索引、状态检查约束），并配套 `20260819-12-commerce-outbox.rollback.sql` 回滚脚本。该迁移已应用到本机 MySQL，两表、索引与约束已验证。

执行迁移前仍应只读检查生产/共享数据库中的表、状态分布和备份；不要把回滚脚本当作无损降级。回滚前必须导出或清理新状态（尤其是 `FAILED` 和审核/回调字段），因为旧版状态模型无法表达这些信息。

## 7. 可靠性设施、当前边界与后续生产化工作

订单支付成功、发货、完成、取消以及退款成功/失败都会在业务本地事务内追加一条 PENDING 的 Outbox 事件，事件行与业务变更一起提交或回滚，并通过 `event_type + event_key` 唯一约束保证追加幂等。定时发布器在事务内先向 Redis Stream 发布事件，再标记事件为 PUBLISHED；发布失败按 30 秒起步、指数退避（2 的 n-1 次幂）重试，单事件最多尝试 5 次，超过后标记为 DEAD 并保存错误信息。Redis Stream 消费者按聚合 ID 幂等处理事件。

对账任务扫描三类问题并写入台账：支付单已成功但订单状态异常（不存在/待支付/已取消）、订单已履约但没有成功支付单、退款与订单售后状态不一致。台账问题可人工标记解决。当前订单、支付、退款仍在一个模块化单体内，通过本地事务、条件更新和上述 Outbox/对账设施保证基本一致性。

仍未完成的生产化缺口：支付与退款回调仍为本地模拟接口，没有真实支付渠道签名验签和渠道凭证校验；库存条件更新已经防止负库存，但尚未完成真实数据库压力测试、极端并发基准和故障注入；死信目前是库内 DEAD 标记而非外部 DLQ；对账发现的问题需要人工介入，还没有自动补偿与修复流程。服务拆分后必须补齐跨服务事件、幂等消费、超时、重试、外部死信和自动修复，并保证 Outbox 发布器与消费者在独立进程下不丢事件、不重复处理。

## 8. 本机真实验收记录（2026-08-19）

在本机用户级 MySQL、Redis 和 `yudao-server` 均正常运行的条件下，已使用独立验收买家完成以下链路：创建收货地址、加入商品、创建订单、重复使用同一 `Idempotency-Key`、创建模拟支付、成功支付回调、相同回调重放和冲突回调拒绝。随后完成退款申请、管理员审核、成功退款回调、相同退款回调重放和冲突退款回调拒绝。

验收观察到：订单由 `PENDING_PAYMENT` 转为 `PAID_PENDING_SHIPMENT`；退款由 `REQUESTED` 经管理员审核进入 `APPROVED`，再转为 `SUCCESS`；订单主状态保持不变，订单售后状态同步为退款成功。MySQL 与 Redis 的真实连接、管理员登录和重启后的应用查询均已验证。退款接口返回的 `DATETIME` 字段按数据库秒精度写入，首次回调与幂等重放不会产生仅由毫秒差异造成的响应漂移。

同日另完成商家发货真实验收：新建并审核通过验收商家（`merchant0821`，店铺 `store_accept_0821`），商家建商品后经提审、管理员审核通过、上架，买家加购下单并模拟支付回调后，商家待发货查询只返回自己商家的订单且包含商品、地址、金额和买家信息；空物流字段被拒，发货成功，重复发货被拒，跨商家发货按“订单不存在”拒绝；买家确认收货后订单转为已完成并保留物流信息。验收明细留存于本机 `/tmp/curmerce-merchant-accept-20260819.json`。

真实支付渠道签名、外部死信、自动补偿仍属于后续生产化工作，不应把当前模拟回调当作生产支付安全边界。
