# Curmerce Media Architecture and Runbook

## Scope

The media subsystem currently remains inside `yudao-module-infra`. Business modules depend only on `FileApi` for reference replacement, so the persistence and delivery implementation can later move to a dedicated Curmerce media module without rewriting product, member, order, or community tables.

## Ingestion pipeline

```text
authenticated user
-> per-user rate limit and atomic daily/storage quota reservation
-> multipart upload, or constrained S3 PUT ticket
-> server-side metadata HEAD and byte retrieval
-> real JPEG/PNG/WebP detection and dimension limits
-> ClamAV scan when enabled
-> owner-scoped SHA-256 deduplication
-> stable asset row and URL
-> asynchronous content moderation
-> asynchronous WebP and AVIF variants
-> business reference binding
```

Presigned tickets constrain the path, MIME type, byte length, and expiry. Finalization re-reads the object and does not trust the browser declaration. Ticket ownership, expiry, mismatch, retry, and finalization state are persisted in `infra_media_upload_ticket`.

The original upload is the source of truth. Stable URLs use `/app-api/infra/file/assets/{assetKey}` and never expose an object-store path. Public responses use immutable caching and an SHA-256 ETag. Private responses require the owner or an administrator and use `private, no-store`; the administration UI fetches private previews with an authorization header and a Blob URL.

## Lifecycle and safety

- `infra_file_reference` records each managed image used by a business record.
- A newly uploaded image that has never been bound is eligible for cleanup after 24 hours.
- A previously bound image becomes eligible seven days after its last reference is removed.
- Deletion preflights active references and removes derived variants with the original.
- ClamAV rejects the EICAR marker even when the daemon is disabled. When enabled, `fail-closed` is the default.
- Optional HTTP moderation accepts the image bytes and headers `Content-Type` and `X-Content-SHA256`, then returns JSON such as `{"decision":"SAFE|REVIEW|REJECT","reason":"..."}`.
- Moderation errors quarantine assets when `fail-closed` is enabled. Administrators can quarantine, release, reject, or retry from `/admin/media`.

## Derivatives

Without imgproxy, Java ImageIO creates `thumb-webp` (256 px) and `card-webp` (640 px). With signed imgproxy enabled, the asynchronous worker also creates matching AVIF variants. Delivery falls back to the original while a requested variant is absent.

imgproxy receives a short-lived private source URL. In the local Compose topology it uses host networking so the signed MinIO host remains unchanged. Never rewrite the hostname in a SigV4 URL.

## Database-to-object-storage migration

Back up `infra_file` and `infra_file_content` before applying migration 22 or moving objects. The administration migration endpoint is dry-run by default and accepts a target file-configuration ID, a batch size up to 200, and an explicit `switchMetadata` flag.

Each object is copied, read back, and SHA-256 verified before the database row may switch to the target configuration. `infra_media_migration` records source and target paths, attempts, hashes, errors, copy completion, and switch completion. The source object is never deleted automatically. Re-running a batch skips completed work and retries failed records.

Recommended sequence:

1. Back up both file tables and verify the backup can be read.
2. Start and health-check the loopback MinIO deployment.
3. Create a private S3 file configuration without making it master.
4. Run migration dry-runs until candidate counts and bytes are understood.
5. Copy in bounded batches with metadata switching disabled.
6. Enable metadata switching and rerun bounded batches.
7. Verify stable URLs, ETags, variants, business pages, and a backend restart.
8. Make S3 the master only after direct PUT and finalization pass.
9. Retain the database source until a separate reviewed cleanup decision.

## Metrics

Local actuator metrics are available under `/actuator/metrics`. Media meters include upload bytes and outcomes, delivery count, direct-upload ticket outcomes, variant outcomes, orphan cleanup, and moderation decisions. Do not expose actuator endpoints publicly without network and authorization controls.

## Runtime acceptance

Verify at least: unauthenticated rejection, valid upload, extension spoof rejection, excessive dimensions, EICAR rejection, quota exhaustion, constrained direct PUT and finalization, duplicate upload behavior, stable URL plus ETag/304, private access denial, business reference replacement, delayed orphan cleanup, WebP and AVIF variants, administrative quarantine/release, dry-run migration, verified copy and switch, restart persistence, and media metrics.
