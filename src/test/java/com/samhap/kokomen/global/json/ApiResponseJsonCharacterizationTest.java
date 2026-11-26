package com.samhap.kokomen.global.json;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.samhap.kokomen.admin.service.dto.AdminPaymentPageResponse;
import com.samhap.kokomen.admin.service.dto.AdminPaymentResponse;
import com.samhap.kokomen.admin.service.dto.AdminPaymentResultResponse;
import com.samhap.kokomen.answer.domain.AnswerMemoVisibility;
import com.samhap.kokomen.answer.domain.AnswerRank;
import com.samhap.kokomen.global.BaseTest;
import com.samhap.kokomen.global.dto.ErrorResponse;
import com.samhap.kokomen.interview.domain.InterviewMode;
import com.samhap.kokomen.interview.domain.InterviewState;
import com.samhap.kokomen.interview.domain.InterviewType;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignBlockKind;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignDifficulty;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignStage;
import com.samhap.kokomen.interview.service.dto.FeedbackResponse;
import com.samhap.kokomen.interview.service.dto.InterviewResultResponse;
import com.samhap.kokomen.interview.service.dto.InterviewSummaryResponse;
import com.samhap.kokomen.interview.service.dto.RootQuestionReferenceAnswer;
import com.samhap.kokomen.interview.service.dto.systemdesign.SystemDesignAnswerCompletedResponse;
import com.samhap.kokomen.interview.service.dto.systemdesign.SystemDesignDiagramResponse;
import com.samhap.kokomen.interview.service.dto.systemdesign.SystemDesignDiagramResponse.Block;
import com.samhap.kokomen.interview.service.dto.systemdesign.SystemDesignDiagramResponse.Connection;
import com.samhap.kokomen.interview.service.dto.systemdesign.SystemDesignDiagramResponse.Position;
import com.samhap.kokomen.interview.service.dto.systemdesign.SystemDesignDiagramResponse.Prop;
import com.samhap.kokomen.interview.service.dto.systemdesign.SystemDesignEvaluationPointResponse;
import com.samhap.kokomen.interview.service.dto.systemdesign.SystemDesignFeedbackResponse;
import com.samhap.kokomen.interview.service.dto.systemdesign.SystemDesignInterviewResponse;
import com.samhap.kokomen.interview.service.dto.systemdesign.SystemDesignQuestionAndAnswerResponse;
import com.samhap.kokomen.interview.service.dto.systemdesign.SystemDesignReportResponse;
import com.samhap.kokomen.interview.service.dto.systemdesign.SystemDesignRequirementQuestionResponse;
import com.samhap.kokomen.interview.service.dto.systemdesign.SystemDesignTopicResponse;
import com.samhap.kokomen.interview.tool.InterviewProceedState;
import com.samhap.kokomen.member.service.dto.RankingPageResponse;
import com.samhap.kokomen.member.service.dto.RankingResponse;
import com.samhap.kokomen.payment.domain.PaymentState;
import com.samhap.kokomen.payment.domain.ServiceType;
import com.samhap.kokomen.payment.domain.TosspaymentsStatus;
import com.samhap.kokomen.token.dto.RefundReasonResponse;
import com.samhap.kokomen.token.dto.TokenPurchaseResponse;
import com.samhap.kokomen.token.dto.TokenPurchaseResponses;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.mock.http.MockHttpOutputMessage;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;

/**
 * 대표 API 응답을 주입받는 매퍼(MVC가 응답을 쓰는 매퍼, SessionAndJsonWiringTest)로 직렬화한 문자열을 그대로 고정한다.
 * 필드 순서, snake_case 이름, null 생략과 {@code @JsonInclude(ALWAYS)}, enum 이름, LocalDateTime과 실수 표기를 한 글자씩 비교한다.
 * 값은 이 클래스의 고정 값으로 만들고, 엔티티를 거치지 않고 응답 record를 직접 만든다.
 *
 * <p>문자열 비교는 매퍼의 문자열 출력이다. MVC는 UTF-8 바이트로 쓰므로 이모지 같은 BMP 밖 문자는 서로게이트 쌍 이스케이프가 되고,
 * 그 바이트는 MVC JSON 변환기로 직접 쓴 결과로 따로 비교한다.
 */
class ApiResponseJsonCharacterizationTest extends BaseTest {

