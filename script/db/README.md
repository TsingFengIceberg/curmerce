# Curmerce MySQL Baseline

`generate-foundation-schema.sh` extracts only MySQL table definitions for the imported `system` and `infra` modules from the pinned `ruoyi-vue-pro` reference submodule. It deliberately excludes all upstream records because that dataset contains credentials and private-looking demonstration data.

The generated schema is written to `target/generated-db/` and remains untracked. Apply `foundation-seed.sql` afterward, replacing its three placeholders with a locally generated BCrypt administrator password hash, OAuth client secret, and the loopback API base URL used to serve uploaded files, for example `http://127.0.0.1:48080`. The file base URL must use the same port as `CURMERCE_SERVER_PORT`. Never commit those local values.

## Merchant onboarding migration

Apply `migrations/20260813-01-merchant-onboarding.sql` only after taking a local backup and running its duplicate preflight queries. Any returned duplicate active username or role code is a stop condition; do not rename or delete data automatically. Run the post-apply count and `information_schema` checks in the same session. Use a MySQL client credential file or the existing user-owned service environment; never put a password in a command or this repository.

The adjacent rollback file is a review aid for disposable local data only. It is not idempotent, is not run by application startup, and must not be used to remove business data or System users without explicit approval.

## Ordinary product persistence migration

`migrations/20260814-02-ordinary-product-model.sql` depends on the merchant
onboarding migration and adds the first Curmerce catalog persistence model:
platform categories, merchant/store-owned products, and merchant-owned SKUs.
The product row owns descriptive content and review/listing state; the SKU row
is the authority for price in cents and current stock. The migration does not
seed categories or products and does not enable the upstream Mall modules.

Before applying it, verify the read-only preflight results in the migration:
the three product table names and the named store composite key must not already
exist, the existing store rows must be structurally valid, and MySQL must be
8.0.16 or newer with InnoDB. Take a local backup first. Use the existing
user-owned credential environment or a protected client credential file; never
put a password in a command, plan, SQL output, or repository file.

The migration adds `uk_commerce_store_id_merchant (id, merchant_id)` so the
product's `(store_id, merchant_id)` foreign key proves that a store belongs to
the product merchant. It then creates category, product, and SKU tables with
named unique keys, checks, foreign keys, and management/public-query indexes.
The product and SKU business codes remain reserved after logical deletion;
this is intentional because future order snapshots, audit records, and repair
tools may retain those identifiers.

Category self-parenting, longer cycles, and maximum depth are validated by the
future category application service. MySQL does not allow a `CHECK` constraint
to reference the table's auto-increment identifier, so the migration enforces
the parent foreign key, status, and nonnegative sort but deliberately does not
claim to enforce the complete tree shape in DDL.

Run every post-apply `information_schema` query from the migration in the same
review session. Confirm native MySQL `JSON` columns, the exact index column
order, all foreign-key targets, enforced check constraints, and the absence of
an accidental `tenant_id`. H2 tests use `VARCHAR` for the two JSON fields and
mapper round-trip tests to cover the type-handler contract; they do not replace
the live MySQL JSON checks.

The adjacent `20260814-02-ordinary-product-model.rollback.sql` is a disposable
review aid. It refuses to remove rows and contains only commented destructive
statements. Do not run it for normal development or after a partial migration;
MySQL DDL auto-commits. If a statement fails after earlier statements applied,
inspect the live schema and prepare a forward repair after review instead of
rerunning or dropping tables.

## Media asset migration

`migrations/20260826-22-media-asset-foundation.sql` adds stable asset metadata,
business references, upload quotas, direct-upload tickets, moderation state,
and the persistent object-storage migration ledger. Back up both `infra_file`
and `infra_file_content` before running it. All four preflight table names and
all listed `infra_file` columns must be absent; a returned row is a stop
condition because MySQL DDL auto-commits.

Apply every prior numbered migration first, then run the post-apply column and
index queries in the same session. The rollback companion is only a review aid
for a disposable database. It deliberately leaves destructive statements
commented and requires references, derived variants, quota usage, upload
tickets, and migration audit records to be empty.

Object bytes are migrated separately through the authenticated media
administration API described in `docs/media-architecture.md`. That workflow is
dry-run and non-destructive by default, verifies SHA-256 after copying, and
never removes the database source automatically.

## Community schema isolation and media Outbox

Migration 23 atomically moves the eight Community-owned tables from `curmerce`
to `curmerce_community` with one cross-schema `RENAME TABLE`. Stop Community,
take a protected backup, record exact row counts, and confirm the target schema
contains no tables before applying it through the local administrative Unix
socket. Create `curmerce_community@127.0.0.1` separately and grant only
`SELECT`, `INSERT`, `UPDATE`, and `DELETE` on `curmerce_community.*`; never put
its password in a migration file. Verify both directions: the Community account
must not read `curmerce`, and the Core account must not read
`curmerce_community`.

Migration 24 creates `community_media_outbox` in the Community schema. Its
unique business key stores the latest desired media-reference payload, while a
monotonic version and processing token prevent an old worker from completing a
newer update. Do not remove the table while unfinished rows exist. The rollback
companions are review aids for a stopped disposable runtime, not normal rollback
automation.

## Search projection Outbox migration

Migration 25 (`20260828-25-kafka-consumer-receipt.sql`) creates the Core-side
Kafka consumer receipt ledger. Migration 26 (`20260828-26-community-search-outbox.sql`)
creates the Community-owned transactional Outbox for product and post search
projection events. Apply both after the earlier numbered migrations and verify
the schema name, unique event keys, status checks, and retry indexes. The
rollback files only support a stopped disposable local database after pending
events have been reviewed; they are not routine application rollback.

## Commerce reliability and Auction ownership migrations

Migration 27 (`migrations/20260830-27-commerce-reliability.sql`) adds the
operational indexes and constraints used by Commerce Outbox publishing and
payment/refund reconciliation. Apply it after the preceding numbered
migrations, inspect generated issue rows, and keep the rollback script as a
review aid only. Reconciliation repair endpoints never invent payment facts.

Migration 28 (`migrations/20260830-28-auction-schema.sql`) creates the dedicated
`curmerce_auction` schema and copies Auction sessions and bids from Core. Stop
Auction writers first, record the source and target counts from
`curmerce_auction.ownership_cutover`, and require `verified=1` before enabling
`CURMERCE_AUCTION_LOCAL_STORE_ENABLED=true`. The Core tables remain available as
a read-only rollback source during the transition; the migration does not
delete them and does not grant the Auction account access to the Core schema.
Create the dedicated `curmerce_auction@127.0.0.1` account with only the
permissions needed for `curmerce_auction.*`, and keep its password outside Git.
