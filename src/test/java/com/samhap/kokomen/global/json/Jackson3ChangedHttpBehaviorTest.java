package com.samhap.kokomen.global.json;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.samhap.kokomen.global.BaseControllerTest;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Jackson 3 전환으로 바뀐 동작 가운데 받아들이기로 한 것을 HTTP 수준에서 고정한다. global.json의 특성 테스트와 달리 이 클래스는
 * Jackson 3에서만 통과한다(develop의 Jackson 2에서는 실패한다).
 * <ul>
 *     <li>Jackson 2는 record를 읽다가 생성자 인자를 모두 본 순간 객체를 먼저 만들었다. 그 뒤에 같은 키가 다시 오면 넣을 곳이 없어
 *     500(목록 필드는 400)이었고, 그 뒤의 모르는 필드는 값을 끝까지 읽지 않고 건너뛰었다. Jackson 3은 객체가 끝날 때까지 모은 뒤
 *     만든다. 같은 키는 마지막 값이 이기고, 모르는 필드의 문자열도 끝까지 읽으므로 길이 제한(2천만 자)을 넘으면 400이다.</li>
 *     <li>면접 시작, 온보딩 설문, 프로필 수정은 본문을 인증보다 먼저 읽고 검증한다. 401은 본문을 읽고 검증까지 통과했다는 뜻이고,
 *     검증 문구가 나오면 어느 값으로 객체를 만들었는지 알 수 있다.</li>
 * </ul>
 */
class Jackson3ChangedHttpBehaviorTest extends BaseControllerTest {

    private static final String INTERVIEW_START_URL = "/api/v1/interviews";
    private static final String ONBOARDING_SURVEY_URL = "/api/v1/members/me/onboarding-survey";
    private static final String PROFILE_URL = "/api/v1/members/me/profile";
    private static final String UNAUTHORIZED_BODY = "{\"message\":\"로그인이 필요합니다\"}";
    private static final String UNREADABLE_BODY = "{\"message\":\"잘못된 요청 형식입니다. JSON 형식을 확인해주세요.\"}";
    private static final String ALL_INTERVIEW_FIELDS =
            "\"category\":\"DATABASE\",\"max_question_count\":3,\"mode\":\"TEXT\",\"include_live_coding\":false";
    private static final String SURVEY_FIELDS_EXCEPT_PREP_STAGES = "\"career_goal\":\"BACKEND\","
            + "\"tech_topics\":[\"DATABASE\"],\"target_company_type\":\"BIG_TECH\",\"interview_experience\":\"NONE\","
            + "\"weak_points\":[\"CS\"],\"goal_description\":\"목표\"";

    // Jackson 3 전환으로 바뀐 동작: develop(Jackson 2)에서는 넷 다 500 "서버에 문제가 발생하였습니다."였다.
    @Test
    void 모든_필드_뒤에_같은_키가_다시_오면_마지막_값으로_읽는다() throws Exception {
        assertResponse(postJson(INTERVIEW_START_URL, "{" + ALL_INTERVIEW_FIELDS + ",\"mode\":\"VOICE\"}"), 401,
                UNAUTHORIZED_BODY);
        assertResponse(postJson(INTERVIEW_START_URL, "{" + ALL_INTERVIEW_FIELDS + ",\"max_question_count\":null}"), 400,
                "{\"message\":\"max_question_count는 null일 수 없습니다.\"}");
        assertResponse(postJson(INTERVIEW_START_URL, "{" + ALL_INTERVIEW_FIELDS + ",\"category\":\"NOPE\"}"), 400,
                "{\"message\":\"JSON 파싱 오류: 'category' 필드에 유효하지 않은 값이 전달되었습니다. (전달된 값: 'NOPE')\"}");
        assertResponse(request(patch(PROFILE_URL), "{\"nickname\":\"\",\"nickname\":\"꼬꼬면\"}"), 401,
                UNAUTHORIZED_BODY);
    }

    // Jackson 3 전환으로 바뀐 동작: develop(Jackson 2)에서는 둘 다 400 "잘못된 요청 형식입니다. JSON 형식을 확인해주세요."였다.
    @Test
    void 모든_필드_뒤에_목록_필드가_다시_오면_마지막_목록으로_읽는다() throws Exception {
        assertResponse(postJson(ONBOARDING_SURVEY_URL,
                        "{\"prep_stages\":[]," + SURVEY_FIELDS_EXCEPT_PREP_STAGES + ",\"prep_stages\":[\"BEGINNER\"]}"),
                401, UNAUTHORIZED_BODY);
        assertResponse(postJson(ONBOARDING_SURVEY_URL,
                        "{\"prep_stages\":[\"BEGINNER\"]," + SURVEY_FIELDS_EXCEPT_PREP_STAGES + ",\"prep_stages\":[]}"),
                400, "{\"message\":\"prep_stages는 최소 1개를 선택해야 합니다.\"}");
    }

    // Jackson 3 전환으로 바뀐 동작: develop(Jackson 2)은 2천만 자를 넘는 경우도 건너뛰고 받아들였다(401).
    // 2천만 자는 Jackson 2 값으로 둔 문자열 길이 제한(Jackson2CompatibleJsonMapper.MAX_STRING_LENGTH)이다.
    @Test
    void 모든_필드_뒤의_모르는_필드도_문자열을_끝까지_읽어_2천만_자를_넘으면_400이다() throws Exception {
        assertResponse(postJson(INTERVIEW_START_URL,
                        "{" + ALL_INTERVIEW_FIELDS + ",\"unknown\":\"" + "a".repeat(20_000_000) + "\"}"),
                401, UNAUTHORIZED_BODY);
        assertResponse(postJson(INTERVIEW_START_URL,
                        "{" + ALL_INTERVIEW_FIELDS + ",\"unknown\":\"" + "a".repeat(20_000_001) + "\"}"),
                400, UNREADABLE_BODY);
    }

    private MockHttpServletResponse postJson(String url, String body) throws Exception {
        return request(post(url), body);
    }

    private MockHttpServletResponse request(MockHttpServletRequestBuilder builder, String body) throws Exception {
        return mockMvc.perform(builder.contentType(MediaType.APPLICATION_JSON).content(body)).andReturn().getResponse();
    }

    private static void assertResponse(MockHttpServletResponse response, int status, String body) {
        assertThat(response.getStatus()).isEqualTo(status);
        assertThat(response.getContentAsByteArray()).isEqualTo(body.getBytes(StandardCharsets.UTF_8));
    }
}
