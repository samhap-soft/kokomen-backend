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
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * 예외 처리기가 응답을 만들지 못하면 서블릿 컨테이너가 Boot 오류 페이지(/error)로 넘기고, 그 JSON은 앱 매퍼가 쓴다.
 * MockMvc는 /error로 넘기지 않으므로 실제 서버를 띄워 부른다. 본문이 JSON 빈 문자열이면 예외 처리기가 경로 없는
 * InvalidFormatException에서 실패해 이 경로로 간다. 시각은 java.util.Date라 UTC 오프셋 표기(+00:00)가 그대로인지 본다.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = "management.server.port=0")
class ErrorPageJsonCharacterizationTest extends BaseTest {

    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

    @LocalServerPort
    private int port;

    @Test
    void 예외_처리기가_응답을_만들지_못한_400은_Boot_오류_JSON이고_시각은_UTC_오프셋으로_쓴다() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/interviews"))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("\"\""))
                .build();

        HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.headers().firstValue("Content-Type")).hasValue("application/json");
        assertThat(response.body()).matches(
                "\\{\"timestamp\":\"\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}\\+00:00\","
                        + "\"status\":400,\"error\":\"Bad Request\",\"path\":\"/api/v1/interviews\"}");
    }
}
