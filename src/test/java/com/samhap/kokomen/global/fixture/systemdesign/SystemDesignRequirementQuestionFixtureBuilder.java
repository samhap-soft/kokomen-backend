package com.samhap.kokomen.global.fixture.systemdesign;

import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignInterview;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignRequirementAnswerType;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignRequirementItem;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignRequirementQuestion;
import java.util.List;

/**
 * 역질문 행. 기본값은 하루 생성량을 물어 정해 둔 사실로 답한 행(SEED, 확정 항목 없음)이다. 답의 종류와 확정 항목을 저장하기 전에 만든
 * 행(둘 다 NULL)은 저장한 뒤 DB에서 두 컬럼을 NULL로 바꿔 만든다. 엔티티는 새 행에 NULL을 받지 않는다.
 */
public class SystemDesignRequirementQuestionFixtureBuilder {

    private SystemDesignInterview systemDesignInterview;
    private Integer questionOrder;
    private String content;
    private String answer;
    private List<String> askedRequirementCodes;
    private SystemDesignRequirementAnswerType answerType;
    private List<SystemDesignRequirementItem> requirementItems;

    public static SystemDesignRequirementQuestionFixtureBuilder builder() {
        return new SystemDesignRequirementQuestionFixtureBuilder();
    }

    public SystemDesignRequirementQuestionFixtureBuilder systemDesignInterview(
            SystemDesignInterview systemDesignInterview) {
        this.systemDesignInterview = systemDesignInterview;
        return this;
    }

    public SystemDesignRequirementQuestionFixtureBuilder questionOrder(Integer questionOrder) {
        this.questionOrder = questionOrder;
        return this;
    }

    public SystemDesignRequirementQuestionFixtureBuilder content(String content) {
        this.content = content;
        return this;
    }

    public SystemDesignRequirementQuestionFixtureBuilder answer(String answer) {
        this.answer = answer;
        return this;
    }

    public SystemDesignRequirementQuestionFixtureBuilder askedRequirementCodes(List<String> askedRequirementCodes) {
        this.askedRequirementCodes = askedRequirementCodes;
        return this;
    }

    public SystemDesignRequirementQuestionFixtureBuilder answerType(SystemDesignRequirementAnswerType answerType) {
        this.answerType = answerType;
        return this;
    }

    public SystemDesignRequirementQuestionFixtureBuilder requirementItems(
            List<SystemDesignRequirementItem> requirementItems) {
        this.requirementItems = requirementItems;
        return this;
    }

    public SystemDesignRequirementQuestion build() {
        return new SystemDesignRequirementQuestion(
                systemDesignInterview != null ? systemDesignInterview
                        : SystemDesignInterviewFixtureBuilder.builder().build(),
                questionOrder != null ? questionOrder : 1,
                content != null ? content : "하루에 단축 URL이 몇 개나 만들어지나요?",
                answer != null ? answer : "새로 만들어지는 단축 URL은 하루 평균 10만 개 정도예요.",
                askedRequirementCodes != null ? askedRequirementCodes : List.of("DAILY_CREATION_VOLUME"),
                answerType != null ? answerType : SystemDesignRequirementAnswerType.SEED,
                requirementItems != null ? requirementItems : List.of()
        );
    }
}
