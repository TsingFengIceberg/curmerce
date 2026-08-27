<div align="center">

# Curmerce

English | [中文](./README.md)

[![JDK](https://img.shields.io/badge/JDK-21-007396?logo=openjdk&logoColor=white)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.5.15-6DB33F?logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![MySQL](https://img.shields.io/badge/MySQL-8.4_LTS-4479A1?logo=mysql&logoColor=white)](https://www.mysql.com/)
[![Redis](https://img.shields.io/badge/Redis-8.2-DC382D?logo=redis&logoColor=white)](https://redis.io/)
[![Next.js](https://img.shields.io/badge/Next.js-15.5-000000?logo=nextdotjs&logoColor=white)](https://nextjs.org/)
[![Maven](https://img.shields.io/badge/Maven-build-C71A36?logo=apachemaven&logoColor=white)](https://maven.apache.org/)

</div>

Curmerce is a community-content-driven, multi-mode commerce platform for interest-based consumption. It is also an autumn-recruiting portfolio project designed to demonstrate modern Java backend development, complex business modeling, transactional reliability, and architectural evolution.

The project has moved beyond foundation evaluation into a **runnable modular-monolith baseline**. Standard commerce, individual listings, basic limited releases, basic auctions, and community content now have usable end-to-end flows, with a Next.js acceptance interface for buyers, merchants, and platform administrators.

## Implemented Capabilities

- **Member and platform foundation**: buyer registration and login, profiles, three-level shipping addresses, default-address handling, and separation between platform-administrator and merchant-owner identities and permissions.
- **Merchants and products**: merchant creation and approval, store profiles, platform category trees, SPU/SKU drafts, review, listing and delisting, plus merchant and store ownership checks.
- **Standard transaction lifecycle**: public catalog, cart, checkout, order snapshots, transactional inventory deduction, simulated payment callbacks, merchant shipment, buyer receipt confirmation, cancellation, timeout closure, and basic refunds.
- **Individual listings**: one-item-one-stock publishing, purchase and sold-order views, seller shipment, buyer receipt confirmation, and inventory restoration after cancellation.
- **Limited releases**: campaign and SKU configuration, time-based states, campaign inventory, per-user limits, order creation, payment, inventory restoration after cancellation or timeout, and automatic opening and ending.
- **Basic auctions**: session lifecycle, starting price and minimum increments, idempotent bidding, winner settlement, single-order creation, payment-timeout failure, and reuse of the standard fulfillment and refund lifecycle.
- **Community foundation**: text posts with optional images, drafts and publishing, topics, search, comments and replies, likes, favorites, following feeds, reports and administrator moderation, plus optional post-product associations.
- **Reliability baseline**: database constraints, idempotency keys for critical operations, duplicate payment and refund callback protection, order-state guards, inventory restoration, reconciliation queries, and local event delivery through Transactional Outbox and Redis Stream.
- **Media asset foundation**: authenticated uploads, real-image validation, rate limits and user quotas, SHA-256 deduplication, stable asset URLs, business references and delayed orphan cleanup, private access, antivirus scanning, asynchronous WebP/AVIF variants, content moderation, administrator governance, and resumable object-storage migration.
- **Acceptance frontend**: basic operational pages for buyers, individual sellers, merchants, and platform administrators across the current primary workflows.

## Current Architecture

```text
Curmerce
├── yudao-server                  Single Spring Boot runtime
│   ├── yudao-module-system       Authentication, authorization, and system foundation
│   ├── yudao-module-infra        Files, configuration, and shared infrastructure
│   ├── curmerce-module-member    Members, profiles, and addresses
│   ├── curmerce-module-commerce  Merchants, products, trade, releases, and auctions
│   └── curmerce-module-community Community content and interactions
└── curmerce-web                  Next.js acceptance frontend
```

The current design intentionally makes state machines, transaction boundaries, ownership rules, and database constraints correct inside one process and one MySQL instance first. A module must not directly modify data owned by another module; cross-module behavior is expressed through application interfaces and events so the boundaries remain extractable later.

MySQL is the business source of truth. Redis currently supports framework capabilities and the local event stream. Kafka, Elasticsearch, Spring Cloud, and Spring AI are not presented as completed features.

The project is built and run with JDK 21. The imported parent POM still retains Java 17 source compatibility, so raising the complete compilation target to Java 21 will be handled as a dedicated compatibility change rather than being misrepresented here as finished work.

## Local Setup and Verification

The local environment requires JDK 21, Maven, Node.js/npm, MySQL, and Redis. Follow the database, migration, and private-credential documentation below. Never commit passwords, tokens, or machine-local environment files.

- [Database initialization and migrations](./script/db/README.md)
- [Local basic demo](./docs/local-basic-demo.md)
- [Basic acceptance checklist](./docs/basic-acceptance-checklist.md)
- [Order and refund contract](./docs/commerce-order-refund-contract.md)
- [Media architecture and runbook](./docs/media-architecture.md)
- [Local MinIO, ClamAV, and imgproxy deployment](./deploy/media/README.md)

Core backend tests:

```bash
mvn -pl yudao-module-infra,curmerce-module-commerce,curmerce-module-community -am -Dtest='*Test' -Dsurefire.failIfNoSpecifiedTests=false test
```

Frontend production build:

```bash
cd curmerce-web
npm run build
```

Do not run `next dev` and `next build` against the same `.next` directory concurrently. Stop the development server before producing a production build.

## Current Boundaries

- Payment and refund callbacks are simulated to verify state machines and idempotency; they are not integrations with a real payment provider.
- Limited releases and auctions are currently database-transaction baselines without Redis/Lua reservation, queue-based load leveling, real-time push, or distributed compensation.
- The buyer-side limited-release page currently purchases one item and does not yet provide multi-SKU or quantity selection; the merchant side already uses product and SKU selectors.
- Community posts may be published without images or products. Product association still uses identifier input and needs a user-facing search selector.
- The community currently provides a basic chronological feed without recommendation algorithms, a notification center, deeply nested comments, or large-scale asynchronous counters.
- Media content moderation is disabled by default and requires an explicitly configured compatible HTTP moderation service. ClamAV, imgproxy, and MinIO are also optional local capabilities. Database file storage remains the minimum runnable mode, while large files and production-like deployments should use private object storage.
- Agent capabilities, Kafka, Elasticsearch, Spring Cloud service extraction, and production-grade observability remain future work.

## Next Directions

1. Close the remaining UI gaps, automated-test gaps, and reproducible-environment gaps in the existing baseline workflows.
2. Use orders, inventory, limited releases, and auctions to progressively study concurrency control, reliable messaging, compensation, and reconciliation.
3. After monolith behavioral contracts are stable, evaluate extraction in the order of Agent, community, search projections, and auctions.
4. Finally add retrieval, comparison, rule explanation, and controlled read-only Agent tools grounded in product and community experience.

## Foundation and References

Curmerce retains the generic system and infrastructure capabilities of `ruoyi-vue-pro` while implementing separate member, commerce, and community modules on top. Third-party projects are isolated as Git submodules under [`reference-submodules/`](./reference-submodules/) for source review, design comparison, and attribution only; they are not Curmerce-owned business implementations.
