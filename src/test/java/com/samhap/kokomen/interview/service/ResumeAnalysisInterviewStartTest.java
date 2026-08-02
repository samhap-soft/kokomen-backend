package com.samhap.kokomen.interview.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.samhap.kokomen.global.BaseTest;
import com.samhap.kokomen.global.dto.MemberAuth;
import com.samhap.kokomen.global.exception.BadRequestException;
import com.samhap.kokomen.global.exception.ForbiddenException;
import com.samhap.kokomen.global.exception.NotFoundException;
import com.samhap.kokomen.global.fixture.member.MemberFixtureBuilder;
import com.samhap.kokomen.global.fixture.resume.DimensionScoreFixture;
import com.samhap.kokomen.global.fixture.resume.GeneratedQuestionForAnalysisFixtureBuilder;
import com.samhap.kokomen.global.fixture.resume.ResumeAnalysisFixtureBuilder;
import com.samhap.kokomen.global.fixture.token.TokenFixtureBuilder;
import com.samhap.kokomen.interview.domain.GeneratedQuestion;
import com.samhap.kokomen.interview.domain.Interview;
import com.samhap.kokomen.interview.domain.InterviewMode;
import com.samhap.kokomen.interview.domain.InterviewType;
import com.samhap.kokomen.interview.domain.Question;
import com.samhap.kokomen.interview.external.dto.response.SupertoneResponse;
import com.samhap.kokomen.interview.repository.GeneratedQuestionRepository;
import com.samhap.kokomen.interview.repository.InterviewRepository;
import com.samhap.kokomen.interview.repository.QuestionRepository;
import com.samhap.kokomen.interview.service.dto.InterviewSummaryResponse;
import com.samhap.kokomen.interview.service.dto.resumeanalysis.ResumeAnalysisInterviewStartRequest;
import com.samhap.kokomen.interview.service.dto.start.InterviewStartResponse;
import com.samhap.kokomen.interview.service.dto.start.InterviewStartTextModeResponse;
import com.samhap.kokomen.interview.service.dto.start.InterviewStartVoiceModeResponse;
import com.samhap.kokomen.member.domain.Member;
import com.samhap.kokomen.member.repository.MemberRepository;
import com.samhap.kokomen.resume.domain.ResumeAnalysis;
import com.samhap.kokomen.resume.domain.ResumeAnalysisState;
import com.samhap.kokomen.resume.repository.ResumeAnalysisRepository;
import com.samhap.kokomen.token.domain.TokenType;
import com.samhap.kokomen.token.repository.TokenRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

class ResumeAnalysisInterviewStartTest extends BaseTest {

    @Autowired
    private InterviewStartFacadeService interviewStartFacadeService;

    @Autowired
    private InterviewQueryService interviewQueryService;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private TokenRepository tokenRepository;

    @Autowired
    private ResumeAnalysisRepository resumeAnalysisRepository;

    @Autowired
    private GeneratedQuestionRepository generatedQuestionRepository;

    @Autowired
    private InterviewRepository interviewRepository;

    @Autowired
    private QuestionRepository questionRepository;

    @Test
    void COMPLETED_분석의_질문으로_텍스트모드_면접을_시작한다() {
        // given
        Member member = saveMemberWithTokens(20);
        ResumeAnalysis analysis = saveAnalysis(member, ResumeAnalysisState.COMPLETED);
        GeneratedQuestion question = saveFiveQuestions(analysis).get(0);
        desyncIdSequences(member, question);

        // when
        InterviewStartResponse response = interviewStartFacadeService.startResumeAnalysisInterview(
                analysis.getId(),
                new ResumeAnalysisInterviewStartRequest(question.getId(), 5, InterviewMode.TEXT),
                new MemberAuth(member.getId()));

        // then
        assertThat(response).isInstanceOf(InterviewStartTextModeResponse.class);
        assertThat(response.interviewId()).isEqualTo(2L);
        assertThat(response.questionId()).isEqualTo(3L);
        assertThat(interviewRepository.findById(response.interviewId())).isPresent()
                .get()
                .satisfies(interview -> {
                    assertThat(interview.getInterviewType()).isEqualTo(InterviewType.RESUME_BASED);
                    assertThat(interview.getGeneratedQuestion().getId()).isEqualTo(question.getId());
                });
    }

