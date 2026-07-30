package com.samhap.kokomen.resume.service.dto;

/**
 * 요청 스레드에서 확정한 사실(과금 대상 여부·JD 제공 여부·추출된 원문)을 워커로 넘기는 커맨드.
 * 워커는 이 값을 DB에서 다시 읽지 않으며 jdProvided를 문자열로 재계산하지도 않는다.
 * MultipartFile·byte[]는 담지 않는다(요청 종료 후 유효성·힙 점유 문제).
 * 정적 팩토리를 두지 않는다 — 파사드가 new로 직접 생성하고 무과금 사본은 파사드의 private withoutBilling이 만든다.
 */
public record ResumeAnalysisCommand(
        Long analysisId,
        Long billingMemberId,
        boolean jdProvided,
        String resumeText,
        String portfolioText,
        String jobPosition,
        String jobDescription,
        String jobCareer
) {

    public boolean isBillable() {
        return billingMemberId != null;
    }
}
