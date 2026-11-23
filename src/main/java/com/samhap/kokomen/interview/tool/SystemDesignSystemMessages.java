package com.samhap.kokomen.interview.tool;

import com.samhap.kokomen.interview.tool.SystemDesignProblemContext.Requirement;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 시스템 설계 면접 LLM 호출의 system 프롬프트. system 블록에는 문제마다 정해진 값(문제 문맥, 숨은 요구사항)만 넣는다.
 * 같은 호출 종류 × 같은 문제면 글자 그대로 같아 캐시 프리픽스를 함께 쓴다. 면접마다 다른 값은 user 메시지에만 넣는다.
 * 블록과 조각은 앞뒤 줄바꿈을 지우고 빈 줄 하나로 잇고, 끝에 줄바꿈을 붙이지 않는다.
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

    private static String topic(SystemDesignProblemContext problem) {
        return tagged("topic", "제목: " + problem.title() + LINE_SEPARATOR + "설명: " + problem.description());
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
