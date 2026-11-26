package com.samhap.kokomen.global.json;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.samhap.kokomen.category.domain.Category;
import com.samhap.kokomen.global.BaseTest;
import com.samhap.kokomen.interview.domain.InterviewMode;
import com.samhap.kokomen.interview.service.dto.InterviewRequest;
import com.samhap.kokomen.interview.service.dto.RootQuestionCustomInterviewRequest;
import com.samhap.kokomen.interview.service.dto.systemdesign.SystemDesignAnswerRequest;
import com.samhap.kokomen.interview.service.dto.systemdesign.SystemDesignDiagramRequest;
import com.samhap.kokomen.interview.service.dto.systemdesign.SystemDesignDiagramRequest.Block;
import com.samhap.kokomen.interview.service.dto.systemdesign.SystemDesignDiagramRequest.Connection;
import com.samhap.kokomen.interview.service.dto.systemdesign.SystemDesignDiagramRequest.Position;
import com.samhap.kokomen.interview.service.dto.systemdesign.SystemDesignDiagramRequest.Prop;
import com.samhap.kokomen.member.domain.survey.PrepStage;
import com.samhap.kokomen.member.service.dto.OnboardingSurveySubmitRequest;
import com.samhap.kokomen.token.domain.RefundReasonCode;
import com.samhap.kokomen.token.dto.TokenRefundRequest;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 요청 본문을 주입받는 매퍼(MVC가 요청을 읽는 매퍼)로 읽은 값을 고정한다. HTTP 상태와 본문은 HttpJsonCharacterizationTest가 본다.
 * snake_case 이름, 명시한 {@code @JsonProperty} 이름, 중복 키, 문자열·소수 강제 변환, null과 누락을 본다.
 */
class RequestJsonCharacterizationTest extends BaseTest {

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void snake_case_키로_읽고_camelCase_키는_버린다() throws Exception {
        assertAll(
                () -> assertThat(objectMapper.readValue("""
                        {"category":"DATABASE","max_question_count":3,"mode":"VOICE","include_live_coding":true}""",
                        InterviewRequest.class))
                        .isEqualTo(new InterviewRequest(Category.DATABASE, 3,
                                InterviewMode.VOICE, true)),
                () -> assertThat(objectMapper.readValue("""
                        {"category":"DATABASE","maxQuestionCount":3,"mode":"VOICE","includeLiveCoding":true}""",
                        InterviewRequest.class))
                        .isEqualTo(new InterviewRequest(Category.DATABASE, null,
                                InterviewMode.VOICE, false))
        );
    }

    @Test
    void 명시한_JsonProperty_이름은_snake_case로_바꾸지_않는다() throws Exception {
        assertAll(
                () -> assertThat(objectMapper.readValue(
                        "{\"rootQuestionId\":10,\"maxQuestionCount\":3,\"mode\":\"TEXT\"}",
                        RootQuestionCustomInterviewRequest.class))
                        .isEqualTo(new RootQuestionCustomInterviewRequest(10L, 3, InterviewMode.TEXT)),
                () -> assertThat(objectMapper.readValue(
                        "{\"root_question_id\":10,\"max_question_count\":3,\"mode\":\"TEXT\"}",
                        RootQuestionCustomInterviewRequest.class))
                        .isEqualTo(new RootQuestionCustomInterviewRequest(null, null, InterviewMode.TEXT))
        );
    }

    @Test
    void 중복_키는_마지막_값이고_숫자와_boolean은_문자열과_소수에서도_읽는다() throws Exception {
        assertAll(
                () -> assertThat(objectMapper.readValue("""
                        {"category":"NETWORK","category":"DATABASE","max_question_count":"5","mode":"TEXT",\
                        "include_live_coding":"false"}""", InterviewRequest.class))
                        .isEqualTo(new InterviewRequest(Category.DATABASE, 5,
                                InterviewMode.TEXT, false)),
                () -> assertThat(objectMapper.readValue("{\"max_question_count\":4.99}", InterviewRequest.class)
                        .maxQuestionCount()).isEqualTo(4),
                () -> assertThat(objectMapper.readValue("{\"max_question_count\":0}", InterviewRequest.class)
                        .maxQuestionCount()).isZero(),
                () -> assertThat(objectMapper.readValue("{}", InterviewRequest.class))
                        .isEqualTo(new InterviewRequest(null, null, null, false))
        );
    }

    @Test
    void 시스템_설계_답변의_다이어그램은_좌표_문자열과_정수를_실수로_읽고_null_원소를_남긴다() throws Exception {
        SystemDesignAnswerRequest request = objectMapper.readValue("""
                {"answer":"API 서버 뒤에 DB를 둡니다.","diagram":{"blocks":[\
                {"id":"a","kind":"DB","label":"A","props":[{"key":"k","value":"v","extra":1}],\
                "position":{"x":"12.5","y":3}},null],\
                "connections":[{"id":"c1","source":"a","target":"b"}],"viewport":{"zoom":2}}}""",
                SystemDesignAnswerRequest.class);

        assertThat(request).isEqualTo(new SystemDesignAnswerRequest("API 서버 뒤에 DB를 둡니다.",
                new SystemDesignDiagramRequest(
                        Arrays.asList(new Block("a", "DB", "A", List.of(new Prop("k", "v")), new Position(12.5, 3.0)),
                                null),
                        List.of(new Connection("c1", "a", "b", null)))));
    }

    @Test
    void enum_목록과_enum은_이름이나_순번으로_읽는다() throws Exception {
        assertAll(
                () -> assertThat(objectMapper.readValue(
                        "{\"prep_stages\":[\"BEGINNER\",1,null],\"goal_description\":\"목표 😀\"}",
                        OnboardingSurveySubmitRequest.class).prepStages())
                        .containsExactly(PrepStage.BEGINNER, PrepStage.JOB_SEEKING, null),
                () -> assertThat(objectMapper.readValue(
                        "{\"refund_reason_code\":\"OTHER\",\"refund_reason_text\":\"기타\"}", TokenRefundRequest.class))
                        .isEqualTo(new TokenRefundRequest(RefundReasonCode.OTHER, "기타"))
        );
    }
}
