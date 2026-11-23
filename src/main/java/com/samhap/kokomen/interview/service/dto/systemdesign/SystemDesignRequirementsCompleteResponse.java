package com.samhap.kokomen.interview.service.dto.systemdesign;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignInterview;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignQuestion;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignStage;

/**
 * 요구사항 정리 완료 결과. curQuestionId는 첫 설계 질문의 면접 안 순번(1)이다.
 */
public record SystemDesignRequirementsCompleteResponse(
        SystemDesignStage stage,
        // 조회 응답과 같은 계약(number | null, string | null)이라 null이어도 키를 내보내게 둔다.
        @JsonInclude(JsonInclude.Include.ALWAYS)
        Integer curQuestionId,
        @JsonInclude(JsonInclude.Include.ALWAYS)
        String curQuestion
) {

    public static SystemDesignRequirementsCompleteResponse of(SystemDesignInterview systemDesignInterview,
                                                              SystemDesignQuestion firstQuestion) {
        return new SystemDesignRequirementsCompleteResponse(systemDesignInterview.getStage(),
                firstQuestion.getQuestionOrder(), firstQuestion.getContent());
    }
}
