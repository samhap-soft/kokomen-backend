package com.samhap.kokomen.global.json;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.samhap.kokomen.global.BaseControllerTest;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * MVC가 요청 본문을 읽고 응답을 쓰는 결과를 HTTP 수준에서 고정한다. 응답은 받은 바이트를 그대로 비교한다.
 * <ul>
 *     <li>면접 시작(POST /api/v1/interviews)은 본문을 인증보다 먼저 읽는다. 본문을 못 읽으면 400이고, 읽으면 비로그인이라 401이다.
 *     그래서 401은 "본문을 문제없이 읽었다"는 뜻이다.</li>
 *     <li>토스 웹훅은 인증 없이 본문을 읽고, 등록되지 않은 paymentKey면 아무것도 하지 않고 200이다.</li>
 *     <li>UTF-8 응답에서 BMP 밖 문자(이모지)는 서로게이트 쌍 이스케이프(\\uD83D\\uDE00)로 나간다.</li>
 * </ul>
 */
class HttpJsonCharacterizationTest extends BaseControllerTest {

    private static final String INTERVIEW_START_URL = "/api/v1/interviews";
    private static final String WEBHOOK_URL = "/api/v1/webhooks/tosspayments";
    private static final String UNAUTHORIZED_BODY = "{\"message\":\"로그인이 필요합니다\"}";
    private static final String UNREADABLE_BODY = "{\"message\":\"잘못된 요청 형식입니다. JSON 형식을 확인해주세요.\"}";
    private static final String VALID_INTERVIEW_FIELDS =
            "\"category\":\"DATABASE\",\"max_question_count\":3,\"mode\":\"TEXT\"";

