-- ============================================================================
-- 저장된 이력서·포트폴리오의 추출 텍스트 캐시 무효화.
--
-- member_resume.content / member_portfolio.content 는 원본 PDF에서 뽑은 텍스트의 캐시다.
-- ResumeContentService 의 getOrExtractResumeContent / getOrExtractPortfolioContent 는
-- hasContent() 가 true면 재추출하지 않고 이 값을 그대로 돌려준다.
--
-- 문제: 이 캐시를 채운 과거 코드는 링크 annotation을 추출하지 않는 추출기를 썼다.
-- PDFTextStripper 는 "GitHub" 같은 글자에 URL이 annotation으로만 걸린 경우 그 URL을 잃는다.
-- 지금은 두 경로 모두 extractTextWithLinks 로 본문 뒤에 <links> 블록을 덧붙이지만,
-- 캐시가 이미 채워진 행은 단축 경로를 타서 링크 없는 옛 텍스트가 계속 쓰인다.
-- 그 결과 같은 이력서가 "파일로 새로 업로드"와 "저장된 자료 재사용"에서 서로 다른 LLM 입력이 되고,
-- 링크 교차 검증이 관찰항목인 technical_skills 점수가 제출 방식에 따라 갈린다.
--
-- 해소: 캐시를 비워 다음 사용 시 현재 추출기로 다시 뽑게 한다. 원본 PDF와 URL은 건드리지 않으므로
-- 데이터 손실이 아니라 캐시 무효화다.
--
-- 안전성 근거: 이 저장소에는 S3 객체를 삭제하는 코드가 없다(deleteObject 호출 0건).
-- ResumeAnalysisCleanupScheduler 도 DB 행만 지우고 member_resume/member_portfolio 와 S3 는
-- 건드리지 않는다. 따라서 재추출에 필요한 원본이 남아 있다.
-- 원본이 어떤 이유로든 없는 행은 다음 사용 시 BadRequestException("...텍스트를 추출하는 데
-- 실패했습니다.")로 그 사용자에게만 드러난다 -- 그 행은 캐시가 한 번이라도 비었으면 이미 같은
-- 상태였으므로 이 마이그레이션이 새로 만드는 고장이 아니다.
--
-- 비용: 자료당 S3 다운로드 + 추출이 다음 사용 시 한 번 더 일어난다(일회성). 이후 다시 캐시된다.
--
-- 멱등: WHERE content IS NOT NULL 이므로 재실행하면 0행이 갱신된다.
-- 순수 DML 이며 DDL 을 섞지 않는다.
-- ============================================================================

UPDATE member_resume
SET content = NULL
WHERE content IS NOT NULL;

UPDATE member_portfolio
SET content = NULL
WHERE content IS NOT NULL;
