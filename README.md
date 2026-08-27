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

项目当前已经从基座审查阶段进入**可运行的模块化单体基础版本**：普通商城、个人闲置、基础限时发售、基础拍卖和社区内容均已形成可操作闭环，并提供面向买家、商家和平台管理员的 Next.js 验收界面。

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
Curmerce
├── yudao-server                  单个 Spring Boot 启动进程
│   ├── yudao-module-system       认证、权限和系统管理基座
│   ├── yudao-module-infra        文件、配置和通用基础设施
│   ├── curmerce-module-member    会员、资料和地址
│   ├── curmerce-module-commerce  商家、商品、交易、限时发售和拍卖
│   └── curmerce-module-community 社区内容和互动
└── curmerce-web                  Next.js 验收前端
```

当前坚持先在一个进程和一个 MySQL 实例内做对状态机、事务边界、所有权和数据库约束。模块不应直接修改其他模块拥有的数据；跨模块行为通过应用接口和事件表达，为后续服务拆分保留边界。

MySQL 是业务事实来源。Redis 当前用于框架能力和本地事件流；Kafka、Elasticsearch、Spring Cloud 与 Spring AI 尚未作为已完成功能引入。

项目使用 JDK 21 构建和运行。由于当前基座父 POM 仍保留 Java 17 源码兼容级别，将编译目标完整提升到 Java 21 会作为单独的兼容性改造进行，而不会在 README 中将其误写为已经完成。

## 本地运行与验证

本地环境需要 JDK 21、Maven、Node.js/npm、MySQL 和 Redis。数据库基线、迁移和私有凭据配置请遵循以下文档，不要把密码、Token 或本机环境文件提交到仓库：

- [数据库初始化与迁移](./script/db/README.md)
- [本地基础演示](./docs/local-basic-demo.md)
- [基础验收清单](./docs/basic-acceptance-checklist.md)
- [订单与退款契约](./docs/commerce-order-refund-contract.md)
- [媒体架构与运行手册](./docs/media-architecture.md)
- [MinIO、ClamAV 与 imgproxy 本地部署](./deploy/media/README.md)

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
- Agent、Kafka、Elasticsearch、Spring Cloud 服务拆分和生产级可观测性仍属于后续阶段。

## 后续方向

1. 收敛现有基础闭环的 UI 缺口、自动化测试和可重复环境初始化。
2. 以订单、库存、限时发售和拍卖为学习载体，逐步实现并发控制、可靠消息、补偿和对账。
3. 在单体行为契约稳定后，按 Agent、社区、搜索投影、拍卖的顺序评估服务拆分。
4. 最后接入基于商品和社区经验的检索、比较、规则解释与受控只读 Agent 工具。

## 基座与参考项目

Curmerce 保留 `ruoyi-vue-pro` 的通用系统与基础设施能力，并在其上实现独立的会员、交易和社区模块。第三方项目以 Git 子模块隔离在 [`reference-submodules/`](./reference-submodules/) 中，仅作为源码审查、设计比较和归属记录，不属于 Curmerce 自有业务实现。