    private static final String DIAGRAM_JSON = """
            {"blocks":[{"id":"client","kind":"CLIENT","label":"모바일 앱","props":[{"key":"개수","value":"3"}],\
            "position":{"x":0.0,"y":80.5}},{"id":"api","kind":"API_SERVER","label":"API","props":[],\
            "position":{"x":1.0E20,"y":-0.125}}],\
            "connections":[{"id":"c1","source":"client","target":"api","label":"HTTPS"},\
            {"id":"c2","source":"api","target":"client"}]}""";

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RequestMappingHandlerAdapter handlerAdapter;

    @Test
    void 랭킹_페이지_응답은_필드_순서와_snake_case_이름을_그대로_쓴다() throws Exception {
        RankingPageResponse response = new RankingPageResponse(
                List.of(new RankingResponse(7L, "꼬꼬면", 1500, 12), new RankingResponse(3L, "면접왕", 900, 0)),
                0, 2L, 1, false);

        assertThat(objectMapper.writeValueAsString(response)).isEqualTo("""
                {"data":[{"id":7,"nickname":"꼬꼬면","score":1500,"finished_interview_count":12},\
                {"id":3,"nickname":"면접왕","score":900,"finished_interview_count":0}],\
                "current_page":0,"total_ranking_count":2,"total_pages":1,"has_next":false}""");
    }

    @Test
    void 인터뷰_결과_응답은_피드백_목록과_null_필드를_생략한_모양으로_쓴다() throws Exception {
        InterviewResultResponse response = new InterviewResultResponse(
                List.of(
                        new FeedbackResponse(11L, 101L, "프로세스와 스레드의 차이는?", "프로세스는 \"독립된\" 메모리를 가진다.",
                                AnswerRank.A, "핵심을 짚었다.\n예시를 더하자.", 3L, true, "제출한 메모", null,
                                AnswerMemoVisibility.PUBLIC),
                        new FeedbackResponse(12L, 102L, "컨텍스트 스위칭이란?", "😀 모르겠습니다 </script>", AnswerRank.F,
                                "다시 공부하자.", null, null, null, "임시 메모", AnswerMemoVisibility.PRIVATE)),
                "종합 피드백", 80, 25L, 4L, false, "꼬꼬면", 120L, 7L, null, null, InterviewMode.TEXT, true,
                List.of(new RootQuestionReferenceAnswer("면접왕", 55L, "모범 답안", AnswerRank.A)));

        assertThat(objectMapper.writeValueAsString(response)).isEqualTo("""
                {"feedbacks":[{"question_id":11,"answer_id":101,"question":"프로세스와 스레드의 차이는?",\
                "answer":"프로세스는 \\"독립된\\" 메모리를 가진다.","answer_rank":"A",\
                "answer_feedback":"핵심을 짚었다.\\n예시를 더하자.","answer_like_count":3,"answer_already_liked":true,\
                "submitted_answer_memo_content":"제출한 메모","answer_memo_visibility":"PUBLIC"},\
                {"question_id":12,"answer_id":102,"question":"컨텍스트 스위칭이란?","answer":"😀 모르겠습니다 </script>",\
                "answer_rank":"F","answer_feedback":"다시 공부하자.","temp_answer_memo_content":"임시 메모",\
                "answer_memo_visibility":"PRIVATE"}],\
                "total_feedback":"종합 피드백","total_score":80,"interview_view_count":25,"interview_like_count":4,\
                "interview_already_liked":false,"interviewee_nickname":"꼬꼬면","total_member_count":120,\
                "interviewee_rank":7,"interview_mode":"TEXT","include_live_coding":true,\
                "root_question_reference_answers":[{"nickname":"면접왕","interview_id":55,"answer_content":"모범 답안",\
                "answer_rank":"A"}]}""");
    }