    @Test
    void JSON_응답의_Content_Type과_이모지를_포함한_본문_바이트를_지금처럼_쓴다() throws Exception {
        MockHttpServletResponse response = mockMvc.perform(get("/api/v1/interviews/{interviewId}/result", "😀<꼬>&"))
                .andReturn().getResponse();

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getHeader(HttpHeaders.CONTENT_TYPE)).isEqualTo("application/json");
        assertThat(response.getContentAsByteArray()).isEqualTo(
                "{\"message\":\"'interviewId'에 유효하지 않은 값이 전달되었습니다. (전달된 값: '\\uD83D\\uDE00<꼬>&')\"}"
                        .getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void 잘못된_enum_값은_400과_필드_이름과_받은_값을_담은_문구다() throws Exception {
        assertResponse(postInterview("{\"category\":\"NOPE\",\"max_question_count\":3,\"mode\":\"TEXT\"}"), 400,
                "{\"message\":\"JSON 파싱 오류: 'category' 필드에 유효하지 않은 값이 전달되었습니다. (전달된 값: 'NOPE')\"}");
        assertResponse(postInterview("{\"category\":\"database\",\"max_question_count\":3,\"mode\":\"TEXT\"}"), 400,
                "{\"message\":\"JSON 파싱 오류: 'category' 필드에 유효하지 않은 값이 전달되었습니다. (전달된 값: 'database')\"}");
        assertResponse(postInterview("{\"category\":\"\",\"max_question_count\":3,\"mode\":\"TEXT\"}"), 400,
                "{\"message\":\"JSON 파싱 오류: 'category' 필드에 유효하지 않은 값이 전달되었습니다. (전달된 값: '')\"}");
    }

    @Test
    void 숫자나_boolean_자리에_잘못된_문자열이면_400과_필드_이름과_받은_값을_담은_문구다() throws Exception {
        assertResponse(postInterview(
                        "{\"category\":\"DATABASE\",\"max_question_count\":\"abc\",\"mode\":\"TEXT\"}"), 400,
                "{\"message\":\"JSON 파싱 오류: 'max_question_count' 필드에 유효하지 않은 값이 전달되었습니다. (전달된 값: 'abc')\"}");
        assertResponse(postInterview("{" + VALID_INTERVIEW_FIELDS + ",\"include_live_coding\":\"maybe\"}"), 400,
                "{\"message\":\"JSON 파싱 오류: 'include_live_coding' 필드에 유효하지 않은 값이 전달되었습니다. (전달된 값: 'maybe')\"}");
    }

    @Test
    void 정수_범위를_넘는_값은_400과_고정_문구다() throws Exception {
        assertResponse(postInterview("{\"category\":\"DATABASE\",\"max_question_count\":2147483648,\"mode\":\"TEXT\"}"),
                400, UNREADABLE_BODY);
    }

    @Test
    void 숫자_문자열과_소수와_boolean_문자열은_지금처럼_받아들인다() throws Exception {
        assertResponse(postInterview("{\"category\":\"DATABASE\",\"max_question_count\":\"3\",\"mode\":\"TEXT\"}"), 401,
                UNAUTHORIZED_BODY);
        assertResponse(postInterview("{\"category\":\"DATABASE\",\"max_question_count\":3.9,\"mode\":\"TEXT\"}"), 401,
                UNAUTHORIZED_BODY);
        assertResponse(postInterview("{" + VALID_INTERVIEW_FIELDS + ",\"include_live_coding\":\"true\"}"), 401,
                UNAUTHORIZED_BODY);
    }

    @Test
    void 읽을_수_없는_본문은_400과_고정_문구다() throws Exception {
        assertResponse(postInterview("{\"category\":\"DATABASE\","), 400, UNREADABLE_BODY);
        assertResponse(postInterview("[" + "{" + VALID_INTERVIEW_FIELDS + "}" + "]"), 400, UNREADABLE_BODY);
        assertResponse(postInterview(""), 400, UNREADABLE_BODY);
        assertResponse(postInterview(
                        "{\"category\":{\"name\":\"DATABASE\"},\"max_question_count\":3,\"mode\":\"TEXT\"}"),
                400, UNREADABLE_BODY);
    }

    @Test
    void 본문이_JSON_null이나_빈_문자열_하나면_지금처럼_응답한다() throws Exception {
        assertResponse(postInterview("null"), 400, UNREADABLE_BODY);
        assertResponse(postInterview("\"\""), 400, "");
    }

    @Test
    void 알_수_없는_필드와_본문_뒤에_붙은_내용은_무시하고_읽는다() throws Exception {
        assertResponse(postInterview(
                        "{" + VALID_INTERVIEW_FIELDS + ",\"unknown\":{\"nested\":[1,2,3]},\"camelCase\":1}"),
                401, UNAUTHORIZED_BODY);
        assertResponse(postInterview("{" + VALID_INTERVIEW_FIELDS + "} trailing"), 401, UNAUTHORIZED_BODY);
        assertResponse(postInterview("{" + VALID_INTERVIEW_FIELDS + "}{\"category\":\"NOPE\"}"), 401,
                UNAUTHORIZED_BODY);
    }

    @Test
    void 중복_키는_앞의_값도_읽으므로_앞의_값이_잘못되면_400이다() throws Exception {
        assertResponse(postInterview("{\"category\":\"NETWORK\"," + VALID_INTERVIEW_FIELDS + "}"), 401,
                UNAUTHORIZED_BODY);
        assertResponse(postInterview("{\"category\":\"NOPE\"," + VALID_INTERVIEW_FIELDS + "}"), 400,
                "{\"message\":\"JSON 파싱 오류: 'category' 필드에 유효하지 않은 값이 전달되었습니다. (전달된 값: 'NOPE')\"}");
    }

    @Test
    void 본문_중첩_깊이는_1000까지_읽고_넘으면_400이다() throws Exception {
        assertResponse(postInterview("{" + VALID_INTERVIEW_FIELDS + ",\"unknown\":" + nested(999) + "}"), 401,
                UNAUTHORIZED_BODY);
        assertResponse(postInterview("{" + VALID_INTERVIEW_FIELDS + ",\"unknown\":" + nested(1000) + "}"), 400,
                UNREADABLE_BODY);
    }

    @Test
    void 본문_문자열_값은_2천만_자를_넘으면_읽지_못해_400이다() throws Exception {
        String tooLong = "a".repeat(20_000_001);

        assertResponse(mockMvc.perform(post("/api/v1/answers/{answerId}/memo", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"visibility\":\"PUBLIC\",\"content\":\"" + tooLong + "\"}"))
                .andReturn().getResponse(), 400, UNREADABLE_BODY);
    }

    @Test
    void 토스_웹훅은_모르는_필드와_null_primitive를_받아들이고_등록되지_않은_결제면_200이다() throws Exception {
        MockHttpServletResponse response = postWebhook("""
                {"eventType":"PAYMENT_STATUS_CHANGED","createdAt":"2026-11-26T10:00:00.000000","unknownTop":1,\
                "data":{"paymentKey":"unknown-key","orderId":"order-1","status":"DONE","isPartialCancelable":null,\
                "requestedAt":"2026-11-26T10:00:00+09:00","approvedAt":"2026-11-26T01:00:05Z","unknownData":[1]}}""");

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentAsByteArray()).isEmpty();
    }

