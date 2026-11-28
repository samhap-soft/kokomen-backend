package com.samhap.kokomen.interview.tool;

import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignDiagram;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignStage;
import com.samhap.kokomen.interview.tool.SystemDesignProblemContext.Requirement;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 시스템 설계 면접 LLM 호출의 user 메시지. 넣는 값은 출처(면접자 입력, LLM이 만들어 저장한 문장, 시드)와 관계없이 모두 이스케이프한다.
 * 면접자가 태그를 닫고 지시를 끼워 넣는 것과, 저장된 LLM 문장에 섞인 태그가 다음 프롬프트로 돌아오는 것을 함께 막는다.
 */
public final class SystemDesignUserMessages {

    private static final String LINE_SEPARATOR = "\n";
    private static final String NONE = "(없음)";
    private static final String NO_REQUIREMENT_QUESTIONS = "면접자는 요구사항을 확인하지 않고 설계를 시작했다.";
    private static final String CONFIRMED = "확인함";
    private static final String NOT_CONFIRMED = "확인하지 않음";
    private static final String ASKED_HIDDEN_REQUIREMENTS_HEADING = "이미 답한 숨은 요구사항:";
    private static final String CONFIRMED_ITEMS_HEADING = "지금까지 확정한 요구사항(물은 순서):";

    private SystemDesignUserMessages() {
    }

    /**
     * 역질문 답(L1). 이전 역질문과 그때의 면접관 답(물은 순서), 지금까지 확정한 요구사항, 이번 역질문 순서다.
     */
    public static String requirementAnswer(SystemDesignRequirementAnswerCommand command) {
        return SystemDesignSystemMessages.joinBlocks(
                SystemDesignSystemMessages.tagged("previous_requirement_questions",
                        command.previousTurns().isEmpty() ? NONE : requirementItems(command.previousTurns())),
                SystemDesignSystemMessages.tagged("confirmed_requirements",
                        confirmedRequirements(command.problem().requirements(), command.confirmedRequirements())),
                SystemDesignSystemMessages.tagged("current_requirement_question",
                        candidateQuestion(command.currentQuestion())));
    }

    /**
     * 이미 답한 숨은 요구사항(정해 둔 사실, 처음 물은 순서)과 확정 항목(물은 순서)을 따로 적는다. 숨은 요구사항 줄은 system 블록의
     * hidden_requirements와 같은 모양이라 모델이 판정 필드와 짝짓는다. 이 문제에 없는 code는 건너뛴다. 둘 다 없으면 없음만 쓴다.
     */
    private static String confirmedRequirements(List<Requirement> requirements,
                                                SystemDesignConfirmedRequirements confirmed) {
        List<String> askedLines = confirmed.askedRequirementCodes().stream()
                .flatMap(code -> requirements.stream().filter(requirement -> requirement.code().equals(code)))
                .map(requirement -> "- " + SystemDesignToolSchemas.requirementFieldName(requirement.code())
                        + " | 이름: " + escape(requirement.name()) + " | 정해 둔 사실: " + escape(requirement.answer()))
                .toList();
        List<String> itemLines = confirmed.items().stream()
                .map(item -> "- " + item.source().name() + " | 이름: " + escape(item.name()) + " | 내용: "
                        + escape(item.detail()))
                .toList();
        if (askedLines.isEmpty() && itemLines.isEmpty()) {
            return NONE;
        }
        return ASKED_HIDDEN_REQUIREMENTS_HEADING + LINE_SEPARATOR + linesOrNone(askedLines) + LINE_SEPARATOR
                + CONFIRMED_ITEMS_HEADING + LINE_SEPARATOR + linesOrNone(itemLines);
    }

    private static String linesOrNone(List<String> lines) {
        return lines.isEmpty() ? NONE : String.join(LINE_SEPARATOR, lines);
    }

    /**
     * 답변 평가(L2). 역질문 기록, 이전 설계 대화, 이번 질문·답변·다이어그램, 다음 기준 질문 순서다. 이전 답변의 rank는 넣지 않아
     * 지금 답변만으로 채점하게 하고, 다이어그램은 답변마다 누적되므로 이번 것만 넣는다.
     */
    public static String answer(SystemDesignAnswerCommand command) {
        return SystemDesignSystemMessages.joinBlocks(
                requirementsDialogue(command.requirementTurns()),
                designDialogue(command.previousTurns()),
                currentTurn(command.currentTurn(), command.currentDiagram()),
                nextBaseQuestion(command.nextBaseQuestion()));
    }

