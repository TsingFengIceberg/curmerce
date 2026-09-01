# Agent and Community Service Boundary

This document is the first executable boundary baseline for the extracted
Agent and Community services. It records ownership and failure behavior before
any further service split.

## Ownership

| Service | Owns | Must not do |
| --- | --- | --- |
| Core (`48080`) | members, permissions, products, inventory, orders, payment and refund facts | expose internal APIs through Gateway or allow another service to write its tables |
| Community (`48083`) | `community_*` tables, post/topic/comment/reaction/follow/report state | issue access tokens or read Core tables directly |
| Agent (`48084`) | read-only retrieval orchestration and degradation metadata | modify commerce/community state or access either database directly |
| Auction (`48086`) | auction sessions and bids when `CURMERCE_AUCTION_LOCAL_STORE_ENABLED=true`; Core ownership checks and settlement-order contract | read or write Core auction tables; when local store is disabled, use the Core proxy path only |
| Gateway (`48082`) | public routing, CORS, trace IDs and service-unavailable responses | become a business-data owner |

## Typed Contracts

Community calls Core through `CoreServiceHttpClient` and the internal token:

- token and permission checks: `/internal-api/curmerce/core/auth/check` and `/internal-api/curmerce/core/permission/check`
- member/profile lookup: `/internal-api/curmerce/core/member/{id}`
- product ownership and summary lookup: `/internal-api/curmerce/core/product/{id}`
- media reference registration: `/internal-api/curmerce/core/media/references`

Agent calls Core and Community through short-timeout HTTP clients and reports a
degraded source rather than failing the complete read-only response. Neither
service writes the other service's schema.

## Acceptance Sequence

Run the host-side check after the services are started:

```bash
./script/verify/service-boundary-smoke.sh
```

The check verifies health and public routing, then stops Community temporarily.
The expected behavior is:

1. Gateway returns HTTP `503` for a Community route and preserves the trace ID.
2. Core catalog remains HTTP `200`.
3. Agent remains HTTP `200` and reports `community` in `degradedSources`.
4. Community restarts and becomes routable again without restarting Gateway.
5. Auction health is available on `48086` and its public route is served through Gateway.
6. Auction can be stopped independently: its route returns `503` with a trace ID while the Core catalog remains available.
7. Auction can restart and become routable again without restarting Gateway.
8. With `CURMERCE_AUCTION_LOCAL_STORE_ENABLED=true`, Auction reads and writes only
   `curmerce_auction.auction_session` and `curmerce_auction.auction_bid`; Core
   catalog, identity, ownership, and order facts remain behind typed HTTP APIs.
9. Migration 28 reports equal source/target row counts before the old Core tables
   are considered a rollback source rather than an active write source.

`script/verify/cloud-runtime-regression.sh` remains the broader check for
Prometheus, Kafka/Elasticsearch, Outbox metrics, and database schema isolation.

## Extraction Gate

Do not extract another domain until these contracts have tests, ownership has
remained table-safe, and the failure sequence passes repeatedly. Search and
Auction now have transitional boundaries with executable smoke checks. Auction
data ownership is implemented behind a feature flag so the deployment can be
cut over and rolled back deliberately. The next gate is disabling Core's old
Auction writers after migration-28 verification, then adding cross-service
reconciliation and failure-injection checks before extracting another domain.
