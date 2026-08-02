# 이력서 분석 통합 — 후속 작업 4건

`feat/new`(이력서 평가 + 질문생성 API 통합) 작업 중 발견했으나 그 브랜치 범위를 넘어
별도 작업으로 분리한 항목이다. 각 항목의 근거는 발견 시점에 실행으로 확인했다.

우선순위는 **1 → 2 → 3 → 4** 순을 권한다. 1번이 2번의 선행 조건이고, 3번은 유료 기능
남용과 직접 연결되며, 4번은 순수 설계 정리다.

---

## 1. 추출 텍스트 캐시가 저장되지 않는다 (기존 버그)

`ResumeContentService.getOrExtractResumeContent` / `getOrExtractPortfolioContent`는
캐시 미스 시 S3에서 PDF를 받아 추출하고 `resume.updateContent(...)`로 저장하려 한다.
그런데 그 쓰기가 **플러시되지 않는다** — `open-in-view: false`이고 호출자
`ResumeAnalysisFacadeService.submitMemberAnalysis`에 트랜잭션이 없어 엔티티가
detached 상태다. 실행으로 확인했다(재추출 후에도 `content`가 `null`).

현재 이 버그가 드러나지 않는 이유는 업로드 경로가 INSERT 시점에 `content`를 채우고
이후 아무도 비우지 않아 미스가 발생하지 않기 때문이다. 즉 **잠재 상태**다.

**왜 고쳐야 하는가:** 이 버그가 2번을 불가능하게 만든다. 캐시를 비우면 다시 채워지지
않으므로 매 제출마다 S3 다운로드 + PDFBox 추출이 영구히 발생하고, 그것도 동시 추출을
6개로 제한하는 세마포어 안에서다.

**수정 방향:** 트랜잭션 경계 안에서 엔티티를 다시 로드하거나 `save()`를 명시한다.
어느 쪽이든 "캐시에 썼다"가 실제로 성립하는지 테스트로 고정할 것.

## 2. 기존 저장 자료가 링크 없이 채점된다

`PDFTextStripper`는 링크 annotation을 추출하지 않는다. "GitHub" 같은 글자에 URL이
annotation으로만 걸린 이력서는 그 URL을 잃고, 링크 교차 검증이 관찰항목인
`technical_skills` 채점이 영향을 받는다.

`feat/new`는 두 제출 경로(파일 업로드 / 저장자료 재사용)가 모두
`extractTextWithLinks`를 쓰도록 통일하고 양쪽 패리티를 테스트로 고정했다. 그러나
`member_resume.content` / `member_portfolio.content`에 **이미 캐시된 옛 텍스트**는
링크 없이 채워진 것이고, `hasContent()`가 재추출을 단축하므로 계속 쓰인다.

캐시를 비우는 마이그레이션을 작성했다가 철회했다(커밋 `e363403`). 이유는 1번과 아래
3번이다. 두 선행 조건을 해소한 뒤 재적용해야 한다.

**추가 선행 조건 — 과거 CloudFront 도메인:** `S3Service.extractKeyFromCdnUrl`은 URL이
현재 `AwsConstant.CLOUD_FRONT_DOMAIN_URL`로 시작하지 않으면 `BadRequestException`을
던진다. 과거 도메인으로 저장된 행은 재추출이 **불가능**하므로, 그 행의 `content`를
비우면 지금 동작하는 행이 영구히 깨진다. 배포 전에 다음을 세어 규모를 확인할 것.

```sql
SELECT COUNT(*) FROM member_resume
WHERE content IS NOT NULL AND resume_url NOT LIKE 'https://dhtg8wzvkbfxr.cloudfront.net/%';
SELECT COUNT(*) FROM member_portfolio
WHERE content IS NOT NULL AND portfolio_url NOT LIKE 'https://dhtg8wzvkbfxr.cloudfront.net/%';
```

**수정 방향:** 과거 도메인을 허용 목록으로 처리하거나, 무효화 대상에서 그 행을 제외한다.
전자가 낫다 — 제외하면 그 사용자는 영구히 옛 채점 기준에 남는다.

