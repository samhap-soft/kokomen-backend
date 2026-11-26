package com.samhap.kokomen.interview.domain.systemdesign;

import static com.samhap.kokomen.interview.domain.systemdesign.SystemDesignErrorMessage.DIAGRAM_BLOCK_ID;
import static com.samhap.kokomen.interview.domain.systemdesign.SystemDesignErrorMessage.DIAGRAM_BLOCK_LABEL;
import static com.samhap.kokomen.interview.domain.systemdesign.SystemDesignErrorMessage.DIAGRAM_CONNECTION_ID;
import static com.samhap.kokomen.interview.domain.systemdesign.SystemDesignErrorMessage.DIAGRAM_CONNECTION_LABEL;
import static com.samhap.kokomen.interview.domain.systemdesign.SystemDesignErrorMessage.DIAGRAM_DANGLING_CONNECTION;
import static com.samhap.kokomen.interview.domain.systemdesign.SystemDesignErrorMessage.DIAGRAM_DUPLICATE_BLOCK_ID;
import static com.samhap.kokomen.interview.domain.systemdesign.SystemDesignErrorMessage.DIAGRAM_DUPLICATE_CONNECTION_ID;
import static com.samhap.kokomen.interview.domain.systemdesign.SystemDesignErrorMessage.DIAGRAM_FORMAT;
import static com.samhap.kokomen.interview.domain.systemdesign.SystemDesignErrorMessage.DIAGRAM_POSITION;
import static com.samhap.kokomen.interview.domain.systemdesign.SystemDesignErrorMessage.DIAGRAM_PROP_KEY;
import static com.samhap.kokomen.interview.domain.systemdesign.SystemDesignErrorMessage.DIAGRAM_PROP_VALUE;
import static com.samhap.kokomen.interview.domain.systemdesign.SystemDesignErrorMessage.DIAGRAM_SELF_CONNECTION;
import static com.samhap.kokomen.interview.domain.systemdesign.SystemDesignErrorMessage.DIAGRAM_TOO_MANY_BLOCKS;
import static com.samhap.kokomen.interview.domain.systemdesign.SystemDesignErrorMessage.DIAGRAM_TOO_MANY_CONNECTIONS;
import static com.samhap.kokomen.interview.domain.systemdesign.SystemDesignErrorMessage.DIAGRAM_TOO_MANY_PROPS;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.samhap.kokomen.global.exception.BadRequestException;
import com.samhap.kokomen.global.infrastructure.Jackson2CompatibleJsonMapper;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;

/**
 * 시스템 설계 다이어그램. 검사를 마친 값만 담는 불변 값 객체다.
 *
 * <p>검사는 {@link #from(Input)} 한곳에서 원시 값을 보고 {@link SystemDesignErrorMessage}의 다이어그램 규칙 순서(1~16번)대로 한다.
 * 요청 DTO와 DB 값({@link #fromJson(String)})이 모두 이 메서드를 거치므로 시드의 시작 다이어그램도 같은 규칙·같은 순서로 검사된다.
 * 값 record의 생성자에서 검사하지 않는 이유는 Java가 생성자 인자를 먼저 평가해 이 순서를 지킬 수 없기 때문이다.
 *
 * <p>문자열은 자르거나 공백을 지우지 않고 받은 그대로 검사·저장하고, 글자 수는 {@code String.length()}(JS {@code .length}와 같다)로 센다.
 */
public final class SystemDesignDiagram {

    public static final int MAX_BLOCK_COUNT = 30;
    public static final int MAX_CONNECTION_COUNT = 60;
    public static final int MAX_ID_LENGTH = 64;
    public static final int MAX_BLOCK_LABEL_LENGTH = 50;
    public static final int MAX_CONNECTION_LABEL_LENGTH = 30;
    public static final int MAX_PROP_COUNT = 10;
    public static final int MAX_PROP_KEY_LENGTH = 30;
    public static final int MAX_PROP_VALUE_LENGTH = 100;

    private static final int MIN_ID_LENGTH = 1;
    private static final int MIN_BLOCK_LABEL_LENGTH = 1;
    // 전역 Jackson처럼 모르는 필드는 버리고, 저장할 때는 null인 연결 label을 키째 쓰지 않는다.
    private static final ObjectMapper OBJECT_MAPPER = Jackson2CompatibleJsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .changeDefaultPropertyInclusion(inclusion ->
                    JsonInclude.Value.construct(JsonInclude.Include.NON_NULL, JsonInclude.Include.NON_NULL))
            .build();
    private static final SystemDesignDiagram EMPTY = new SystemDesignDiagram(List.of(), List.of());

    private final List<Block> blocks;
    private final List<Connection> connections;

    private SystemDesignDiagram(List<Block> blocks, List<Connection> connections) {
        this.blocks = List.copyOf(blocks);
        this.connections = List.copyOf(connections);
    }

