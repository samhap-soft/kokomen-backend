package com.samhap.kokomen.interview.service.dto.systemdesign;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.samhap.kokomen.interview.domain.Interview;
import com.samhap.kokomen.interview.domain.InterviewState;
import com.samhap.kokomen.interview.domain.InterviewType;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignDifficulty;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignInterview;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignProblem;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignRequirementQuestion;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignStage;
import java.util.List;

/**
 * 새로고침해도 이어서 진행할 수 있는 면접 상태 전부. curQuestionId는 답이 아직 없는 현재 질문의 면접 안 순번이다.
 */
public record SystemDesignInterviewResponse(
        Long interviewId,
        InterviewType interviewType,
        InterviewState interviewState,
        SystemDesignDifficulty difficulty,
        SystemDesignTopicResponse topic,
        SystemDesignStage stage,
        List<SystemDesignRequirementQuestionResponse> requirementQuestions,
        int maxRequirementQuestionCount,
        // 전역 non_null 설정과 달리 null이어도 키를 내보낸다(프론트 계약: number | null, string | null).
        @JsonInclude(JsonInclude.Include.ALWAYS)
        Integer curQuestionId,
        @JsonInclude(JsonInclude.Include.ALWAYS)
        String curQuestion,
        List<SystemDesignQuestionAndAnswerResponse> prevQuestionsAndAnswers,
        int curQuestionCount,
        int maxQuestionCount,
        SystemDesignDiagramResponse diagram
) {

    /**
     * @param curQuestionId 현재 질문 순번. 요구사항 단계와 끝난 면접에서는 null이다
     * @param curQuestion   현재 질문 문장. curQuestionId가 null이면 null이다
     */
    public static SystemDesignInterviewResponse of(
            SystemDesignInterview systemDesignInterview,
            List<SystemDesignRequirementQuestionResponse> requirementQuestions,
            Integer curQuestionId,
            String curQuestion,
            List<SystemDesignQuestionAndAnswerResponse> prevQuestionsAndAnswers
    ) {
        Interview interview = systemDesignInterview.getInterview();
        SystemDesignProblem problem = systemDesignInterview.getProblem();
        return new SystemDesignInterviewResponse(
                interview.getId(),
                interview.getInterviewType(),
                interview.getInterviewState(),
                problem.getDifficulty(),
                SystemDesignTopicResponse.from(problem),
                systemDesignInterview.getStage(),
                requirementQuestions,
                SystemDesignRequirementQuestion.MAX_COUNT,
                curQuestionId,
                curQuestion,
                prevQuestionsAndAnswers,
                prevQuestionsAndAnswers.size(),
                SystemDesignInterview.MAX_QUESTION_COUNT,
                SystemDesignDiagramResponse.from(systemDesignInterview.getCurrentDiagram())
        );
    }
}
