package com.samhap.kokomen.interview.domain.systemdesign;

import static com.samhap.kokomen.interview.domain.systemdesign.SystemDesignErrorMessage.REQUIREMENT_QUESTION_LENGTH;
import static com.samhap.kokomen.interview.domain.systemdesign.SystemDesignErrorMessage.REQUIREMENT_QUESTION_LIMIT;

import com.samhap.kokomen.global.domain.BaseEntity;
import com.samhap.kokomen.global.exception.BadRequestException;
import com.samhap.kokomen.global.persistence.StringListJsonConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 요구사항 역질문과 답. askedRequirementCodes는 LLM이 YES로 판정하고 서버가 검증한 숨은 요구사항 code 목록이다(문제 순서).
 * 면접당 {@link #MAX_COUNT}개까지이고 서비스가 진행 락 안에서 센다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "system_design_requirement_question", indexes = {
        @Index(name = "idx_system_design_requirement_question_interview_id", columnList = "system_design_interview_id")
})
public class SystemDesignRequirementQuestion extends BaseEntity {

    public static final int MAX_COUNT = 5;
    public static final int MAX_CONTENT_LENGTH = 200;

    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "system_design_interview_id", nullable = false)
    private SystemDesignInterview systemDesignInterview;

    @Column(name = "content", nullable = false, length = MAX_CONTENT_LENGTH)
    private String content;

    @Column(name = "answer", nullable = false, length = 1_000)
    private String answer;

    @Column(name = "asked_requirement_codes", nullable = false, columnDefinition = "JSON")
    @Convert(converter = StringListJsonConverter.class)
    private List<String> askedRequirementCodes;

    public SystemDesignRequirementQuestion(SystemDesignInterview systemDesignInterview, String content, String answer,
                                           List<String> askedRequirementCodes) {
        this.systemDesignInterview = systemDesignInterview;
        this.content = normalizeContent(content);
        this.answer = answer;
        this.askedRequirementCodes = List.copyOf(askedRequirementCodes);
    }

    /**
     * {@link SystemDesignText#normalize(String)}로 다듬은 뒤 1~{@value #MAX_CONTENT_LENGTH}자인지 본다. 다듬은 값을 저장·응답한다.
     */
    public static String normalizeContent(String raw) {
        String content = SystemDesignText.normalize(raw);
        if (content.isEmpty() || content.length() > MAX_CONTENT_LENGTH) {
            throw new BadRequestException(REQUIREMENT_QUESTION_LENGTH.getMessage());
        }
        return content;
    }

    public static void validateCount(long currentCount) {
        if (currentCount >= MAX_COUNT) {
            throw new BadRequestException(REQUIREMENT_QUESTION_LIMIT.getMessage());
        }
    }
}
