package com.samhap.kokomen.global.external.bedrock;

import com.samhap.kokomen.global.external.llm.ToolField;
import com.samhap.kokomen.global.external.llm.ToolSchema;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import software.amazon.awssdk.core.document.Document;
import software.amazon.awssdk.services.bedrockruntime.model.SpecificToolChoice;
import software.amazon.awssdk.services.bedrockruntime.model.Tool;
import software.amazon.awssdk.services.bedrockruntime.model.ToolChoice;
import software.amazon.awssdk.services.bedrockruntime.model.ToolConfiguration;
import software.amazon.awssdk.services.bedrockruntime.model.ToolInputSchema;
import software.amazon.awssdk.services.bedrockruntime.model.ToolSpecification;

/**
 * provider 중립 {@link ToolSchema}를 Bedrock Converse {@link ToolConfiguration}으로 변환한다.
 * 스키마는 flat(중첩 object 없음)하게 유지하며, 지정한 도구를 반드시 호출하도록 tool_choice를 강제한다.
 */
public final class BedrockToolSchemaRenderer {

    private BedrockToolSchemaRenderer() {
    }

    public static ToolConfiguration render(ToolSchema schema) {
        Map<String, Document> properties = new LinkedHashMap<>();
        for (ToolField field : schema.fields()) {
            properties.put(field.name(), renderField(field));
        }

        Document json = Document.fromMap(Map.of(
                "type", Document.fromString("object"),
                "properties", Document.fromMap(properties),
                "required", Document.fromList(schema.requiredFields().stream()
                        .map(field -> Document.fromString(field.name()))
                        .toList())));

        Tool tool = Tool.builder()
                .toolSpec(ToolSpecification.builder()
                        .name(schema.name())
                        .description(schema.description())
                        .inputSchema(ToolInputSchema.builder().json(json).build())
                        .build())
                .build();

        return ToolConfiguration.builder()
                .tools(tool)
                .toolChoice(ToolChoice.builder()
                        .tool(SpecificToolChoice.builder().name(schema.name()).build())
                        .build())
                .build();
    }

    private static Document renderField(ToolField field) {
        Map<String, Document> property = new LinkedHashMap<>();
        property.put("type", Document.fromString("string"));
        property.put("description", Document.fromString(field.description()));
        if (field.enumValues() != null) {
            property.put("enum", Document.fromList(field.enumValues().stream()
                    .map(Document::fromString)
                    .toList()));
        }
        return Document.fromMap(property);
    }
}