    @Test
    void 토스_웹훅의_잘못된_enum과_숫자는_바깥_필드_이름을_담은_400이다() throws Exception {
        assertResponse(postWebhookData("\"paymentKey\":\"pk\",\"status\":\"NOPE\""), 400,
                "{\"message\":\"JSON 파싱 오류: 'data' 필드에 유효하지 않은 값이 전달되었습니다. (전달된 값: 'NOPE')\"}");
        assertResponse(postWebhookData("\"paymentKey\":\"pk\",\"totalAmount\":\"abc\""), 400,
                "{\"message\":\"JSON 파싱 오류: 'data' 필드에 유효하지 않은 값이 전달되었습니다. (전달된 값: 'abc')\"}");
        assertResponse(postWebhookData("\"paymentKey\":\"pk\",\"status\":\"😀\""), 400,
                "{\"message\":\"JSON 파싱 오류: 'data' 필드에 유효하지 않은 값이 전달되었습니다. (전달된 값: '\\uD83D\\uDE00')\"}");
    }

    @Test
    void 토스_웹훅의_잘못된_날짜_형식은_400과_고정_문구다() throws Exception {
        assertResponse(postWebhookData("\"paymentKey\":\"pk\",\"requestedAt\":\"2026/11/26 10:00\""), 400,
                UNREADABLE_BODY);
        assertResponse(postWebhookData("\"paymentKey\":\"pk\",\"requestedAt\":\"2026-11-26T10:00:00\""), 400,
                UNREADABLE_BODY);
    }

    private MockHttpServletResponse postInterview(String body) throws Exception {
        return mockMvc.perform(post(INTERVIEW_START_URL).contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn().getResponse();
    }

    private MockHttpServletResponse postWebhookData(String dataFields) throws Exception {
        return postWebhook("{\"eventType\":\"PAYMENT_STATUS_CHANGED\",\"data\":{" + dataFields + "}}");
    }

    private MockHttpServletResponse postWebhook(String body) throws Exception {
        MockHttpServletRequestBuilder request = post(WEBHOOK_URL)
                .header("tosspayments-webhook-transmission-time", "2026-11-26T10:00:00+09:00")
                .header("tosspayments-webhook-transmission-id", "transmission-1")
                .header("tosspayments-webhook-transmission-retried-count", "0")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
        return mockMvc.perform(request).andReturn().getResponse();
    }

    private static void assertResponse(MockHttpServletResponse response, int status, String body) {
        assertThat(response.getStatus()).isEqualTo(status);
        assertThat(response.getContentAsByteArray()).isEqualTo(body.getBytes(StandardCharsets.UTF_8));
    }

    // 배열을 depth겹으로 감싼다. 바깥 객체까지 더하면 문서 전체의 중첩 깊이는 depth + 1이다.
    private static String nested(int depth) {
        return "[".repeat(depth) + "]".repeat(depth);
    }
}
