# file-service

Business-agnostic blob storage for every AgriContract service (`docs/design/file-service-phase2-design.md`):
the bytes go to S3-compatible object storage under a key file-service generates, and the technical metadata goes
to the `file` table in `file_db`. file-service never knows what a file is for: the meaning is in the field name of
the owning service (`cadastralExtractFileId`, `sellerEvidenceFileId`...).

NestJS 11 on Node 22, port **8089**. Internal only: the gateway never routes it.

## Status

This is the scaffold and the store core of #116. **No upload endpoint is exposed yet.** The request and response
of `POST /internal/v1/files` (`storeOnBehalfOf`, `contracts/events/supplementary-services-api.yaml`) are not frozen,
and the split with #274 is open (asked on #116). Not done yet: virus scan and `file.ready`/`file.failed` (#131),
store-on-behalf, `store()`, confirm-attached, download and delete (#274), email intake (#275), retention (#276).

## The store core

`StoreFileUseCase` is the one function every entrypoint will call (design §3). The entrypoint passes its own
`IngestPolicy`, so the ingest channel comes from the path the file took, never from the caller (§2.1):

| Policy | Channel | Content types | Cap |
| --- | --- | --- | --- |
| `DIRECT_UPLOAD_POLICY` | `DIRECT_UPLOAD` | `image/jpeg`, `image/png`, `application/pdf` | 10 MiB |

For each store:

1. The content type is normalized (`Image/JPEG; x=y` -> `image/jpeg`) and checked, and the declared length must be
   a positive integer within the cap. Anything else is refused **before a byte is read**.
2. The fileId is a new UUID, and it is also the object key, in one flat bucket (§2.3).
3. The body goes to object storage as **one streamed `PutObject`** with the declared `Content-Length`. `ByteMeter`
   sits in between: it counts the bytes, computes the SHA-256 `storageHash`, and fails the stream as soon as the
   body sends a byte more than declared, or at the end if it sent fewer. Nothing is buffered whole: the meter holds
   back at most one chunk (see below).
4. Only then is the `file` row inserted: `PROCESSING`, `attached = FALSE`, `uploaded_by`, `ingest_channel`,
   `content_type`, the counted `file_size` and the `storage_hash`.

Failure handling:

- **A body that lies about its length** never leaves an object. The meter holds back the latest chunk until the
  next one arrives or the body ends with exactly the declared length. Storage commits an object once it has the
  declared `Content-Length`, so without the hold-back a too-long body would hand over the full length before its
  extra chunk showed up, and the object would be committed while the store fails. The int-spec caught this race on
  SeaweedFS.
- **A body that fails part way** (too short, caller gone) aborts the PUT at once. Otherwise the store would wait for
  the missing bytes until the 30 s socket timeout.
- **Blob first, row second**, because `storage_hash` is `NOT NULL` and comes from the stored bytes. If the row cannot
  be written, the blob is deleted, so a row never points at a missing blob. If that delete fails too, or the process
  dies in between, a blob is left with no row. §7's orphan cleanup works from rows and cannot see it, so the
  warning names the object key for a manual delete.
- Errors carry a code (`ECONNREFUSED`, `ER_...`, the S3 error name), never a message that could quote hosts, keys
  or the uploader.

## Configuration

Environment variables only (no `.env` file). The defaults match the compose services, so a local run needs none.

| Variable | Default | |
| --- | --- | --- |
| `DB_HOST` / `DB_PORT` / `DB_NAME` | `localhost` / `3317` / `file_db` | compose `file-db` |
| `DB_USERNAME` / `DB_PASSWORD` | `root` / `password` | |
| `S3_ENDPOINT` | `http://localhost:8333` | compose `object-storage` (SeaweedFS S3 API); `http(s)://host[:port]` |
| `S3_REGION` | `us-east-1` | |
| `S3_BUCKET` | `agricontract-files` | Created on startup when missing |
| `S3_ACCESS_KEY` / `S3_SECRET_KEY` | `agricontract` / `agricontract-dev-secret` | Dev identity of `infra/seaweedfs/s3.json` |
| `SERVER_PORT` | `8089` | |

Startup fails when MySQL or the bucket cannot be reached. `GET /actuator/health` answers 200 with
`components.db` and `components.storage` UP, or 503 with an error code for the one that is down.

## Database

`db/migration/V1__create_file.sql` is the `file` table of design §8 in MySQL syntax, with the same columns,
defaults, CHECK constraint and indexes. `email_intake_failure` comes with the email intake (#275). The app does not
migrate: compose's `file-migrate` (Flyway 10.10) runs before `file-service`, and CI runs the same image.

## Run and test

```bash
# in backend/
docker compose up -d --wait file-db object-storage
docker compose up file-migrate
docker compose up -d --build file-service      # optional: the service itself on :8089
```

| Command (in `backend/file-service`) | |
| --- | --- |
| `npm run lint:check` | ESLint with Prettier |
| `npm test` | Unit tests, no Docker |
| `npm run test:int` | The store core on the compose `object-storage` and `file-db` (migrated). Uses a bucket of its own per run and deletes it |
| `npm run build` | |

In Git Bash on Windows, put `MSYS_NO_PATHCONV=1` in front of a `docker run` that passes container paths such as
`-dir=/data`; otherwise they are rewritten to Windows paths.

## Layout

```
src/domain/stored-file.ts            the File aggregate (§2): channels, statuses, a new PROCESSING file
src/application/store-file.use-case  the store core shared by every entrypoint
src/application/byte-meter.ts        count + SHA-256 + length enforcement while streaming
src/application/ingest-policy.ts     what each entrypoint accepts
src/application/ports/               ObjectStoragePort, FileRepository
src/infrastructure/storage/          S3 client and adapter, bucket creation on startup
src/infrastructure/persistence/      mysql2 pool, `file` repository
src/infrastructure/health/           /actuator/health
```