    @Test
    void 인터뷰_결과_응답을_MVC_JSON_변환기로_쓴_바이트는_이모지를_서로게이트_쌍_이스케이프로_쓴다() throws Exception {
        InterviewResultResponse response = new InterviewResultResponse(
                List.of(new FeedbackResponse(12L, 102L, "컨텍스트 스위칭이란?", "😀 모르겠습니다 </script>", AnswerRank.F,
                        "다시 공부하자.", null, null, null, "임시 메모", AnswerMemoVisibility.PRIVATE)),
                "종합 피드백 🙂", 80, null, null, null, null, null, null, null, null, InterviewMode.VOICE, false, List.of());

        assertThat(writeWithMvcJsonConverter(response)).isEqualTo("""
                {"feedbacks":[{"question_id":12,"answer_id":102,"question":"컨텍스트 스위칭이란?",\
                "answer":"\\uD83D\\uDE00 모르겠습니다 </script>","answer_rank":"F","answer_feedback":"다시 공부하자.",\
                "temp_answer_memo_content":"임시 메모","answer_memo_visibility":"PRIVATE"}],\
                "total_feedback":"종합 피드백 \\uD83D\\uDE42","total_score":80,"interview_mode":"VOICE",\
                "include_live_coding":false,"root_question_reference_answers":[]}""".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void 시스템_설계_면접_응답은_정수_enum_다이어그램_실수와_ALWAYS_null_키를_쓴다() throws Exception {
        SystemDesignInterviewResponse requirements = new SystemDesignInterviewResponse(
                42L, InterviewType.SYSTEM_DESIGN, InterviewState.IN_PROGRESS, SystemDesignDifficulty.BASIC,
                new SystemDesignTopicResponse("URL 단축기", "긴 URL을 짧게 바꾼다."), SystemDesignStage.REQUIREMENTS,
                List.of(new SystemDesignRequirementQuestionResponse("하루 요청 수는?", "1억 건입니다.")),
                5, null, null, List.of(), 0, 6, diagram(), null);
        SystemDesignInterviewResponse highLevel = new SystemDesignInterviewResponse(
                43L, InterviewType.SYSTEM_DESIGN, InterviewState.IN_PROGRESS, SystemDesignDifficulty.BASIC,
                new SystemDesignTopicResponse("URL 단축기", "긴 URL을 짧게 바꾼다."), SystemDesignStage.HIGH_LEVEL,
                List.of(), 5, 2, "캐시를 어디에 두나요?",
                List.of(new SystemDesignQuestionAndAnswerResponse(1, SystemDesignStage.HIGH_LEVEL, "전체 구조를 그려 주세요.",
                        "API 서버 뒤에 DB를 둡니다.", diagram())),
                1, 6, new SystemDesignDiagramResponse(List.of(), List.of()), "LLM_PENDING");

        assertThat(objectMapper.writeValueAsString(requirements)).isEqualTo("""
                {"interview_id":42,"interview_type":"SYSTEM_DESIGN","interview_state":"IN_PROGRESS",\
                "difficulty":"BASIC","topic":{"title":"URL 단축기","description":"긴 URL을 짧게 바꾼다."},\
                "stage":"REQUIREMENTS","requirement_questions":[{"question":"하루 요청 수는?","answer":"1억 건입니다."}],\
                "max_requirement_question_count":5,"cur_question_id":null,"cur_question":null,\
                "prev_questions_and_answers":[],"cur_question_count":0,"max_question_count":6,\
                "diagram":%s}""".formatted(DIAGRAM_JSON));
        assertThat(objectMapper.writeValueAsString(highLevel)).isEqualTo("""
                {"interview_id":43,"interview_type":"SYSTEM_DESIGN","interview_state":"IN_PROGRESS",\
                "difficulty":"BASIC","topic":{"title":"URL 단축기","description":"긴 URL을 짧게 바꾼다."},\
                "stage":"HIGH_LEVEL","requirement_questions":[],"max_requirement_question_count":5,\
                "cur_question_id":2,"cur_question":"캐시를 어디에 두나요?",\
                "prev_questions_and_answers":[{"question_id":1,"stage":"HIGH_LEVEL","question":"전체 구조를 그려 주세요.",\
                "answer":"API 서버 뒤에 DB를 둡니다.","diagram":%s}],\
                "cur_question_count":1,"max_question_count":6,"diagram":{"blocks":[],"connections":[]},\
                "cur_answer_proceed_state":"LLM_PENDING"}""".formatted(DIAGRAM_JSON));
    }

    @Test
    void 시스템_설계_마지막_답변_결과는_다음_질문이_없어도_null_키를_쓴다() throws Exception {
        SystemDesignAnswerCompletedResponse response = new SystemDesignAnswerCompletedResponse(
                InterviewProceedState.COMPLETED, InterviewState.FINISHED, SystemDesignStage.DEEP_DIVE, AnswerRank.B,
                null, null);

        assertThat(objectMapper.writeValueAsString(response)).isEqualTo("""
                {"proceed_state":"COMPLETED","interview_state":"FINISHED","stage":"DEEP_DIVE","cur_answer_rank":"B",\
                "next_question_id":null,"next_question":null}""");
    }

    @Test
    void 시스템_설계_리포트는_is_met_이름과_null_생략을_그대로_쓴다() throws Exception {
        SystemDesignReportResponse response = new SystemDesignReportResponse(
                42L, new SystemDesignTopicResponse("URL 단축기", "긴 URL을 짧게 바꾼다."), SystemDesignDifficulty.BASIC,
                72, "전체적으로 좋았다.", null,
                List.of(new SystemDesignEvaluationPointResponse("캐시로 읽기 부하를 줄인다.", true),
                        new SystemDesignEvaluationPointResponse("단축 키 충돌을 막는다.", false)),
                List.of("만료 정책"), List.of(),
                List.of(new SystemDesignFeedbackResponse(1, SystemDesignStage.HIGH_LEVEL, "전체 구조를 그려 주세요.",
                        "API 서버 뒤에 DB를 둡니다.", new SystemDesignDiagramResponse(List.of(), List.of()), AnswerRank.C,
                        "캐시가 빠졌다.")),
                diagram(), 1572, null);

        assertThat(objectMapper.writeValueAsString(response)).isEqualTo("""
                {"interview_id":42,"topic":{"title":"URL 단축기","description":"긴 URL을 짧게 바꾼다."},\
                "difficulty":"BASIC","total_score":72,"total_feedback":"전체적으로 좋았다.",\
                "evaluation_points":[{"description":"캐시로 읽기 부하를 줄인다.","is_met":true},\
                {"description":"단축 키 충돌을 막는다.","is_met":false}],"missed_requirements":["만료 정책"],\
                "requirement_questions":[],"feedbacks":[{"question_id":1,"stage":"HIGH_LEVEL",\
                "question":"전체 구조를 그려 주세요.","answer":"API 서버 뒤에 DB를 둡니다.",\
                "diagram":{"blocks":[],"connections":[]},"answer_rank":"C","answer_feedback":"캐시가 빠졌다."}],\
                "final_diagram":%s,"user_cur_score":1572}""".formatted(DIAGRAM_JSON));
    }

    @Test
    void 인터뷰_목록_응답은_LocalDateTime을_ISO_문자열로_쓰고_0인_초와_소수_초를_지금처럼_쓴다() throws Exception {
        List<InterviewSummaryResponse> response = List.of(
                new InterviewSummaryResponse(5L, InterviewType.CATEGORY_BASED, InterviewState.FINISHED,
                        InterviewMode.VOICE, "DATABASE", LocalDateTime.of(2026, 11, 26, 10, 0, 0, 123456000),
                        "트랜잭션 격리 수준은?", 3, 3, 85, 10L, 2L, null, 1, false),
                new InterviewSummaryResponse(6L, InterviewType.SYSTEM_DESIGN, InterviewState.IN_PROGRESS,
                        InterviewMode.TEXT, null, LocalDateTime.of(2026, 1, 2, 3, 4), null, 6, 0, null, null, null,
                        true, 0, true));

        assertThat(objectMapper.writeValueAsString(response)).isEqualTo("""
                [{"interview_id":5,"interview_type":"CATEGORY_BASED","interview_state":"FINISHED",\
                "interview_mode":"VOICE","interview_category":"DATABASE","created_at":"2026-11-26T10:00:00.123456",\
                "root_question":"트랜잭션 격리 수준은?","max_question_count":3,"cur_answer_count":3,"score":85,\
                "interview_view_count":10,"interview_like_count":2,"submitted_answer_memo_count":1,\
                "has_temp_answer_memo":false},\
                {"interview_id":6,"interview_type":"SYSTEM_DESIGN","interview_state":"IN_PROGRESS",\
                "interview_mode":"TEXT","created_at":"2026-01-02T03:04:00","max_question_count":6,\
                "cur_answer_count":0,"interview_already_liked":true,"submitted_answer_memo_count":0,\
                "has_temp_answer_memo":true}]""");
    }

    @Test
    void 토큰_구매_목록_응답은_필드_순서와_null_생략을_그대로_쓴다() throws Exception {
        TokenPurchaseResponses response = new TokenPurchaseResponses(List.of(
                new TokenPurchaseResponse(9L, 1000L, "토큰 10개", "TOKEN_10", 10, 4, "사용 중", "간편결제", "토스페이"),
                new TokenPurchaseResponse(8L, 500L, "토큰 5개", "TOKEN_5", 5, 0, "환불 완료", "카드", null)), 3L);

        assertThat(objectMapper.writeValueAsString(response)).isEqualTo("""
                {"token_purchases":[{"id":9,"price":1000,"order_name":"토큰 10개","product_name":"TOKEN_10",\
                "count":10,"remaining_count":4,"state":"사용 중","payment_method":"간편결제","easy_pay_provider":"토스페이"},\
                {"id":8,"price":500,"order_name":"토큰 5개","product_name":"TOKEN_5","count":5,"remaining_count":0,\
                "state":"환불 완료","payment_method":"카드"}],"total_page_count":3}""");
    }

    @Test
    void 환불_사유_목록은_primitive_boolean을_그대로_쓴다() throws Exception {
        List<RefundReasonResponse> response = List.of(
                new RefundReasonResponse("CHANGE_OF_MIND", "단순 변심", false),
                new RefundReasonResponse("OTHER", "기타", true));

        assertThat(objectMapper.writeValueAsString(response)).isEqualTo("""
                [{"code":"CHANGE_OF_MIND","message":"단순 변심","requires_reason_text":false},\
                {"code":"OTHER","message":"기타","requires_reason_text":true}]""");
    }

    @Test
    void 관리자_결제_목록은_날짜와_JSON_문자열_metadata와_URL을_지금처럼_쓴다() throws Exception {
        AdminPaymentPageResponse response = new AdminPaymentPageResponse(List.of(new AdminPaymentResponse(
                1L, "pk_1", 2L, "order-1", "토큰 10개", 1000L,
                "{\"product_name\":\"TOKEN_10\",\"count\":10,\"unit_price\":100}", PaymentState.COMPLETED,
                ServiceType.INTERVIEW, LocalDateTime.of(2026, 11, 26, 10, 0, 0),
                LocalDateTime.of(2026, 11, 26, 10, 0, 5, 500000000),
                new AdminPaymentResultResponse("간편결제", 1000L, TosspaymentsStatus.DONE,
                        LocalDateTime.of(2026, 11, 26, 9, 59, 58), LocalDateTime.of(2026, 11, 26, 10, 0, 1, 1000),
                        null, null, null,
                        "https://dashboard.tosspayments.com/receipt/redirection?transactionId=tx_1&ref=PX",
                        "토스페이"))),
                0, 1L, 1, false);

        assertThat(objectMapper.writeValueAsString(response)).isEqualTo("""
                {"data":[{"id":1,"payment_key":"pk_1","member_id":2,"order_id":"order-1","order_name":"토큰 10개",\
                "total_amount":1000,\
                "metadata":"{\\"product_name\\":\\"TOKEN_10\\",\\"count\\":10,\\"unit_price\\":100}",\
                "state":"COMPLETED","service_type":"INTERVIEW","created_at":"2026-11-26T10:00:00",\
                "updated_at":"2026-11-26T10:00:05.5","result":{"method":"간편결제","balance_amount":1000,\
                "tosspayments_status":"DONE","requested_at":"2026-11-26T09:59:58",\
                "approved_at":"2026-11-26T10:00:01.000001",\
                "receipt_url":"https://dashboard.tosspayments.com/receipt/redirection?transactionId=tx_1&ref=PX",\
                "easy_pay_provider":"토스페이"}}],"current_page":0,"total_count":1,"total_pages":1,"has_next":false}""");
    }

    @Test
    void 에러_응답은_message_하나만_쓰고_null이면_빈_객체다() throws Exception {
        assertThat(objectMapper.writeValueAsString(new ErrorResponse("잘못된 요청 형식입니다. JSON 형식을 확인해주세요.")))
                .isEqualTo("{\"message\":\"잘못된 요청 형식입니다. JSON 형식을 확인해주세요.\"}");
        assertThat(objectMapper.writeValueAsString(new ErrorResponse(null))).isEqualTo("{}");
    }

    // MVC가 application/json 응답에 고르는 변환기(목록에서 처음으로 쓸 수 있다고 답하는 변환기)로 바이트를 쓴다.
    @SuppressWarnings("unchecked")
    private byte[] writeWithMvcJsonConverter(Object value) throws Exception {
        HttpMessageConverter<Object> converter = (HttpMessageConverter<Object>) handlerAdapter.getMessageConverters()
                .stream()
                .filter(candidate -> candidate.canWrite(value.getClass(), MediaType.APPLICATION_JSON))
                .findFirst()
                .orElseThrow();
        MockHttpOutputMessage outputMessage = new MockHttpOutputMessage();
        converter.write(value, MediaType.APPLICATION_JSON, outputMessage);
        return outputMessage.getBodyAsBytes();
    }

    private static SystemDesignDiagramResponse diagram() {
        return new SystemDesignDiagramResponse(
                List.of(new Block("client", SystemDesignBlockKind.CLIENT, "모바일 앱", List.of(new Prop("개수", "3")),
                                new Position(0.0, 80.5)),
                        new Block("api", SystemDesignBlockKind.API_SERVER, "API", List.of(),
                                new Position(1.0E20, -0.125))),
                List.of(new Connection("c1", "client", "api", "HTTPS"), new Connection("c2", "api", "client", null)));
    }
}
