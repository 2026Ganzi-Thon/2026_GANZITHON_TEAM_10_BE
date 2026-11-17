# Records 작업 인수인계

## 브랜치와 현재 상태

- 작업 브랜치: `feat/records`
- 기준 브랜치: 최신 `origin/dev` (`13920f6`, Sheep PR 병합 포함)
- Records API, 엔티티, 저장소, 서비스, 테스트가 구현되어 있습니다.
- 현재 Records 작업은 로컬에만 있으며, 아직 원격 `feat/records` 브랜치는 없습니다.
- 마지막 검증: `./gradlew build --no-daemon` 성공, 테스트 67개 통과.

## 구현된 기능

- 회고 완료 저장: `POST /api/records`
  - 그림 AI 작업에서 준비된 `recordId`로 회고를 완료합니다.
  - 저장 트랜잭션에서 양털을 지급하며, 같은 날짜 첫 기록만 `sheepEarned: 1`, 이후는 `0`입니다.
- 오늘 완료 여부: `GET /api/records/today`
- 회고 목록: `GET /api/records`
  - 전체 날짜, 최신순, 커서 기반 페이지네이션
- 회고 상세: `GET /api/records/{recordId}`
  - 이미지, 그림 설명, 제목, 대화 메시지, AI 분석 4개 항목 포함
- 월별 캘린더: `GET /api/records/calendar?month=yyyy-MM`
  - 날짜별 최신 회고 이미지 표시
- 회고 삭제: `DELETE /api/records/{recordId}`
  - 논리 삭제이며, 삭제 후 같은 날짜의 이전 회고가 캘린더 대표 기록이 됩니다.
- 그림 업로드 측에서 회고 ID를 발급할 수 있도록 `RecordPortAdapter` 구현

## 노트북에서 이어가기

저장소를 열고 원격 브랜치를 가져옵니다.

```bash
git fetch origin
git switch feat/records
git pull --ff-only origin feat/records
```

브랜치가 아직 원격에 없다면 먼저 로컬 브랜치로 push합니다.

```bash
git push -u origin feat/records
```

## 이어서 확인할 사항

1. **그림/AI 원본 데이터 연결**: `RecordSnapshotReader` 구현체가 실제 AI·그림 모듈 데이터를 반환해야 합니다. 구현체가 없으면 완료 저장 API는 `RECORD_SOURCE_UNAVAILABLE`을 반환합니다.
2. **AI 분석 nullable 여부**: 현재 Records 저장 검증은 `mainElements`, `spatialComposition`, `lineExpression`, `emotionMeaning` 네 항목을 모두 요구합니다. `feat/drawing-ai`의 최신 변경에서는 일부 분석이 null일 수 있으므로, 그 브랜치가 dev에 병합되면 저장 정책과 검증 테스트를 함께 조정하세요.
3. **AI 생성 제목**: Records는 스냅샷의 AI 생성 `title`을 사용합니다. 그림/AI 모듈에서 제목이 실제 제공되는지 통합 확인이 필요합니다.
4. **Notion 명세와 응답 형식 대조**: 목록 커서/페이지 크기, 캘린더 응답 키(`items`와 `records`), 상세 권한 오류 코드를 팀 명세와 맞추세요.
5. **MySQL 통합 확인**: 자동 테스트는 H2 MySQL 모드 기반입니다. 실제 MySQL 환경 검증은 별도로 하지 않았습니다.

AI 모듈과 병합할 때는 `RecordPort` API와 `recordId` 발급 시점, 스냅샷 필드의 null 허용 정책을 먼저 대조하고 `./gradlew build --no-daemon`을 다시 실행하세요.
