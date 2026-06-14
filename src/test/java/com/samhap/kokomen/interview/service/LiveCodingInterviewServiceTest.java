package com.samhap.kokomen.interview.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.samhap.kokomen.category.domain.Category;
import com.samhap.kokomen.global.BaseTest;
import com.samhap.kokomen.global.dto.MemberAuth;
import com.samhap.kokomen.global.exception.BadRequestException;
import com.samhap.kokomen.global.fixture.interview.RootQuestionFixtureBuilder;
import com.samhap.kokomen.global.fixture.member.MemberFixtureBuilder;
import com.samhap.kokomen.global.fixture.token.TokenFixtureBuilder;
import com.samhap.kokomen.interview.domain.Interview;
import com.samhap.kokomen.interview.domain.InterviewMode;
import com.samhap.kokomen.interview.domain.InterviewType;
import com.samhap.kokomen.interview.domain.Question;
import com.samhap.kokomen.interview.domain.RootQuestion;
import com.samhap.kokomen.interview.domain.RootQuestionType;
import com.samhap.kokomen.interview.repository.InterviewRepository;
import com.samhap.kokomen.interview.repository.QuestionRepository;
import com.samhap.kokomen.interview.repository.RootQuestionRepository;
import com.samhap.kokomen.interview.service.dto.RootQuestionCustomInterviewRequest;
import com.samhap.kokomen.interview.service.dto.start.InterviewStartResponse;
import com.samhap.kokomen.member.domain.Member;
import com.samhap.kokomen.member.repository.MemberRepository;
import com.samhap.kokomen.token.domain.TokenType;
import com.samhap.kokomen.token.repository.TokenRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class LiveCodingInterviewServiceTest extends BaseTest {

    @Autowired
    private InterviewStartFacadeService interviewStartFacadeService;
    @Autowired
    private InterviewRepository interviewRepository;
    @Autowired
    private QuestionRepository questionRepository;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private RootQuestionRepository rootQuestionRepository;
    @Autowired
    private TokenRepository tokenRepository;

    @Test
    void 코드_타입_루트_질문으로_커스텀_인터뷰를_시작하면_LIVE_CODING_타입으로_저장된다() {
        Member member = saveMemberWithTokens();
        RootQuestion codingRootQuestion = rootQuestionRepository.save(
                RootQuestionFixtureBuilder.builder()
                        .category(Category.ALGORITHM_DATA_STRUCTURE)
                        .questionType(RootQuestionType.CODE)
                        .title("Two Sum")
                        .build());
        RootQuestionCustomInterviewRequest request =
                new RootQuestionCustomInterviewRequest(codingRootQuestion.getId(), 3, InterviewMode.TEXT);

        InterviewStartResponse response =
                interviewStartFacadeService.startRootQuestionCustomInterview(request, new MemberAuth(member.getId()));

        Interview saved = interviewRepository.findById(response.interviewId()).orElseThrow();
        assertThat(saved.getInterviewType()).isEqualTo(InterviewType.LIVE_CODING);
    }

    @Test
    void 일반_타입_루트_질문으로_커스텀_인터뷰를_시작하면_CATEGORY_BASED_타입으로_저장된다() {
        Member member = saveMemberWithTokens();
        RootQuestion csRootQuestion = rootQuestionRepository.save(
                RootQuestionFixtureBuilder.builder().category(Category.OPERATING_SYSTEM).build());
        RootQuestionCustomInterviewRequest request =
                new RootQuestionCustomInterviewRequest(csRootQuestion.getId(), 3, InterviewMode.TEXT);

        InterviewStartResponse response =
                interviewStartFacadeService.startRootQuestionCustomInterview(request, new MemberAuth(member.getId()));

        Interview saved = interviewRepository.findById(response.interviewId()).orElseThrow();
        assertThat(saved.getInterviewType()).isEqualTo(InterviewType.CATEGORY_BASED);
    }

    @Test
    void 라이브_코테는_보이스_모드를_지원하지_않아_예외가_발생한다() {
        Member member = saveMemberWithTokens();
        RootQuestion codingRootQuestion = rootQuestionRepository.save(
                RootQuestionFixtureBuilder.builder()
                        .category(Category.ALGORITHM_DATA_STRUCTURE)
                        .questionType(RootQuestionType.CODE)
                        .title("Two Sum")
                        .build());
        RootQuestionCustomInterviewRequest request =
                new RootQuestionCustomInterviewRequest(codingRootQuestion.getId(), 3, InterviewMode.VOICE);

        assertThatThrownBy(() ->
                interviewStartFacadeService.startRootQuestionCustomInterview(request, new MemberAuth(member.getId())))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("라이브 코테");
    }

    @Test
    void 코드_타입_커스텀_인터뷰의_첫_질문은_제목과_본문을_합친_내용으로_저장된다() {
        Member member = saveMemberWithTokens();
        RootQuestion codingRootQuestion = rootQuestionRepository.save(
                RootQuestion.forCode(Category.ALGORITHM_DATA_STRUCTURE, "Two Sum",
                        "정수 배열에서 두 수의 합이 target이 되는 인덱스를 반환하세요."));
        RootQuestionCustomInterviewRequest request =
                new RootQuestionCustomInterviewRequest(codingRootQuestion.getId(), 3, InterviewMode.TEXT);

        InterviewStartResponse response =
                interviewStartFacadeService.startRootQuestionCustomInterview(request, new MemberAuth(member.getId()));

        Question question = questionRepository.findById(response.questionId()).orElseThrow();
        assertThat(question.getContent())
                .isEqualTo("Two Sum\n\n정수 배열에서 두 수의 합이 target이 되는 인덱스를 반환하세요.");
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