    @Test
    void 음성모드_면접_시작은_토큰_2배를_요구한다() {
        // given
        given(supertoneClient.request(any())).willReturn(new SupertoneResponse(new byte[0]));
        Member enough = saveMemberWithTokens(6);
        Member notEnough = saveMemberWithTokens(5);
        ResumeAnalysis enoughAnalysis = saveAnalysis(enough, ResumeAnalysisState.COMPLETED);
        ResumeAnalysis notEnoughAnalysis = saveAnalysis(notEnough, ResumeAnalysisState.COMPLETED);
        GeneratedQuestion enoughQuestion = saveFiveQuestions(enoughAnalysis).get(0);
        GeneratedQuestion notEnoughQuestion = saveFiveQuestions(notEnoughAnalysis).get(0);
        desyncIdSequences(enough, enoughQuestion);

        // when
        InterviewStartResponse response = interviewStartFacadeService.startResumeAnalysisInterview(
                enoughAnalysis.getId(),
                new ResumeAnalysisInterviewStartRequest(enoughQuestion.getId(), 3, InterviewMode.VOICE),
                new MemberAuth(enough.getId()));

        // then
        assertThat(response).isInstanceOf(InterviewStartVoiceModeResponse.class);
        assertThat(response.interviewId()).isEqualTo(2L);
        assertThat(response.questionId()).isEqualTo(3L);
        assertThat(((InterviewStartVoiceModeResponse) response).rootQuestionVoiceUrl())
                .isEqualTo("https://dhtg8wzvkbfxr.cloudfront.net/mock-path/3.wav");
        assertThat(interviewRepository.findById(response.interviewId())).isPresent()
                .get()
                .satisfies(interview -> assertThat(interview.getInterviewMode()).isEqualTo(InterviewMode.VOICE));
        assertThatThrownBy(() -> interviewStartFacadeService.startResumeAnalysisInterview(
                notEnoughAnalysis.getId(),
                new ResumeAnalysisInterviewStartRequest(notEnoughQuestion.getId(), 3, InterviewMode.VOICE),
                new MemberAuth(notEnough.getId())))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("토큰 갯수가 부족합니다.");
    }

    @Test
    void EVALUATION_COMPLETED_상태에서는_면접을_시작할_수_없다() {
        // given
        Member member = saveMemberWithTokens(20);
        ResumeAnalysis analysis = saveAnalysis(member, ResumeAnalysisState.EVALUATION_COMPLETED);
        GeneratedQuestion question = saveFiveQuestions(analysis).get(0);

        // when & then
        assertThatThrownBy(() -> interviewStartFacadeService.startResumeAnalysisInterview(
                analysis.getId(),
                new ResumeAnalysisInterviewStartRequest(question.getId(), 5, InterviewMode.TEXT),
                new MemberAuth(member.getId())))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("질문 생성이 완료되지 않았습니다.");
    }

    @Test
    void 미claim_게스트_분석으로는_면접을_시작할_수_없다() {
        // given
        Member member = saveMemberWithTokens(20);
        ResumeAnalysis analysis = resumeAnalysisRepository.save(ResumeAnalysisFixtureBuilder.builder()
                .guest(UUID.randomUUID().toString(), "11.22.33.61")
                .state(ResumeAnalysisState.COMPLETED)
                .build());
        GeneratedQuestion question = saveFiveQuestions(analysis).get(0);

        // when & then
        assertThatThrownBy(() -> interviewStartFacadeService.startResumeAnalysisInterview(
                analysis.getId(),
                new ResumeAnalysisInterviewStartRequest(question.getId(), 5, InterviewMode.TEXT),
                new MemberAuth(member.getId())))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("먼저 이력서 분석을 내 계정에 연결해야 합니다.");
    }

    @Test
    void 다른_회원의_분석으로는_면접을_시작할_수_없다() {
        // given
        Member owner = saveMemberWithTokens(20);
        Member other = saveMemberWithTokens(20);
        ResumeAnalysis analysis = saveAnalysis(owner, ResumeAnalysisState.COMPLETED);
        GeneratedQuestion question = saveFiveQuestions(analysis).get(0);

        // when & then
        assertThatThrownBy(() -> interviewStartFacadeService.startResumeAnalysisInterview(
                analysis.getId(),
                new ResumeAnalysisInterviewStartRequest(question.getId(), 5, InterviewMode.TEXT),
                new MemberAuth(other.getId())))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("본인의 이력서 분석만 조회할 수 있습니다.");
    }

