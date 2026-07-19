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
        assertThat(prompt).doesNotContain(CodingInterviewPromptFragments.FEEDBACK_TONE_BY_RANK);
    }

    @Test
    void 코딩_종료_시스템_메시지는_코딩_전용_루브릭과_코딩_전용_피드백_톤을_사용한다() {
        String prompt = InterviewSystemMessageBuilder.end(InterviewType.LIVE_CODING);

        assertThat(prompt).contains(
                CodingInterviewPromptFragments.PERSONA,
                CodingInterviewPromptFragments.RUBRIC,
                InterviewPromptFragments.RANK_MAPPING,
                CodingInterviewPromptFragments.FEEDBACK_TONE_BY_RANK);
        // 도메인 톤 분리: CS용 공용 톤이 새어 들어오지 않는다.
        assertThat(prompt).doesNotContain(InterviewPromptFragments.FEEDBACK_TONE_BY_RANK);
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
        assertThat(prompt).doesNotContain(PersonalityInterviewPromptFragments.FEEDBACK_TONE_BY_RANK);
    }

    @Test
    void 인성_종료_시스템_메시지는_인성_전용_루브릭과_인성_전용_피드백_톤을_사용한다() {
        String prompt = InterviewSystemMessageBuilder.end(InterviewType.PERSONALITY);

        assertThat(prompt).contains(
                PersonalityInterviewPromptFragments.PERSONA,
                PersonalityInterviewPromptFragments.RUBRIC,
                InterviewPromptFragments.RANK_MAPPING,
                PersonalityInterviewPromptFragments.FEEDBACK_TONE_BY_RANK);
        // 도메인 톤 분리: CS용 공용 톤이 새어 들어오지 않는다.
        assertThat(prompt).doesNotContain(InterviewPromptFragments.FEEDBACK_TONE_BY_RANK);
    }

    // ---------- 도메인별 시니어 기준 분리 ----------

    @Test
    void CS_단계별_시스템_메시지는_공용_시니어_기준을_포함한다() {
        assertThat(InterviewSystemMessageBuilder.proceed(InterviewType.CATEGORY_BASED, true))
                .contains(InterviewPromptFragments.SENIOR_STANDARD);
        assertThat(InterviewSystemMessageBuilder.end(InterviewType.CATEGORY_BASED))
                .contains(InterviewPromptFragments.SENIOR_STANDARD);
        assertThat(InterviewSystemMessageBuilder.answerFeedback(InterviewType.CATEGORY_BASED))
                .contains(InterviewPromptFragments.SENIOR_STANDARD);
    }

    @Test
    void 코딩_시스템_메시지는_코딩_전용_시니어_기준을_쓰고_CS용은_쓰지_않는다() {
        String proceed = InterviewSystemMessageBuilder.proceed(InterviewType.LIVE_CODING, false);
        assertThat(proceed).contains(CodingInterviewPromptFragments.SENIOR_STANDARD);
        assertThat(proceed).doesNotContain(InterviewPromptFragments.SENIOR_STANDARD);
    }

    @Test
    void 인성_시스템_메시지는_인성_전용_시니어_기준을_쓰고_CS용은_쓰지_않는다() {
        String proceed = InterviewSystemMessageBuilder.proceed(InterviewType.PERSONALITY, false);
        assertThat(proceed).contains(PersonalityInterviewPromptFragments.SENIOR_STANDARD);
        assertThat(proceed).doesNotContain(InterviewPromptFragments.SENIOR_STANDARD);
    }

    // ---------- 공용 원칙 조각 (적응형/위생/grounding/독립채점) ----------

    @Test
    void 진행_단계는_적응형_난이도와_질문위생_가드를_포함한다() {
        String proceed = InterviewSystemMessageBuilder.proceed(InterviewType.CATEGORY_BASED, true);
        assertThat(proceed).contains(
                InterviewPromptFragments.ADAPTIVE_FOLLOWUP_PRINCIPLE,
                InterviewPromptFragments.QUESTION_HYGIENE_GUARD,
                InterviewPromptFragments.GROUNDING_RULE,
                InterviewPromptFragments.INDEPENDENCE_PRINCIPLE);
    }

    @Test
    void 종료_단계는_grounding과_독립채점을_포함하되_질문용_가드는_제외한다() {
        String end = InterviewSystemMessageBuilder.end(InterviewType.CATEGORY_BASED);
        assertThat(end).contains(
                InterviewPromptFragments.GROUNDING_RULE,
                InterviewPromptFragments.INDEPENDENCE_PRINCIPLE);
        // 종료 단계엔 꼬리 질문이 없으므로 질문용 원칙은 넣지 않는다.
        assertThat(end).doesNotContain(
                InterviewPromptFragments.ADAPTIVE_FOLLOWUP_PRINCIPLE,
                InterviewPromptFragments.QUESTION_HYGIENE_GUARD);
    }

    @Test
    void 답변피드백_단계는_grounding을_포함하되_질문용_원칙은_제외한다() {
        String answerFeedback = InterviewSystemMessageBuilder.answerFeedback(InterviewType.CATEGORY_BASED);
        assertThat(answerFeedback).contains(InterviewPromptFragments.GROUNDING_RULE);
        assertThat(answerFeedback).doesNotContain(
                InterviewPromptFragments.ADAPTIVE_FOLLOWUP_PRINCIPLE,
                InterviewPromptFragments.QUESTION_HYGIENE_GUARD);
    }

    // ---------- 채점 캘리브레이션 앵커 (Phase 3) ----------

    @Test
    void 채점_단계는_캘리브레이션_주의와_도메인_예시를_포함한다() {
        String proceed = InterviewSystemMessageBuilder.proceed(InterviewType.LIVE_CODING, false);
        assertThat(proceed).contains(
                InterviewPromptFragments.CALIBRATION_NOTE,
                CodingInterviewPromptFragments.RUBRIC_EXAMPLES);

        String end = InterviewSystemMessageBuilder.end(InterviewType.PERSONALITY);
        assertThat(end).contains(
                InterviewPromptFragments.CALIBRATION_NOTE,
                PersonalityInterviewPromptFragments.RUBRIC_EXAMPLES);
    }

    // ---------- 코딩 특화 채점 (Phase 4) ----------

    @Test
    void 코딩_채점_단계는_턴모드_분기와_dry_run_지침을_포함한다() {
        assertThat(InterviewSystemMessageBuilder.proceed(InterviewType.LIVE_CODING, false))
                .contains(CodingInterviewPromptFragments.CODE_SCORING_GUIDANCE);
        assertThat(InterviewSystemMessageBuilder.end(InterviewType.LIVE_CODING))
                .contains(CodingInterviewPromptFragments.CODE_SCORING_GUIDANCE);
    }

    @Test
    void CS_인성_채점_단계는_코딩_전용_채점_지침을_포함하지_않는다() {
        assertThat(InterviewSystemMessageBuilder.proceed(InterviewType.CATEGORY_BASED, false))
                .doesNotContain(CodingInterviewPromptFragments.CODE_SCORING_GUIDANCE);
        assertThat(InterviewSystemMessageBuilder.proceed(InterviewType.PERSONALITY, false))
                .doesNotContain(CodingInterviewPromptFragments.CODE_SCORING_GUIDANCE);
    }
}
