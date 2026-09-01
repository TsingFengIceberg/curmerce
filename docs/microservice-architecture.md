# First Service Extraction Architecture

## Decision

Curmerce preserves the complete `v0.1-modular-monolith` baseline and begins its distributed evolution with one stable Gateway, one core commerce process, and two independently deployable edge domains:

```text
Browser -> Gateway :48082
             |-> Core :48080 -> system, infra, member, commerce tables
             |-> Community :48083 -> community_* tables
             |-> Auction :48086 -> auction state and bids (owned schema)
             `-> Agent :48084 -> read-only calls to Core and Community
                    |
                  Nacos :8848
```

This is a deliberate hybrid architecture, not a claim that every module has become a microservice. Product, inventory, cart, order, payment, refund, and limited release remain together because separating them now would create distributed transaction failure modes before a useful ownership boundary exists. Auction has crossed the first extraction gate: its sessions and bids can be stored in an owned schema, while catalog ownership and settlement orders remain explicit Core contracts.

## Ownership

| Runtime | Owned state | May not do |
| --- | --- | --- |
| Core | system, infrastructure, member, commerce, and media state | Directly write community tables |
| Community | posts, topics, comments, likes, favorites, follows, reports, and post-product references | Link Commerce, Member, System, or Infra persistence modules; write their tables |
| Agent | no domain state in this stage | Write any domain database or bypass domain authorization |
| Auction | auction sessions, bids, lifecycle state, and bid idempotency in `curmerce_auction` when local store is enabled | Read or write Core auction tables; bypass Core ownership checks or create orders without the settlement contract |
| Gateway | no domain state | Implement domain rules or expose `/internal-api/**` |

The local deployment shares one MySQL process to control operational cost, but physical ownership is now enforced by separate `curmerce` and `curmerce_community` schemas and mutually restricted accounts. Community has only `SELECT`, `INSERT`, `UPDATE`, and `DELETE` on its schema; Core cannot access Community tables, and Community cannot access Core tables. A production-like environment must use a distinct Community password even though the local configuration retains a migration fallback.

## Contracts

External API paths remain stable at the Gateway, so the existing frontend does not need to know which process owns an endpoint. Gateway routes community paths before the core catch-all route.

Community uses a small internal Core API for:

- access-token validation;
- role and permission checks;
- department data permission retrieval;
- active-member validation and member summaries;
- visible-product summaries;
- media-reference replacement.

Internal calls carry `X-Curmerce-Internal-Token`. Core compares a configured token of at least 32 characters using constant-time comparison. Internal paths are not routed by Gateway. This is service-to-service authentication for a loopback development topology, not a complete zero-trust or mTLS design.

API DTOs live in `curmerce-cloud-api`; persistence objects are never shared as remote contracts. Correlation IDs accepted at Gateway are constrained to safe characters and length, generated when absent or invalid, returned to the caller, and propagated independently from OpenTelemetry. Micrometer tracing propagates W3C `traceparent` through Gateway, Community RestClient, and Core while logs expose `traceId` and `spanId`.

## Failure semantics

- Gateway returns an unavailable response when a registered route has no healthy instance; it does not silently run community code inside Core.
- Community maps Core transport failures to a stable business error instead of leaking client exceptions.
- Agent reads Core and Community independently. One unavailable source produces partial results and a `degradedSources` marker; both unavailable sources still produce a valid empty response.
- Community restoration is discovered through Nacos without restarting Gateway.
- Community post transactions write the latest desired media-reference state to `community_media_outbox` instead of calling Core synchronously. A leased publisher performs the idempotent Core replacement outside the database transaction, retries with bounded exponential backoff, rejects stale worker completion by version and token, and recovers expired leases. A scheduled post scan re-upserts current desired state. This provides measurable eventual convergence, not cross-service strong atomicity.
- Core and Community downstream failures are protected by independent Resilience4j circuit breakers. Gateway, Core, Community, and Agent expose loopback Prometheus endpoints. OpenTelemetry tracing is sampled locally, while OTLP export stays disabled until a collector endpoint is explicitly configured.

## What this stage proves

This stage proves registration and discovery, a stable Gateway entry point, independently running Community, Agent, and Auction processes, explicit HTTP contracts, loopback service authentication, schema-level data ownership for Community and Auction, trace propagation, circuit breaking, observable Outbox retry, and failure degradation.

It does not yet prove Kafka delivery, distributed transaction compensation, Elasticsearch projection, Spring AI model integration, production secret management, multi-node Nacos, or an external telemetry backend with dashboards and alerts. Those capabilities should be added only with a concrete failure scenario and automated evidence.

---

# 第一次服务拆分架构

Curmerce 保留完整的 `v0.1-modular-monolith` 基线，并以混合架构开始分布式演进：Gateway 提供稳定入口，核心交易保留在 Core，Community 成为真正独立部署的社区服务，Agent 成为可独立失败和降级的只读检索服务。这不代表所有模块都已经微服务化。

商品、库存、购物车、订单、支付、退款和限时发售继续留在 Core，因为此时拆开会先制造分布式事务问题，却没有形成足够有价值的数据所有权或独立扩缩容收益。拍卖已经跨过第一道拆分门槛：开启本地存储时，场次、出价和生命周期状态由 Auction 的独立 Schema 持有；商品所有权校验和结算订单仍通过 Core 的显式契约完成。Community 只拥有帖子、话题、评论、点赞、收藏、关注、举报与帖子商品关联数据，不得依赖 Commerce、Member、System 或 Infra 的持久化模块，也不得写入它们的表。Agent 和 Gateway 均不拥有领域数据。

现阶段为了控制本地运行成本，Core 与 Community 仍共享一个 MySQL 进程，但已分别使用 `curmerce` 与 `curmerce_community` Schema 和互相不可越权的最小权限账号。Community 账号只拥有自己 Schema 的读写权限，Core 不能访问 Community 表，Community 也不能访问 Core 表；正式环境还必须为 Community 使用独立密码。

前端继续只访问 Gateway 的 `48082`，无需知道接口属于哪个进程。Community 通过小型内部 Core API 完成 Token、角色权限、会员、可见商品和媒体引用操作。调用携带至少 32 字符的内部 Token，Core 使用恒定时间比较；Gateway 不暴露内部路径。这适合当前回环开发拓扑，但不等同于生产级 mTLS 或零信任体系。

故障语义是本阶段的重点：Community 无实例时 Gateway 必须明确失败；Community 调用 Core 失败时转换为稳定业务错误；Agent 分别读取 Core 与 Community，单个来源失败时返回部分结果并标记 `degradedSources`，两个来源都失败时仍返回结构完整的空结果；Community 恢复后由 Nacos 自动发现，无需重启 Gateway。

Community 帖子事务不再同步调用 Core，而是在同一事务写入 `community_media_outbox` 的最新期望状态。发布器使用租约在事务外执行幂等替换，失败后指数退避重试，通过版本与处理 Token 拒绝旧 Worker 覆盖新状态，并恢复过期租约；定时帖子扫描负责重新写入当前期望状态。这提供可观测的最终收敛，不代表跨服务强原子性。

Core 与 Community 下游调用使用独立的 Resilience4j 断路器。Gateway、Core、Community 和 Agent 均暴露仅本地可访问的 Prometheus 端点；OpenTelemetry 默认在本地采样并传播 W3C `traceparent`，只有明确配置 Collector 后才启用 OTLP 导出。业务关联 ID 与标准 Trace ID 分开保留。

本阶段能够证明注册发现、统一网关、独立社区和 Auction 进程、可独立失败的 Agent、显式 HTTP 契约、回环服务认证、Community/Auction 的 Schema 级数据所有权、Trace 透传、断路器、Outbox 重试和降级。Auction 的本地存储由 `CURMERCE_AUCTION_LOCAL_STORE_ENABLED` 控制，迁移 28 会保留旧 Core 表作为回滚源，尚未完成物理删除和跨服务自动对账。Kafka、分布式补偿、Elasticsearch、Spring AI 模型、生产密钥治理、多节点 Nacos，以及外部遥测存储、仪表盘与告警仍不属于已完成功能。
