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

`script/verify/cloud-runtime-regression.sh` remains the broader check for
Prometheus, Kafka/Elasticsearch, Outbox metrics, and database schema isolation.

## Extraction Gate

Do not extract another domain until these contracts have tests, ownership has
remained table-safe, and the failure sequence passes repeatedly. The next
candidate is search/notification projection work, followed by Auction only
after its single-process state machine is independently covered.