    /**
     * 소유권 검증이 상태 검증보다 먼저 와야 한다. 순서가 뒤바뀌면 남의 분석에 대해서도
     * "질문 생성이 완료되지 않았습니다."가 나가면서 소유하지 않은 분석의 진행 상태가 드러난다.
     */
    @Test
    void 남의_질문미완료_분석은_상태가_아니라_소유권으로_먼저_거절된다() {
        // given
        Member owner = saveMemberWithTokens(20);
        Member other = saveMemberWithTokens(20);
        ResumeAnalysis analysis = saveAnalysis(owner, ResumeAnalysisState.EVALUATION_COMPLETED);
        GeneratedQuestion question = saveFiveQuestions(analysis).get(0);

        // when & then
        assertThatThrownBy(() -> interviewStartFacadeService.startResumeAnalysisInterview(
                analysis.getId(),
                new ResumeAnalysisInterviewStartRequest(question.getId(), 5, InterviewMode.TEXT),
                new MemberAuth(other.getId())))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("본인의 이력서 분석만 조회할 수 있습니다.");
    }

    @Test
    void 분석에_속하지_않는_질문_ID로는_면접을_시작할_수_없다() {
        // given
        Member member = saveMemberWithTokens(20);
        ResumeAnalysis target = saveAnalysis(member, ResumeAnalysisState.COMPLETED);
        ResumeAnalysis other = saveAnalysis(member, ResumeAnalysisState.COMPLETED);
        saveFiveQuestions(target);
        GeneratedQuestion otherQuestion = saveFiveQuestions(other).get(0);

        // when & then
        assertThatThrownBy(() -> interviewStartFacadeService.startResumeAnalysisInterview(
                target.getId(),
                new ResumeAnalysisInterviewStartRequest(otherQuestion.getId(), 5, InterviewMode.TEXT),
                new MemberAuth(member.getId())))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("해당 이력서 분석에 속하지 않는 질문입니다.");
    }

    @Test
    void 존재하지_않는_질문_ID로는_시작할_수_없다() {
        // given
        Member member = saveMemberWithTokens(20);
        ResumeAnalysis analysis = saveAnalysis(member, ResumeAnalysisState.COMPLETED);
        saveFiveQuestions(analysis);

        // when & then
        assertThatThrownBy(() -> interviewStartFacadeService.startResumeAnalysisInterview(
                analysis.getId(),
                new ResumeAnalysisInterviewStartRequest(999_999L, 5, InterviewMode.TEXT),
                new MemberAuth(member.getId())))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("존재하지 않는 질문입니다.");
    }

    /**
     * 미끼 회원을 먼저 저장해 회원 ID와 분석 ID가 어긋나게 만든다. 두 값이 우연히 같으면 면접 주인을
     * 분석 ID로 잘못 조회해도 테스트가 통과해버리므로, 어긋남 자체를 먼저 단정한다.
     */
    @Test
    void 면접은_분석_ID가_아니라_로그인_회원_ID로_생성된다() {
        // given
        saveMemberWithTokens(20);
        Member member = saveMemberWithTokens(20);
        ResumeAnalysis analysis = saveAnalysis(member, ResumeAnalysisState.COMPLETED);
        GeneratedQuestion question = saveFiveQuestions(analysis).get(2);
        assertThat(analysis.getId()).isNotEqualTo(member.getId());
        assertThat(question.getId()).isNotEqualTo(analysis.getId());

        // when
        InterviewStartResponse response = interviewStartFacadeService.startResumeAnalysisInterview(
                analysis.getId(),
                new ResumeAnalysisInterviewStartRequest(question.getId(), 7, InterviewMode.TEXT),
                new MemberAuth(member.getId()));

        // then
        Interview interview = interviewRepository.findById(response.interviewId()).orElseThrow();
        assertThat(interview.getMember().getId()).isEqualTo(member.getId());
        assertThat(interview.getGeneratedQuestion().getId()).isEqualTo(question.getId());
        assertThat(interview.getMaxQuestionCount()).isEqualTo(7);
        assertThat(interview.getInterviewMode()).isEqualTo(InterviewMode.TEXT);
    }

    /**
     * 토큰이 넉넉한 미끼 회원의 ID를 분석 ID와 일치시켜, 토큰 검증에 회원 ID 대신 분석 ID를 넘기면
     * 부족 판정이 사라지도록 만든다.
     */
    @Test
    void 토큰은_분석_ID가_아니라_로그인_회원_ID로_검증된다() {
        // given
        Member decoy = saveMemberWithTokens(20);
        Member member = saveMemberWithTokens(2);
        ResumeAnalysis analysis = saveAnalysis(member, ResumeAnalysisState.COMPLETED);
        GeneratedQuestion question = saveFiveQuestions(analysis).get(0);
        assertThat(analysis.getId()).isEqualTo(decoy.getId());

        // when & then
        assertThatThrownBy(() -> interviewStartFacadeService.startResumeAnalysisInterview(
                analysis.getId(),
                new ResumeAnalysisInterviewStartRequest(question.getId(), 5, InterviewMode.TEXT),
                new MemberAuth(member.getId())))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("토큰 갯수가 부족합니다.");
    }

