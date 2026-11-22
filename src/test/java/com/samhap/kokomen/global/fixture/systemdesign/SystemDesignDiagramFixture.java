package com.samhap.kokomen.global.fixture.systemdesign;

import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignDiagram;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignDiagram.Input;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignDiagram.InputBlock;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignDiagram.InputConnection;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignDiagram.InputProp;
import java.util.List;

public final class SystemDesignDiagramFixture {

    // URL 단축기 문제의 시작 다이어그램(블록 3개, 연결 2개)을 저장 모양 그대로 적은 JSON
    private static final String URL_SHORTENER_STARTER_JSON = """
            {"blocks":[{"id":"client","kind":"CLIENT","label":"Client","props":[],"position":{"x":0,"y":80}},\
            {"id":"api","kind":"API_SERVER","label":"API Server","props":[],"position":{"x":260,"y":80}},\
            {"id":"db","kind":"DB","label":"DB","props":[],"position":{"x":520,"y":80}}],\
            "connections":[{"id":"c1","source":"client","target":"api","label":"HTTPS"},\
            {"id":"c2","source":"api","target":"db"}]}""";

    private SystemDesignDiagramFixture() {
    }

    public static SystemDesignDiagram urlShortenerStarter() {
        return SystemDesignDiagram.fromJson(URL_SHORTENER_STARTER_JSON);
    }

    // 답변과 함께 낼 법한 다이어그램. 속성, 한글·이모지, 라벨 없는 연결과 빈 라벨 연결, 음수·소수 좌표를 담는다.
    public static SystemDesignDiagram withCache() {
        return SystemDesignDiagram.from(new Input(
                List.of(new InputBlock("client", "CLIENT", "Client", List.of(), 0.0, 80.0),
                        new InputBlock("lb", "LOAD_BALANCER", "로드 밸런서", List.of(), 130.0, 80.0),
                        new InputBlock("api", "API_SERVER", "API Server", List.of(new InputProp("개수", "3")), 260.0,
                                80.0),
                        new InputBlock("cache", "CACHE", "Redis 캐시 ⚡",
                                List.of(new InputProp("TTL", "1일"), new InputProp("정책", "LRU")), 390.0, -40.5),
                        new InputBlock("db", "DB", "MySQL", List.of(new InputProp("복제본", "2")), 520.0, 80.0)),
                List.of(new InputConnection("c1", "client", "lb", "HTTPS"),
                        new InputConnection("c2", "lb", "api", null),
                        new InputConnection("c3", "api", "cache", "조회"),
                        new InputConnection("c4", "api", "db", ""))));
    }
}
