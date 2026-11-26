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
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 요구사항 역질문과 답. askedRequirementCodes는 LLM이 YES로 판정하고 서버가 검증한 숨은 요구사항 code 목록이다(문제 순서).
 * 면접당 {@link #MAX_COUNT}개까지이고 서비스가 진행 락 안에서 센다. questionOrder는 면접 안 순번(1~{@value #MAX_COUNT})이고,
 * (면접, 순번) 유니크와 순번 CHECK가 락 없이도 개수 한도를 DB에서 지킨다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "system_design_requirement_question", uniqueConstraints = {
        @UniqueConstraint(name = "uk_system_design_requirement_question_order",
                columnNames = {"system_design_interview_id", "question_order"})
})
public class SystemDesignRequirementQuestion extends BaseEntity {

    public static final int MAX_COUNT = 5;
    public static final int MAX_CONTENT_LENGTH = 200;
    public static final int MAX_ANSWER_LENGTH = 1_000;
    // 응답을 받지 못해 다시 보낸 역질문으로 볼 시간. 역질문 응답은 길면 30초 가까이 걸리고 프론트는 그 뒤에 다시 보낸다.
    public static final Duration RETRY_WINDOW = Duration.ofSeconds(60);

    private static final int FIRST_QUESTION_ORDER = 1;

    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "system_design_interview_id", nullable = false)
    private SystemDesignInterview systemDesignInterview;

    @Column(name = "question_order", nullable = false)
    private Integer questionOrder;

    @Column(name = "content", nullable = false, length = MAX_CONTENT_LENGTH)
    private String content;

    @Column(name = "answer", nullable = false, length = MAX_ANSWER_LENGTH)
    private String answer;

    @Convert(converter = StringListJsonConverter.class)
    @Column(name = "asked_requirement_codes", nullable = false, columnDefinition = "JSON")
    private List<String> askedRequirementCodes;

    public SystemDesignRequirementQuestion(SystemDesignInterview systemDesignInterview, int questionOrder,
                                           String content, String answer, List<String> askedRequirementCodes) {
        validateQuestionOrder(questionOrder);
        validateAnswer(answer);
        this.systemDesignInterview = systemDesignInterview;
        this.questionOrder = questionOrder;
        this.content = normalizeContent(content);
        this.answer = answer;
        this.askedRequirementCodes = List.copyOf(askedRequirementCodes);
    }

    private static void validateQuestionOrder(int questionOrder) {
        if (questionOrder < FIRST_QUESTION_ORDER || questionOrder > MAX_COUNT) {
            throw new IllegalArgumentException(
                    "역질문 순번은 %d~%d이어야 합니다: %d".formatted(FIRST_QUESTION_ORDER, MAX_COUNT, questionOrder));
        }
    }

    // 답은 면접자가 설계 근거로 삼는 사실 문장이라 잘라 저장하지 않고, 컬럼 한도를 넘으면 거절한다.
    private static void validateAnswer(String answer) {
        if (answer != null && answer.length() > MAX_ANSWER_LENGTH) {
            throw new IllegalArgumentException(
                    "역질문 답은 %d자 이하여야 합니다: %d자".formatted(MAX_ANSWER_LENGTH, answer.length()));
        }
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

    /**
     * 이 역질문(직전에 저장한 역질문)과 다듬은 내용이 같은 질문이 저장한 지 {@link #RETRY_WINDOW} 안(경계 포함)에 다시 왔는지 본다.
     * 그렇다면 응답을 받지 못해 다시 보낸 요청으로 보고 서비스가 새로 답하지 않는다. 저장 전이라 생성 시각이 없으면 재시도가 아니다.
     *
     * @param content     {@link #normalizeContent(String)}로 다듬은 질문
     * @param requestedAt 다시 온 요청을 처리하는 시각
     */
    public boolean isRetriedBy(String content, LocalDateTime requestedAt) {
        LocalDateTime savedAt = getCreatedAt();
        return savedAt != null && this.content.equals(content) && !requestedAt.isAfter(savedAt.plus(RETRY_WINDOW));
    }

    public static void validateCount(long currentCount) {
        if (currentCount >= MAX_COUNT) {
            throw new BadRequestException(REQUIREMENT_QUESTION_LIMIT.getMessage());
        }
    }

    /**
     * 면접자가 확인한 숨은 요구사항: 모든 역질문에서 물은 요구사항 code의 합집합(처음 물은 순서, 중복 없음). 마지막 답변 채점의 확인 여부와
     * 리포트의 놓친 요구사항(이 합집합에 없는 요구사항)이 이 한 정의를 함께 쓴다.
     */
    public static List<String> collectAskedRequirementCodes(
            List<SystemDesignRequirementQuestion> requirementQuestions) {
        return requirementQuestions.stream()
                .flatMap(requirementQuestion -> requirementQuestion.getAskedRequirementCodes().stream())
                .distinct()
                .toList();
    }
}
