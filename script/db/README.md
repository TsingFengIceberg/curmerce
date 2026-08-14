# Curmerce MySQL Baseline

`generate-foundation-schema.sh` extracts only MySQL table definitions for the imported `system` and `infra` modules from the pinned `ruoyi-vue-pro` reference submodule. It deliberately excludes all upstream records because that dataset contains credentials and private-looking demonstration data.

The generated schema is written to `target/generated-db/` and remains untracked. Apply `foundation-seed.sql` afterward, replacing the two placeholders with a locally generated BCrypt administrator password hash and OAuth client secret. Never commit those local values.

## Merchant onboarding migration

Apply `migrations/20260813-01-merchant-onboarding.sql` only after taking a local backup and running its duplicate preflight queries. Any returned duplicate active username or role code is a stop condition; do not rename or delete data automatically. Run the post-apply count and `information_schema` checks in the same session. Use a MySQL client credential file or the existing user-owned service environment; never put a password in a command or this repository.

The adjacent rollback file is a review aid for disposable local data only. It is not idempotent, is not run by application startup, and must not be used to remove business data or System users without explicit approval.
