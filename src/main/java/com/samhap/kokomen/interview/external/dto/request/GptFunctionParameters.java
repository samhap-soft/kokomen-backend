package com.samhap.kokomen.interview.external.dto.request;

import java.util.List;
import java.util.Map;

public record GptFunctionParameters(
        String type,
        Map<String, FunctionParamProperty> properties,
        List<String> required
) {
}
