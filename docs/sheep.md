# Sheep API · 내부 연동

브랜치: feat/sheep. 기준 dev: 4c3ba5e2a458d9825e9f0724ebe79ec9f8638b9d.
이번 작업에는 Sheep API·엔티티·서비스·WoolPort 구현만 포함합니다. User/Record 엔티티와 그림·AI 구현은 없습니다.

## 공개 API

| Method | 경로 | 응답 data |
|---|---|---|
| GET | /api/sheep | {balance: 0} |
| GET | /api/sheep/transactions?size=20&cursor=... | {items: [...], nextCursor: null, hasNext: false} |

- 팀 ApiResponse {success,data,error} 사용. balance만 반환하며 sheepStage는 구현하지 않습니다.
- CurrentUserProvider로 인증된 본인 데이터만 조회합니다. 제공자가 없으면 401. 프로덕션 코드에서 X-User-Id 등 헤더를 직접 신뢰하지 않습니다.
- 지갑이 없으면 조회는 0이며 DB에 행을 만들지 않습니다. 첫 변경 시 원자적 upsert 후 행 잠금으로 지갑 생성/변경을 직렬화합니다.
- 내역은 id 내림차순. cursor 선택(양의 정수), size 기본 20, 1~50.
- 내역 필드: transactionId(Long), type(String), amount(Integer), balanceAfter(Integer), referenceId(String), meetingId(Long|null), createdAt(+09:00).
- referenceId는 문자열입니다. conversation:12:question:6 같은 원문을 보존하며 숫자나 해시로 대체하지 않습니다.

## 타입과 비용

| type | amount | referenceId | meetingId |
|---|---|---|---|
| RECORD | +1 | recordId의 문자열 | null |
| MEETING_CREATE | -1 | meetingId의 문자열 | meetingId |
| MEETING_JOIN | -1 | meetingId의 문자열 | meetingId |
| MEETING_REFUND | +1 | meetingId의 문자열 | meetingId |
| AI_EXTRA_CHAT | -1 | AI 요청의 문자열 키 | null |

동일 사용자·유형·원인 키는 한 번만 처리합니다. 모임별 UNIQUE도 적용합니다. 환불은 원래 생성/참여 차감이 있을 때만 한 번 +1. 두 종류를 모두 차감해도 현 정책의 환불은 모임/사용자당 최대 +1입니다. 환불 자격(언제 취소 가능한지)은 모임 담당 코드가 검증해야 합니다.

### 재참여 정책 (확정)

- 참여 취소 후 같은 모임에 재참여할 수 없습니다. 환불 여부와 관계없이 취소된 참여 이력을 유지합니다.
- 모임 담당 코드는 참여 요청 시 기존 취소 이력을 확인하고, 참여 상태를 다시 만들거나 양털 차감을 호출하기 전에 재참여를 거절해야 합니다. 취소 이력을 삭제해서는 안 됩니다.
- Sheep의 동일 모임 참여 차감 중복 처리는 기존 요청 재시도를 위한 것입니다. `applied=false`는 재참여 허용을 뜻하지 않습니다. Sheep 거래 내역만으로 참여 취소 상태를 판단하지 않습니다.
- 이 브랜치에는 모임 엔티티/API가 없으므로 재참여 요청을 실제로 차단하는 검증은 모임 담당 코드에 연결해야 합니다.

## 내부 메서드

```java
sheepService.awardRecord(userId, recordId);              // SheepAwardResponse(sheepEarned,sheepBalance)
sheepService.useMeetingCreate(userId, meetingId);        // SheepChangeResponse
sheepService.useMeetingJoin(userId, meetingId);
sheepService.refundMeeting(userId, meetingId);
sheepService.spendExtraChat(userId, "conversation:12:question:6");
```

