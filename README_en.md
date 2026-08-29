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

The project has completed its runnable `v0.1-modular-monolith` baseline and entered its first service-extraction stage. Standard commerce, individual listings, basic limited releases, basic auctions, and community content have usable end-to-end flows. Core commerce remains together, while Community and a read-only Agent now run as independently deployable and failure-isolated services.

## Implemented Capabilities

- **Member and platform foundation**: buyer registration and login, profiles, three-level shipping addresses, default-address handling, and separation between platform-administrator and merchant-owner identities and permissions.
- **Merchants and products**: merchant creation and approval, store profiles, platform category trees, SPU/SKU drafts, review, listing and delisting, plus merchant and store ownership checks.
- **Standard transaction lifecycle**: public catalog, cart, checkout, order snapshots, transactional inventory deduction, simulated payment callbacks, merchant shipment, buyer receipt confirmation, cancellation, timeout closure, and basic refunds.
- **Individual listings**: one-item-one-stock publishing, purchase and sold-order views, seller shipment, buyer receipt confirmation, and inventory restoration after cancellation.
- **Limited releases**: campaign and SKU configuration, time-based states, campaign inventory, per-user limits, order creation, payment, inventory restoration after cancellation or timeout, and automatic opening and ending.
- **Basic auctions**: session lifecycle, starting price and minimum increments, idempotent bidding, winner settlement, single-order creation, payment-timeout failure, and reuse of the standard fulfillment and refund lifecycle.
- **Community foundation**: text posts with optional images, drafts and publishing, topics, search, comments and replies, likes, favorites, following feeds, reports and administrator moderation, plus optional post-product associations.
- **Reliability baseline**: database constraints, idempotency keys for critical operations, duplicate payment and refund callback protection, order-state guards, inventory restoration, reconciliation queries, commerce event Outbox delivery, and a leased, exponentially retried, reconciled Community media desired-state Outbox.
- **Service resilience and observability**: Resilience4j circuit breakers for Core, Community, and Agent downstream calls; Prometheus metrics for Gateway, Core, Community, and Agent; and locally sampled OpenTelemetry W3C tracing with opt-in export.
- **Media asset foundation**: authenticated uploads, real-image validation, rate limits and user quotas, SHA-256 deduplication, stable asset URLs, business references and delayed orphan cleanup, private access, antivirus scanning, asynchronous WebP/AVIF variants, content moderation, administrator governance, and resumable object-storage migration.
- **Acceptance frontend**: basic operational pages for buyers, individual sellers, merchants, and platform administrators across the current primary workflows.
- **Frontend design standard**: Xiaohongshu-inspired content-first surfaces for consumers and community, an Apple-inspired restrained operational workspace for merchants, and a neutral graphite administration workspace. All three share Curmerce-owned tokens, radii, spacing, focus states, and responsive rules; source references remain under the ignored `design-references/` directory.

## Current Architecture

```text
curmerce-web :3003 -> Spring Cloud Gateway :48082
                            |-> Core :48080      system, infra, member, commerce
                            |-> Community :48083 curmerce_community schema and APIs
                            |-> Agent :48084     read-only retrieval and source degradation
                            `-> Search :48085    Kafka-driven Elasticsearch product/post projections
                                      |
                                  Nacos :8848
