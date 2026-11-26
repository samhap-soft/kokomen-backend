package com.samhap.kokomen.global.json;

import static org.assertj.core.api.Assertions.assertThat;

import com.samhap.kokomen.global.BaseTest;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * JSON이 아닌 형식만 받겠다는 요청(Accept)에 실제 서버가 내는 응답을 고정한다. MVC 변환기 가운데 응답 본문(목록, ErrorResponse)을 쓸 수
 * 있는 것은 JSON 변환기뿐이라 쓸 변환기가 없다.
 * <ul>
 *     <li>핸들러가 정상 결과를 내면 406이다. 그 전에 GlobalExceptionHandler의 마지막 처리기가 ERROR 로그를 남기고 응답을 만들려다
 *     실패한다.</li>
 *     <li>예외가 나면 GlobalExceptionHandler가 오류 본문을 쓰지 못해 Spring 기본 처리로 넘어간다. Spring 예외는 원래 상태 코드로
 *     끝나고, 우리 예외(로그인 필요 등)는 처리할 곳이 없어 서블릿 컨테이너가 ERROR 로그를 남기고 500으로 끝낸다.</li>
 *     <li>Boot 오류 페이지도 JSON만 쓸 수 있어 본문은 모두 비어 있다. MockMvc는 오류 페이지로 넘기지 않으므로 실제 서버를 띄운다.</li>
 *     <li>관리 서버(actuator)도 WebConfig의 변환기 설정과 GlobalExceptionHandler를 받는다. actuator는 JSON만 내므로 406이다.</li>
 * </ul>
 * application/xml은 develop부터 이랬다. application/yaml은 전환하면서 MVC에서 Jackson 2 YAML 변환기를 빼(WebConfig) 같아진,
 * 전환 뒤에만 통과하는 경우다(develop은 YAML 본문으로 200·400·401을 썼고, 관리 서버는 500과 YAML 오류 본문을 썼다).
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = "management.server.port=0")
class NonJsonAcceptServerResponseTest extends BaseTest {

    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
    private static final String INTERVIEW_START_BODY =
            "{\"category\":\"DATABASE\",\"max_question_count\":3,\"mode\":\"TEXT\"}";

    @LocalServerPort
    private int port;

    @LocalManagementPort
    private int managementPort;

    @ParameterizedTest
    @ValueSource(strings = {"application/yaml", "application/xml"})
    void JSON이_아닌_형식만_받겠다는_요청은_본문_없이_끝나고_우리_예외는_500이다(String accept) throws Exception {
        HttpResponse<String> ranking = send(request(port, "/api/v1/members/ranking", accept).GET());
        HttpResponse<String> typeMismatch = send(request(port, "/api/v1/interviews/abc/result", accept).GET());
        HttpResponse<String> unauthorized = send(request(port, "/api/v1/interviews", accept)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(INTERVIEW_START_BODY)));

        assertEmptyResponse(ranking, 406);
        assertEmptyResponse(typeMismatch, 400);
        assertEmptyResponse(unauthorized, 500);
    }

    @ParameterizedTest
    @ValueSource(strings = {"application/yaml", "application/xml"})
    void 관리_서버도_JSON이_아닌_형식만_받겠다는_요청은_406이고_본문이_없다(String accept) throws Exception {
        HttpResponse<String> health = send(request(managementPort, "/actuator/health", accept).GET());

        assertEmptyResponse(health, 406);
    }

    private static HttpRequest.Builder request(int port, String path, String accept) {
        return HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).header("Accept", accept);
    }

    private static HttpResponse<String> send(HttpRequest.Builder request) throws Exception {
        return HTTP_CLIENT.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static void assertEmptyResponse(HttpResponse<String> response, int status) {
        assertThat(response.statusCode()).isEqualTo(status);
        assertThat(response.headers().firstValue("Content-Type")).isEmpty();
        assertThat(response.body()).isEmpty();
    }
}
