package com.samhap.kokomen.global.aop;

import static org.assertj.core.api.Assertions.assertThat;

import com.samhap.kokomen.category.domain.Category;
import com.samhap.kokomen.global.BaseTest;
import com.samhap.kokomen.global.dto.MemberAuth;
import com.samhap.kokomen.global.fixture.interview.RootQuestionFixtureBuilder;
import com.samhap.kokomen.global.fixture.member.MemberFixtureBuilder;
import com.samhap.kokomen.global.fixture.token.TokenFixtureBuilder;
import com.samhap.kokomen.interview.domain.InterviewMode;
import com.samhap.kokomen.interview.domain.RootQuestion;
import com.samhap.kokomen.interview.repository.RootQuestionRepository;
import com.samhap.kokomen.interview.service.InterviewStartFacadeService;
import com.samhap.kokomen.interview.service.dto.InterviewRequest;
import com.samhap.kokomen.member.domain.Member;
import com.samhap.kokomen.member.repository.MemberRepository;
import com.samhap.kokomen.token.domain.TokenType;
import com.samhap.kokomen.token.repository.TokenRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class RootQuestionMetricAspectTest extends BaseTest {

    @Autowired
    private InterviewStartFacadeService interviewStartFacadeService;
    @Autowired
    private MeterRegistry meterRegistry;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private RootQuestionRepository rootQuestionRepository;
    @Autowired
    private TokenRepository tokenRepository;

    // MeterRegistry는 캐시된 컨텍스트에서 테스트끼리 공유되고 DB 초기화 뒤 루트 질문 ID가 다시 쓰이므로 증가분만 본다.
    @Test
    void 면접을_시작하면_루트_질문의_면접_시작_카운터가_1_오른다() {
        Member member = saveMemberWithTokens();
        RootQuestion rootQuestion = rootQuestionRepository.save(
                RootQuestionFixtureBuilder.builder().category(Category.OPERATING_SYSTEM).questionOrder(1).build());
        InterviewRequest request = new InterviewRequest(Category.OPERATING_SYSTEM, 3, InterviewMode.TEXT, false);
        double before = readInterviewStartCount(rootQuestion);

        interviewStartFacadeService.startInterview(request, new MemberAuth(member.getId()));

        assertThat(readInterviewStartCount(rootQuestion)).isEqualTo(before + 1);
    }

    private double readInterviewStartCount(RootQuestion rootQuestion) {
        return meterRegistry.counter("root_question_interview_count",
                "root_question_id", String.valueOf(rootQuestion.getId())).count();
    }

    private Member saveMemberWithTokens() {
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        tokenRepository.save(
                TokenFixtureBuilder.builder().memberId(member.getId()).type(TokenType.FREE).tokenCount(20).build());
        tokenRepository.save(
                TokenFixtureBuilder.builder().memberId(member.getId()).type(TokenType.PAID).tokenCount(0).build());
        return member;
    }
}