    private static String nextBaseQuestion(SystemDesignNextBaseQuestion question) {
        return "<next_base_question" + turnAttributes(question.questionOrder(), question.stage()) + ">"
                + escape(question.content()) + "</next_base_question>";
    }

    /**
     * 마지막 답변 평가와 리포트(L3). 답변 평가와 같은 기록 뒤에 다음 기준 질문 대신 숨은 요구사항의 확인 여부를 둔다.
     * 요구사항의 답(수치)은 넣지 않아, 확인하지 않은 요구사항의 수치를 종합 평가가 근거 없이 끌어 쓰지 않게 한다.
     */
    public static String end(SystemDesignEndCommand command) {
        return SystemDesignSystemMessages.joinBlocks(
                requirementsDialogue(command.requirementTurns()),
                designDialogue(command.previousTurns()),
                currentTurn(command.currentTurn(), command.currentDiagram()),
                hiddenRequirementsStatus(command.problem().requirements(), command.confirmedRequirementCodes()));
    }

    // 확인 여부는 code로 정하고 줄에는 이름을 문제의 요구사항 순서로 쓴다. 이름도 시드 문장이지만 출처와 관계없이 이스케이프한다.
    private static String hiddenRequirementsStatus(List<Requirement> requirements, List<String> confirmedCodes) {
        if (requirements.isEmpty()) {
            return SystemDesignSystemMessages.tagged("hidden_requirements_status", NONE);
        }
        return SystemDesignSystemMessages.tagged("hidden_requirements_status", requirements.stream()
                .map(requirement -> "- " + escape(requirement.name()) + ": "
                        + (confirmedCodes.contains(requirement.code()) ? CONFIRMED : NOT_CONFIRMED))
                .collect(Collectors.joining(LINE_SEPARATOR)));
    }

    private static String requirementsDialogue(List<SystemDesignRequirementTurn> turns) {
        return SystemDesignSystemMessages.tagged("requirements_dialogue",
                turns.isEmpty() ? NO_REQUIREMENT_QUESTIONS : requirementItems(turns));
    }

    private static String requirementItems(List<SystemDesignRequirementTurn> turns) {
        return turns.stream()
                .map(turn -> SystemDesignSystemMessages.tagged("item", candidateQuestion(turn.question())
                        + LINE_SEPARATOR + "<interviewer_answer>" + escape(turn.answer()) + "</interviewer_answer>"))
                .collect(Collectors.joining(LINE_SEPARATOR));
    }

    private static String candidateQuestion(String question) {
        return "<candidate_question>" + escape(question) + "</candidate_question>";
    }

    private static String designDialogue(List<SystemDesignAnswerTurn> turns) {
        if (turns.isEmpty()) {
            return SystemDesignSystemMessages.tagged("design_dialogue", NONE);
        }
        return SystemDesignSystemMessages.tagged("design_dialogue", turns.stream()
                .map(turn -> element("turn", turnAttributes(turn.questionOrder(), turn.stage()),
                        questionAndAnswer(turn)))
                .collect(Collectors.joining(LINE_SEPARATOR)));
    }

    private static String currentTurn(SystemDesignAnswerTurn turn, SystemDesignDiagram diagram) {
        String candidateDiagram = SystemDesignSystemMessages.tagged("candidate_diagram",
                SystemDesignDiagramRenderer.render(diagram));
        return element("current_turn", turnAttributes(turn.questionOrder(), turn.stage()),
                questionAndAnswer(turn) + LINE_SEPARATOR + candidateDiagram);
    }

    private static String questionAndAnswer(SystemDesignAnswerTurn turn) {
        return "<interviewer_question>" + escape(turn.question()) + "</interviewer_question>" + LINE_SEPARATOR
                + "<candidate_answer>" + escape(turn.answer()) + "</candidate_answer>";
    }

    // 순번과 단계는 서버가 정한 정수와 enum 이름이라 이스케이프할 글자가 없다.
    private static String turnAttributes(int questionOrder, SystemDesignStage stage) {
        return " order=\"" + questionOrder + "\" stage=\"" + stage.name() + "\"";
    }

    private static String element(String tag, String attributes, String content) {
        return "<" + tag + attributes + ">" + LINE_SEPARATOR + content + LINE_SEPARATOR + "</" + tag + ">";
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
