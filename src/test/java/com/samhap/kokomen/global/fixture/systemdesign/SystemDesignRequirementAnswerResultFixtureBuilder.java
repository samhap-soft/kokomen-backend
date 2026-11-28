package com.samhap.kokomen.global.fixture.systemdesign;

import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignRequirementAnswerType;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignRequirementItem;
import com.samhap.kokomen.interview.external.dto.response.SystemDesignRequirementAnswerResult;
import java.util.List;

/**
 * 역질문 답 클라이언트가 돌려주는 검증을 마친 결과. 기본값은 URL 단축기의 하루 생성량을 물어 정해 둔 사실로 답한 결과다.
 */
public class SystemDesignRequirementAnswerResultFixtureBuilder {

    private List<String> askedRequirementCodes;
    private SystemDesignRequirementAnswerType answerType;
    private String answer;
    private List<SystemDesignRequirementItem> requirementItems;

    public static SystemDesignRequirementAnswerResultFixtureBuilder builder() {
        return new SystemDesignRequirementAnswerResultFixtureBuilder();
    }

    // 숨은 요구사항에 없는 요구사항을 면접관이 정해 답한 결과다. 물은 숨은 요구사항이 없다.
    public SystemDesignRequirementAnswerResultFixtureBuilder assumed(String answer) {
        this.askedRequirementCodes = List.of();
        this.answerType = SystemDesignRequirementAnswerType.ASSUMED;
        this.answer = answer;
        return this;
    }

    public SystemDesignRequirementAnswerResultFixtureBuilder askedRequirementCodes(
            List<String> askedRequirementCodes) {
        this.askedRequirementCodes = askedRequirementCodes;
        return this;
    }

    public SystemDesignRequirementAnswerResultFixtureBuilder answerType(SystemDesignRequirementAnswerType answerType) {
        this.answerType = answerType;
        return this;
    }

    public SystemDesignRequirementAnswerResultFixtureBuilder answer(String answer) {
        this.answer = answer;
        return this;
    }

    public SystemDesignRequirementAnswerResultFixtureBuilder requirementItems(
            List<SystemDesignRequirementItem> requirementItems) {
        this.requirementItems = requirementItems;
        return this;
    }

    public SystemDesignRequirementAnswerResult build() {
        return new SystemDesignRequirementAnswerResult(
                askedRequirementCodes != null ? askedRequirementCodes : List.of("DAILY_CREATION_VOLUME"),
                answerType != null ? answerType : SystemDesignRequirementAnswerType.SEED,
                answer != null ? answer : "하루 평균 10만 개 정도예요.",
                requirementItems != null ? requirementItems : List.of()
        );
    }
}
