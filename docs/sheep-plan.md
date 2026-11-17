# feat/sheep 구현 계획

기준 dev: 4c3ba5e2a458d9825e9f0724ebe79ec9f8638b9d. 새 clone의 feat/sheep에서 작업한다.

## 범위
- GET /api/sheep, GET /api/sheep/transactions.
- Sheep wallet, immutable ledger, record reward decisions (0/1) only. No User/Record/drawing/AI entity implementations.
- Lazy wallet: missing read=0 without INSERT; first mutation uses atomic MySQL-compatible upsert and lock.
- Internal REQUIRED use/refund methods and MANDATORY awardRecord called in the record-save transaction after caller validates completion/ownership.
- Same user/type/reference dedupe; meeting refund once; real charge required.
- Confirmed meeting policy: no rejoining after cancellation. Meeting domain retains cancelled membership and rejects rejoin before invoking Sheep; ledger deduplication alone is not a membership check.
- First record save/day +1, further record saves 0, demo flag permits +1 per new record. A separate per-record decision prevents a previous zero decision being rewarded by a next-day retry.
- Exact common/port/Ports.java contract from drawing PR, no drawing/AI implementations. WoolPort adapter with String referenceId and NOT_ENOUGH_WOOL.
- Real auth supplied via CurrentUserProvider. No auth implementation or userId header in production.

## 작업 순서
1. Baseline test, then HTTP tests before feature code; confirm missing endpoints fail.
2. Add persistence dependencies, entities/repositories, lazy wallet and ledger service from failing service tests.
3. Implement controllers/adapters and test real MVC/H2/JPA transactions.
4. Add daily/demo and concurrency/rollback/cross-day retry coverage, exact SQL/Hibernate validation.
5. Run entire suite and bootJar. Review diff against dev, ensure no records/User feature code. Document MySQL/auth limitations.

## 검증 대상
Authentication rejection, initial zero, owner-scoped pagination, string keys, duplicate/parallel spending/refund, insufficient funds, original debit required, outer rollback, real-record source uniqueness, daily/demo rewards, first zero decision retried next day, Seoul midnight and stale persistence-context locking.
