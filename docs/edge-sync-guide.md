# Edge sync payloads, conflict handling, and mobile queue guide

This guide shows the exact HTTP payloads used by the edge → cloud sync endpoints, how conflicts are detected, and how to model the mobile (React Native) offline queue so it aligns with the backend contracts already in place.

## Authentication for edge sync (cloud side)

- Endpoints: all `/api/cloud/sync/**`
- Header: `X-Edge-Sync-Token: <token>` (or `Authorization: Bearer <token>`) validated by `EdgeSyncAuthenticationFilter`.
- Configuration (cloud):
  - `APP_EDGE_SYNC_TOKEN` (maps to `app.edge.sync.token`)
  - `EDGE_COMPANY_NUMBER` (maps to `app.edge.sync.company-number`)
  - `EDGE_NODE_ID` (maps to `app.edge.sync.edge-node-id`, optional label for logs)
- Effect: requests authenticate as an edge principal without a user JWT; method-level security stays in place because the filter sets an authenticated principal.

## HTTP payloads (edge → cloud)

### 1) Sessions/observations pull
- Endpoint: `POST /api/cloud/sync/sessions`
- Request (`CloudSessionSyncRequest`):
```json
{
  "farmId": "6f3d1b3a-9c67-4a7e-8e1a-0e8c9f1c2c44",
  "since": "2026-01-06T10:15:30",
  "includeDeleted": true
}
```
- Response (`ScoutingSyncResponse`):
```json
{
  "sessions": [
    {
      "id": "a1c7...",
      "version": 4,
      "farmId": "6f3d...",
      "status": "COMPLETE",
      "syncStatus": "SYNCED",
      "updatedAt": "2026-01-06T10:20:11",
      "sections": [...],
      "recommendations": [...]
    }
  ],
  "observations": [
    {
      "id": "b91d...",
      "sessionId": "a1c7...",
      "version": 3,
      "speciesCode": "APHIDS",
      "bayIndex": 1,
      "benchIndex": 2,
      "spotIndex": 0,
      "count": 5,
      "syncStatus": "SYNCED",
      "deleted": false,
      "updatedAt": "2026-01-06T10:20:11"
    }
  ]
}
```
- Usage: the edge compares versions/updatedAt to its local store and merges changes (including `deleted=true` rows when `includeDeleted` is set).

### 2) Photo metadata registration
- Endpoint: `POST /api/cloud/sync/photos/register`
- Request (`PhotoMetadataRequest`):
```json
{
  "sessionId": "a1c7...",
  "observationId": "b91d...",        // optional
  "localPhotoId": "device-uuid-12345",
  "purpose": "leaf_damage",           // optional
  "capturedAt": "2026-01-06T09:55:00" // optional
}
```
- Behavior: idempotent on `(farmId, localPhotoId)`; creates or returns the same photo record with `syncStatus=PENDING_UPLOAD` until confirmation.

### 3) Photo upload confirmation
- Endpoint: `POST /api/cloud/sync/photos/confirm`
- Request (`PhotoUploadConfirmationRequest`):
```json
{
  "sessionId": "a1c7...",
  "localPhotoId": "device-uuid-12345",
  "objectKey": "companies/ACME/farms/6f3d/sessions/a1c7/photos/device-uuid-12345.jpg"
}
```
- Behavior: marks the photo `SYNCED`, links the storage key, and triggers cache eviction/processing on the cloud side.

## Conflict handling example (observations)

1. Edge edits offline with stale version:
   ```json
   {
     "id": "obs-1",
     "version": 2,
     "count": 6,
     "clientRequestId": "uuid-x"
   }
   ```
2. Cloud already has `version=3` for `obs-1`.
3. Edge sends the update → cloud checks `request.version` vs `current version` and returns `409 Conflict` when they differ.
4. Edge resolution options:
   - Fetch `/api/cloud/sync/sessions` with the latest `since` timestamp to refresh versions, reapply the local change, and retry.
   - Or mark the local row `SyncStatus.CONFLICT` and prompt the user to resolve.

This flow matches the existing optimistic-lock check in `ScoutingSessionService` for observation upserts.

## React Native offline queue (recommended shape)

Align the mobile queue with backend contracts so retries and sync are idempotent:

- **Identifiers**
  - Use `clientRequestId` for observations and `localPhotoId` for photos; store them with the queued job so retries are stable.
  - Persist server-assigned `version` and `syncStatus` after every successful push/pull.

- **Queue collections**
  - `observationJobs`: `{ sessionId, payload, clientRequestId, lastKnownVersion }`
  - `photoMetadataJobs`: `{ sessionId, observationId?, localPhotoId, purpose, capturedAt }`
  - `photoConfirmJobs`: `{ sessionId, localPhotoId, objectKey }`

- **Dispatch order**
  1. Push observation upserts/deletes first; handle `409` by marking `CONFLICT` locally and refreshing via `/api/cloud/sync/sessions`.
  2. Register photo metadata (`/api/cloud/sync/photos/register`).
  3. Upload binary to storage (presigned URL or local staging), then confirm (`/api/cloud/sync/photos/confirm`).

- **Retry policy**
  - Exponential backoff with jitter; cap retries but keep items until the cloud responds with `200/201` or a terminal `409`.
  - On `409`, fetch the latest snapshot, merge, and resubmit with the new `version` or surface a conflict prompt.

- **State transitions**
  - `LOCAL_ONLY` → after enqueue
  - `PENDING_UPLOAD` → after a request is sent but before acknowledgment
  - `SYNCED` → after cloud success
  - `CONFLICT` → after repeated `409` responses; requires user or automatic resolution

- **Storage hints**
  - Keep the queue in durable storage (SQLite/AsyncStorage) so it survives app restarts.
  - Cache the last successful `since` timestamp to minimize payload size on sync pulls.

Following this shape keeps the mobile queue compatible with the backend’s idempotency keys, optimistic locking, and sync statuses.