## 3. 게스트 남용 방어가 위조 가능한 값을 키로 쓴다

`ClientIpArgumentResolver`는 `X-Forwarded-For`를 쉼표로 잘라 **맨 왼쪽** 값을 쓴다.
그 항목은 클라이언트가 임의로 채워 보내는 값이다(프록시가 덧붙이는 것은 오른쪽).

게스트 방어 두 겹이 전부 이 값을 키로 쓴다 — 365일 1회 락
(`guest:resume-analysis:started:` + IP)과 시간당 5회 상한. 헤더만 매번 바꾸면 둘 다
우회되고, 우회 대상은 **LLM 2콜(평가 + 질문생성)짜리 유료 연산**이다.

브랜치 이전부터 있던 노출이다(기존 게스트 면접도 같은 `ClientIp`로 1회 락을 건다).
`feat/new`가 바꾼 것은 노출의 **비용**이다.

**수정 방향:** traefik이 앞에 있으므로 신뢰 경계에서 세어 오른쪽 홉을 쓰거나, traefik이
`X-Real-IP`를 세팅하게 하고 그것을 읽는다. 정확한 형태는 프록시 구성에 달려 있다.
기존 게스트 면접에도 함께 영향이 가므로 그쪽 회귀도 함께 볼 것.

## 4. 회원 시도 상한이 없어 무료 1회 회계를 고칠 수 없다

`existsChargeableByMemberId`는 무료 사용 소진 판정에서 `CAPACITY`·`STALE_SWEEP`·
`PERSISTENCE`만 제외한다. 그래서 첫 제출이 `EVALUATION_LLM`이나 `OUTPUT_TRUNCATED`로
죽으면 사용자는 아무것도 받지 못하고 과금도 되지 않았는데 무료 1회가 소진된다.

그 두 사유를 제외 목록에 넣는 것이 공정하지만 지금은 할 수 없다 — 두 사유가 **입력으로
유발 가능**하고(큰 이력서 → 잘림) 회원에게 시도 상한이 없어 **무한 무료 2콜 루프**가
열린다.

**수정 방향:** 게스트의 시간당 5회에 대응하는 회원 시도 상한을 먼저 넣고, 그 다음
두 사유를 제외 목록에 추가한다. 순서를 뒤집으면 안 된다.

## 5. (설계 정리) `resume ↔ interview` 계층

`InterviewStartFacadeService`가 내부 서비스 `ResumeAnalysisService.readById`를 직접
주입한다. 관례는 다른 도메인이 파사드에만 의존하는 것이다. 면접 시작이
`ResumeAnalysis` 엔티티의 상태와 소유자를 봐야 하는데 파사드는 DTO만 노출하기 때문에
이렇게 됐다.

파사드에 엔티티 반환 메서드를 추가하는 단순 정리는 `resume ↔ interview` 순환 의존을
만들 위험이 있다. 어느 방향으로 의존을 끊을지 설계 판단이 필요하다.

---

## 함께 알아둘 것 (수정 대상은 아님)

- **워커가 탈출하지 못하는 상태 1건(수용됨)**: `markTokenCharged`의 CAS와 `useTokens`
  사이에서 프로세스가 죽으면 `charged_token_count=5`인데 실제 차감이 없다. 매출 손실
  방향이고 과다 청구는 아니다.
- **무료 1회 재부여(의도적 수용)**: 구 질문생성 이력이 테이블째 삭제되어 판정 근거가
  하나로 줄었고, 구 플로우를 유료로 쓴 기존 회원에게 무료 1회가 다시 부여된다.
  구 이력이 이미 없으므로 되돌릴 수단이 없다.
- **전 프로젝트 문서 누락(기존)**: REST Docs 식별자 81개 중 66개가 참조되지 않는 생성
  파일을 갖고 있고, 3개는 어디서도 참조되지 않는다(`auth-logout-google`,
  `auth-withdraw-google`, `token-purchases-multiple-pages`). v1 proceed 예외와 TTS
  폴링은 문서화되지 않은 상태다 — 스니펫을 생성하는 테스트가 없다.
