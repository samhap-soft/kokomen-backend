package com.samhap.kokomen.global.json;

import static org.assertj.core.api.Assertions.assertThat;

import com.samhap.kokomen.global.BaseTest;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalManagementPort;

/**
 * 관리 포트(운영 8081)의 actuator JSON 모양을 고정한다. 운영처럼 별도 관리 서버로 띄워 실제 HTTP로 부른다.
 * actuator 응답은 앱 응답과 다른 매퍼로 쓸 수 있으므로 키 이름 규칙(camelCase/snake_case)과 순서를 본다.
 * 값이 실행마다 바뀌는 숫자는 정규식으로 비교한다.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = "management.server.port=0")
class ActuatorJsonCharacterizationTest extends BaseTest {

    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

    @LocalManagementPort
    private int managementPort;

    @Test
    void 헬스_응답은_상태와_구성요소_상태만_보인다() throws Exception {
        HttpResponse<String> response = get("/actuator/health");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Type"))
                .hasValue("application/vnd.spring-boot.actuator.v3+json");
        assertThat(response.body()).isEqualTo("""
                {"status":"UP","components":{"db":{"status":"UP"},"diskSpace":{"status":"UP"},\
                "livenessState":{"status":"UP"},"ping":{"status":"UP"},"readinessState":{"status":"UP"},\
                "redis":{"status":"UP"},"ssl":{"status":"UP"}},"groups":["liveness","readiness"]}""");
    }

    @Test
    void 메트릭_상세_응답의_키_이름과_순서를_지금처럼_쓴다() throws Exception {
        HttpResponse<String> response = get("/actuator/metrics/jvm.threads.daemon");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body().replaceAll("\"value\":[0-9.E]+", "\"value\":0")).isEqualTo("""
                {"name":"jvm.threads.daemon","description":"The current number of live daemon threads",\
                "baseUnit":"threads","measurements":[{"statistic":"VALUE","value":0}],"availableTags":[]}""");
    }

    @Test
    void 인포_응답은_빈_객체다() throws Exception {
        HttpResponse<String> response = get("/actuator/info");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo("{}");
    }

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + managementPort + path))
                .GET()
                .build();
        return HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
