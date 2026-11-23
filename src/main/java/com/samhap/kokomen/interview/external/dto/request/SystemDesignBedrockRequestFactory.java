package com.samhap.kokomen.interview.external.dto.request;

import com.samhap.kokomen.global.external.bedrock.BedrockToolSchemaRenderer;
import com.samhap.kokomen.interview.tool.SystemDesignAnswerCommand;
import com.samhap.kokomen.interview.tool.SystemDesignEndCommand;
import com.samhap.kokomen.interview.tool.SystemDesignProblemContext;
import com.samhap.kokomen.interview.tool.SystemDesignProblemContext.EvaluationPoint;
import com.samhap.kokomen.interview.tool.SystemDesignProblemContext.Requirement;
import com.samhap.kokomen.interview.tool.SystemDesignRequirementAnswerCommand;
import com.samhap.kokomen.interview.tool.SystemDesignSystemMessages;
import com.samhap.kokomen.interview.tool.SystemDesignToolSchemas;
import com.samhap.kokomen.interview.tool.SystemDesignUserMessages;
import java.util.List;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.Message;
import software.amazon.awssdk.services.bedrockruntime.model.SystemContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ToolConfiguration;

/**
 * 시스템 설계 면접 Bedrock 요청의 조립. system 블록 하나와 user 메시지 하나다. 역질문은 면접자가 묻고 면접관이 답해 역할이 반대이고,
 * 답변마다 다이어그램을 같은 턴에 붙여야 해서 기존 면접처럼 assistant/user 차례를 쌓지 않는다. 도구는 하나를 toolChoice로 강제하고,
 * system 블록 끝의 cachePoint는 BedrockConverseClient가 붙인다.
 */
public final class SystemDesignBedrockRequestFactory {

    private SystemDesignBedrockRequestFactory() {
    }

    public static List<SystemContentBlock> createRequirementAnswerSystem(SystemDesignProblemContext problem) {
        return List.of(SystemContentBlock.builder()
                .text(SystemDesignSystemMessages.requirementAnswer(problem))
                .build());
    }

    public static List<Message> createRequirementAnswerMessages(SystemDesignRequirementAnswerCommand command) {
        return List.of(userMessage(SystemDesignUserMessages.requirementAnswer(command)));
    }

    // 도구 하나를 toolChoice로 강제한다.
    public static ToolConfiguration createRequirementAnswerToolConfig(List<Requirement> requirements) {
        return BedrockToolSchemaRenderer.render(SystemDesignToolSchemas.requirementAnswer(requirements));
    }

    public static List<SystemContentBlock> createAnswerSystem(SystemDesignProblemContext problem) {
        return List.of(SystemContentBlock.builder()
                .text(SystemDesignSystemMessages.answer(problem))
                .build());
    }

    public static List<Message> createAnswerMessages(SystemDesignAnswerCommand command) {
        return List.of(userMessage(SystemDesignUserMessages.answer(command)));
    }

    public static ToolConfiguration createAnswerToolConfig() {
        return BedrockToolSchemaRenderer.render(SystemDesignToolSchemas.answer());
    }

    public static List<SystemContentBlock> createEndSystem(SystemDesignProblemContext problem) {
        return List.of(SystemContentBlock.builder()
                .text(SystemDesignSystemMessages.end(problem))
                .build());
    }

    public static List<Message> createEndMessages(SystemDesignEndCommand command) {
        return List.of(userMessage(SystemDesignUserMessages.end(command)));
    }

    public static ToolConfiguration createEndToolConfig(List<EvaluationPoint> points) {
        return BedrockToolSchemaRenderer.render(SystemDesignToolSchemas.end(points));
    }

    private static Message userMessage(String text) {
        return Message.builder()
                .role(ConversationRole.USER)
                .content(List.of(ContentBlock.builder().text(text).build()))
                .build();
    }
}
