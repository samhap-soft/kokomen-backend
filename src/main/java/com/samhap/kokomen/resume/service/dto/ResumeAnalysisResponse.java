package com.samhap.kokomen.resume.service.dto;

import com.samhap.kokomen.interview.domain.GeneratedQuestion;
import com.samhap.kokomen.resume.domain.MemberPortfolio;
import com.samhap.kokomen.resume.domain.MemberResume;
import com.samhap.kokomen.resume.domain.ResumeAnalysis;
import com.samhap.kokomen.resume.domain.ResumeAnalysisState;
import java.time.LocalDateTime;
import java.util.List;

public record ResumeAnalysisResponse(
        Long analysisId,
        ResumeAnalysisState state,
        boolean jdProvided,
        boolean interviewAvailable,
        Boolean questionRetryable,
        ResumeInfo resume,
        PortfolioInfo portfolio,
        String jobPosition,
        String jobDescription,
        String jobCareer,
        ResumeAnalysisEvaluationResponse evaluation,
        List<ResumeAnalysisQuestionResponse> questions,
        LocalDateTime createdAt
) {

    public static ResumeAnalysisResponse of(ResumeAnalysis analysis, List<GeneratedQuestion> questions,
                                            boolean questionRetryable) {
        return new ResumeAnalysisResponse(
                analysis.getId(),
                analysis.getState(),
                analysis.isJdProvided(),
                !analysis.isGuest() && analysis.getState().isQuestionReady(),
                toQuestionRetryable(analysis, questionRetryable),
                toResumeInfo(analysis.getMemberResume()),
                toPortfolioInfo(analysis.getMemberPortfolio()),
                analysis.getJobPosition(),
                analysis.getJobDescription(),
                analysis.getJobCareer(),
                ResumeAnalysisEvaluationResponse.fromNullable(analysis),
                toQuestionResponses(analysis, questions),
                analysis.getCreatedAt());
    }

    private static Boolean toQuestionRetryable(ResumeAnalysis analysis, boolean questionRetryable) {
        if (analysis.getState() != ResumeAnalysisState.QUESTION_FAILED) {
            return null;
        }
        return questionRetryable;
    }

    private static ResumeInfo toResumeInfo(MemberResume memberResume) {
        if (memberResume == null) {
            return null;
        }
        return new ResumeInfo(memberResume.getId(), memberResume.getTitle());
    }

    private static PortfolioInfo toPortfolioInfo(MemberPortfolio memberPortfolio) {
        if (memberPortfolio == null) {
            return null;
        }
        return new PortfolioInfo(memberPortfolio.getId(), memberPortfolio.getTitle());
    }

    private static List<ResumeAnalysisQuestionResponse> toQuestionResponses(ResumeAnalysis analysis,
                                                                           List<GeneratedQuestion> questions) {
        if (!analysis.getState().isQuestionReady() || questions == null || questions.isEmpty()) {
            return null;
        }
        return questions.stream()
                .map(ResumeAnalysisQuestionResponse::from)
                .toList();
    }
}