```

Core product, inventory, order, payment, and refund behavior remains in `yudao-server` to avoid prematurely introducing distributed transaction failures. Community no longer depends on Commerce, Member, or Infra persistence modules and reaches required core capabilities through an internal-key-protected HTTP contract. Agent requires no model credentials at this stage and provides a failure-isolated read-only retrieval and source-degradation skeleton.

MySQL remains the business source of truth. Core and Community share one local MySQL process but use separate `curmerce` and `curmerce_community` schemas with mutually restricted accounts. Redis supports framework capabilities and the local event stream, while Spring Cloud Gateway and Nacos support the first extraction. Kafka and Elasticsearch are now available as optional search-event transport and asynchronous projections. Spring AI model integration is not complete; the local Compose runtime provides optional telemetry, metrics, and tracing infrastructure rather than production alerting or SLOs.

The project is built and run with JDK 21, and the root Maven build consistently targets Java 21 source and bytecode.

## Local Setup and Verification

The local environment requires JDK 21, Maven, Node.js/npm, MySQL, and Redis. Follow the database, migration, and private-credential documentation below. Never commit passwords, tokens, or machine-local environment files.

- [Database initialization and migrations](./script/db/README.md)
- [Local basic demo](./docs/local-basic-demo.md)
- [Basic acceptance checklist](./docs/basic-acceptance-checklist.md)
- [Order and refund contract](./docs/commerce-order-refund-contract.md)
- [Media architecture and runbook](./docs/media-architecture.md)
- [Local MinIO, ClamAV, and imgproxy deployment](./deploy/media/README.md)
- [First service extraction architecture](./docs/microservice-architecture.md)
- [Cloud local runtime and failure verification](./deploy/cloud/README.md)
- [Frontend design standard](./docs/frontend-design-standard.md)
- [Agent and Community service-boundary acceptance](./docs/service-extraction-acceptance.md)

Core backend tests:

```bash
mvn -pl yudao-module-infra,curmerce-module-commerce,curmerce-module-community -am -Dtest='*Test' -Dsurefire.failIfNoSpecifiedTests=false test
```

Frontend production build:

```bash
cd curmerce-web
npm run build
```

Frontend regression tests:

```bash
npm run typecheck
npm run test:components
npm run test:e2e -- e2e/surface-smoke.spec.ts
```

Service-boundary failure acceptance (run after the services are started):

```bash
./script/verify/service-boundary-smoke.sh
```

Independent Search projection acceptance (run after Kafka, Elasticsearch, and Search are enabled):

```bash
./script/verify/search-projection-smoke.sh
```

Do not run `next dev` and `next build` against the same `.next` directory concurrently. Stop the development server before producing a production build.

## Current Boundaries

- Payment and refund callbacks are simulated to verify state machines and idempotency; they are not integrations with a real payment provider.
- Limited releases and auctions are currently database-transaction baselines without Redis/Lua reservation, queue-based load leveling, real-time push, or distributed compensation.
- The buyer-side limited-release page currently purchases one item and does not yet provide multi-SKU or quantity selection; the merchant side already uses product and SKU selectors.
- Community posts may be published without images or products. Product association still uses identifier input and needs a user-facing search selector.
- The community currently provides a basic chronological feed without recommendation algorithms, a notification center, deeply nested comments, or large-scale asynchronous counters.
- Media content moderation is disabled by default and requires an explicitly configured compatible HTTP moderation service. ClamAV, imgproxy, and MinIO are also optional local capabilities. Database file storage remains the minimum runnable mode, while large files and production-like deployments should use private object storage.
- Agent is currently a model-free read-only retrieval skeleton, not a complete Spring AI or RAG product capability.
- Community and Core still share one local MySQL process to control runtime cost, but now use separate schemas and least-privilege accounts. Local configuration may temporarily fall back to the same password; production-like environments must set a distinct `COMMUNITY_MYSQL_PASSWORD`.
- Community media references converge through a latest-desired-state Outbox with leases, unbounded retries, and scheduled full reconciliation. This is eventual consistency, not cross-service atomicity; prolonged Core outages accumulate visible pending work.
- OTLP export is disabled by default. The local Compose runtime provides a Collector, Tempo, Prometheus, and Grafana when enabled, but this is not a production alerting or SLO claim.
- Search projection is disabled by default. When enabled, product and post transaction Outboxes publish Kafka events; the Search service ignores duplicate and out-of-order older event IDs, retries failures before dead-letter routing, and can rebuild indexes from the Core and Community APIs.

## Next Directions

1. Continue validating Kafka, Elasticsearch, Collector, metrics, and tracing on the local Compose runtime and expand automated fault regression.
2. Use orders, inventory, limited releases, and auctions to progressively study concurrency control, reliable messaging, compensation, and reconciliation.
3. Evaluate independent deployment boundaries for Search and Auction, including index rebuilds, message dead letters, and cross-service reconciliation scenarios.
4. Build Spring AI, RAG, rule explanation, and permission-controlled domain tools on the current read-only Agent skeleton.

## Foundation and References

Curmerce retains the generic system and infrastructure capabilities of `ruoyi-vue-pro` while implementing separate member, commerce, and community modules on top. Third-party projects are isolated as Git submodules under [`reference-submodules/`](./reference-submodules/) for source review, design comparison, and attribution only; they are not Curmerce-owned business implementations.