- 외부에서 호출할 지급/차감 HTTP API는 없습니다.
- awardRecord는 MANDATORY 전파입니다. 기록 담당자가 실제 최종 저장·소유권·준비 상태를 검증하고 **같은 DB 트랜잭션에서** 호출합니다. Sheep은 아직 없는 Record/User 테이블을 조회하거나 생성하지 않습니다. 단순히 트랜잭션을 열었다고 실제 회고 저장이 검증되는 것은 아니며, 이 부분은 호출 도메인의 계약입니다.
- 일반 모드: 한국 시간 하루 첫 최종 저장만 +1. 같은 날 이후 저장은 sheepEarned=0. 다음 날짜의 새 기록은 +1.
- sheep_record_rewards는 보상 0 결정도 보존합니다. 어제 0으로 처리한 기록을 오늘 재시도해도 지급하지 않습니다. 보상 0은 공개 거래 내역에는 나타나지 않습니다.
- DEMO_DAILY_LIMIT_OFF=true(기본 false)는 신규 recordId마다 +1. 같은 recordId 재요청은 시연에서도 0. 보상 모드/결정 내역은 불변으로 보존합니다.
- 사용·환불은 REQUIRED 전파이며 호출자의 모임/AI 트랜잭션에 참여합니다. 질문/모임 저장이 실패하면 차감과 거래 내역도 롤백합니다.
- 동시 변경은 지갑의 upsert/행 잠금 및 원인 키 UNIQUE로 제어합니다. 기존 영속성 컨텍스트에 오래된 지갑이 있어도 잠금 후 refresh하여 덮어쓰기/초과 사용을 방지합니다.
- 양털 부족: NOT_ENOUGH_WOOL. 잘못된 값: INVALID_REQUEST. 차감 없는 환불: REFUND_NOT_ELIGIBLE.
- 모든 내부 호출은 서버가 검증한 userId와 원인 ID로만 수행합니다. 호출자가 프론트가 보낸 임의 ID를 그대로 신뢰하면 안 됩니다.

## 동료 PR과의 연결

common/port/Ports.java는 그림·AI PR #1의 9560525f412d839e0ff0878bbc06c854c0e6dcbc 버전과 동일한 정의만 가져왔습니다. 다른 도메인의 구현을 넣은 것은 아닙니다.

WoolPortAdapter는 int balance(userId), void spend(userId,String referenceId)를 실제 지갑에 연결합니다. NOT_ENOUGH_WOOL은 PR의 동일 오류 코드/문구입니다. 두 PR 병합 시 공통 ErrorCode에서는 양쪽 추가 코드를 모두 유지하되 NOT_ENOUGH_WOOL을 중복 선언하지 않도록 확인합니다.

원래 PR의 RecordPort/인증/모임 인터페이스는 이번 브랜치에서 구현하지 않습니다. CurrentUserProvider는 인증 담당자가 제공해야 합니다. 테스트용 X-Test-User 제공자는 src/test에만 있고 실행 JAR에는 포함되지 않습니다.

## DB

- sheep_wallets: user_id PK, balance, created_at, updated_at.
- sheep_transactions: 확정된 +1/-1 거래, 문자열 reference_id, meeting_id, reward_mode, reward_date, balance_after.
- sheep_record_rewards: record_id PK, user_id, decided_date, sheep_earned(0/1), nullable transaction_id. 0 처리한 기록의 다음 날 재시도도 차단.
- User/Record FK는 해당 담당 엔티티가 아직 없으므로 만들지 않습니다. Sheep 내부 FK는 생성 SQL에 포함됩니다.
- DB 시각은 팀 규칙대로 Asia/Seoul 로컬 DATETIME, API는 +09:00입니다.

src/main/resources/db/sheep-mysql.sql은 **새 MySQL 8 로컬 DB에 수동 적용할 초기 스키마**입니다. 기존 DB에는 백업/데이터 검토 후 별도 마이그레이션해야 합니다. 자동 실행/운영 DB 변경은 하지 않습니다.

reference_id는 utf8mb4_bin을 사용합니다. 대소문자가 다른 문자열 키를 같은 요청으로 잘못 판단하지 않도록 MySQL 시작 시 이진 collation도 읽기 검증합니다. Hibernate 자동 생성 테이블이 기본 case-insensitive collation이면 시작을 거절하므로 반드시 생성 SQL 또는 검토된 마이그레이션을 적용하세요. 이 검증 자체는 DDL을 실행하지 않습니다.

테스트 SQL은 H2가 지원하지 않는 컬럼별 collation 절만 제거한 동등 스키마이며 H2 기본 문자열 비교는 대소문자를 구분합니다. H2 검증은 실제 MySQL 실행 검증을 대체하지 않습니다.

## 실행·검증

```powershell
$env:JAVA_HOME='C:/path/to/jdk-17'
.\gradlew.bat test bootJar
# 한글 경로의 Gradle 테스트 클래스 로딩 오류를 피하려면:
.\scripts\verify-sheep.ps1
```

로컬 서버는 기본 README의 application-local.yml + MySQL 설정을 따릅니다. 이 브랜치만 실행하면 실제 CurrentUserProvider가 없어 양털 API는 401입니다. 임의 로그인 우회 코드를 추가하지 않습니다. 정상 HTTP 동작은 테스트 전용 인증 제공자를 넣은 실제 서버 테스트로 검증합니다.

검증 범위: HTTP 인증/소유권/필드/검증, 실제 WoolPort 선택, 최초 잔액, 일일/시연 보상, 0 결정의 다음 날 재시도, 문자열 키, 병렬 생성/차감/환불, 실패 롤백, 오래된 지갑 캐시, SQL/JPA 매핑.
