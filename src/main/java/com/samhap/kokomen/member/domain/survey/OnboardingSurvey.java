package com.samhap.kokomen.member.domain.survey;

import com.samhap.kokomen.category.domain.Category;
import com.samhap.kokomen.global.domain.BaseEntity;
import com.samhap.kokomen.global.exception.BadRequestException;
import com.samhap.kokomen.member.domain.Member;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.LastModifiedDate;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "onboarding_survey")
public class OnboardingSurvey extends BaseEntity {

    private static final int GOAL_DESCRIPTION_MAX_LENGTH = 1000;

    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Enumerated(EnumType.STRING)
    @Column(name = "career_goal", nullable = false, length = 30)
    private CareerGoal careerGoal;

    @Convert(converter = PrepStageListJsonConverter.class)
    @Column(name = "prep_stages", nullable = false, columnDefinition = "JSON")
    private List<PrepStage> prepStages;

    @Convert(converter = CategoryListJsonConverter.class)
    @Column(name = "tech_topics", nullable = false, columnDefinition = "JSON")
    private List<Category> techTopics;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_company_type", nullable = false, length = 30)
    private TargetCompanyType targetCompanyType;

    @Enumerated(EnumType.STRING)
    @Column(name = "interview_experience", nullable = false, length = 30)
    private InterviewExperience interviewExperience;

    @Convert(converter = WeakPointListJsonConverter.class)
    @Column(name = "weak_points", nullable = false, columnDefinition = "JSON")
    private List<WeakPoint> weakPoints;

    @Column(name = "goal_description", length = GOAL_DESCRIPTION_MAX_LENGTH)
    private String goalDescription;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public OnboardingSurvey(Member member, CareerGoal careerGoal, List<PrepStage> prepStages,
                           List<Category> techTopics, TargetCompanyType targetCompanyType,
                           InterviewExperience interviewExperience, List<WeakPoint> weakPoints,
                           String goalDescription) {
        this.member = member;
        applyAnswers(careerGoal, prepStages, techTopics, targetCompanyType, interviewExperience, weakPoints,
                goalDescription);
    }

    public void update(CareerGoal careerGoal, List<PrepStage> prepStages, List<Category> techTopics,
                       TargetCompanyType targetCompanyType, InterviewExperience interviewExperience,
                       List<WeakPoint> weakPoints, String goalDescription) {
        applyAnswers(careerGoal, prepStages, techTopics, targetCompanyType, interviewExperience, weakPoints,
                goalDescription);
    }

    private void applyAnswers(CareerGoal careerGoal, List<PrepStage> prepStages, List<Category> techTopics,
                              TargetCompanyType targetCompanyType, InterviewExperience interviewExperience,
                              List<WeakPoint> weakPoints, String goalDescription) {
        validateRequired(careerGoal, "career_goal은 null일 수 없습니다.");
        validateRequired(targetCompanyType, "target_company_type은 null일 수 없습니다.");
        validateRequired(interviewExperience, "interview_experience는 null일 수 없습니다.");
        validateChoices(prepStages, "prep_stages");
        validateChoices(techTopics, "tech_topics");
        validateChoices(weakPoints, "weak_points");
        validateStackCategories(techTopics);
        validateGoalDescriptionLength(goalDescription);

        this.careerGoal = careerGoal;
        this.prepStages = List.copyOf(prepStages);
        this.techTopics = List.copyOf(techTopics);
        this.targetCompanyType = targetCompanyType;
        this.interviewExperience = interviewExperience;
        this.weakPoints = List.copyOf(weakPoints);
        this.goalDescription = goalDescription;
    }

    private void validateRequired(Object value, String message) {
        if (value == null) {
            throw new BadRequestException(message);
        }
    }

    private void validateChoices(List<? extends Enum<?>> choices, String fieldName) {
        // API 경로는 DTO의 @NotEmpty가 먼저 막지만, 엔티티도 같은 규칙을 스스로 지켜야 한다.
        // 빈 목록은 NOT NULL JSON 컬럼을 통과해 []로 저장되므로 여기서 막지 않으면 조용히 새어 들어간다.
        if (choices == null || choices.isEmpty()) {
            throw new BadRequestException("%s는 최소 1개를 선택해야 합니다.".formatted(fieldName));
        }
        // List.of()로 만든 불변 리스트는 contains(null)에서 NPE를 던지므로 stream으로 검사한다.
        if (choices.stream().anyMatch(Objects::isNull)) {
            throw new BadRequestException("%s에는 null이 포함될 수 없습니다.".formatted(fieldName));
        }
        if (new HashSet<>(choices).size() != choices.size()) {
            throw new BadRequestException("%s에 중복된 값이 있습니다.".formatted(fieldName));
        }
    }

    private void validateStackCategories(List<Category> techTopics) {
        if (!techTopics.stream().allMatch(Category::isStack)) {
            throw new BadRequestException("tech_topics에는 기술 카테고리만 선택할 수 있습니다.");
        }
    }

    private void validateGoalDescriptionLength(String goalDescription) {
        if (goalDescription != null && goalDescription.length() > GOAL_DESCRIPTION_MAX_LENGTH) {
            throw new BadRequestException(
                    "goal_description은 최대 %d자까지 입력할 수 있습니다.".formatted(GOAL_DESCRIPTION_MAX_LENGTH));
        }
    }
}
