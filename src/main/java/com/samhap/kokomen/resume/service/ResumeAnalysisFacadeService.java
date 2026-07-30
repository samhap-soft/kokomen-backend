package com.samhap.kokomen.resume.service;

import java.time.Duration;

/**
 * §0-6이 확정한 Redis 키·과금 상수의 정본 위치.
 *
 * <p>이 태스크(Task 11)는 상수 블록만 만든다. Task 13가 <b>같은 파일에</b> {@code @Service}·필드·
 * 명시 생성자·제출/재시도/조회 메서드를 채우며, 그때도 아래 상수 블록은 그대로 유지하고 재선언하지 않는다.
 *
 * <p>{@code ResumeAnalysisStateService}는 이 상수를 참조만 한다(같은 패키지이므로 import가 없다).
 * 상수를 두 클래스에 이중 선언하면 프로덕션이 {@code started:}로 걸고 다른 경로가 다른 키로 해제하는
 * 결함을 테스트가 초록으로 통과시킨다(§8-10). 테스트도 리터럴을 쓰지 않고 이 상수를 참조한다.
 */
public class ResumeAnalysisFacadeService {

    public static final String GUEST_RESUME_ANALYSIS_LOCK_KEY_PREFIX = "guest:resume-analysis:started:";
    public static final Duration GUEST_RESUME_ANALYSIS_LOCK_TTL = Duration.ofDays(365);
    public static final String GUEST_RESUME_ANALYSIS_ATTEMPT_KEY_PREFIX = "guest:resume-analysis:attempt:";
    public static final int GUEST_MAX_ATTEMPTS_PER_HOUR = 5;
    public static final int RESUME_ANALYSIS_TOKEN_COST = 5;
}