    /**
     * 규칙 1~3번을 먼저 보고, 블록을 배열 순서대로 하나씩 4~11번, 연결을 배열 순서대로 하나씩 12~16번을 본다.
     * 번호는 {@link SystemDesignErrorMessage}에 둔 다이어그램 문구의 순서다. 처음 어긴 규칙 하나의 문구로 400을 던진다.
     * 배열 원소가 null이면 그 원소를 볼 차례에 1번({@link SystemDesignErrorMessage#DIAGRAM_FORMAT}) 문구로 알린다.
     *
     * <p>DB에서 읽을 때({@link #fromJson(String)})도 같은 규칙을 거친다. 규칙은 완화만 한다. 조이면 이미 저장된 행을 읽을 수 없으므로 데이터 이전이 먼저다.
     */
    public static SystemDesignDiagram from(Input input) {
        validate(input != null && input.blocks() != null && input.connections() != null, DIAGRAM_FORMAT);
        validate(input.blocks().size() <= MAX_BLOCK_COUNT, DIAGRAM_TOO_MANY_BLOCKS);
        validate(input.connections().size() <= MAX_CONNECTION_COUNT, DIAGRAM_TOO_MANY_CONNECTIONS);

        // Set.of·Set.copyOf는 contains(null)에서 NPE를 던지므로 HashSet을 쓴다.
        // null인 source·target은 15번 규칙(없는 블록을 가리키는 연결)으로 알린다.
        Set<String> blockIds = new HashSet<>();
        List<Block> blocks = new ArrayList<>();
        for (InputBlock inputBlock : input.blocks()) {
            blocks.add(readBlock(inputBlock, blockIds));
        }
        Set<String> connectionIds = new HashSet<>();
        List<Connection> connections = new ArrayList<>();
        for (InputConnection inputConnection : input.connections()) {
            connections.add(readConnection(inputConnection, connectionIds, blockIds));
        }
        return new SystemDesignDiagram(blocks, connections);
    }

    private static Block readBlock(InputBlock block, Set<String> blockIds) {
        validate(block != null, DIAGRAM_FORMAT);
        validate(hasLengthBetween(block.id(), MIN_ID_LENGTH, MAX_ID_LENGTH), DIAGRAM_BLOCK_ID);
        validate(blockIds.add(block.id()), DIAGRAM_DUPLICATE_BLOCK_ID);
        SystemDesignBlockKind kind = SystemDesignBlockKind.from(block.kind());
        validate(hasLengthBetween(block.label(), MIN_BLOCK_LABEL_LENGTH, MAX_BLOCK_LABEL_LENGTH), DIAGRAM_BLOCK_LABEL);
        List<Prop> props = readProps(block.props());
        validate(isFinite(block.x()) && isFinite(block.y()), DIAGRAM_POSITION);
        return new Block(block.id(), kind, block.label(), props, new Position(block.x(), block.y()));
    }

    // 속성은 하나씩 이름(9번) 다음에 값(10번)을 본다.
    private static List<Prop> readProps(List<InputProp> inputProps) {
        validate(inputProps != null, DIAGRAM_FORMAT);
        validate(inputProps.size() <= MAX_PROP_COUNT, DIAGRAM_TOO_MANY_PROPS);
        List<Prop> props = new ArrayList<>();
        for (InputProp prop : inputProps) {
            validate(prop != null, DIAGRAM_FORMAT);
            validate(hasLengthBetween(prop.key(), 0, MAX_PROP_KEY_LENGTH), DIAGRAM_PROP_KEY);
            validate(hasLengthBetween(prop.value(), 0, MAX_PROP_VALUE_LENGTH), DIAGRAM_PROP_VALUE);
            props.add(new Prop(prop.key(), prop.value()));
        }
        return List.copyOf(props);
    }

    // 연결은 블록을 모두 본 뒤에 보므로 blockIds에는 이 다이어그램의 블록 id가 모두 들어 있다.
    private static Connection readConnection(InputConnection connection, Set<String> connectionIds,
                                             Set<String> blockIds) {
        validate(connection != null, DIAGRAM_FORMAT);
        validate(hasLengthBetween(connection.id(), MIN_ID_LENGTH, MAX_ID_LENGTH), DIAGRAM_CONNECTION_ID);
        validate(connectionIds.add(connection.id()), DIAGRAM_DUPLICATE_CONNECTION_ID);
        validate(connection.label() == null || connection.label().length() <= MAX_CONNECTION_LABEL_LENGTH,
                DIAGRAM_CONNECTION_LABEL);
        validate(blockIds.contains(connection.source()) && blockIds.contains(connection.target()),
                DIAGRAM_DANGLING_CONNECTION);
        validate(!connection.source().equals(connection.target()), DIAGRAM_SELF_CONNECTION);
        return new Connection(connection.id(), connection.source(), connection.target(), connection.label());
    }

    private static boolean hasLengthBetween(String value, int minLength, int maxLength) {
        return value != null && value.length() >= minLength && value.length() <= maxLength;
    }

    private static boolean isFinite(Double value) {
        return value != null && Double.isFinite(value);
    }

