package com.samhap.kokomen.interview.domain.systemdesign;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.samhap.kokomen.global.exception.ConflictException;
import com.samhap.kokomen.global.fixture.interview.InterviewFixtureBuilder;
import com.samhap.kokomen.global.fixture.systemdesign.SystemDesignDiagramFixture;
import com.samhap.kokomen.global.fixture.systemdesign.SystemDesignInterviewFixtureBuilder;
import com.samhap.kokomen.global.fixture.systemdesign.SystemDesignProblemFixtureBuilder;
import com.samhap.kokomen.interview.domain.Interview;
import com.samhap.kokomen.interview.domain.InterviewState;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class SystemDesignInterviewTest {

    @Test
    void 시작하면_요구사항_단계이고_문제의_시작_다이어그램으로_시작한다() {
        // given
        Interview interview = InterviewFixtureBuilder.systemDesign().build();
        SystemDesignDiagram starterDiagram = SystemDesignDiagramFixture.urlShortenerStarter();
        SystemDesignProblem problem = SystemDesignProblemFixtureBuilder.builder()
                .starterDiagram(starterDiagram)
                .build();
        SystemDesignProblem problemWithoutStarter = SystemDesignProblemFixtureBuilder.builder().build();

        // when
        SystemDesignInterview systemDesignInterview = SystemDesignInterview.start(interview, problem);
        SystemDesignInterview emptyStart = SystemDesignInterview.start(interview, problemWithoutStarter);

        // then
        assertAll(
                () -> assertThat(systemDesignInterview.getInterview()).isSameAs(interview),
                () -> assertThat(systemDesignInterview.getProblem()).isSameAs(problem),
                () -> assertThat(systemDesignInterview.getStage()).isEqualTo(SystemDesignStage.REQUIREMENTS),
                () -> assertThat(systemDesignInterview.getCurrentDiagram()).isEqualTo(starterDiagram),
                () -> assertThat(systemDesignInterview.getReportScore()).isNull(),
                () -> assertThat(emptyStart.getCurrentDiagram()).isEqualTo(SystemDesignDiagram.empty())
        );
    }

    @Test
    void 요구사항_단계가_아니면_역질문을_409로_거절한다() {
        // given
        SystemDesignInterview requirements = interviewAt(SystemDesignStage.REQUIREMENTS);
        SystemDesignInterview highLevel = interviewAt(SystemDesignStage.HIGH_LEVEL);
        SystemDesignInterview deepDive = interviewAt(SystemDesignStage.DEEP_DIVE);

        // when & then
        assertAll(
                () -> assertThatCode(requirements::validateRequirementStage).doesNotThrowAnyException(),
                () -> assertThatThrownBy(highLevel::validateRequirementStage)
                        .isInstanceOf(ConflictException.class)
                        .hasMessage("요구사항 단계가 끝났어요."),
                () -> assertThatThrownBy(deepDive::validateRequirementStage)
                        .isInstanceOf(ConflictException.class)
                        .hasMessage("요구사항 단계가 끝났어요.")
        );
    }

    @Test
    void 요구사항_완료_가능_검사는_설계_단계면_409이고_단계를_바꾸지_않는다() {
        // given
        SystemDesignInterview requirements = interviewAt(SystemDesignStage.REQUIREMENTS);
        SystemDesignInterview highLevel = interviewAt(SystemDesignStage.HIGH_LEVEL);
        SystemDesignInterview deepDive = interviewAt(SystemDesignStage.DEEP_DIVE);

        // when & then
        assertAll(
                () -> assertThatCode(requirements::validateRequirementsCompletable).doesNotThrowAnyException(),
                () -> assertThat(requirements.getStage()).isEqualTo(SystemDesignStage.REQUIREMENTS),
                () -> assertThatThrownBy(highLevel::validateRequirementsCompletable)
                        .isInstanceOf(ConflictException.class)
                        .hasMessage("이미 설계 단계예요."),
                () -> assertThatThrownBy(deepDive::validateRequirementsCompletable)
                        .isInstanceOf(ConflictException.class)
                        .hasMessage("이미 설계 단계예요.")
        );
    }

    @Test
    void 요구사항을_완료하면_개략_설계_단계가_된다() {
        // given
        SystemDesignInterview systemDesignInterview = interviewAt(SystemDesignStage.REQUIREMENTS);

        // when
        systemDesignInterview.completeRequirements();

        // then
        assertThat(systemDesignInterview.getStage()).isEqualTo(SystemDesignStage.HIGH_LEVEL);
    }

    @Test
    void 이미_설계_단계면_완료를_409로_거절한다() {
        // given
        SystemDesignInterview highLevel = interviewAt(SystemDesignStage.HIGH_LEVEL);
        SystemDesignInterview deepDive = interviewAt(SystemDesignStage.DEEP_DIVE);

        // when & then
        assertAll(
                () -> assertThatThrownBy(highLevel::completeRequirements)
                        .isInstanceOf(ConflictException.class)
                        .hasMessage("이미 설계 단계예요."),
                () -> assertThatThrownBy(deepDive::completeRequirements)
                        .isInstanceOf(ConflictException.class)
                        .hasMessage("이미 설계 단계예요."),
                () -> assertThat(highLevel.getStage()).isEqualTo(SystemDesignStage.HIGH_LEVEL),
                () -> assertThat(deepDive.getStage()).isEqualTo(SystemDesignStage.DEEP_DIVE)
        );
    }

    @Test
    void 답변_가능_검사는_끝남_요구사항_단계_질문_순번_순서로_409를_낸다() {
        // given
        SystemDesignInterview finished = finishedInterviewAt(SystemDesignStage.DEEP_DIVE);
        SystemDesignInterview finishedInRequirements = finishedInterviewAt(SystemDesignStage.REQUIREMENTS);
        SystemDesignInterview requirements = interviewAt(SystemDesignStage.REQUIREMENTS);
        SystemDesignInterview highLevel = interviewAt(SystemDesignStage.HIGH_LEVEL);
        SystemDesignInterview deepDive = interviewAt(SystemDesignStage.DEEP_DIVE);

        // when & then
        assertAll(
                () -> assertThatThrownBy(() -> finished.validateAnswerable(5, 6))
                        .isInstanceOf(ConflictException.class)
                        .hasMessage("이미 끝난 면접이에요."),
                () -> assertThatThrownBy(() -> finishedInRequirements.validateAnswerable(null, 1))
                        .isInstanceOf(ConflictException.class)
                        .hasMessage("이미 끝난 면접이에요."),
                () -> assertThatThrownBy(() -> requirements.validateAnswerable(2, 1))
                        .isInstanceOf(ConflictException.class)
                        .hasMessage("요구사항 정리를 먼저 끝내 주세요."),
                () -> assertThatThrownBy(() -> highLevel.validateAnswerable(2, 1))
                        .isInstanceOf(ConflictException.class)
                        .hasMessage("지금 질문이 아니에요."),
                () -> assertThatThrownBy(() -> highLevel.validateAnswerable(null, 1))
                        .isInstanceOf(ConflictException.class)
                        .hasMessage("지금 질문이 아니에요."),
                () -> assertThatCode(() -> highLevel.validateAnswerable(1, 1)).doesNotThrowAnyException(),
                () -> assertThatCode(() -> deepDive.validateAnswerable(6, 6)).doesNotThrowAnyException()
        );
    }

    @Test
    void 답변을_기록하면_현재_다이어그램과_단계가_바뀐다() {
        // given
        SystemDesignInterview systemDesignInterview = interviewAt(SystemDesignStage.HIGH_LEVEL);
        SystemDesignDiagram starterDiagram = SystemDesignDiagramFixture.urlShortenerStarter();
        SystemDesignDiagram diagramWithCache = SystemDesignDiagramFixture.withCache();

        // when
        systemDesignInterview.recordAnswer(starterDiagram, null);
        SystemDesignStage stageAfterFirstAnswer = systemDesignInterview.getStage();
        SystemDesignDiagram diagramAfterFirstAnswer = systemDesignInterview.getCurrentDiagram();
        systemDesignInterview.recordAnswer(diagramWithCache, SystemDesignStage.DEEP_DIVE);

        // then
        assertAll(
                () -> assertThat(stageAfterFirstAnswer).isEqualTo(SystemDesignStage.HIGH_LEVEL),
                () -> assertThat(diagramAfterFirstAnswer).isEqualTo(starterDiagram),
                () -> assertThat(systemDesignInterview.getStage()).isEqualTo(SystemDesignStage.DEEP_DIVE),
                () -> assertThat(systemDesignInterview.getCurrentDiagram()).isEqualTo(diagramWithCache)
        );
    }

    @Test
    void 리포트_점수가_0에서_100_밖이면_거절한다() {
        // given
        SystemDesignInterview lowest = interviewAt(SystemDesignStage.DEEP_DIVE);
        SystemDesignInterview highest = interviewAt(SystemDesignStage.DEEP_DIVE);
        SystemDesignInterview outOfRange = interviewAt(SystemDesignStage.DEEP_DIVE);

        // when
        lowest.finish(0);
        highest.finish(100);

        // then
        assertAll(
                () -> assertThat(lowest.getReportScore()).isZero(),
                () -> assertThat(highest.getReportScore()).isEqualTo(100),
                () -> assertThatThrownBy(() -> outOfRange.finish(-1)).isInstanceOf(IllegalArgumentException.class),
                () -> assertThatThrownBy(() -> outOfRange.finish(101)).isInstanceOf(IllegalArgumentException.class),
                () -> assertThat(outOfRange.getReportScore()).isNull(),
                () -> assertThatThrownBy(() -> SystemDesignInterviewFixtureBuilder.builder().reportScore(101).build())
                        .isInstanceOf(IllegalArgumentException.class)
        );
    }

    private static SystemDesignInterview interviewAt(SystemDesignStage stage) {
        return SystemDesignInterviewFixtureBuilder.builder()
                .stage(stage)
                .build();
    }

    private static SystemDesignInterview finishedInterviewAt(SystemDesignStage stage) {
        Interview finishedInterview = InterviewFixtureBuilder.systemDesign()
                .interviewState(InterviewState.FINISHED)
                .totalScore(60)
                .finishedAt(LocalDateTime.now())
                .build();
        return SystemDesignInterviewFixtureBuilder.builder()
                .interview(finishedInterview)
                .stage(stage)
                .reportScore(80)
                .build();
    }
}
