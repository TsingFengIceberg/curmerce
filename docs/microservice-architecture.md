# First Service Extraction Architecture

## Decision

Curmerce preserves the complete `v0.1-modular-monolith` baseline and begins its distributed evolution with one stable Gateway, one core commerce process, and two independently deployable edge domains:

```text
Browser -> Gateway :48082
             |-> Core :48080 -> system, infra, member, commerce tables
             |-> Community :48083 -> community_* tables
             `-> Agent :48084 -> read-only calls to Core and Community
                    |
                  Nacos :8848
```

This is a deliberate hybrid architecture, not a claim that every module has become a microservice. Product, inventory, cart, order, payment, refund, limited release, and auction remain together because separating them now would create distributed transaction failure modes before a useful ownership boundary exists.

## Ownership

| Runtime | Owned state | May not do |
| --- | --- | --- |
| Core | system, infrastructure, member, commerce, and media state | Directly write community tables |
| Community | posts, topics, comments, likes, favorites, follows, reports, and post-product references | Link Commerce, Member, System, or Infra persistence modules; write their tables |
| Agent | no domain state in this stage | Write any domain database or bypass domain authorization |
| Gateway | no domain state | Implement domain rules or expose `/internal-api/**` |

The current local deployment shares one MySQL server and schema to control operational cost. Ownership is enforced in code and dependency direction first; separate database credentials and schemas are a later hardening step. This limitation must remain explicit.

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

API DTOs live in `curmerce-cloud-api`; persistence objects are never shared as remote contracts. Trace IDs accepted at Gateway are constrained to safe characters and length, generated when absent or invalid, returned to the caller, and propagated to Community-to-Core calls.

## Failure semantics

- Gateway returns an unavailable response when a registered route has no healthy instance; it does not silently run community code inside Core.
- Community maps Core transport failures to a stable business error instead of leaking client exceptions.
- Agent reads Core and Community independently. One unavailable source produces partial results and a `degradedSources` marker; both unavailable sources still produce a valid empty response.
- Community restoration is discovered through Nacos without restarting Gateway.
- Local database transactions still protect state inside each owner. Media-reference replacement is an idempotent remote Core write, but it does not participate in the Community database transaction: a remote success followed by a local rollback can leave a stale reference. This stage does not claim distributed atomicity; an Outbox-backed retry and reconciliation job remain required hardening.

## What this stage proves

This stage proves registration and discovery, a stable Gateway entry point, an actual independently running Community service, an independently failing read-only Agent process, explicit HTTP contracts, loopback service authentication, trace propagation, and failure degradation.

It does not yet prove Kafka delivery, distributed transaction compensation, Elasticsearch projection, Spring AI model integration, production secret management, multi-node Nacos, or production-grade observability. Those capabilities should be added only with a concrete failure scenario and automated evidence.

---

# 第一次服务拆分架构

Curmerce 保留完整的 `v0.1-modular-monolith` 基线，并以混合架构开始分布式演进：Gateway 提供稳定入口，核心交易保留在 Core，Community 成为真正独立部署的社区服务，Agent 成为可独立失败和降级的只读检索服务。这不代表所有模块都已经微服务化。

商品、库存、购物车、订单、支付、退款、限时发售和拍卖继续留在 Core，因为此时拆开会先制造分布式事务问题，却没有形成足够有价值的数据所有权或独立扩缩容收益。Community 只拥有帖子、话题、评论、点赞、收藏、关注、举报与帖子商品关联数据，不得依赖 Commerce、Member、System 或 Infra 的持久化模块，也不得写入它们的表。Agent 和 Gateway 均不拥有领域数据。

现阶段为了控制本地运行成本，Core 与 Community 仍共享一个 MySQL 服务和 Schema，所有权首先通过代码边界和依赖方向约束；未来还需用独立数据库账号和 Schema 进一步硬化。这个限制必须明确，不能将其描述为已经完成物理数据库隔离。

前端继续只访问 Gateway 的 `48082`，无需知道接口属于哪个进程。Community 通过小型内部 Core API 完成 Token、角色权限、会员、可见商品和媒体引用操作。调用携带至少 32 字符的内部 Token，Core 使用恒定时间比较；Gateway 不暴露内部路径。这适合当前回环开发拓扑，但不等同于生产级 mTLS 或零信任体系。

故障语义是本阶段的重点：Community 无实例时 Gateway 必须明确失败；Community 调用 Core 失败时转换为稳定业务错误；Agent 分别读取 Core 与 Community，单个来源失败时返回部分结果并标记 `degradedSources`，两个来源都失败时仍返回结构完整的空结果；Community 恢复后由 Nacos 自动发现，无需重启 Gateway。

本地数据库事务仍只保护各自所有者内部的状态。媒体引用替换虽然是可重试的 Core 远程写入，但不会参与 Community 数据库事务：远程成功后若本地回滚，仍可能留下失效引用。本阶段不声称已经实现分布式原子性，后续仍需用 Outbox 重试与对账修复任务完成可靠性硬化。

本阶段能够证明注册发现、统一网关、独立社区进程、可独立失败的 Agent、显式 HTTP 契约、回环服务认证、Trace ID 透传和降级。Kafka、分布式补偿、Elasticsearch、Spring AI 模型、生产密钥治理、多节点 Nacos 与生产级可观测性仍不属于已完成功能。
