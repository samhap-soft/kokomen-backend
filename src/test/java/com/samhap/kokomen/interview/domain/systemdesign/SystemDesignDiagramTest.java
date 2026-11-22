package com.samhap.kokomen.interview.domain.systemdesign;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.samhap.kokomen.global.exception.BadRequestException;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignDiagram.Block;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignDiagram.Connection;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignDiagram.Input;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignDiagram.InputBlock;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignDiagram.InputConnection;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignDiagram.InputProp;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignDiagram.Position;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignDiagram.Prop;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class SystemDesignDiagramTest {

    private static final String FORMAT = "다이어그램 형식이 맞지 않아요.";
    private static final String BLOCK_ID = "블록 id는 1~64자여야 해요.";
    private static final String BLOCK_KIND = "블록 종류를 알 수 없어요.";
    private static final String BLOCK_LABEL = "블록 이름은 1~50자로 적어 주세요.";
    private static final String POSITION = "블록 위치가 올바르지 않아요.";
    private static final String CONNECTION_ID = "연결 id는 1~64자여야 해요.";
    private static final String DANGLING = "연결이 없는 블록을 가리켜요.";

    @Test
    void 블록이_30개면_통과하고_31개면_거절한다() {
        assertAll(
                () -> assertThat(SystemDesignDiagram.from(new Input(blocks(30), List.of())).blocks()).hasSize(30),
                () -> assertRejected(new Input(blocks(31), List.of()), "블록은 30개까지 그릴 수 있어요.")
        );
    }

    @Test
    void 연결이_60개면_통과하고_61개면_거절한다() {
        assertAll(
                () -> assertThat(SystemDesignDiagram.from(new Input(blocks(2), connections(60))).connections())
                        .hasSize(60),
                () -> assertRejected(new Input(blocks(2), connections(61)), "연결은 60개까지 그릴 수 있어요.")
        );
    }

    @Test
    void blocks나_connections가_null이면_형식_오류다() {
        assertAll(
                () -> assertRejected(null, FORMAT),
                () -> assertRejected(new Input(null, List.of()), FORMAT),
                () -> assertRejected(new Input(List.of(), null), FORMAT)
        );
    }

    @Test
    void blocks_connections_props에_null_원소가_있으면_형식_오류다() {
        assertAll(
                () -> assertRejected(blocksOf((InputBlock) null), FORMAT),
                () -> assertRejected(new Input(blocks(2), Arrays.asList(connection("c1", "b1", "b2"), null)),
                        FORMAT),
                () -> assertRejected(blocksOf(blockWithProps(Arrays.asList(prop("key", "value"), null))), FORMAT)
        );
    }

    @Test
    void null_원소는_그_원소를_볼_차례에_알린다() {
        InputBlock emptyLabelBlock = blockWithLabel("");

        assertAll(
                () -> assertRejected(blocksOf(emptyLabelBlock, null), BLOCK_LABEL),
                () -> assertRejected(blocksOf(null, emptyLabelBlock), FORMAT)
        );
    }

    @Test
    void 블록_id는_1자부터_64자까지_받고_빈_문자열과_65자는_거절한다() {
        assertAll(
                () -> assertAccepted(blocksOf(block("a"))),
                () -> assertAccepted(blocksOf(block("a".repeat(64)))),
                () -> assertRejected(blocksOf(block("")), BLOCK_ID),
                () -> assertRejected(blocksOf(block("a".repeat(65))), BLOCK_ID),
                () -> assertRejected(blocksOf(block(null)), BLOCK_ID)
        );
    }

    // JS .length처럼 UTF-16 단위로 센다. 이모지는 서로게이트 쌍이라 한 개가 2단위다.
    @Test
    void 블록_id는_UTF_16_단위로_세어_이모지_32개까지_받고_33개는_거절한다() {
        String maxId = "😀".repeat(32);
        String overId = "😀".repeat(33);

        assertAll(
                () -> assertThat(maxId).hasSize(64),
                () -> assertAccepted(blocksOf(block(maxId))),
                () -> assertThat(overId).hasSize(66),
                () -> assertRejected(blocksOf(block(overId)), BLOCK_ID)
        );
    }

    @Test
    void 블록_id가_겹치면_거절한다() {
        assertRejected(blocksOf(block("api"), block("api")), "블록 id가 겹쳐요.");
    }

    @Test
    void 모르는_블록_종류는_거절한다() {
        assertAll(
                () -> assertRejected(blocksOf(blockWithKind("SERVER")), BLOCK_KIND),
                () -> assertRejected(blocksOf(blockWithKind("api_server")), BLOCK_KIND),
                () -> assertRejected(blocksOf(blockWithKind("")), BLOCK_KIND),
                () -> assertRejected(blocksOf(blockWithKind(null)), BLOCK_KIND)
        );
    }

    @Test
    void 블록_이름은_1자부터_50자까지_받는다() {
        assertAll(
                () -> assertAccepted(blocksOf(blockWithLabel("A"))),
                () -> assertAccepted(blocksOf(blockWithLabel("가".repeat(50)))),
                () -> assertRejected(blocksOf(blockWithLabel("")), BLOCK_LABEL),
                () -> assertRejected(blocksOf(blockWithLabel("가".repeat(51))), BLOCK_LABEL),
                () -> assertRejected(blocksOf(blockWithLabel(null)), BLOCK_LABEL)
        );
    }

    @Test
    void 블록_속성은_10개까지_받는다() {
        assertAll(
                () -> assertAccepted(blocksOf(blockWithProps(List.of()))),
                () -> assertAccepted(blocksOf(blockWithProps(props(10)))),
                () -> assertRejected(blocksOf(blockWithProps(props(11))), "블록 속성은 10개까지 넣을 수 있어요.")
        );
    }

    @Test
    void props가_null이면_형식_오류다() {
        assertRejected(blocksOf(blockWithProps(null)), FORMAT);
    }

    @Test
    void 속성_이름은_30자_속성_값은_100자까지_받는다() {
        String keyMessage = "속성 이름은 30자까지 적을 수 있어요.";
        String valueMessage = "속성 값은 100자까지 적을 수 있어요.";

        assertAll(
                () -> assertAccepted(blocksOf(blockWithProps(List.of(prop("k".repeat(30), "v".repeat(100)))))),
                () -> assertAccepted(blocksOf(blockWithProps(List.of(prop("", ""))))),
                () -> assertRejected(blocksOf(blockWithProps(List.of(prop("k".repeat(31), "v")))), keyMessage),
                () -> assertRejected(blocksOf(blockWithProps(List.of(prop(null, "v")))), keyMessage),
                () -> assertRejected(blocksOf(blockWithProps(List.of(prop("k", "v".repeat(101))))), valueMessage),
                () -> assertRejected(blocksOf(blockWithProps(List.of(prop("k", null)))), valueMessage)
        );
    }

    @Test
    void 속성은_하나씩_이름_다음에_값을_본다() {
        List<InputProp> props = List.of(prop("k", "v".repeat(101)), prop("k".repeat(31), "v"));

        assertRejected(blocksOf(blockWithProps(props)), "속성 값은 100자까지 적을 수 있어요.");
    }

    @Test
    void 좌표가_없거나_NaN이나_무한대면_거절한다() {
        assertAll(
                () -> assertAccepted(blocksOf(blockAt(-12.5, 3.25))),
                () -> assertRejected(blocksOf(blockAt(null, 0.0)), POSITION),
                () -> assertRejected(blocksOf(blockAt(0.0, null)), POSITION),
                () -> assertRejected(blocksOf(blockAt(Double.NaN, 0.0)), POSITION),
                () -> assertRejected(blocksOf(blockAt(0.0, Double.POSITIVE_INFINITY)), POSITION),
                () -> assertRejected(blocksOf(blockAt(Double.NEGATIVE_INFINITY, 0.0)), POSITION)
        );
    }

    @Test
    void 연결_id는_1자부터_64자까지_받고_빈_문자열과_65자는_거절한다() {
        assertAll(
                () -> assertAccepted(new Input(blocks(2), List.of(connection("c", "b1", "b2")))),
                () -> assertAccepted(new Input(blocks(2), List.of(connection("c".repeat(64), "b1", "b2")))),
                () -> assertRejected(new Input(blocks(2), List.of(connection("", "b1", "b2"))), CONNECTION_ID),
                () -> assertRejected(new Input(blocks(2), List.of(connection("c".repeat(65), "b1", "b2"))),
                        CONNECTION_ID),
                () -> assertRejected(new Input(blocks(2), List.of(connection(null, "b1", "b2"))), CONNECTION_ID)
        );
    }

    @Test
    void 연결_id가_겹치면_거절한다() {
        assertRejected(new Input(blocks(2), List.of(connection("c1", "b1", "b2"), connection("c1", "b2", "b1"))),
                "연결 id가 겹쳐요.");
    }

    @Test
    void 블록과_연결은_같은_id를_쓸_수_있다() {
        assertAccepted(new Input(blocks(2), List.of(connection("b1", "b1", "b2"))));
    }

    @Test
    void 연결_이름은_없거나_30자까지_받는다() {
        assertAll(
                () -> assertAccepted(new Input(blocks(2), List.of(labeledConnection(null)))),
                () -> assertAccepted(new Input(blocks(2), List.of(labeledConnection("")))),
                () -> assertAccepted(new Input(blocks(2), List.of(labeledConnection("가".repeat(30))))),
                () -> assertRejected(new Input(blocks(2), List.of(labeledConnection("가".repeat(31)))),
                        "연결 이름은 30자까지 적을 수 있어요.")
        );
    }

    @Test
    void 없는_블록을_가리키는_연결은_거절한다() {
        assertAll(
                () -> assertRejected(new Input(blocks(2), List.of(connection("c1", "x", "b2"))), DANGLING),
                () -> assertRejected(new Input(blocks(2), List.of(connection("c1", "b1", "x"))), DANGLING),
                () -> assertRejected(new Input(blocks(2), List.of(connection("c1", null, "b2"))), DANGLING),
                () -> assertRejected(new Input(blocks(2), List.of(connection("c1", "b1", null))), DANGLING)
        );
    }

    @Test
    void 자기_자신과의_연결은_거절한다() {
        assertRejected(new Input(blocks(2), List.of(connection("c1", "b1", "b1"))),
                "블록을 자기 자신과 연결할 수 없어요.");
    }

    @Test
    void 같은_방향의_연결이_여러_개여도_받는다() {
        SystemDesignDiagram diagram = SystemDesignDiagram.from(new Input(blocks(2), List.of(
                connection("c1", "b1", "b2"), connection("c2", "b1", "b2"), connection("c3", "b2", "b1"))));

        assertThat(diagram.connections()).extracting(Connection::id).containsExactly("c1", "c2", "c3");
    }

    @Test
    void 개수_초과는_원소_검사보다_먼저_알린다() {
        List<InputBlock> blocksWithEmptyLabel = new ArrayList<>(blocks(30));
        blocksWithEmptyLabel.add(blockWithLabel(""));

        assertAll(
                () -> assertRejected(new Input(blocksWithEmptyLabel, List.of()), "블록은 30개까지 그릴 수 있어요."),
                () -> assertRejected(new Input(List.of(blockWithLabel("")), connections(61)),
                        "연결은 60개까지 그릴 수 있어요.")
        );
    }

    @Test
    void 처음_어긴_규칙_하나만_알린다() {
        assertAll(
                () -> assertRejected(blocksOf(new InputBlock("a".repeat(65), "server", "Server", List.of(), 0.0, 0.0)),
                        BLOCK_ID),
                () -> assertRejected(new Input(List.of(block("a"), block("a")),
                        List.of(new InputConnection("c1", "a", "b", "가".repeat(31)))), "블록 id가 겹쳐요."),
                () -> assertRejected(blocksOf(new InputBlock("a", "DB", "", List.of(), 0.0, 0.0),
                        new InputBlock("b", "server", "Server", List.of(), 0.0, 0.0)), BLOCK_LABEL)
        );
    }

    @Test
    void 블록마다_4번부터_11번_규칙을_차례로_본다() {
        InputBlock first = block("a");
        List<InputProp> tooManyProps = new ArrayList<>(props(10));
        tooManyProps.add(prop("k".repeat(31), "v".repeat(101)));
        List<InputProp> longKeyAndValue = List.of(prop("k".repeat(31), "v".repeat(101)));
        List<InputProp> longValue = List.of(prop("k", "v".repeat(101)));

        assertAll(
                () -> assertRejected(blocksOf(first, new InputBlock("", "server", "", tooManyProps, null, null)),
                        BLOCK_ID),
                () -> assertRejected(blocksOf(first, new InputBlock("a", "server", "", tooManyProps, null, null)),
                        "블록 id가 겹쳐요."),
                () -> assertRejected(blocksOf(first, new InputBlock("b", "server", "", tooManyProps, null, null)),
                        BLOCK_KIND),
                () -> assertRejected(blocksOf(first, new InputBlock("b", "DB", "", tooManyProps, null, null)),
                        BLOCK_LABEL),
                () -> assertRejected(blocksOf(first, new InputBlock("b", "DB", "DB", tooManyProps, null, null)),
                        "블록 속성은 10개까지 넣을 수 있어요."),
                () -> assertRejected(blocksOf(first, new InputBlock("b", "DB", "DB", longKeyAndValue, null, null)),
                        "속성 이름은 30자까지 적을 수 있어요."),
                () -> assertRejected(blocksOf(first, new InputBlock("b", "DB", "DB", longValue, null, null)),
                        "속성 값은 100자까지 적을 수 있어요."),
                () -> assertRejected(blocksOf(first, new InputBlock("b", "DB", "DB", List.of(), null, null)),
                        POSITION)
        );
    }

    @Test
    void 연결마다_12번부터_16번_규칙을_차례로_본다() {
        List<InputBlock> blocks = List.of(block("a"), block("b"));
        InputConnection first = connection("c1", "a", "b");
        String longLabel = "가".repeat(31);

        assertAll(
                () -> assertRejected(new Input(blocks, List.of(first, new InputConnection("", "x", "x", longLabel))),
                        CONNECTION_ID),
                () -> assertRejected(new Input(blocks, List.of(first, new InputConnection("c1", "x", "x", longLabel))),
                        "연결 id가 겹쳐요."),
                () -> assertRejected(new Input(blocks, List.of(first, new InputConnection("c2", "x", "x", longLabel))),
                        "연결 이름은 30자까지 적을 수 있어요."),
                () -> assertRejected(new Input(blocks, List.of(first, new InputConnection("c2", "x", "x", null))),
                        DANGLING),
                () -> assertRejected(new Input(blocks, List.of(first, new InputConnection("c2", "a", "a", null))),
                        "블록을 자기 자신과 연결할 수 없어요.")
        );
    }

    @Test
    void 다이어그램_문자열은_공백을_지우지_않고_그대로_검사하고_담는다() {
        SystemDesignDiagram diagram = SystemDesignDiagram.from(new Input(
                List.of(new InputBlock(" ", "CUSTOM", "  API  ", List.of(prop(" 키 ", " 값 ")), 0.0, 0.0),
                        block("b")),
                List.of(new InputConnection(" c ", " ", "b", " 호출 "))));

        assertAll(
                () -> assertThat(diagram.blocks().get(0)).isEqualTo(new Block(" ", SystemDesignBlockKind.CUSTOM,
                        "  API  ", List.of(new Prop(" 키 ", " 값 ")), new Position(0.0, 0.0))),
                () -> assertThat(diagram.connections().get(0)).isEqualTo(new Connection(" c ", " ", "b", " 호출 "))
        );
    }

    @Test
    void 검사를_통과하면_검사한_값을_담는다() {
        SystemDesignDiagram diagram = SystemDesignDiagram.from(new Input(
                List.of(new InputBlock("client", "CLIENT", "Client", List.of(prop("개수", "3")), 0.0, 80.5),
                        new InputBlock("api", "API_SERVER", "API Server", List.of(), 260.0, -80.0)),
                List.of(new InputConnection("c1", "client", "api", "HTTPS"), connection("c2", "api", "client"))));

        assertAll(
                () -> assertThat(diagram.blocks()).containsExactly(
                        new Block("client", SystemDesignBlockKind.CLIENT, "Client", List.of(new Prop("개수", "3")),
                                new Position(0.0, 80.5)),
                        new Block("api", SystemDesignBlockKind.API_SERVER, "API Server", List.of(),
                                new Position(260.0, -80.0))),
                () -> assertThat(diagram.connections()).containsExactly(
                        new Connection("c1", "client", "api", "HTTPS"),
                        new Connection("c2", "api", "client", null))
        );
    }

    @Test
    void 만든_뒤에는_목록을_바꿀_수_없고_입력을_바꿔도_그대로다() {
        List<InputBlock> inputBlocks = new ArrayList<>(List.of(block("b1"), block("b2")));
        List<InputProp> inputProps = new ArrayList<>(List.of(prop("k", "v")));
        inputBlocks.add(new InputBlock("b3", "DB", "DB", inputProps, 0.0, 0.0));
        SystemDesignDiagram diagram = SystemDesignDiagram.from(new Input(inputBlocks, List.of()));

        inputBlocks.clear();
        inputProps.clear();

        assertAll(
                () -> assertThat(diagram.blocks()).hasSize(3),
                () -> assertThat(diagram.blocks().get(2).props()).containsExactly(new Prop("k", "v")),
                () -> assertThatThrownBy(() -> diagram.blocks().clear())
                        .isInstanceOf(UnsupportedOperationException.class),
                () -> assertThatThrownBy(() -> diagram.connections().clear())
                        .isInstanceOf(UnsupportedOperationException.class),
                () -> assertThatThrownBy(() -> diagram.blocks().get(2).props().clear())
                        .isInstanceOf(UnsupportedOperationException.class)
        );
    }

    @Test
    void 내용이_같으면_같은_다이어그램이다() {
        SystemDesignDiagram diagram = SystemDesignDiagram.from(new Input(blocks(2), connections(1)));
        SystemDesignDiagram sameDiagram = SystemDesignDiagram.from(new Input(blocks(2), connections(1)));
        SystemDesignDiagram otherDiagram = SystemDesignDiagram.from(new Input(blocks(2), List.of()));

        assertAll(
                () -> assertThat(diagram).isEqualTo(sameDiagram),
                () -> assertThat(diagram).hasSameHashCodeAs(sameDiagram),
                () -> assertThat(diagram).isNotEqualTo(otherDiagram)
        );
    }

    @Test
    void 빈_다이어그램은_블록과_연결이_없다() {
        SystemDesignDiagram empty = SystemDesignDiagram.empty();

        assertAll(
                () -> assertThat(empty.blocks()).isEmpty(),
                () -> assertThat(empty.connections()).isEmpty(),
                () -> assertThat(empty.toJson()).isEqualTo("{\"blocks\":[],\"connections\":[]}"),
                () -> assertThat(empty).isEqualTo(SystemDesignDiagram.from(new Input(List.of(), List.of())))
        );
    }

    @Test
    void toJson은_정해진_필드만_정해진_모양으로_쓴다() {
        SystemDesignDiagram diagram = SystemDesignDiagram.from(new Input(
                List.of(new InputBlock("client", "CLIENT", "Client", List.of(prop("개수", "3")), 0.0, 80.5),
                        new InputBlock("api", "API_SERVER", "API Server", List.of(), 260.0, -80.0)),
                List.of(new InputConnection("c1", "client", "api", "HTTPS"), connection("c2", "api", "client"))));

        assertThat(diagram.toJson()).isEqualTo("""
                {"blocks":[{"id":"client","kind":"CLIENT","label":"Client","props":[{"key":"개수","value":"3"}],\
                "position":{"x":0.0,"y":80.5}},\
                {"id":"api","kind":"API_SERVER","label":"API Server","props":[],"position":{"x":260.0,"y":-80.0}}],\
                "connections":[{"id":"c1","source":"client","target":"api","label":"HTTPS"},\
                {"id":"c2","source":"api","target":"client"}]}""");
    }

    @Test
    void 연결_이름이_없으면_JSON에_label_키가_없다() {
        SystemDesignDiagram diagram = SystemDesignDiagram.from(new Input(blocks(2), List.of(
                new InputConnection("c1", "b1", "b2", null), new InputConnection("c2", "b2", "b1", ""))));

        assertThat(diagram.toJson())
                .contains("{\"id\":\"c1\",\"source\":\"b1\",\"target\":\"b2\"}")
                .contains("{\"id\":\"c2\",\"source\":\"b2\",\"target\":\"b1\",\"label\":\"\"}");
    }

    @Test
    void toJson과_fromJson이_왕복한다() {
        SystemDesignDiagram diagram = SystemDesignDiagram.from(new Input(
                List.of(new InputBlock("client", "CLIENT", "모바일 앱 📱", List.of(prop("\"따옴표\"", "역슬래시 \\")), 0.0,
                                80.5),
                        new InputBlock("api", "API_SERVER", "API Server", List.of(prop("개수", "3")), 1.0E20, -0.125),
                        new InputBlock("db", "DB", "MySQL", List.of(), 520.0, 80.0)),
                List.of(new InputConnection("c1", "client", "api", "HTTPS"), connection("c2", "api", "db"))));

        SystemDesignDiagram restored = SystemDesignDiagram.fromJson(diagram.toJson());

        assertAll(
                () -> assertThat(restored).isEqualTo(diagram),
                () -> assertThat(restored.toJson()).isEqualTo(diagram.toJson()),
                () -> assertThat(restored.connections().get(1).label()).isNull()
        );
    }

    @Test
    void fromJson은_모르는_필드를_버린다() {
        String json = """
                {"version":2,"blocks":[{"id":"api","kind":"API_SERVER","label":"API","selected":true,\
                "props":[{"key":"개수","value":"3","color":"red"}],"position":{"x":1,"y":2,"z":3},"width":120}],\
                "connections":[],"viewport":{"zoom":1.5}}""";

        SystemDesignDiagram diagram = SystemDesignDiagram.fromJson(json);

        assertThat(diagram.toJson()).isEqualTo("""
                {"blocks":[{"id":"api","kind":"API_SERVER","label":"API","props":[{"key":"개수","value":"3"}],\
                "position":{"x":1.0,"y":2.0}}],"connections":[]}""");
    }

    @Test
    void fromJson은_규칙을_어긴_JSON을_거절한다() {
        assertAll(
                () -> assertThatThrownBy(() -> SystemDesignDiagram.fromJson("""
                        {"blocks":[{"id":"api","kind":"server","label":"API","props":[],"position":{"x":0,"y":0}}],\
                        "connections":[]}"""))
                        .isInstanceOf(BadRequestException.class)
                        .hasMessage(BLOCK_KIND),
                () -> assertThatThrownBy(() -> SystemDesignDiagram.fromJson("""
                        {"blocks":[{"id":"api","kind":"API_SERVER","label":"API","props":[]}],"connections":[]}"""))
                        .isInstanceOf(BadRequestException.class)
                        .hasMessage(POSITION),
                () -> assertThatThrownBy(() -> SystemDesignDiagram.fromJson("""
                        {"blocks":[{"id":"api","kind":"API_SERVER","label":"API","position":{"x":0,"y":0}}],\
                        "connections":[]}"""))
                        .isInstanceOf(BadRequestException.class)
                        .hasMessage(FORMAT),
                () -> assertThatThrownBy(() -> SystemDesignDiagram.fromJson("{\"blocks\":[]}"))
                        .isInstanceOf(BadRequestException.class)
                        .hasMessage(FORMAT)
        );
    }

    @Test
    void fromJson은_깨진_JSON을_형식_오류로_거절한다() {
        assertAll(
                () -> assertThatThrownBy(() -> SystemDesignDiagram.fromJson("{\"blocks\":["))
                        .isInstanceOf(BadRequestException.class)
                        .hasMessage(FORMAT),
                () -> assertThatThrownBy(() -> SystemDesignDiagram.fromJson("{\"blocks\":{},\"connections\":[]}"))
                        .isInstanceOf(BadRequestException.class)
                        .hasMessage(FORMAT),
                () -> assertThatThrownBy(() -> SystemDesignDiagram.fromJson("[]"))
                        .isInstanceOf(BadRequestException.class)
                        .hasMessage(FORMAT),
                () -> assertThatThrownBy(() -> SystemDesignDiagram.fromJson("null"))
                        .isInstanceOf(BadRequestException.class)
                        .hasMessage(FORMAT),
                () -> assertThatThrownBy(() -> SystemDesignDiagram.fromJson(""))
                        .isInstanceOf(BadRequestException.class)
                        .hasMessage(FORMAT),
                () -> assertThatThrownBy(() -> SystemDesignDiagram.fromJson(null))
                        .isInstanceOf(BadRequestException.class)
                        .hasMessage(FORMAT)
        );
    }

    private static void assertAccepted(Input input) {
        assertThatCode(() -> SystemDesignDiagram.from(input)).doesNotThrowAnyException();
    }

    private static void assertRejected(Input input, String message) {
        assertThatThrownBy(() -> SystemDesignDiagram.from(input))
                .isInstanceOf(BadRequestException.class)
                .hasMessage(message);
    }

    private static Input blocksOf(InputBlock... blocks) {
        return new Input(Arrays.asList(blocks), List.of());
    }

    private static List<InputBlock> blocks(int count) {
        return IntStream.rangeClosed(1, count)
                .mapToObj(i -> block("b" + i))
                .toList();
    }

    private static InputBlock block(String id) {
        return new InputBlock(id, "API_SERVER", "API 서버", List.of(), 0.0, 0.0);
    }

    private static InputBlock blockWithKind(String kind) {
        return new InputBlock("b1", kind, "API 서버", List.of(), 0.0, 0.0);
    }

    private static InputBlock blockWithLabel(String label) {
        return new InputBlock("b1", "API_SERVER", label, List.of(), 0.0, 0.0);
    }

    private static InputBlock blockWithProps(List<InputProp> props) {
        return new InputBlock("b1", "API_SERVER", "API 서버", props, 0.0, 0.0);
    }

    private static InputBlock blockAt(Double x, Double y) {
        return new InputBlock("b1", "API_SERVER", "API 서버", List.of(), x, y);
    }

    private static List<InputProp> props(int count) {
        return IntStream.rangeClosed(1, count)
                .mapToObj(i -> prop("key" + i, "value" + i))
                .toList();
    }

    private static InputProp prop(String key, String value) {
        return new InputProp(key, value);
    }

    private static List<InputConnection> connections(int count) {
        return IntStream.rangeClosed(1, count)
                .mapToObj(i -> connection("c" + i, "b1", "b2"))
                .toList();
    }

    private static InputConnection connection(String id, String source, String target) {
        return new InputConnection(id, source, target, null);
    }

    private static InputConnection labeledConnection(String label) {
        return new InputConnection("c1", "b1", "b2", label);
    }
}
