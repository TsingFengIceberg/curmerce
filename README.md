<div align="center">

# Curmerce

[English](./README_en.md) | 中文

[![Java](https://img.shields.io/badge/Java-21-007396?logo=openjdk&logoColor=white)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.x-6DB33F?logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![Spring Cloud](https://img.shields.io/badge/Spring_Cloud-planned-6DB33F?logo=spring&logoColor=white)](https://spring.io/projects/spring-cloud)
[![MySQL](https://img.shields.io/badge/MySQL-8.x-4479A1?logo=mysql&logoColor=white)](https://www.mysql.com/)
[![Redis](https://img.shields.io/badge/Redis-planned-DC382D?logo=redis&logoColor=white)](https://redis.io/)
[![Kafka](https://img.shields.io/badge/Apache_Kafka-planned-231F20?logo=apachekafka&logoColor=white)](https://kafka.apache.org/)
[![Elasticsearch](https://img.shields.io/badge/Elasticsearch-planned-005571?logo=elasticsearch&logoColor=white)](https://www.elastic.co/elasticsearch)
[![Spring AI](https://img.shields.io/badge/Spring_AI-planned-6DB33F?logo=spring&logoColor=white)](https://spring.io/projects/spring-ai)
[![Maven](https://img.shields.io/badge/Maven-build-C71A36?logo=apachemaven&logoColor=white)](https://maven.apache.org/)
[![Docker Compose](https://img.shields.io/badge/Docker_Compose-local_env-2496ED?logo=docker&logoColor=white)](https://docs.docker.com/compose/)

</div>

Curmerce 是一个面向兴趣消费场景的社区内容驱动型多模式交易平台，也是一个用于展示现代 Java 后端、复杂业务建模、分布式系统与生产工程能力的秋招项目。

## 我们会做什么

- **社区内容：**图文帖子、话题、评论、点赞、收藏、关注，以及帖子与商品关联。
- **多模式交易：**商家常规售卖、个人闲置的一物一库存、限量发售和直播竞拍。
- **完整交易闭环：**商品、库存、购物车、订单、支付、履约、退款与纠纷处理。
- **消费 Agent：**基于商品和社区经验进行检索、比较与规则解释，并通过受控 Tool 查询平台数据。

## 架构路线

项目将以现代版 `ruoyi-vue-pro` 为候选基座，先在一个 Spring Boot 进程中构建边界清晰的模块化单体，把业务状态机、数据库约束和测试做正确；随后参考 `yudao-cloud`，在同一仓库中逐步拆分 Agent、社区、竞拍等真正需要独立部署的服务。

MySQL 始终作为交易事实来源。Redis、Kafka、Elasticsearch、Spring Cloud 等组件只会在有明确业务问题和可验证场景时逐步引入，而不是在项目初期一次性堆叠。

## 当前阶段

项目目前处于基础建设和基座审查阶段。第三方实现以 Git 子模块形式隔离在 [`reference-submodules/`](./reference-submodules/) 中，仅用于源码审查和设计对照。
