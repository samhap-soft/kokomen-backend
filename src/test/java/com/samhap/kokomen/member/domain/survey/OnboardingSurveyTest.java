package com.samhap.kokomen.member.domain.survey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.samhap.kokomen.category.domain.Category;
import com.samhap.kokomen.global.exception.BadRequestException;
import com.samhap.kokomen.global.fixture.member.MemberFixtureBuilder;
import com.samhap.kokomen.global.fixture.member.OnboardingSurveyFixtureBuilder;
import com.samhap.kokomen.member.domain.Member;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class OnboardingSurveyTest {

    private final Member member = MemberFixtureBuilder.builder().build();

    @Test
    void 기술_카테고리만_관심_분야로_선택하면_생성에_성공한다() {
        assertThatCode(() -> OnboardingSurveyFixtureBuilder.builder()
                .member(member)
                .techTopics(Category.findStackCategories())
                .build())
                .doesNotThrowAnyException();
    }

    @Test
    void 인성_면접을_관심_분야로_선택하면_생성에_실패한다() {
        assertThatThrownBy(() -> OnboardingSurveyFixtureBuilder.builder()
                .member(member)
                .techTopics(List.of(Category.JAVA_SPRING, Category.PERSONALITY))
                .build())
                .isInstanceOf(BadRequestException.class)
                .hasMessage("tech_topics에는 기술 카테고리만 선택할 수 있습니다.");
    }

    @Test
    void prep_stages에_중복된_값이_있으면_생성에_실패한다() {
        assertThatThrownBy(() -> OnboardingSurveyFixtureBuilder.builder()
                .member(member)
                .prepStages(List.of(PrepStage.JOB_SEEKING, PrepStage.JOB_SEEKING))
                .build())
                .isInstanceOf(BadRequestException.class)
                .hasMessage("prep_stages에 중복된 값이 있습니다.");
    }

    @Test
    void tech_topics에_중복된_값이_있으면_생성에_실패한다() {
        assertThatThrownBy(() -> OnboardingSurveyFixtureBuilder.builder()
                .member(member)
                .techTopics(List.of(Category.JAVA_SPRING, Category.JAVA_SPRING))
                .build())
                .isInstanceOf(BadRequestException.class)
                .hasMessage("tech_topics에 중복된 값이 있습니다.");
    }

    @Test
    void weak_points에_중복된_값이_있으면_생성에_실패한다() {
        assertThatThrownBy(() -> OnboardingSurveyFixtureBuilder.builder()
                .member(member)
                .weakPoints(List.of(WeakPoint.CS, WeakPoint.CS))
                .build())
                .isInstanceOf(BadRequestException.class)
                .hasMessage("weak_points에 중복된 값이 있습니다.");
    }

    @Test
    void 복수_선택_항목에_null이_포함되면_생성에_실패한다() {
        assertThatThrownBy(() -> OnboardingSurveyFixtureBuilder.builder()
                .member(member)
                .prepStages(Arrays.asList(PrepStage.JOB_SEEKING, null))
                .build())
                .isInstanceOf(BadRequestException.class)
                .hasMessage("prep_stages에는 null이 포함될 수 없습니다.");
    }

    @Test
    void goal_description이_1000자를_초과하면_생성에_실패한다() {
        assertThatThrownBy(() -> OnboardingSurveyFixtureBuilder.builder()
                .member(member)
                .goalDescription("가".repeat(1001))
                .build())
                .isInstanceOf(BadRequestException.class)
                .hasMessage("goal_description은 최대 1000자까지 입력할 수 있습니다.");
    }

    @Test
    void goal_description이_1000자면_생성에_성공한다() {
        assertThatCode(() -> OnboardingSurveyFixtureBuilder.builder()
                .member(member)
                .goalDescription("가".repeat(1000))
                .build())
                .doesNotThrowAnyException();
    }

    @Test
    void goal_description은_없어도_생성에_성공한다() {
        OnboardingSurvey onboardingSurvey = OnboardingSurveyFixtureBuilder.builder()
                .member(member)
                .goalDescription(null)
                .build();

        assertThat(onboardingSurvey.getGoalDescription()).isNull();
    }

    @Test
    void 수정하면_모든_응답이_교체된다() {
        OnboardingSurvey onboardingSurvey = OnboardingSurveyFixtureBuilder.builder()
                .member(member)
                .careerGoal(CareerGoal.BACKEND)
                .prepStages(List.of(PrepStage.BEGINNER))
                .techTopics(List.of(Category.JAVA_SPRING))
                .targetCompanyType(TargetCompanyType.BIG_TECH)
                .interviewExperience(InterviewExperience.NONE)
                .weakPoints(List.of(WeakPoint.CS))
                .goalDescription("이전 목표")
                .build();

        onboardingSurvey.update(
                CareerGoal.FRONTEND,
                List.of(PrepStage.SWITCHING, PrepStage.GRADUATING),
                List.of(Category.REACT, Category.FRONTEND),
                TargetCompanyType.STARTUP,
                InterviewExperience.FOUR_PLUS,
                List.of(WeakPoint.MENTAL),
                null
        );

        assertThat(onboardingSurvey.getCareerGoal()).isEqualTo(CareerGoal.FRONTEND);
        assertThat(onboardingSurvey.getPrepStages()).containsExactly(PrepStage.SWITCHING, PrepStage.GRADUATING);
        assertThat(onboardingSurvey.getTechTopics()).containsExactly(Category.REACT, Category.FRONTEND);
        assertThat(onboardingSurvey.getTargetCompanyType()).isEqualTo(TargetCompanyType.STARTUP);
        assertThat(onboardingSurvey.getInterviewExperience()).isEqualTo(InterviewExperience.FOUR_PLUS);
        assertThat(onboardingSurvey.getWeakPoints()).containsExactly(WeakPoint.MENTAL);
        assertThat(onboardingSurvey.getGoalDescription()).isNull();
    }

    @Test
    void 수정할_때도_관심_분야_검증을_수행한다() {
        OnboardingSurvey onboardingSurvey = OnboardingSurveyFixtureBuilder.builder()
                .member(member)
                .build();

        assertThatThrownBy(() -> onboardingSurvey.update(
                CareerGoal.BACKEND,
                List.of(PrepStage.JOB_SEEKING),
                List.of(Category.PERSONALITY),
                TargetCompanyType.ANY,
                InterviewExperience.NONE,
                List.of(WeakPoint.CS),
                null
        ))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("tech_topics에는 기술 카테고리만 선택할 수 있습니다.");
    }

    @Test
    void 검증에_실패하면_기존_응답이_변경되지_않는다() {
        OnboardingSurvey onboardingSurvey = OnboardingSurveyFixtureBuilder.builder()
                .member(member)
                .careerGoal(CareerGoal.BACKEND)
                .build();

        assertThatThrownBy(() -> onboardingSurvey.update(
                CareerGoal.MOBILE,
                List.of(PrepStage.JOB_SEEKING),
                List.of(Category.PERSONALITY),
                TargetCompanyType.ANY,
                InterviewExperience.NONE,
                List.of(WeakPoint.CS),
                null
        ))
                .isInstanceOf(BadRequestException.class);

        assertThat(onboardingSurvey.getCareerGoal()).isEqualTo(CareerGoal.BACKEND);
    }
}
