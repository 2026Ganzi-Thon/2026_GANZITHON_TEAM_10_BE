# 모양 MOYANG — 백엔드

그림과 AI의 질문으로 마음을 알아보고, 동의한 사용자끼리 그림을 나누는 서비스의 백엔드 API 서버입니다.
2026 간지톤(아리톤) 10팀 · 백엔드 백승효 · 박준서 · 박준석

## 시작하기

JDK 17 이상이 필요합니다. (21·24로 빌드해도 결과물은 Java 17 기준으로 만들어집니다) Gradle은 래퍼가 포함되어 있어 따로 설치하지 않습니다.

```bash
git clone https://github.com/2026Ganzi-Thon/2026_GANZITHON_TEAM_10_BE.git
cd 2026_GANZITHON_TEAM_10_BE
./gradlew build              # Windows: .\gradlew.bat build
java -jar build/libs/app.jar
```

| 주소 | 용도 |
| --- | --- |
| http://localhost:8080/health | 서버 상태 확인 (`serverTime`이 +09:00이면 정상) |
| http://localhost:8080/swagger-ui/index.html | API 문서 (프론트 공유용) |

### 로컬 DB 설정 (DB를 쓰는 기능부터)

`src/main/resources/application-local.example.yml` 을 복사해 `application-local.yml` 로 저장하고 내 비밀번호를 넣은 뒤 `--spring.profiles.active=local` 로 실행합니다.
`application-local.yml` 은 `.gitignore` 에 들어 있으며 **절대 커밋하지 않습니다.**
Upstage 키 같은 비밀값은 프로젝트 루트 `.env` 에 `UPSTAGE_API_KEY=...` 로 적으면 자동으로 읽습니다 (`.env` 도 커밋 금지).
JPA가 들어간 뒤로는 DB 없이 서버가 뜨지 않으므로, 로컬 실행은 이 방법으로 합니다. (`./gradlew build` 테스트는 H2로 돌아서 DB가 없어도 됩니다)
local 프로필에서는 다른 담당 기능이 올라오기 전까지 `X-User-Id` 헤더로 로그인을 흉내 냅니다 (`common/port/LocalStubs`). 그 외 프로필에서는 401로 막힙니다.

### 운영 환경 변수

운영(`prod`)에서는 값을 코드에 적지 않고 환경 변수로만 받습니다.

| 환경 변수 | 용도 |
| --- | --- |
| `DB_URL` | RDS 접속 주소 (`...?serverTimezone=Asia/Seoul&characterEncoding=UTF-8`) |
| `DB_USERNAME` / `DB_PASSWORD` | RDS 계정 |
| `CORS_ALLOWED_ORIGINS` | 허용할 프론트 주소 (쉼표로 구분) |
| `UPSTAGE_API_KEY` | AI 그림 대화용 Upstage 키 (없으면 기본 질문 · 기본 카드로 동작) |
| `UPLOAD_DIR` | 그림 파일 저장 폴더 (운영 필수. 재배포에도 남는 jar 밖 경로로) |

## 기술과 구조

Java 17 · Spring Boot 4.1.0 · Gradle · MySQL 8(AWS RDS) · Spring Data JPA · springdoc(Swagger) · AWS EC2 · GitHub Actions

기능(도메인)별로 폴더를 나눕니다. 한 기능의 컨트롤러·서비스·엔티티·DTO는 그 폴더 안에 둡니다.

```
src/main/java/com/ganzithon/moyang/
├─ common/       공통 응답(ApiResponse) · 예외(ErrorCode, BusinessException)
├─ config/       CORS · Swagger 등 설정
├─ health/       /health
├─ auth/         로그인(데모 계정)                 · 백승효
├─ user/         내 정보 · 온보딩(고민 선택)        · 백승효
├─ drawing/      그림 올리기 · 앱 그리기            · 박준서
├─ ai/           AI 그림 대화 (Upstage Solar)       · 박준서
├─ record/       회고 카드 · 기록 · 캘린더          · 박준석
├─ sheep/        양털 · 양 상태 · 양털 내역         · 박준석
└─ meeting/      그림 나눔 모임                     · 백승효
```

