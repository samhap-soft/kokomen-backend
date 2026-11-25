package com.samhap.kokomen.interview.tool;

import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignDiagram;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignDiagram.Block;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignDiagram.Connection;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignDiagram.Prop;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignText;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 다이어그램을 LLM이 읽는 텍스트로 그린다. 형식은 {@link SystemDesignPromptFragments#DIAGRAM_READING_GUIDE}가 설명하는 그대로다.
 * 검사를 통과한 값만 들어오므로 자르지 않고, 화면 좌표는 평가와 관계없어 쓰지 않는다.
 *
 * <p>id·이름·속성은 다듬지 않고 저장한 값이라 줄바꿈이 들어 있을 수 있다. 한 줄에 한 항목인 형식이 깨지지 않게 줄을 나누는 글자를
 * 하나씩 공백 하나로 바꾸고, user 메시지에 들어가므로 {@link SystemDesignUserMessages#escape(String)}로 이스케이프한다.
 */
public final class SystemDesignDiagramRenderer {

    private static final String LINE_SEPARATOR = "\n";
    private static final String NONE_LINE = "- (없음)";
    // TAB과 CR·LF, 그리고 유니코드가 줄바꿈으로 정한 나머지 글자(VT, FF, NEL, LINE SEPARATOR, PARAGRAPH SEPARATOR)
    private static final Pattern LINE_BREAK_OR_TAB = Pattern.compile("[\\t\\n\\x0B\\f\\r\\x{85}\\x{2028}\\x{2029}]");

    private SystemDesignDiagramRenderer() {
    }

    public static String render(SystemDesignDiagram diagram) {
        List<String> lines = new ArrayList<>();
        lines.add("[블록]");
        lines.addAll(orNone(diagram.blocks().stream()
                .map(SystemDesignDiagramRenderer::blockLine)
                .toList()));
        lines.add("[연결]");
        lines.addAll(orNone(diagram.connections().stream()
                .map(SystemDesignDiagramRenderer::connectionLine)
                .toList()));
        return String.join(LINE_SEPARATOR, lines);
    }

    private static List<String> orNone(List<String> lines) {
        if (lines.isEmpty()) {
            return List.of(NONE_LINE);
        }
        return lines;
    }

    // - {id}: {이름} [{종류}] ({속성 이름}: {값}, {속성 이름}: {값}). 속성이 없으면 괄호를 쓰지 않는다.
    private static String blockLine(Block block) {
        String line = blockHead(block);
        if (block.props().isEmpty()) {
            return line;
        }
        return line + block.props().stream()
                .map(SystemDesignDiagramRenderer::propText)
                .collect(Collectors.joining(", ", " (", ")"));
    }

    /**
     * 블록 줄에서 속성 괄호 앞부분({@code - {id}: {이름} [{종류}]}). 증거 검증기가 블록 줄을 앞부분과 속성으로 나눠 볼 때 쓴다.
     */
    static String blockHead(Block block) {
        return "- " + text(block.id()) + ": " + text(block.label()) + " [" + block.kind().name() + "]";
    }

    /**
     * 블록 줄의 속성 하나({@code {속성 이름}: {값}}). 블록 줄은 속성을 ", "로 이어 괄호로 묶는다.
     */
    static String propText(Prop prop) {
        return text(prop.key()) + ": " + text(prop.value());
    }

    // - {출발} → {도착} : {이름}. 이름이 없거나 공백뿐이면 " : {이름}"을 쓰지 않는다.
    private static String connectionLine(Connection connection) {
        String line = "- " + text(connection.source()) + " → " + text(connection.target());
        String label = text(connection.label());
        if (SystemDesignText.normalize(label).isEmpty()) {
            return line;
        }
        return line + " : " + label;
    }

    private static String text(String value) {
        if (value == null) {
            return "";
        }
        return SystemDesignUserMessages.escape(LINE_BREAK_OR_TAB.matcher(value).replaceAll(" "));
    }
}
