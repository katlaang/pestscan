# Offline sync guide

This document outlines the offline-first additions for scouting data so clients can sync safely after operating without connectivity.

## Data model updates

- **Version field**: Every mutable entity now exposes a `version` for optimistic concurrency. Clients send the last known version when updating existing rows.
- **Timestamps**: `updatedAt` is populated automatically to drive "changes since" queries.
- **Soft deletion**: Records are marked `deleted=true` and retain a `deletedAt` timestamp instead of being physically removed.
- **Idempotency key**: Observation writes accept a client-provided `clientRequestId` to allow safe retries.

## Write APIs

### Upsert observation

- Endpoint: `POST /scouting/sessions/{sessionId}/observations`
- Request body: `UpsertObservationRequest` including `sessionId`, `sessionTargetId`, location tags, species code, counts, optional `version`, and optional `clientRequestId`.
- Behavior:
  - If `clientRequestId` matches an existing observation in another session, the server rejects with `409 Conflict`.
  - If `version` does not match the current row, the server rejects with `409 Conflict` to signal a stale update.
  - Soft-deleted rows with the same key are restored when the client retries with the same `clientRequestId`.

### Bulk upsert observations

- Endpoint: `POST /scouting/sessions/{sessionId}/observations:bulk`
- Request body: `BulkUpsertObservationsRequest` that includes the `sessionId` and an array of `UpsertObservationRequest` payloads.
- Behavior: Validates that the path `sessionId` matches the payload. Processes each observation through the same upsert flow as above.

### Delete observation

- Endpoint: `DELETE /scouting/sessions/{sessionId}/observations/{observationId}`
- Behavior: Performs a **soft delete** (marks `deleted=true` and sets `deletedAt`) so the row can be propagated in sync responses.

## Sync API

### Fetch changes since timestamp

- Endpoint: `GET /scouting/farms/{farmId}/sync?since=<ISO timestamp>&includeDeleted=<boolean>`
- Response: `ScoutingSyncResponse` containing updated `ScoutingSessionDetailDto` and `ScoutingObservationDto` entries.
- Notes:
  - When `includeDeleted` is `false` (default), deleted observations are omitted.
  - When `includeDeleted` is `true`, deleted observations are returned with `deleted=true` and `deletedAt` populated.
  - Clients should update their local stores with the returned `version` and `updatedAt` values to avoid conflicts on the next write.

## Conflict handling

- Updates with stale `version` values return `409 Conflict` and a message indicating the entity changed on the server.
- Reusing a `clientRequestId` in a different session returns `409 Conflict` to prevent cross-session duplication.
- Clients should re-fetch changes via the sync API, merge locally, and retry with updated versions.

## Client recommendations

- Generate UUIDs for `clientRequestId` values on every new observation created offline.
- Persist versions locally and include them when updating observations after reconnecting.
- Sync regularly using the `since` timestamp of the last successful sync. Include `includeDeleted=true` if you need to propagate removals.
