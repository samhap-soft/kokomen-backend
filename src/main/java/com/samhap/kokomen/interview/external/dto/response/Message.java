package com.samhap.kokomen.interview.external.dto.response;

import java.util.List;

public record Message(
        List<ToolCall> toolCalls
) {
}