    @Test
    void 음성_생성이_실패하면_면접과_질문이_저장되지_않는다() {
        // given
        given(supertoneClient.request(any())).willThrow(new RuntimeException("음성 생성 실패"));
        Member member = saveMemberWithTokens(20);
        ResumeAnalysis analysis = saveAnalysis(member, ResumeAnalysisState.COMPLETED);
        GeneratedQuestion question = saveFiveQuestions(analysis).get(0);

        // when & then
        assertThatThrownBy(() -> interviewStartFacadeService.startResumeAnalysisInterview(
                analysis.getId(),
                new ResumeAnalysisInterviewStartRequest(question.getId(), 5, InterviewMode.VOICE),
                new MemberAuth(member.getId())))
                .isInstanceOf(RuntimeException.class);
        assertThat(interviewRepository.count()).isZero();
    }

    @Test
    void 이력서분석_기반_면접의_목록_조회에서_질문_내용이_정상_노출된다() {
        // given
        Member member = saveMemberWithTokens(20);
        ResumeAnalysis analysis = saveAnalysis(member, ResumeAnalysisState.COMPLETED);
        GeneratedQuestion question = saveFiveQuestions(analysis).get(0);
        interviewStartFacadeService.startResumeAnalysisInterview(
                analysis.getId(),
                new ResumeAnalysisInterviewStartRequest(question.getId(), 5, InterviewMode.TEXT),
                new MemberAuth(member.getId()));

        // when
        List<InterviewSummaryResponse> summaries = interviewQueryService.findMyInterviews(
                new MemberAuth(member.getId()), null, PageRequest.of(0, 10));

        // then
        assertThat(summaries).hasSize(1);
        assertThat(summaries.get(0).rootQuestion()).isEqualTo(question.getContent());
        assertThat(summaries.get(0).interviewCategory()).isEqualTo("이력서 기반");
    }

    private Member saveMemberWithTokens(int freeTokenCount) {
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        tokenRepository.save(TokenFixtureBuilder.builder()
                .memberId(member.getId()).type(TokenType.FREE).tokenCount(freeTokenCount).build());
        tokenRepository.save(TokenFixtureBuilder.builder()
                .memberId(member.getId()).type(TokenType.PAID).tokenCount(0).build());
        return member;
    }

    private ResumeAnalysis saveAnalysis(Member member, ResumeAnalysisState state) {
        return resumeAnalysisRepository.save(ResumeAnalysisFixtureBuilder.builder()
                .member(member)
                .jobPosition("백엔드 개발자")
                .jobCareer("경력 3년")
                .problemSolving(DimensionScoreFixture.of(90, List.of("근거1", "근거2"), List.of("보완1", "보완2")))
                .projectExperience(DimensionScoreFixture.of(80, List.of("근거1", "근거2"), List.of("보완1", "보완2")))
                .technicalSkills(DimensionScoreFixture.of(70, List.of("근거1", "근거2"), List.of("보완1", "보완2")))
                .softSkills(DimensionScoreFixture.of(60, List.of("근거1", "근거2"), List.of("보완1", "보완2")))
                .totalFeedback("전반적으로 우수합니다.")
                .state(state)
                .build());
    }

    private List<GeneratedQuestion> saveFiveQuestions(ResumeAnalysis analysis) {
        return generatedQuestionRepository.saveAll(GeneratedQuestionForAnalysisFixtureBuilder.five(analysis));
    }

    /**
     * interview_id와 question_id가 둘 다 1로 맞물리면 두 값이 뒤바뀌어도 단정이 통과한다.
     * 면접 1건과 질문 2건을 미리 저장해 시퀀스를 어긋내므로, 다음 면접은 2번 질문은 3번이 된다.
     */
    private void desyncIdSequences(Member member, GeneratedQuestion generatedQuestion) {
        Interview seed = interviewRepository.save(
                new Interview(member, generatedQuestion, 3, InterviewMode.TEXT));
        questionRepository.save(new Question(seed, "시퀀스 어긋내기용 질문 1"));
        questionRepository.save(new Question(seed, "시퀀스 어긋내기용 질문 2"));
    }
}
