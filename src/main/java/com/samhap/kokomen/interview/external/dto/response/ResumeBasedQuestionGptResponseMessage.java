package com.samhap.kokomen.interview.external.dto.response;

import java.util.List;

public record ResumeBasedQuestionGptResponseMessage(
        String role,
        String content,
        List<ToolCall> toolCalls
) {
}
