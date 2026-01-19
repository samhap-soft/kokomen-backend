package com.samhap.kokomen.interview.domain;

import com.samhap.kokomen.global.domain.BaseEntity;
import com.samhap.kokomen.global.exception.BadRequestException;
import com.samhap.kokomen.member.domain.Member;
import jakarta.persistence.Column;
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
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "interview", indexes = {
        @Index(name = "idx_interview_like_count", columnList = "like_count"),
        @Index(name = "idx_interview_view_count", columnList = "view_count"),
        @Index(name = "idx_interview_member_id_root_question_id", columnList = "member_id, root_question_id")
})
public class Interview extends BaseEntity {

    public static final int MIN_ALLOWED_MAX_QUESTION_COUNT = 3;
    public static final int MAX_ALLOWED_MAX_QUESTION_COUNT = 20;

    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "root_question_id")
    private RootQuestion rootQuestion;

    @Column(name = "max_question_count", nullable = false)
    private Integer maxQuestionCount;

    @Column(name = "interview_state", nullable = false)
    @Enumerated(EnumType.STRING)
    private InterviewState interviewState;

    @Column(name = "interview_mode", nullable = false)
    @Enumerated(EnumType.STRING)
    private InterviewMode interviewMode;

    @Column(name = "interview_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private InterviewType interviewType;

    @Column(name = "total_feedback", length = 2_000)
    private String totalFeedback;

    @Column(name = "total_score")
    private Integer totalScore;

    @Column(name = "like_count", nullable = false)
    private Long likeCount;

    @Column(name = "view_count", nullable = false)
    private Long viewCount;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    public Interview(
            Long id,
            Member member,
            RootQuestion rootQuestion,
            Integer maxQuestionCount,
            InterviewState interviewState,
            InterviewMode interviewMode,
            InterviewType interviewType,
            String totalFeedback,
            Integer totalScore,
            Long likeCount,
            Long viewCount,
            LocalDateTime finishedAt
    ) {
        validateMaxQuestionCount(maxQuestionCount);
        this.id = id;
        this.member = member;
        this.rootQuestion = rootQuestion;
        this.maxQuestionCount = maxQuestionCount;
        this.interviewState = interviewState;
        this.interviewMode = interviewMode;
        this.interviewType = interviewType;
        this.totalFeedback = totalFeedback;
        this.totalScore = totalScore;
        this.likeCount = likeCount;
        this.viewCount = viewCount;
        this.finishedAt = finishedAt;
    }

    public Interview(Member member, RootQuestion rootQuestion, Integer maxQuestionCount, InterviewMode interviewMode) {
        this(null, member, rootQuestion, maxQuestionCount, InterviewState.IN_PROGRESS, interviewMode,
                InterviewType.CATEGORY_BASED, null, null, 0L, 0L, null);
    }

    private void validateMaxQuestionCount(Integer maxQuestionCount) {
        if (maxQuestionCount < MIN_ALLOWED_MAX_QUESTION_COUNT || maxQuestionCount > MAX_ALLOWED_MAX_QUESTION_COUNT) {
            throw new BadRequestException("최대 질문 개수는 " + MIN_ALLOWED_MAX_QUESTION_COUNT + " 이상 " + MAX_ALLOWED_MAX_QUESTION_COUNT + " 이하이어야 합니다.");
        }
    }

    public boolean isInterviewee(Long memberId) {
        return this.member.getId().equals(memberId);
    }

    public boolean isInProgress() {
        return this.interviewState == InterviewState.IN_PROGRESS;
    }

    public void evaluate(String totalFeedback, Integer totalScore) {
        if (isInProgress()) {
            this.interviewState = InterviewState.FINISHED;
            this.totalFeedback = totalFeedback;
            this.totalScore = totalScore;
            this.finishedAt = LocalDateTime.now();
            return;
        }
        throw new BadRequestException("이미 종료된 인터뷰입니다.");
    }
}
