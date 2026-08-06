<div align="center">

# Curmerce

English | [中文](./README.md)

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

Curmerce is a community-content-driven, multi-mode commerce platform for interest-based consumption. It is also an autumn-recruiting portfolio project designed to demonstrate modern Java backend development, complex business modeling, distributed systems, and production engineering.

## What We Will Build

- **Community content:** image and text posts, topics, comments, likes, favorites, follows, and post-product associations.
- **Multiple transaction modes:** standard merchant sales, one-item-one-stock individual listings, limited releases, and live auctions.
- **A complete transaction lifecycle:** products, inventory, carts, orders, payments, fulfillment, refunds, and disputes.
- **A commerce Agent:** retrieval, comparison, and rule explanation based on products and community experience, with controlled tools for platform queries.

## Architecture Path

The project will evaluate a modern `ruoyi-vue-pro` version as its foundation. It will first build a modular monolith with explicit boundaries inside one Spring Boot process, making business state machines, database constraints, and tests correct before distribution. It will then use `yudao-cloud` as a reference and progressively extract services such as Agent, community, and auction within this same repository when independent deployment provides real value.

MySQL remains the source of truth for transactions. Redis, Kafka, Elasticsearch, Spring Cloud, and other components will be introduced only for concrete business problems and verifiable scenarios instead of being added all at once.

## Current Stage

The project is currently establishing its foundation and reviewing candidate codebases. Third-party implementations are isolated as Git submodules under [`reference-submodules/`](./reference-submodules/) and are used only for source review and design comparison.