    private static void validate(boolean valid, SystemDesignErrorMessage errorMessage) {
        if (!valid) {
            throw new BadRequestException(errorMessage.getMessage());
        }
    }

    public static SystemDesignDiagram empty() {
        return EMPTY;
    }

    /**
     * 저장 모양의 JSON({@link #toJson()}의 결과)을 읽어 {@link #from(Input)}으로 검사한다. JSON이 깨졌으면 형식 오류(1번) 문구로 400이다.
     */
    public static SystemDesignDiagram fromJson(String json) {
        validate(json != null, DIAGRAM_FORMAT);
        DiagramJson diagramJson;
        try {
            diagramJson = OBJECT_MAPPER.readValue(json, DiagramJson.class);
        } catch (JacksonException e) {
            throw new BadRequestException(DIAGRAM_FORMAT.getMessage(), e);
        }
        if (diagramJson == null) {
            return from(null);
        }
        return from(diagramJson.toInput());
    }

    // null 배열과 null 원소를 그대로 남겨 from(Input)이 볼 차례에 형식 오류(1번) 문구로 알리게 한다.
    private static <T, R> List<R> mapKeepingNull(List<T> values, Function<T, R> mapper) {
        if (values == null) {
            return null;
        }
        return values.stream()
                .map(value -> value == null ? null : mapper.apply(value))
                .toList();
    }

    /**
     * 아래 저장 모양(DiagramJson)의 필드만 쓴 정규화 JSON. 연결 label이 null이면 키를 쓰지 않는다.
     */
    public String toJson() {
        try {
            return OBJECT_MAPPER.writeValueAsString(DiagramJson.from(this));
        } catch (JacksonException e) {
            throw new IllegalStateException("시스템 설계 다이어그램을 JSON으로 바꾸지 못했습니다.", e);
        }
    }

    public List<Block> blocks() {
        return blocks;
    }

    public List<Connection> connections() {
        return connections;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SystemDesignDiagram that)) {
            return false;
        }
        return blocks.equals(that.blocks) && connections.equals(that.connections);
    }

    @Override
    public int hashCode() {
        return Objects.hash(blocks, connections);
    }

    @Override
    public String toString() {
        return "SystemDesignDiagram{blocks=" + blocks + ", connections=" + connections + '}';
    }

    /**
     * 검사하기 전의 원시 값. 모르는 종류와 빠진 좌표를 각 규칙의 문구(6번, 11번)로 알리려고 {@code kind}는 String, 좌표는 Double로 받는다.
     */
    public record Input(List<InputBlock> blocks, List<InputConnection> connections) {
    }

    public record InputBlock(String id, String kind, String label, List<InputProp> props, Double x, Double y) {
    }

    public record InputProp(String key, String value) {
    }

    public record InputConnection(String id, String source, String target, String label) {
    }

    /**
     * 검사를 마친 값. 생성자는 검사하지 않으며 {@link SystemDesignDiagram#from(Input)}만 이 값을 담는다.
     */
    public record Block(String id, SystemDesignBlockKind kind, String label, List<Prop> props, Position position) {
    }

    public record Prop(String key, String value) {
    }

    public record Position(double x, double y) {
    }

    public record Connection(String id, String source, String target, String label) {
    }

    // 저장 모양(DB JSON 컬럼에 쓰는 모양). 읽기와 쓰기가 같은 모양을 쓰도록 여기 한곳에만 둔다.
    private record DiagramJson(List<BlockJson> blocks, List<ConnectionJson> connections) {

        private static DiagramJson from(SystemDesignDiagram diagram) {
            return new DiagramJson(
                    diagram.blocks.stream().map(BlockJson::from).toList(),
                    diagram.connections.stream().map(ConnectionJson::from).toList());
        }

        private Input toInput() {
            return new Input(mapKeepingNull(blocks, BlockJson::toInput),
                    mapKeepingNull(connections, ConnectionJson::toInput));
        }
    }

    private record BlockJson(String id, String kind, String label, List<PropJson> props, PositionJson position) {

        private static BlockJson from(Block block) {
            return new BlockJson(block.id(), block.kind().name(), block.label(),
                    block.props().stream().map(PropJson::from).toList(),
                    new PositionJson(block.position().x(), block.position().y()));
        }

        private InputBlock toInput() {
            Double x = position == null ? null : position.x();
            Double y = position == null ? null : position.y();
            return new InputBlock(id, kind, label, mapKeepingNull(props, PropJson::toInput), x, y);
        }
    }

    private record PropJson(String key, String value) {

        private static PropJson from(Prop prop) {
            return new PropJson(prop.key(), prop.value());
        }

        private InputProp toInput() {
            return new InputProp(key, value);
        }
    }

    private record PositionJson(Double x, Double y) {
    }

    private record ConnectionJson(String id, String source, String target, String label) {

        private static ConnectionJson from(Connection connection) {
            return new ConnectionJson(connection.id(), connection.source(), connection.target(), connection.label());
        }

        private InputConnection toInput() {
            return new InputConnection(id, source, target, label);
        }
    }
}
