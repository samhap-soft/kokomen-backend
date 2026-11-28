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
 * 필드는 string(enum 포함)과 원소가 객체인 배열만 그린다. 배열 원소의 객체는 도구 입력 최상위와 같은 모양(type·properties·required)이다.
 * 글자 수·개수 같은 제한은 설명에만 적고 스키마 키워드(maxLength·maxItems)로 넣지 않는다. 응답은 서버가 다시 검사한다.
 * 지정한 도구를 반드시 호출하도록 tool_choice를 강제한다.
 */
public final class BedrockToolSchemaRenderer {

    private BedrockToolSchemaRenderer() {
    }

    public static ToolConfiguration render(ToolSchema schema) {
        Document json = objectSchema(schema.fields());

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

    // properties와 required는 필드 순서다.
    private static Document objectSchema(List<ToolField> fields) {
        Map<String, Document> properties = new LinkedHashMap<>();
        for (ToolField field : fields) {
            properties.put(field.name(), renderField(field));
        }
        return Document.fromMap(Map.of(
                "type", Document.fromString("object"),
                "properties", Document.fromMap(properties),
                "required", Document.fromList(fields.stream()
                        .filter(ToolField::required)
                        .map(field -> Document.fromString(field.name()))
                        .toList())));
    }

    private static Document renderField(ToolField field) {
        Map<String, Document> property = new LinkedHashMap<>();
        if (field.isObjectArray()) {
            property.put("type", Document.fromString("array"));
            property.put("description", Document.fromString(field.description()));
            property.put("items", objectSchema(field.itemFields()));
            return Document.fromMap(property);
        }
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
