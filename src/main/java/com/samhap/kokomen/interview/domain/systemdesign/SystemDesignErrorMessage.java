package com.samhap.kokomen.interview.domain.systemdesign;

import lombok.Getter;

/**
 * 시스템 설계 면접의 사용자 오류 문구. 프론트가 message를 그대로 보여 주므로 문구가 곧 계약이다.
 */
@Getter
public enum SystemDesignErrorMessage {

    INTERVIEW_NOT_FOUND("면접을 찾을 수 없어요."),
    NOT_OWNER("본인의 면접만 볼 수 있어요."),
    QUESTION_NOT_FOUND("질문을 찾을 수 없어요."),
    PROCEED_IN_PROGRESS("이전 요청을 처리하고 있어요. 잠시 후 다시 시도해 주세요."),
    // Redis 장애 등으로 진행 락이나 채점 상태를 다루지 못했을 때(503). 내부 문구(레디스 예외 발생) 대신 보인다.
    TEMPORARILY_UNAVAILABLE("일시적인 문제로 요청을 처리하지 못했어요. 잠시 후 다시 시도해 주세요."),

    PROBLEM_UNAVAILABLE("시스템 설계 문제를 불러오지 못했어요. 잠시 후 다시 시도해 주세요."),

    REQUIREMENT_STAGE_OVER("요구사항 단계가 끝났어요."),
    REQUIREMENT_QUESTION_LENGTH("질문은 1~200자로 적어 주세요."),
    REQUIREMENT_QUESTION_LIMIT("역질문은 5개까지 할 수 있어요."),
    REQUIREMENT_ANSWER_FAILED("질문에 답하지 못했어요. 잠시 후 다시 시도해 주세요."),
    LLM_BUSY("요청이 많아 답하지 못했어요. 잠시 후 다시 시도해 주세요."),

    ALREADY_DESIGN_STAGE("이미 설계 단계예요."),

    INTERVIEW_FINISHED("이미 끝난 면접이에요."),
    REQUIREMENTS_NOT_COMPLETED("요구사항 정리를 먼저 끝내 주세요."),
    NOT_CURRENT_QUESTION("지금 질문이 아니에요."),
    ANSWER_LENGTH("답변은 1~3,000자로 적어 주세요."),

    REPORT_NOT_READY("면접이 끝나야 리포트를 볼 수 있어요."),

    // 다이어그램 검증 문구. SystemDesignDiagram이 검사하는 순서대로 두고, 위에서부터 1~16번 규칙이라 부른다.
    // SystemDesignDiagram의 주석과 테스트 이름이 이 번호를 쓴다.
    DIAGRAM_FORMAT("다이어그램 형식이 맞지 않아요."),
    DIAGRAM_TOO_MANY_BLOCKS("블록은 30개까지 그릴 수 있어요."),
    DIAGRAM_TOO_MANY_CONNECTIONS("연결은 60개까지 그릴 수 있어요."),
    DIAGRAM_BLOCK_ID("블록 id는 1~64자여야 해요."),
    DIAGRAM_DUPLICATE_BLOCK_ID("블록 id가 겹쳐요."),
    DIAGRAM_UNKNOWN_KIND("블록 종류를 알 수 없어요."),
    DIAGRAM_BLOCK_LABEL("블록 이름은 1~50자로 적어 주세요."),
    DIAGRAM_TOO_MANY_PROPS("블록 속성은 10개까지 넣을 수 있어요."),
    DIAGRAM_PROP_KEY("속성 이름은 30자까지 적을 수 있어요."),
    DIAGRAM_PROP_VALUE("속성 값은 100자까지 적을 수 있어요."),
    DIAGRAM_POSITION("블록 위치가 올바르지 않아요."),
    DIAGRAM_CONNECTION_ID("연결 id는 1~64자여야 해요."),
    DIAGRAM_DUPLICATE_CONNECTION_ID("연결 id가 겹쳐요."),
    DIAGRAM_CONNECTION_LABEL("연결 이름은 30자까지 적을 수 있어요."),
    DIAGRAM_DANGLING_CONNECTION("연결이 없는 블록을 가리켜요."),
    DIAGRAM_SELF_CONNECTION("블록을 자기 자신과 연결할 수 없어요."),
    ;

    private final String message;

    SystemDesignErrorMessage(String message) {
        this.message = message;
    }
}