각 기능 폴더 안은 아래처럼 둡니다.

```
meeting/
├─ controller/   MeetingController
├─ service/      MeetingService
├─ repository/   MeetingRepository
├─ domain/       Meeting (엔티티), MeetingStatus (enum)
└─ dto/          MeetingCreateRequest, MeetingResponse
```

## 코드 규칙

- 모든 응답은 `ApiResponse` 로 감쌉니다: `{ "success": true, "data": {...}, "error": null }`
- 에러는 `throw new BusinessException(ErrorCode.MEETING_FULL)` 처럼 던지면 공통 형식으로 응답됩니다. 새 에러 코드는 `ErrorCode` 에 추가합니다.
- 주소는 소문자·복수형 (`/api/meetings`, `/api/records/{recordId}`)
- 이름: 클래스 `PascalCase`, 메서드·변수 `camelCase`, 상수·enum `UPPER_SNAKE_CASE`
- 요청/응답 DTO는 `XxxRequest` / `XxxResponse`, 엔티티는 DTO로 바꿔서 응답합니다 (엔티티를 그대로 반환하지 않음)
- 시각은 `Asia/Seoul` 기준으로 저장·응답합니다
- API 명세는 노션 **🔌 API 명세서**가 기준입니다. 바꾸면 노션과 프론트에 먼저 알립니다

## GitHub 협업 규칙 (프론트와 동일)

### 브랜치와 병합

`main` 은 배포되는 안정 코드, `dev` 는 작업 내용을 합쳐 확인하는 브랜치입니다. 기능 개발이나 수정은 최신 `dev` 에서 작업 브랜치를 만들어 진행합니다.
브랜치 이름은 `종류/작업-내용` (소문자와 하이픈)으로 씁니다.

```
feat/meeting-create
feat/sheep-refund
fix/record-daily-limit
docs/update-readme
```

1. 최신 `dev` 에서 작업 브랜치를 만듭니다 (`git checkout dev && git pull && git checkout -b feat/...`)
2. 작업 브랜치에 커밋하고 GitHub에 push합니다
3. GitHub 웹에서 **작업 브랜치 → dev** Pull Request를 만들고 변경 사항과 테스트 방법을 적습니다
4. 백엔드 팀원 **1명 이상 승인** 후 GitHub 웹에서 병합하고 작업 브랜치를 삭제합니다
5. `dev` 통합 확인이 끝나면 **dev → main** PR로 병합합니다 (main 병합 = 배포)

`main` 에 직접 push하지 않습니다. PR 하나에는 목적 하나만 담고, 작게 나눠 올립니다.

### 커밋 메시지

`종류(범위): 변경 내용` 형식을 씁니다. 범위는 소문자와 하이픈, 제목 끝에 마침표를 붙이지 않습니다.

```
feat(meeting): 모임 만들기 API 추가
fix(sheep): 예약 취소 시 양털 중복 환불 수정
refactor(record): 회고 저장 로직 분리
docs(readme): 실행 방법 수정
```

종류: `feat` `fix` `refactor` `test` `docs` `chore` `build` `ci` `revert`

### 올리면 안 되는 것

`application-local.yml` · `.pem` · `.env` · 비밀번호·API 키가 들어간 파일. 실수로 올렸다면 즉시 팀에 알리고 키를 교체합니다.

### 마감

대회 공지에 따라 **2026년 11월 20일(금) 23:59까지 개발과 커밋을 완료**하고, 이후에는 어떤 브랜치에도 커밋하지 않습니다.

## Sheep 기능 개발

양털 API·내부 지급/사용/환불·WoolPort 연결·DB·테스트는 [docs/sheep.md](docs/sheep.md)를 참고합니다.
