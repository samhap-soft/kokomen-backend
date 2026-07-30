package com.samhap.kokomen.resume.service.dto;

/**
 * 질문 콜 전용 커맨드. 평가 콜 결과를 렌더한 evaluationResult를 user 메시지에만 주입하기 위해
 * 평가 커맨드와 분리한다(system 메시지를 요청별로 바꾸면 캐시 프리픽스가 갈린다).
 */
public record ResumeAnalysisQuestionCallCommand(
        Long analysisId,
        String resumeText,
        String portfolioText,
        String jobPosition,
        String jobCareer,
        String evaluationResult
) {

    public static ResumeAnalysisQuestionCallCommand of(ResumeAnalysisCommand command, String evaluationResult) {
        return new ResumeAnalysisQuestionCallCommand(command.analysisId(), command.resumeText(),
                command.portfolioText(), command.jobPosition(), command.jobCareer(), evaluationResult);
    }
}
