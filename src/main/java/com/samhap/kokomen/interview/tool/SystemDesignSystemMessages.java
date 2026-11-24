package com.samhap.kokomen.interview.tool;

import com.samhap.kokomen.interview.tool.SystemDesignProblemContext.EvaluationPoint;
import com.samhap.kokomen.interview.tool.SystemDesignProblemContext.Requirement;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 시스템 설계 면접 LLM 호출의 system 프롬프트. system 블록에는 문제마다 정해진 값(문제 문맥, 난이도 코멘트, 숨은 요구사항(역질문 답만),
 * 평가 포인트(마지막 답변 평가만))만 넣는다. 같은 호출 종류 × 같은 문제면 글자 그대로 같아 캐시 프리픽스를 함께 쓴다.
 * 면접마다 다른 값은 user 메시지에만 넣는다. 블록과 조각은 앞뒤 줄바꿈을 지우고 빈 줄 하나로 잇고, 끝에 줄바꿈을 붙이지 않는다.
 */
public final class SystemDesignSystemMessages {

    private static final String BLOCK_SEPARATOR = "\n\n";
    private static final String LINE_SEPARATOR = "\n";
    private static final String NONE = "(없음)";

    private static final String REQUIREMENT_ANSWER_TASK = """
            <task>
            지금은 면접자가 설계를 시작하기 전에 요구사항을 확인하는 단계다. 면접자의 역질문(user 메시지의 <current_requirement_question>)이 <hidden_requirements>의 각 항목을 묻는지 YES/NO로 판정하고, YES로 판정한 항목에 정해 둔 사실만으로 면접관의 답을 만들어라.
            </task>
            """;

    private static final String REQUIREMENT_ANSWER_OUTPUT = """
            <output>
            제공된 도구를 호출해 reasoning, requirement_로 시작하는 필드 전부, answer를 함께 제출하라.
            - reasoning : 역질문이 무엇을 묻는지와 항목별 판정 근거를 200자 이내로 쓴다. 사용자에게 노출되지 않는다.
            - requirement_* : <hidden_requirements>의 항목마다 하나씩 있다. answer_rules의 판정 기준으로 역질문이 그 항목을 물으면 YES, 아니면 NO다. 모든 항목을 빠짐없이 판정한다.
            - answer : 면접관의 답. answer_rules를 따른다.
            </output>
            """;

    private static final String ANSWER_TASK = """
            <task>
            아래는 시스템 설계 면접 기록이다. 면접자는 요구사항을 확인한 뒤 면접관의 질문에 답하며 다이어그램을 함께 제출한다.
            가장 최근 질문에 대한 면접자의 답변과 그때 제출한 다이어그램(user 메시지의 <current_turn>)만 평가해 rank와 feedback을 정하고, <next_base_question>을 바탕으로 다음 질문 한 개를 만들어라.
            </task>
            """;

    private static final String ANSWER_OUTPUT = """
            <output>
            제공된 도구를 호출해 reasoning, rank, feedback, next_question 네 필드를 함께 제출하라.
            - reasoning : answer_analysis(rubric 항목별 점수와 근거, 다이어그램 대조 결과)와 question_planning(기준 질문을 어떻게 구체화할지)을 합쳐 700자 이내로 쓴다. rank를 먼저 확정한 뒤 다음 질문을 설계한다. 사용자에게 노출되지 않는다.
            - rank : rubric 합계를 rank_mapping으로 바꾼 A/B/C/D/F 중 한 글자.
            - feedback : 가장 최근 답변에 대한 3-4문장 피드백. feedback_tone_by_rank를 따른다.
            - next_question : next_question_rules를 지킨 다음 질문.
            </output>
            """;

    private static final String END_TASK = """
            <task>
            아래는 시스템 설계 면접 기록 전체이다. 면접자는 요구사항을 확인한 뒤 면접관의 질문에 답하며 다이어그램을 함께 제출했다.
            가장 최근 답변(user 메시지의 <current_turn>)의 rank와 feedback을 정하고, 면접 전체를 근거로 평가 포인트마다 충족 여부를 판정한 뒤 종합 평가(strengths, improvements, learning_direction)를 작성하라.
            </task>
            """;

    private static final String END_OUTPUT = """
            <output>
            제공된 도구를 호출해 아래 필드를 모두 제출하라.
            - reasoning : last_answer_analysis(가장 최근 답변의 rubric 항목별 점수와 근거)와 종합 평가 정리를 합쳐 400자 이내로 쓴다. 평가 포인트별 근거는 reasoning이 아니라 point_*_evidence에 쓴다. 사용자에게 노출되지 않는다.
            - rank : 가장 최근 답변에 대한 rank. A/B/C/D/F 중 한 글자. 면접 전체가 아니라 가장 최근 답변만 기준으로 한다.
            - feedback : 가장 최근 답변에 대한 3-4문장 피드백. feedback_tone_by_rank를 따른다.
            - point_*_evidence : 평가 포인트마다 판정보다 먼저 쓰는 근거. evaluation_point_rules의 형식으로 300자 이내로 쓴다. 사용자에게 노출되지 않는다.
            - point_* : 평가 포인트마다 바로 앞의 증거와 evaluation_point_rules에 따라 MET 또는 NOT_MET.
            - strengths : 이번 면접에서 드러난 설계 강점 1-2문장.
            - improvements : 보완할 설계 영역 1-2문장. 확인하지 않은 요구사항이 설계에 영향을 줬다면 짚는다.
            - learning_direction : 이번 면접의 약점에 근거한 구체적인 다음 학습 주제 1-2문장(예: "요청이 몰릴 때 큐로 부하를 흡수하는 방식과 바로 처리하는 방식을 비교해 보는 연습"). "더 공부하라" 같은 일반론은 쓰지 않는다.
            strengths, improvements, learning_direction은 서버가 한 단락으로 이어 붙이므로 각 항목을 독립된 한두 문장의 존댓말로 쓰고, 인사·점수·rank는 언급하지 않는다.
            </output>
            """;

