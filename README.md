<div align="center">

# Curmerce

[English](./README_en.md) | 中文

[![JDK](https://img.shields.io/badge/JDK-21-007396?logo=openjdk&logoColor=white)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.5.15-6DB33F?logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![MySQL](https://img.shields.io/badge/MySQL-8.4_LTS-4479A1?logo=mysql&logoColor=white)](https://www.mysql.com/)
[![Redis](https://img.shields.io/badge/Redis-8.2-DC382D?logo=redis&logoColor=white)](https://redis.io/)
[![Next.js](https://img.shields.io/badge/Next.js-15.5-000000?logo=nextdotjs&logoColor=white)](https://nextjs.org/)
[![Maven](https://img.shields.io/badge/Maven-build-C71A36?logo=apachemaven&logoColor=white)](https://maven.apache.org/)

</div>

Curmerce 是一个面向兴趣消费场景的社区内容驱动型多模式交易平台，也是一个用于展示现代 Java 后端、复杂业务建模、事务可靠性与架构演进能力的秋招项目。

项目已经完成 `v0.1-modular-monolith` 可运行基线，并进入第一次服务拆分阶段：普通商城、个人闲置、基础限时发售、基础拍卖和社区内容均已形成可操作闭环；核心交易继续保留在一个进程中，社区与只读 Agent 已成为可独立部署和失败隔离的服务。

## 已实现能力

- **会员与平台基础**：普通用户注册登录、个人资料、三级收货地址、默认地址，以及平台管理员与商家店主的身份和权限隔离。
- **商家与商品**：商家创建和审核、店铺资料、平台分类树、SPU/SKU 草稿、审核、上架和下架，以及商家和店铺所有权校验。
- **普通交易闭环**：公开商品目录、购物车、结算、订单快照、事务内库存扣减、模拟支付回调、商家发货、买家确认收货、取消、超时关闭和基础退款。
- **个人闲置**：一物一库存发布、购买与售出订单、卖家发货、买家收货，以及取消后的库存恢复。
- **限时发售**：活动和 SKU 配置、时间状态、活动库存、用户限购、下单、支付、取消或超时后的库存恢复，以及自动开始和结束。
- **基础拍卖**：场次状态、起拍价和最小加价、出价幂等、胜者结算、唯一订单、支付超时失败，以及复用普通订单履约和退款流程。
- **社区基础**：可选图片的文字帖子、草稿与发布、话题、搜索、评论与回复、点赞、收藏、关注内容流、举报和管理员审核，以及可选的帖子商品关联。
- **可靠性基础**：数据库约束、关键操作幂等键、支付和退款重复回调保护、订单状态约束、库存恢复、对账查询，以及基于 Transactional Outbox 和 Redis Stream 的本地事件投递。
- **媒体资产基础**：认证上传、真实图片校验、限流与用户配额、SHA-256 去重、稳定资产地址、业务引用和延迟孤立清理、私有访问、病毒扫描、异步 WebP/AVIF 衍生图、内容审核、管理员治理，以及可恢复的对象存储迁移。
- **验收前端**：买家、个人卖家、商家和平台管理员的基础操作页面，覆盖当前主要业务闭环。

## 当前架构

```text
curmerce-web :3003 -> Spring Cloud Gateway :48082
                            ├── Core :48080      system、infra、member、commerce
                            ├── Community :48083 community_* 数据与社区接口
                            └── Agent :48084     只读商品/社区检索与故障降级
                                      |
                                  Nacos :8848
```

核心商品、库存、订单、支付和退款仍保留在 `yudao-server`，避免过早制造分布式交易一致性问题。Community 已移除对 Commerce、Member 和 Infra 持久化模块的依赖，通过受内部密钥保护的 HTTP 契约访问核心能力。Agent 当前不依赖模型凭据，只提供可独立失败的只读检索与来源降级骨架。

MySQL 是业务事实来源，Redis 用于框架能力和本地事件流，Spring Cloud Gateway 和 Nacos 已用于本次服务拆分。Kafka、Elasticsearch、Spring AI 模型接入与生产级可观测性尚未作为已完成功能引入。

项目使用 JDK 21 构建和运行，根 Maven 构建也统一使用 Java 21 源码与字节码目标。

## 本地运行与验证

本地环境需要 JDK 21、Maven、Node.js/npm、MySQL 和 Redis。数据库基线、迁移和私有凭据配置请遵循以下文档，不要把密码、Token 或本机环境文件提交到仓库：

- [数据库初始化与迁移](./script/db/README.md)
- [本地基础演示](./docs/local-basic-demo.md)
- [基础验收清单](./docs/basic-acceptance-checklist.md)
- [订单与退款契约](./docs/commerce-order-refund-contract.md)
- [媒体架构与运行手册](./docs/media-architecture.md)
- [MinIO、ClamAV 与 imgproxy 本地部署](./deploy/media/README.md)
- [第一次服务拆分架构](./docs/microservice-architecture.md)
- [Cloud 本地运行与故障验收](./deploy/cloud/README.md)

后端核心测试：

```bash
mvn -pl yudao-module-infra,curmerce-module-commerce,curmerce-module-community -am -Dtest='*Test' -Dsurefire.failIfNoSpecifiedTests=false test
```

前端生产构建：

```bash
cd curmerce-web
npm run build
```

不要让 `next dev` 和 `next build` 同时共享同一个 `.next` 目录；执行生产构建前先停止开发服务。

## 当前边界

- 支付和退款使用模拟回调，只用于验证状态机和幂等性，不代表真实支付渠道接入。
- 限时发售和拍卖当前是数据库事务基础版，尚未引入 Redis/Lua 抢占、消息队列削峰、实时推送或分布式补偿。
- 限时发售买家端当前固定购买一件，尚未提供多 SKU 和数量选择；商家端已经使用商品与 SKU 选择器。
- 社区帖子允许不带图片和商品发布；商品关联暂时使用编号输入，后续需要改为面向用户的搜索选择器。
- 社区目前提供基础时间流，不包含推荐算法、通知中心、复杂楼中楼或大规模异步计数。
- 媒体内容审核默认关闭，需要显式配置兼容的 HTTP 审核服务；ClamAV、imgproxy 和 MinIO 也是可选的本地部署能力。数据库文件存储仍可作为最低运行方式，但大文件和正式环境应使用私有对象存储。
- Agent 当前只是无模型凭据也可运行的只读检索骨架，不是完整的 Spring AI/RAG 产品能力。
- Community 与 Core 当前仍共享一个 MySQL 服务和 Schema，依靠代码依赖与表所有权隔离；独立数据库账号和 Schema 尚待后续硬化。
- Community 的媒体引用通过可重试的远程写入同步，但尚无跨服务原子性；远程成功后本地回滚的失效引用需要后续 Outbox 与对账任务修复。
- Kafka、Elasticsearch、分布式补偿、多节点注册中心和生产级可观测性仍属于后续阶段。

## 后续方向

1. 用独立数据库账号和 Schema 硬化 Community 所有权，并完善远程契约、超时、指标和链路追踪。
2. 以订单、库存、限时发售和拍卖为学习载体，逐步实现并发控制、可靠消息、补偿和对账。
3. 引入 Kafka 与可重建的 Elasticsearch 搜索投影，再评估拍卖服务拆分。
4. 在当前只读 Agent 骨架上接入 Spring AI、RAG、规则解释和受权限控制的领域工具。

## 基座与参考项目

Curmerce 保留 `ruoyi-vue-pro` 的通用系统与基础设施能力，并在其上实现独立的会员、交易和社区模块。第三方项目以 Git 子模块隔离在 [`reference-submodules/`](./reference-submodules/) 中，仅作为源码审查、设计比较和归属记录，不属于 Curmerce 自有业务实现。
