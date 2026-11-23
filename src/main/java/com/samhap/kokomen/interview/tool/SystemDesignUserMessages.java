package com.samhap.kokomen.interview.tool;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 시스템 설계 면접 LLM 호출의 user 메시지. 넣는 값은 출처(면접자 입력, LLM이 만들어 저장한 문장, 시드)와 관계없이 모두 이스케이프한다.
 * 면접자가 태그를 닫고 지시를 끼워 넣는 것과, 저장된 LLM 문장에 섞인 태그가 다음 프롬프트로 돌아오는 것을 함께 막는다.
 */
public final class SystemDesignUserMessages {

    private static final String LINE_SEPARATOR = "\n";
    private static final String NONE = "(없음)";

    private SystemDesignUserMessages() {
    }

    /**
     * 역질문 답(L1). 이전 역질문과 그때의 면접관 답(물은 순서) 뒤에 이번 역질문을 둔다.
     */
    public static String requirementAnswer(SystemDesignRequirementAnswerCommand command) {
        return SystemDesignSystemMessages.joinBlocks(
                SystemDesignSystemMessages.tagged("previous_requirement_questions",
                        previousTurns(command.previousTurns())),
                SystemDesignSystemMessages.tagged("current_requirement_question",
                        candidateQuestion(command.currentQuestion())));
    }

    private static String previousTurns(List<SystemDesignRequirementTurn> turns) {
        if (turns.isEmpty()) {
            return NONE;
        }
        return turns.stream()
                .map(turn -> SystemDesignSystemMessages.tagged("item", candidateQuestion(turn.question())
                        + LINE_SEPARATOR + "<interviewer_answer>" + escape(turn.answer()) + "</interviewer_answer>"))
                .collect(Collectors.joining(LINE_SEPARATOR));
    }

    private static String candidateQuestion(String question) {
        return "<candidate_question>" + escape(question) + "</candidate_question>";
    }

    /**
     * {@code &}를 먼저 바꿔야 이미 있던 {@code &lt;} 같은 글자가 원래 글자로 다시 읽히지 않는다. null은 빈 문자열이다.
     */
    public static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    /**
     * LLM 텍스트 출력의 세 엔티티를 저장 전에 원래 문자로 되돌린다. {@code &amp;}를 마지막에 바꿔 {@link #escape(String)}를 되돌린다.
     * null은 빈 문자열이다.
     */
    public static String unescape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&");
    }
}
