package com.samhap.kokomen.interview.external.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record Message(
        @JsonProperty("tool_calls")
        List<ToolCall> toolCalls
) {
}
