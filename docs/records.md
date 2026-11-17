# Records API and integration

Branch: feat/records. Initial Records work was based on c342123; latest origin/feat/sheep 4119db3 is now merged, including dev drawing/AI code. No User implementation added.

## API

All endpoints use CurrentUserProvider, not a userId supplied by the client. Headers: `Authorization` follows the team's real authentication implementation; POST uses `Content-Type: application/json`. That authentication provider is not implemented here.

| Method | Path | Request | Response data |
|---|---|---|---|
| POST | /api/records | `{ "recordId": 501 }` | `{recordId,recordDate,completedAt,sheepEarned,sheepBalance}` (201) |
| GET | /api/records/today | none | `{completed,recordId,recordDate}` |
| GET | /api/records | date optional yyyy-MM-dd, cursor optional positive recordId, size 1–50 default20 | `{items:[{recordId,recordDate,title,imageUrl,completedAt}],nextCursor,hasNext}` |
| GET | /api/records/{recordId} | none | `{recordId,recordDate,imageUrl,drawingDescription,analysis:{mainElements,spatialComposition,lineExpression,emotionMeaning},messages:[{sequenceNo,role,content,sentAt}],completedAt,title}` |
| GET | /api/records/calendar | month required yyyy-MM | `{month,items:[{date,recordId,imageUrl}]}` |
| DELETE | /api/records/{recordId} | none | 204, no body |

Except DELETE, success uses the existing `ApiResponse {success,data,error}`. Errors also use it: 400 INVALID_REQUEST; 401 UNAUTHORIZED; 403 FORBIDDEN; 404 RECORD_NOT_FOUND; 409 RECORD_NOT_READY; 503 RECORD_SOURCE_UNAVAILABLE.

## Lifecycle and rewards

- Drawing calls RecordPort.create(userId) inside upload transaction. The adapter generates a DB identity recordId in IN_PROGRESS. Upload rollback rolls back creation.
- AI calls RecordPort.cardReady(recordId). A finished/deleted record cannot be reopened by this signal.
- POST accepts only recordId. Client cannot supply authoritative analysis/owner/reward values.
- Completion requires CARD_READY plus a validated server-side source snapshot. Frozen source data and Sheep award are saved in one transaction. Repeated completion returns original completion info with sheepEarned=0 and current balance, even if reader has become unavailable.
- Records captures one Seoul completion time and passes it to the added internal `SheepService.awardRecordAt(userId,recordId,completedAt)` method. Record date and reward day remain equal even when wallet locking crosses midnight. The original `awardRecord` contract remains available for existing callers. Neither method accepts a client-provided timestamp.
- Multiple completions/day allowed. First new completed record per Asia/Seoul day pays1, later ones0, using existing Sheep rules (including demo override). No DAILY_RECORD_ALREADY_EXISTS error.
- Deletion is soft, does not refund/remove earned Sheep or remove reward decisions. Deleted records cannot be restored by save/cardReady. Original drawing/AI rows/files are not deleted here.
- today means a nondeleted completion exists today, not permission to save. Deleted-all today can return false while the daily reward remains consumed.
- list sorts completedAt DESC,id DESC, filters out unfinished/deleted records. Cursor is an owned previously completed recordId (deleted cursor remains usable). Calendar uses the same completion order per date; deleting latest reveals previous completion.
- recordDate is final-save date in Seoul. title is the AI-generated record title, independent of the selected topic. A missing/blank AI title or title over 500 characters is not ready for completion. Separate legacy whatISaid/thoughts/todaysStory are not part of this agreed detail response.

## Required drawing/AI connection

Implement `RecordSnapshotReader.find(recordId)` as a Spring bean in the source domain. Return Optional.empty for missing source; no bean yields503, missing/incomplete source yields409. No production fallback fabricates data.

Snapshot: recordId,userId,ready,title,imageUrl,drawingDescription,analysis,messages. ready means conversation ended and the AI-generated title plus all four analysis fields are persisted. Provider must return a consistent source snapshot; it must not call RecordPort again or take locks in reverse order. No network/AI call during this read transaction.

Source lookup: DrawingRepository.findByRecordId; ConversationRepository.findByRecordId; messages by conversationId ordered id ascending; AI-generated title and analysis via their persisted source fields (not TopicReader or topicTitle). Convert source role into USER/ASSISTANT. Normalize SKIP/DONT_KNOW into their display text and assign sequenceNo 1..N without gaps; preserve original message timestamps (OffsetDateTime). Return all original messages, not whatISaid summary. Analysis strings must be nonblank. The adapter validates identity and required fields again.

Current drawing/AI branch has drawing, conversation and messages, but does not generate/store the required four analysis sections. This branch therefore supplies the contract only; real source integration is still needed. Tests use an explicitly test-only provider and cannot establish actual AI integration success.

## DB and verification

New entities: ReflectionRecord(records), RecordMessage(record_messages embeddable ordered collection). SQL: src/main/resources/db/records-mysql.sql. Apply manually to a new local MySQL8 database, after Sheep schema. Existing databases require a reviewed migration. No public draft endpoint: Drawing owns upload start and calls RecordPort directly.

H2 MySQL mode tests cover HTTP, port lifecycle, ownership, source readiness, immutable snapshots, daily rewards, concurrent save, rollback, pagination, calendar/deletion, midnight-crossing rewards and stale managed-record refresh after locking. Schema validation also loads Records SQL alongside Sheep SQL. Actual MySQL and full drawing→AI→Records→Sheep flow must be checked after team integration. Auth provider is still required in real runtime.

Records branch includes Sheep commits. Merge Sheep PR first, update Records from dev, then review only Records diff in the PR to dev. Do not merge to main directly.

## Sheep synchronization

- Kept latest Sheep Ports, build.gradle without buildDirOverride, two Sheep error additions, real WoolPort and scoped Sheep schema validation.
- Removed LocalStubs.recordPort only in Records branch, since RecordPortAdapter now supplies actual IDs. ConversationFlowTest selects its own isolated fake RecordPort with @Primary; runtime uses the real implementation.
- Records has its own SQL validation test instead of adding unrelated tables to SheepSchemaTest.
- Real RecordSnapshotReader and four AI analysis sections remain unconnected; synchronization does not implement or claim that integration. Notion contract differences previously identified remain a separate follow-up.
