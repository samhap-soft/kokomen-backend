package com.samhap.kokomen.resume.external.dto;

import com.samhap.kokomen.interview.external.dto.response.ToolCall;
import java.util.List;

public record ResumeGptResponseMessage(
        String role,
        String content,
        List<ToolCall> toolCalls
) {
}