    private SystemDesignSystemMessages() {
    }

    /**
     * 역질문 답(L1). 숨은 요구사항의 정해 둔 사실을 담으므로 이 system 블록은 역질문 답 호출에만 쓴다.
     */
    public static String requirementAnswer(SystemDesignProblemContext problem) {
        return joinBlocks(
                tagged("role", SystemDesignPromptFragments.PERSONA),
                REQUIREMENT_ANSWER_TASK,
                SystemDesignPromptFragments.SECURITY_RULES,
                topic(problem),
                tagged("hidden_requirements", hiddenRequirementLines(problem.requirements())),
                SystemDesignPromptFragments.REQUIREMENT_ANSWER_RULES,
                REQUIREMENT_ANSWER_OUTPUT);
    }

    private static String hiddenRequirementLines(List<Requirement> requirements) {
        if (requirements.isEmpty()) {
            return NONE;
        }
        return requirements.stream()
                .map(requirement -> "- " + SystemDesignToolSchemas.requirementFieldName(requirement.code())
                        + " | 이름: " + requirement.name() + " | 정해 둔 사실: " + requirement.answer())
                .collect(Collectors.joining(LINE_SEPARATOR));
    }

    /**
     * 답변 평가(L2). 숨은 요구사항은 넣지 않는다. 채점은 면접자가 확인한 요구사항(user 메시지의 역질문 기록)만 기준으로 해야
     * 루브릭의 "가정을 밝히면 감점하지 않는다"와 맞는다.
     */
    public static String answer(SystemDesignProblemContext problem) {
        return joinBlocks(
                tagged("role", SystemDesignPromptFragments.PERSONA),
                ANSWER_TASK,
                SystemDesignPromptFragments.SECURITY_RULES,
                SystemDesignPromptFragments.GROUNDING_RULE,
                SystemDesignPromptFragments.DIAGRAM_READING_GUIDE,
                difficultyStandard(problem),
                SystemDesignPromptFragments.RUBRIC,
                InterviewPromptFragments.CALIBRATION_NOTE,
                SystemDesignPromptFragments.RUBRIC_EXAMPLES,
                InterviewPromptFragments.RANK_MAPPING,
                InterviewPromptFragments.INDEPENDENCE_PRINCIPLE,
                SystemDesignPromptFragments.FEEDBACK_TONE_BY_RANK,
                SystemDesignPromptFragments.NEXT_QUESTION_RULES,
                topic(problem),
                ANSWER_OUTPUT);
    }

    /**
     * 마지막 답변 평가와 리포트(L3). 답변 평가(L2)와 같은 조각에서 task·output이 바뀌고, 다음 질문 규칙 대신 평가 포인트 규칙과
     * 평가 포인트 목록이 들어간다. 숨은 요구사항은 넣지 않는다.
     */
    public static String end(SystemDesignProblemContext problem) {
        return joinBlocks(
                tagged("role", SystemDesignPromptFragments.PERSONA),
                END_TASK,
                SystemDesignPromptFragments.SECURITY_RULES,
                SystemDesignPromptFragments.GROUNDING_RULE,
                SystemDesignPromptFragments.DIAGRAM_READING_GUIDE,
                difficultyStandard(problem),
                SystemDesignPromptFragments.RUBRIC,
                InterviewPromptFragments.CALIBRATION_NOTE,
                SystemDesignPromptFragments.RUBRIC_EXAMPLES,
                InterviewPromptFragments.RANK_MAPPING,
                InterviewPromptFragments.INDEPENDENCE_PRINCIPLE,
                SystemDesignPromptFragments.FEEDBACK_TONE_BY_RANK,
                SystemDesignPromptFragments.EVALUATION_POINT_RULES,
                topic(problem),
                tagged("evaluation_points", evaluationPointLines(problem.evaluationPoints())),
                END_OUTPUT);
    }

    private static String evaluationPointLines(List<EvaluationPoint> points) {
        if (points.isEmpty()) {
            return NONE;
        }
        return points.stream()
                .map(point -> "- " + SystemDesignToolSchemas.pointFieldName(point.code()) + " | " + point.description())
                .collect(Collectors.joining(LINE_SEPARATOR));
    }

    private static String difficultyStandard(SystemDesignProblemContext problem) {
        return SystemDesignPromptFragments.DIFFICULTY_STANDARD_TEMPLATE.replace(
                SystemDesignPromptFragments.DIFFICULTY_COMMENT_PLACEHOLDER, problem.difficultyComment());
    }

    private static String topic(SystemDesignProblemContext problem) {
        return tagged("topic", "제목: " + problem.title() + LINE_SEPARATOR + "설명: " + problem.description());
    }

    static String tagged(String tag, String content) {
        return "<" + tag + ">" + LINE_SEPARATOR + content + LINE_SEPARATOR + "</" + tag + ">";
    }

    static String joinBlocks(String... blocks) {
        return Arrays.stream(blocks)
                .map(SystemDesignSystemMessages::stripNewlines)
                .collect(Collectors.joining(BLOCK_SEPARATOR));
    }

    private static String stripNewlines(String block) {
        int start = 0;
        int end = block.length();
        while (start < end && block.charAt(start) == '\n') {
            start++;
        }
        while (end > start && block.charAt(end - 1) == '\n') {
            end--;
        }
        return block.substring(start, end);
    }
}
