package com.samhap.kokomen.interview.tool;

/**
 * provider 중립적인 대화 히스토리 한 턴. role 문자열("assistant"/"user")은 GPT chat 메시지와
 * Bedrock Converse 메시지에서 동일하게 사용되므로 각 팩토리는 이 턴을 자신의 SDK 메시지 타입으로
 * 매핑만 하면 된다.
 */
public record ConversationTurn(String role, String content) {

    public static final String ROLE_ASSISTANT = "assistant";
    public static final String ROLE_USER = "user";

    public static ConversationTurn assistant(String content) {
        return new ConversationTurn(ROLE_ASSISTANT, content);
    }

    public static ConversationTurn user(String content) {
        return new ConversationTurn(ROLE_USER, content);
    }
}
