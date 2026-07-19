package com.samhap.kokomen.interview.tool;

import static org.assertj.core.api.Assertions.assertThat;

import com.samhap.kokomen.interview.domain.InterviewType;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 통합 시스템 메시지 빌더가 provider/단계/도메인별로 담아야 할 정본 fragment 구성을 고정한다.
 * GPT↔Bedrock 정합성(같은 흐름은 같은 fragment 집합)과 2콜 구조의 핵심 불변식(진행 단계의 feedbackInline
 * 차이)을 강제하는 drift 가드다. Stage 4~5에서 fragment 내용을 바꿔도 fragment 상수 참조로 검증하므로
 * 이 테스트는 구성(어떤 조각이 들어가는지)만 검증하고 문구 자체는 검증하지 않는다.
 */
class InterviewPromptAssemblyCharacterizationTest {

    // ---------- 일반(CS) 면접 ----------

    @Test
    void CS_GPT_진행_시스템_메시지는_공용_조각을_모두_포함하며_피드백_톤을_포함한다() {
        String prompt = InterviewSystemMessageBuilder.proceed(InterviewType.CATEGORY_BASED, true);

        assertThat(prompt).contains(
                InterviewPromptFragments.PERSONA,
                InterviewPromptFragments.SECURITY_RULES,
                InterviewPromptFragments.LENGTH_NEUTRAL,
                InterviewPromptFragments.RUBRIC,
                InterviewPromptFragments.RANK_MAPPING,
                InterviewPromptFragments.FOLLOW_UP_QUESTION_ALGORITHM,
                InterviewPromptFragments.SINGLE_QUESTION_CONSTRAINT,
                InterviewPromptFragments.FEEDBACK_TONE_BY_RANK);
    }

    @Test
    void CS_Bedrock_진행_시스템_메시지는_같은_공용_조각을_쓰되_피드백_톤은_제외한다() {
        String prompt = InterviewSystemMessageBuilder.proceed(InterviewType.CATEGORY_BASED, false);

        assertThat(prompt).contains(
                InterviewPromptFragments.PERSONA,
                InterviewPromptFragments.SECURITY_RULES,
                InterviewPromptFragments.LENGTH_NEUTRAL,
                InterviewPromptFragments.RUBRIC,
                InterviewPromptFragments.RANK_MAPPING,
                InterviewPromptFragments.FOLLOW_UP_QUESTION_ALGORITHM,
                InterviewPromptFragments.SINGLE_QUESTION_CONSTRAINT);
        // 2콜 구조: 진행 단계에는 피드백을 생성하지 않으므로 톤 조각이 없다.
        assertThat(prompt).doesNotContain(InterviewPromptFragments.FEEDBACK_TONE_BY_RANK);
    }

    @Test
    void CS_종료_시스템_메시지는_GPT와_Bedrock이_동일한_fragment_집합을_사용한다() {
        List<String> endFragments = List.of(
                InterviewPromptFragments.PERSONA,
                InterviewPromptFragments.SECURITY_RULES,
                InterviewPromptFragments.LENGTH_NEUTRAL,
                InterviewPromptFragments.RUBRIC,
                InterviewPromptFragments.RANK_MAPPING,
                InterviewPromptFragments.FEEDBACK_TONE_BY_RANK);

        // 종료 단계는 provider가 동일한 단일 소스를 쓰므로 완전히 같은 문자열이어야 한다.
        String end = InterviewSystemMessageBuilder.end(InterviewType.CATEGORY_BASED);
        assertThat(end).contains(endFragments);
    }

    @Test
    void CS_Bedrock_답변피드백_시스템_메시지는_피드백_톤을_포함하고_루브릭_상세는_제외한다() {
        String prompt = InterviewSystemMessageBuilder.answerFeedback(InterviewType.CATEGORY_BASED);

        assertThat(prompt).contains(
                InterviewPromptFragments.PERSONA,
                InterviewPromptFragments.SECURITY_RULES,
                InterviewPromptFragments.LENGTH_NEUTRAL,
                InterviewPromptFragments.FEEDBACK_TONE_BY_RANK);
        // 답변피드백 단계는 채점이 아닌 피드백 생성이므로 RUBRIC 상세 배점은 넣지 않는다.
        assertThat(prompt).doesNotContain(InterviewPromptFragments.RUBRIC);
    }

