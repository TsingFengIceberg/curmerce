# Curmerce local media services

This Compose project runs a private MinIO bucket, ClamAV, and signed imgproxy on loopback only. It does not contain credentials and is not a production deployment definition.

## Start

1. Create `deploy/media/.env` from `media.env.example`, use a private user-owned data directory, and generate independent credentials and imgproxy signing values.
2. Run `docker compose --env-file deploy/media/.env -f deploy/media/compose.yaml config` and inspect the resolved bind addresses and mounts.
3. Run `docker compose --env-file deploy/media/.env -f deploy/media/compose.yaml up -d`.
4. Verify `docker compose --env-file deploy/media/.env -f deploy/media/compose.yaml ps` and wait for the initial ClamAV signature download.

The MinIO bucket remains private. Browser PUT requests are allowed only from the two local frontend origins in `cors.json`; add another explicit origin before using a different development host.

## Curmerce configuration

Create an S3 file configuration with storage type `20` and these values, substituting secrets from the ignored `.env` file:

```json
{
  "endpoint": "http://127.0.0.1:59000",
  "domain": "http://127.0.0.1:59000/curmerce-media",
  "bucket": "curmerce-media",
  "accessKey": "<MINIO_ROOT_USER>",
  "accessSecret": "<MINIO_ROOT_PASSWORD>",
  "enablePathStyleAccess": true,
  "enablePublicAccess": false,
  "region": "us-east-1"
}
```

Prefer a dedicated MinIO application user instead of the root credentials after local bootstrap. Mark the S3 configuration as master only after a direct-upload smoke test succeeds.

Configure the backend with local secrets, never in a tracked YAML file:

```yaml
curmerce:
  media:
    clam-av:
      enabled: true
      fail-closed: true
      host: 127.0.0.1
      port: 53310
    imgproxy:
      enabled: true
      endpoint: http://127.0.0.1:58081
      key-hex: <IMGPROXY_KEY>
      salt-hex: <IMGPROXY_SALT>
```

## Remote browser access

When the frontend is opened through SSH, tunnel every browser-facing endpoint in one command:

```bash
ssh -p 2002 -L 3003:127.0.0.1:3003 -L 48080:127.0.0.1:48080 -L 59000:127.0.0.1:59000 -L 59001:127.0.0.1:59001 wugang@47.99.117.47
```

The backend-signed MinIO URL includes `127.0.0.1:59000`, so that exact tunnel is required for direct browser PUT. Do not rewrite the signed host after issuing a ticket because AWS SigV4 signs it.

## Stop and inspect

Run `docker compose --env-file deploy/media/.env -f deploy/media/compose.yaml down` to stop containers. Do not add `-v`: MinIO and ClamAV data are bind-mounted under `MEDIA_DATA_DIR` and should be removed only after an explicit backup and deletion decision.
