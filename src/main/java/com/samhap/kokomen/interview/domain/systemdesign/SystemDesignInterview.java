package com.samhap.kokomen.interview.domain.systemdesign;

import static com.samhap.kokomen.interview.domain.systemdesign.SystemDesignErrorMessage.ALREADY_DESIGN_STAGE;
import static com.samhap.kokomen.interview.domain.systemdesign.SystemDesignErrorMessage.INTERVIEW_FINISHED;
import static com.samhap.kokomen.interview.domain.systemdesign.SystemDesignErrorMessage.NOT_CURRENT_QUESTION;
import static com.samhap.kokomen.interview.domain.systemdesign.SystemDesignErrorMessage.REQUIREMENTS_NOT_COMPLETED;
import static com.samhap.kokomen.interview.domain.systemdesign.SystemDesignErrorMessage.REQUIREMENT_STAGE_OVER;

import com.samhap.kokomen.global.domain.BaseEntity;
import com.samhap.kokomen.global.exception.ConflictException;
import com.samhap.kokomen.interview.domain.Interview;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 시스템 설계 면접의 단계와 다이어그램. interview 행과 1:1이고, interview 행이 소유자·면접 상태·종료 시각·등급 합 점수를,
 * 이 행이 단계·현재 다이어그램·리포트 점수를 갖는다. 단계·순번을 어긴 요청은 409다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "system_design_interview", uniqueConstraints = {
        @UniqueConstraint(name = "uk_system_design_interview_interview_id", columnNames = {"interview_id"})
}, indexes = {
        @Index(name = "idx_system_design_interview_problem_id", columnList = "problem_id")
})
public class SystemDesignInterview extends BaseEntity {

    public static final int MAX_QUESTION_COUNT = 6;
    public static final int HIGH_LEVEL_QUESTION_COUNT = 3;

    private static final int MIN_REPORT_SCORE = 0;
    private static final int MAX_REPORT_SCORE = 100;

    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "interview_id", nullable = false)
    private Interview interview;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "problem_id", nullable = false)
    private SystemDesignProblem problem;

    @Column(name = "stage", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private SystemDesignStage stage;

    @Convert(converter = SystemDesignDiagramConverter.class)
    @Column(name = "current_diagram", nullable = false, columnDefinition = "JSON")
    private SystemDesignDiagram currentDiagram;

    @Column(name = "report_score")
    private Integer reportScore;

    public SystemDesignInterview(Interview interview, SystemDesignProblem problem, SystemDesignStage stage,
                                 SystemDesignDiagram currentDiagram, Integer reportScore) {
        if (reportScore != null) {
            validateReportScore(reportScore);
        }
        this.interview = interview;
        this.problem = problem;
        this.stage = stage;
        this.currentDiagram = currentDiagram;
        this.reportScore = reportScore;
    }

    public static SystemDesignInterview start(Interview interview, SystemDesignProblem problem) {
        return new SystemDesignInterview(interview, problem, SystemDesignStage.REQUIREMENTS, problem.startingDiagram(),
                null);
    }

    public void validateRequirementStage() {
        if (stage != SystemDesignStage.REQUIREMENTS) {
            throw new ConflictException(REQUIREMENT_STAGE_OVER.getMessage());
        }
    }

    /**
     * 요구사항 정리를 끝낼 수 있는지만 본다. 단계를 바꾸지 않으므로 진행 락을 잡기 전의 사전 검사에도 쓴다.
     */
    public void validateRequirementsCompletable() {
        if (stage != SystemDesignStage.REQUIREMENTS) {
            throw new ConflictException(ALREADY_DESIGN_STAGE.getMessage());
        }
    }

    public void completeRequirements() {
        validateRequirementsCompletable();
        this.stage = SystemDesignStage.HIGH_LEVEL;
    }

    /**
     * 지금 답할 질문. 진행 중인 면접에서 답이 없는 마지막 질문이다. 요구사항 단계에는 질문이 없고, 끝난 면접에는 마지막 질문에
     * 답이 없더라도 없다.
     *
     * @param questions           이 면접의 설계 질문(순번 순서)
     * @param answeredQuestionIds 답이 저장된 질문의 PK
     */
    public Optional<SystemDesignQuestion> findCurrentQuestion(List<SystemDesignQuestion> questions,
                                                              Set<Long> answeredQuestionIds) {
        if (!interview.isInProgress() || questions.isEmpty()) {
            return Optional.empty();
        }
        SystemDesignQuestion lastQuestion = questions.get(questions.size() - 1);
        if (answeredQuestionIds.contains(lastQuestion.getId())) {
            return Optional.empty();
        }
        return Optional.of(lastQuestion);
    }

    /**
     * 끝난 면접 → 요구사항 단계 → 질문 순번 순서로 보고 처음 어긴 것을 409로 알린다.
     *
     * @param questionOrder 요청이 답하려는 질문 순번. 경로 값을 숫자로 바꾸지 못했으면 null이다
     * @param currentOrder  지금 답할 질문({@link #findCurrentQuestion})의 순번. 없으면 null이고, 어떤 순번이든 지금 질문이 아니다
     */
    public void validateAnswerable(Integer questionOrder, Integer currentOrder) {
        if (!interview.isInProgress()) {
            throw new ConflictException(INTERVIEW_FINISHED.getMessage());
        }
        if (stage == SystemDesignStage.REQUIREMENTS) {
            throw new ConflictException(REQUIREMENTS_NOT_COMPLETED.getMessage());
        }
        if (questionOrder == null || !questionOrder.equals(currentOrder)) {
            throw new ConflictException(NOT_CURRENT_QUESTION.getMessage());
        }
    }

    /**
     * 답변과 함께 낸 다이어그램을 현재 다이어그램으로 둔다. nextStage가 null이면 단계를 그대로 둔다(마지막 답변).
     */
    public void recordAnswer(SystemDesignDiagram diagram, SystemDesignStage nextStage) {
        this.currentDiagram = diagram;
        if (nextStage != null) {
            this.stage = nextStage;
        }
    }

    /**
     * 리포트 점수(0~100)를 저장한다. 면접 종료는 서비스가 같은 트랜잭션에서 {@code interview.evaluate(...)}로 한다.
     */
    public void finish(int reportScore) {
        validateReportScore(reportScore);
        this.reportScore = reportScore;
    }

    private static void validateReportScore(int reportScore) {
        if (reportScore < MIN_REPORT_SCORE || reportScore > MAX_REPORT_SCORE) {
            throw new IllegalArgumentException(
                    "리포트 점수는 %d~%d이어야 합니다: %d".formatted(MIN_REPORT_SCORE, MAX_REPORT_SCORE, reportScore));
        }
    }
}
