package com.samhap.kokomen.interview.tool;

import com.samhap.kokomen.interview.domain.InterviewType;
import java.util.List;

/**
 * 면접 유형(도메인)별 프롬프트 구성 요소를 한곳에 모은 프로파일.
 * interviewType → 도메인 fragment 세트 + 도메인 프레이밍 문구 매핑을 이 enum이 단일하게 소유하며,
 * {@link InterviewSystemMessageBuilder}가 GPT/Bedrock 공용으로 이 프로파일을 사용해 시스템 메시지를 조립한다.
 * rank_mapping/feedback_tone_by_rank 는 도메인과 무관하게 공유되므로 여기 두지 않고 빌더가 직접 참조한다.
 */
enum InterviewPromptProfile {

    GENERAL(
            InterviewPromptFragments.PERSONA,
            InterviewPromptFragments.SECURITY_RULES,
            InterviewPromptFragments.LENGTH_NEUTRAL,
            InterviewPromptFragments.RUBRIC,
            InterviewPromptFragments.FOLLOW_UP_QUESTION_ALGORITHM,
            InterviewPromptFragments.SINGLE_QUESTION_CONSTRAINT,
            "면접 대화 흐름",
            "assistant 메시지는 면접관의 질문, user 메시지는 면접자의 답변이다.",
            "답변",
            InterviewPromptFragments.EVALUATION_CRITERIA,
            ""),

    CODING(
            CodingInterviewPromptFragments.PERSONA,
            CodingInterviewPromptFragments.SECURITY_RULES,
            CodingInterviewPromptFragments.LENGTH_NEUTRAL,
            CodingInterviewPromptFragments.RUBRIC,
            CodingInterviewPromptFragments.FOLLOW_UP_QUESTION_ALGORITHM,
            CodingInterviewPromptFragments.SINGLE_QUESTION_CONSTRAINT,
            "라이브 코딩 테스트의 대화 흐름",
            "assistant의 첫 메시지는 코딩 문제이고, 이후 assistant 메시지는 면접관의 꼬리 질문이며, "
                    + "user 메시지는 면접자가 제출한 코드 또는 그 설명이다.",
            "코드",
            CodingInterviewPromptFragments.EVALUATION_CRITERIA,
            " 코드를 포함할 경우 반드시 마크다운 코드 블록(```)으로 감싼다."),

    PERSONALITY(
            PersonalityInterviewPromptFragments.PERSONA,
            PersonalityInterviewPromptFragments.SECURITY_RULES,
            PersonalityInterviewPromptFragments.LENGTH_NEUTRAL,
            PersonalityInterviewPromptFragments.RUBRIC,
            PersonalityInterviewPromptFragments.FOLLOW_UP_QUESTION_ALGORITHM,
            PersonalityInterviewPromptFragments.SINGLE_QUESTION_CONSTRAINT,
            "인성 면접의 대화 흐름",
            "assistant 메시지는 면접관의 인성 질문, user 메시지는 면접자가 본인의 경험·태도·생각을 서술한 답변이다.",
            "답변",
            PersonalityInterviewPromptFragments.EVALUATION_CRITERIA,
            "");

    private final String persona;
    private final String securityRules;
    private final String lengthNeutral;
    private final String rubric;
    private final String followUpAlgorithm;
    private final String singleQuestionConstraint;
    private final String flowNoun;
    private final String conversationContext;
    private final String answerNoun;
    private final List<String> evaluationCriteria;
    private final String feedbackFormatNote;

    InterviewPromptProfile(String persona, String securityRules, String lengthNeutral, String rubric,
                           String followUpAlgorithm, String singleQuestionConstraint, String flowNoun,
                           String conversationContext, String answerNoun, List<String> evaluationCriteria,
                           String feedbackFormatNote) {
        this.persona = persona;
        this.securityRules = securityRules;
        this.lengthNeutral = lengthNeutral;
        this.rubric = rubric;
        this.followUpAlgorithm = followUpAlgorithm;
        this.singleQuestionConstraint = singleQuestionConstraint;
        this.flowNoun = flowNoun;
        this.conversationContext = conversationContext;
        this.answerNoun = answerNoun;
        this.evaluationCriteria = evaluationCriteria;
        this.feedbackFormatNote = feedbackFormatNote;
    }

    static InterviewPromptProfile from(InterviewType interviewType) {
        return switch (interviewType) {
            case LIVE_CODING -> CODING;
            case PERSONALITY -> PERSONALITY;
            case CATEGORY_BASED, RESUME_BASED -> GENERAL;
        };
    }

    /** 도메인별 "시니어 면접관 수준" 기준. 코딩/인성은 CS 어휘가 어색하므로 각자 전용 조각을 쓴다. */
    String seniorStandard() {
        return switch (this) {
            case GENERAL -> InterviewPromptFragments.SENIOR_STANDARD;
            case CODING -> CodingInterviewPromptFragments.SENIOR_STANDARD;
            case PERSONALITY -> PersonalityInterviewPromptFragments.SENIOR_STANDARD;
        };
    }

    /** 도메인별 피드백 말투 규칙. 코딩은 코드블록 허용, 인성은 STAR 언어 등 도메인에 맞춘다. */
    String feedbackTone() {
        return switch (this) {
            case GENERAL -> InterviewPromptFragments.FEEDBACK_TONE_BY_RANK;
            case CODING -> CodingInterviewPromptFragments.FEEDBACK_TONE_BY_RANK;
            case PERSONALITY -> PersonalityInterviewPromptFragments.FEEDBACK_TONE_BY_RANK;
        };
    }

    /** 도메인별 추가 채점 지침(현재 코딩만: 코드/설명 턴 분기 + dry-run). 없으면 빈 문자열 → 빌더가 생략. */
    String scoringGuidance() {
        return switch (this) {
            case CODING -> CodingInterviewPromptFragments.CODE_SCORING_GUIDANCE;
            case GENERAL, PERSONALITY -> "";
        };
    }

    /** 도메인별 채점 캘리브레이션 앵커(저득점·경계·red flag 예시). */
    String rubricExamples() {
        return switch (this) {
            case GENERAL -> InterviewPromptFragments.RUBRIC_EXAMPLES;
            case CODING -> CodingInterviewPromptFragments.RUBRIC_EXAMPLES;
            case PERSONALITY -> PersonalityInterviewPromptFragments.RUBRIC_EXAMPLES;
        };
    }

    String persona() {
        return persona;
    }

    String securityRules() {
        return securityRules;
    }

    String lengthNeutral() {
        return lengthNeutral;
    }

    String rubric() {
        return rubric;
    }

    String followUpAlgorithm() {
        return followUpAlgorithm;
    }

    String singleQuestionConstraint() {
        return singleQuestionConstraint;
    }

    String flowNoun() {
        return flowNoun;
    }

    String conversationContext() {
        return conversationContext;
    }

    String answerNoun() {
        return answerNoun;
    }

    String evaluationCriteriaSummary() {
        return String.join(", ", evaluationCriteria);
    }

    String feedbackFormatNote() {
        return feedbackFormatNote;
    }
}