    // ---------- 코딩 면접 ----------

    @Test
    void 코딩_진행_시스템_메시지는_코딩_전용_조각과_공용_랭크매핑을_사용한다() {
        String prompt = InterviewSystemMessageBuilder.proceed(InterviewType.LIVE_CODING, false);

        assertThat(prompt).contains(
                CodingInterviewPromptFragments.PERSONA,
                CodingInterviewPromptFragments.SECURITY_RULES,
                CodingInterviewPromptFragments.LENGTH_NEUTRAL,
                CodingInterviewPromptFragments.RUBRIC,
                CodingInterviewPromptFragments.FOLLOW_UP_QUESTION_ALGORITHM,
                CodingInterviewPromptFragments.SINGLE_QUESTION_CONSTRAINT,
                InterviewPromptFragments.RANK_MAPPING);
        assertThat(prompt).doesNotContain(InterviewPromptFragments.FEEDBACK_TONE_BY_RANK);
    }

    @Test
    void 코딩_종료_시스템_메시지는_코딩_루브릭과_공용_피드백_톤을_사용한다() {
        String prompt = InterviewSystemMessageBuilder.end(InterviewType.LIVE_CODING);

        assertThat(prompt).contains(
                CodingInterviewPromptFragments.PERSONA,
                CodingInterviewPromptFragments.RUBRIC,
                InterviewPromptFragments.RANK_MAPPING,
                InterviewPromptFragments.FEEDBACK_TONE_BY_RANK);
    }

    // ---------- 인성 면접 ----------

    @Test
    void 인성_진행_시스템_메시지는_인성_전용_조각과_공용_랭크매핑을_사용한다() {
        String prompt = InterviewSystemMessageBuilder.proceed(InterviewType.PERSONALITY, false);

        assertThat(prompt).contains(
                PersonalityInterviewPromptFragments.PERSONA,
                PersonalityInterviewPromptFragments.SECURITY_RULES,
                PersonalityInterviewPromptFragments.LENGTH_NEUTRAL,
                PersonalityInterviewPromptFragments.RUBRIC,
                PersonalityInterviewPromptFragments.FOLLOW_UP_QUESTION_ALGORITHM,
                PersonalityInterviewPromptFragments.SINGLE_QUESTION_CONSTRAINT,
                InterviewPromptFragments.RANK_MAPPING);
        assertThat(prompt).doesNotContain(InterviewPromptFragments.FEEDBACK_TONE_BY_RANK);
    }

    @Test
    void 인성_종료_시스템_메시지는_인성_루브릭과_공용_피드백_톤을_사용한다() {
        String prompt = InterviewSystemMessageBuilder.end(InterviewType.PERSONALITY);

        assertThat(prompt).contains(
                PersonalityInterviewPromptFragments.PERSONA,
                PersonalityInterviewPromptFragments.RUBRIC,
                InterviewPromptFragments.RANK_MAPPING,
                InterviewPromptFragments.FEEDBACK_TONE_BY_RANK);
    }

    // ---------- 공통 시니어 기준 ----------

    @Test
    void 모든_단계_시스템_메시지는_공용_시니어_기준_조각을_포함한다() {
        assertThat(InterviewSystemMessageBuilder.proceed(InterviewType.CATEGORY_BASED, true))
                .contains(InterviewPromptFragments.SENIOR_STANDARD);
        assertThat(InterviewSystemMessageBuilder.proceed(InterviewType.CATEGORY_BASED, false))
                .contains(InterviewPromptFragments.SENIOR_STANDARD);
        assertThat(InterviewSystemMessageBuilder.end(InterviewType.CATEGORY_BASED))
                .contains(InterviewPromptFragments.SENIOR_STANDARD);
        assertThat(InterviewSystemMessageBuilder.answerFeedback(InterviewType.CATEGORY_BASED))
                .contains(InterviewPromptFragments.SENIOR_STANDARD);
    }
}
