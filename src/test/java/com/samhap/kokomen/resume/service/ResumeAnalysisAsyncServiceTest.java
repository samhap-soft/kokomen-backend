package com.samhap.kokomen.resume.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.samhap.kokomen.global.BaseTest;
import com.samhap.kokomen.global.dto.ClientIp;
import com.samhap.kokomen.global.exception.BadRequestException;
import com.samhap.kokomen.global.exception.ExternalApiException;
import com.samhap.kokomen.global.external.bedrock.BedrockConverseClient;
import com.samhap.kokomen.global.external.bedrock.BedrockConverseProperties;
import com.samhap.kokomen.global.fixture.member.MemberFixtureBuilder;
import com.samhap.kokomen.global.fixture.resume.ResumeAnalysisConverseResponseFixtureBuilder;
import com.samhap.kokomen.global.fixture.resume.ResumeAnalysisEvaluationFixture;
import com.samhap.kokomen.global.fixture.resume.ResumeAnalysisQuestionResultFixture;
import com.samhap.kokomen.global.fixture.token.TokenFixtureBuilder;
import com.samhap.kokomen.interview.domain.GeneratedQuestion;
import com.samhap.kokomen.interview.repository.GeneratedQuestionRepository;
import com.samhap.kokomen.member.domain.Member;
import com.samhap.kokomen.member.repository.MemberRepository;
import com.samhap.kokomen.resume.domain.ResumeAnalysis;
import com.samhap.kokomen.resume.domain.ResumeAnalysisEvaluation;
import com.samhap.kokomen.resume.domain.ResumeAnalysisFailureReason;
import com.samhap.kokomen.resume.domain.ResumeAnalysisJobInput;
import com.samhap.kokomen.resume.domain.ResumeAnalysisState;
import com.samhap.kokomen.resume.external.ResumeAnalysisEvaluationBedrockClient;
import com.samhap.kokomen.resume.external.ResumeAnalysisEvaluationGptClient;
import com.samhap.kokomen.resume.external.ResumeAnalysisQuestionBedrockClient;
import com.samhap.kokomen.resume.external.ResumeAnalysisQuestionGptClient;
import com.samhap.kokomen.resume.repository.ResumeAnalysisRepository;
import com.samhap.kokomen.resume.service.dto.ExtractedContents;
import com.samhap.kokomen.resume.service.dto.GuestInfo;
import com.samhap.kokomen.resume.service.dto.MaterialRefs;
import com.samhap.kokomen.resume.service.dto.ResumeAnalysisCommand;
import com.samhap.kokomen.resume.service.dto.ResumeAnalysisQuestionCallCommand;
import com.samhap.kokomen.resume.tool.ResumeAnalysisToolNames;
import com.samhap.kokomen.token.domain.Token;
import com.samhap.kokomen.token.domain.TokenType;
import com.samhap.kokomen.token.repository.TokenRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;

class ResumeAnalysisAsyncServiceTest extends BaseTest {

    private static final ResumeAnalysisJobInput JOB_INPUT =
            new ResumeAnalysisJobInput("백엔드 개발자", null, "신입");
    private static final ExtractedContents CONTENTS =
            new ExtractedContents("이력서 원문입니다.", "포트폴리오 원문입니다.");

    @Autowired
    private ResumeAnalysisService resumeAnalysisService;

    @Autowired
    private ResumeAnalysisStateService resumeAnalysisStateService;

    @Autowired
    private ResumeAnalysisRepository resumeAnalysisRepository;

    @Autowired
    private GeneratedQuestionRepository generatedQuestionRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private TokenRepository tokenRepository;

    @Autowired
    private BedrockConverseProperties bedrockConverseProperties;

    @Autowired
    private ObjectMapper objectMapper;

    private ResumeAnalysisEvaluationBedrockClient evaluationBedrockClient;
    private ResumeAnalysisEvaluationGptClient evaluationGptClient;
    private ResumeAnalysisQuestionBedrockClient questionBedrockClient;
    private ResumeAnalysisQuestionGptClient questionGptClient;
    private ResumeAnalysisAsyncService asyncService;

    /**
     * 4개 LLM 클라이언트만 평범한 Mockito 목으로 두고 서비스를 수동 조립한다. BaseTest에 목 빈을 추가하지 않으므로
     * 컨텍스트 fork가 늘지 않고, InOrder 검증도 가능하다. 필드명을 asyncService로 둔 것은 BaseTest에 같은 타입의
     * 목 필드가 생겨도 이 수동 조립 인스턴스가 가려지지 않게 하기 위해서다.
     */
    @BeforeEach
    void setUpAsyncService() {
        evaluationBedrockClient = mock(ResumeAnalysisEvaluationBedrockClient.class);
        evaluationGptClient = mock(ResumeAnalysisEvaluationGptClient.class);
        questionBedrockClient = mock(ResumeAnalysisQuestionBedrockClient.class);
        questionGptClient = mock(ResumeAnalysisQuestionGptClient.class);
        asyncService = new ResumeAnalysisAsyncService(
                resumeAnalysisService, resumeAnalysisStateService,
                evaluationBedrockClient, evaluationGptClient, questionBedrockClient, questionGptClient);
    }

    @Test
    void 평가_콜과_질문_콜이_순차로_한_번씩_실행되고_질문이_0부터_순서대로_저장된다() {
        // given
        Long analysisId = saveGuestAnalysis("11.22.33.81").getId();
        given(evaluationBedrockClient.evaluate(any(ResumeAnalysisCommand.class)))
                .willReturn(ResumeAnalysisEvaluationFixture.withoutJd());
        given(questionBedrockClient.generateQuestions(any(ResumeAnalysisQuestionCallCommand.class)))
                .willReturn(ResumeAnalysisQuestionResultFixture.five());

        // when
        asyncService.run(command(analysisId, null, false));

        // then
        InOrder inOrder = inOrder(evaluationBedrockClient, questionBedrockClient);
        inOrder.verify(evaluationBedrockClient).evaluate(any(ResumeAnalysisCommand.class));
        inOrder.verify(questionBedrockClient).generateQuestions(any(ResumeAnalysisQuestionCallCommand.class));
        inOrder.verifyNoMoreInteractions();

        ResumeAnalysis found = resumeAnalysisRepository.findById(analysisId).orElseThrow();
        List<GeneratedQuestion> questions =
                generatedQuestionRepository.findByAnalysisIdOrderByQuestionOrder(analysisId);
        assertAll(
                () -> assertThat(found.getState()).isEqualTo(ResumeAnalysisState.COMPLETED),
                () -> assertThat(found.getTotalScore()).isEqualTo(78),
                () -> assertThat(found.getQuestionStartedAt()).isNotNull(),
                () -> assertThat(found.getCompletedAt()).isNotNull(),
                () -> assertThat(questions).hasSize(5),
                () -> assertThat(questions).extracting(GeneratedQuestion::getQuestionOrder)
                        .containsExactly(0, 1, 2, 3, 4)
        );
    }

    @Test
    void 질문_콜에는_평가_결과가_주입되고_jd_제공여부는_커맨드_값을_사용한다() {
        // given - job_description은 비어있지 않지만 커맨드의 jdProvided는 false다
        Long analysisId = saveGuestAnalysis("11.22.33.82").getId();
        given(evaluationBedrockClient.evaluate(any(ResumeAnalysisCommand.class)))
                .willReturn(ResumeAnalysisEvaluationFixture.withoutJd());
        given(questionBedrockClient.generateQuestions(any(ResumeAnalysisQuestionCallCommand.class)))
                .willReturn(ResumeAnalysisQuestionResultFixture.five());
        ResumeAnalysisCommand command = new ResumeAnalysisCommand(analysisId, null, false,
                "이력서 원문입니다.", "포트폴리오 원문입니다.", "백엔드 개발자", "Java 경험자를 찾습니다.", "신입");

        // when
        asyncService.run(command);

        // then
        ArgumentCaptor<ResumeAnalysisQuestionCallCommand> captor =
                ArgumentCaptor.forClass(ResumeAnalysisQuestionCallCommand.class);
        verify(questionBedrockClient).generateQuestions(captor.capture());
        ResumeAnalysisQuestionCallCommand questionCommand = captor.getValue();
        assertAll(
                () -> assertThat(questionCommand.analysisId()).isEqualTo(analysisId),
                () -> assertThat(questionCommand.resumeText()).isEqualTo("이력서 원문입니다."),
                () -> assertThat(questionCommand.portfolioText()).isEqualTo("포트폴리오 원문입니다."),
                () -> assertThat(questionCommand.evaluationResult()).contains("problem_solving"),
                () -> assertThat(questionCommand.evaluationResult()).contains("total_score=78"),
                () -> assertThat(questionCommand.evaluationResult()).contains("jd_provided=false"),
                () -> assertThat(questionCommand.evaluationResult()).doesNotContain("jd_fit")
        );
    }

    @Test
    void 평가가_실패하면_EVALUATION_FAILED이고_질문_콜은_호출되지_않는다() {
        // given
        Long analysisId = saveGuestAnalysis("11.22.33.83").getId();
        willThrow(new ExternalApiException("Bedrock 호출 실패"))
                .given(evaluationBedrockClient).evaluate(any(ResumeAnalysisCommand.class));
        willThrow(new ExternalApiException("GPT 호출 실패"))
                .given(evaluationGptClient).evaluate(any(ResumeAnalysisCommand.class));

        // when
        asyncService.run(command(analysisId, null, false));

        // then
        ResumeAnalysis found = resumeAnalysisRepository.findById(analysisId).orElseThrow();
        assertAll(
                () -> assertThat(found.getState()).isEqualTo(ResumeAnalysisState.EVALUATION_FAILED),
                () -> assertThat(found.getFailureReason())
                        .isEqualTo(ResumeAnalysisFailureReason.EVALUATION_LLM),
                () -> assertThat(found.getTotalScore()).isNull()
        );
        verify(questionBedrockClient, never()).generateQuestions(any(ResumeAnalysisQuestionCallCommand.class));
        verify(questionGptClient, never()).generateQuestions(any(ResumeAnalysisQuestionCallCommand.class));
    }

    @Test
    void MAX_TOKENS로_잘린_실제_Bedrock_응답도_OUTPUT_TRUNCATED로_기록된다() {
        // given - 잘림 판정이 extractToolUse의 실제 메시지와 맞는지 보려면 손으로 쓴 문구가 아니라
        // 실물 BedrockConverseClient가 던진 예외를 워커에 흘려야 한다
        Long analysisId = saveGuestAnalysis("11.22.33.97").getId();
        BedrockRuntimeClient bedrockRuntimeClient = mock(BedrockRuntimeClient.class);
        BedrockConverseClient converseClient =
                new BedrockConverseClient(bedrockRuntimeClient, bedrockConverseProperties, objectMapper);
        given(bedrockRuntimeClient.converse(any(ConverseRequest.class)))
                .willReturn(ResumeAnalysisConverseResponseFixtureBuilder.builder().buildTruncated());
        willThrow(new ExternalApiException("GPT 호출 실패"))
                .given(evaluationGptClient).evaluate(any(ResumeAnalysisCommand.class));
        ResumeAnalysisAsyncService bedrockWiredAsyncService = new ResumeAnalysisAsyncService(
                resumeAnalysisService, resumeAnalysisStateService,
                new ResumeAnalysisEvaluationBedrockClient(converseClient, bedrockConverseProperties),
                evaluationGptClient,
                new ResumeAnalysisQuestionBedrockClient(converseClient, bedrockConverseProperties),
                questionGptClient);

        // when
        bedrockWiredAsyncService.run(command(analysisId, null, false));

        // then
        ResumeAnalysis found = resumeAnalysisRepository.findById(analysisId).orElseThrow();
        assertAll(
                () -> assertThat(found.getState()).isEqualTo(ResumeAnalysisState.EVALUATION_FAILED),
                () -> assertThat(found.getFailureReason())
                        .isEqualTo(ResumeAnalysisFailureReason.OUTPUT_TRUNCATED)
        );
    }

    @Test
    void 출력이_잘려_tool_use가_아니면_실패_원인은_OUTPUT_TRUNCATED다() {
        // given
        Long analysisId = saveGuestAnalysis("11.22.33.84").getId();
        willThrow(new ExternalApiException("Bedrock 응답이 tool_use가 아닙니다. stopReason=MAX_TOKENS, expected="
                + ResumeAnalysisToolNames.EVALUATION))
                .given(evaluationBedrockClient).evaluate(any(ResumeAnalysisCommand.class));
        willThrow(new ExternalApiException("GPT 호출 실패"))
                .given(evaluationGptClient).evaluate(any(ResumeAnalysisCommand.class));

        // when
        asyncService.run(command(analysisId, null, false));

        // then
        ResumeAnalysis found = resumeAnalysisRepository.findById(analysisId).orElseThrow();
        assertAll(
                () -> assertThat(found.getState()).isEqualTo(ResumeAnalysisState.EVALUATION_FAILED),
                () -> assertThat(found.getFailureReason())
                        .isEqualTo(ResumeAnalysisFailureReason.OUTPUT_TRUNCATED)
        );
    }

    @Test
    void 평가는_성공하고_질문만_실패하면_QUESTION_FAILED이고_평가_결과가_보존된다() {
        // given
        Long analysisId = saveGuestAnalysis("11.22.33.85").getId();
        given(evaluationBedrockClient.evaluate(any(ResumeAnalysisCommand.class)))
                .willReturn(ResumeAnalysisEvaluationFixture.withoutJd());
        willThrow(new ExternalApiException("Bedrock 질문 생성 실패"))
                .given(questionBedrockClient).generateQuestions(any(ResumeAnalysisQuestionCallCommand.class));
        willThrow(new ExternalApiException("GPT 질문 생성 실패"))
                .given(questionGptClient).generateQuestions(any(ResumeAnalysisQuestionCallCommand.class));

        // when
        asyncService.run(command(analysisId, null, false));

        // then
        ResumeAnalysis found = resumeAnalysisRepository.findById(analysisId).orElseThrow();
        assertAll(
                () -> assertThat(found.getState()).isEqualTo(ResumeAnalysisState.QUESTION_FAILED),
                () -> assertThat(found.getFailureReason()).isEqualTo(ResumeAnalysisFailureReason.QUESTION_LLM),
                () -> assertThat(found.getTotalScore()).isEqualTo(78),
                () -> assertThat(found.getProblemSolvingScore()).isEqualTo(90),
                () -> assertThat(found.getSoftSkillsReason()).containsExactly("근거1", "근거2"),
                () -> assertThat(generatedQuestionRepository.findByAnalysisIdOrderByQuestionOrder(analysisId))
                        .isEmpty()
        );
    }

    @Test
    void Bedrock_평가가_실패하면_GPT_폴백으로_완료되고_질문_콜은_Bedrock을_건너뛴다() {
        // given
        Long analysisId = saveGuestAnalysis("11.22.33.86").getId();
        willThrow(new ExternalApiException("Bedrock 호출 실패"))
                .given(evaluationBedrockClient).evaluate(any(ResumeAnalysisCommand.class));
        given(evaluationGptClient.evaluate(any(ResumeAnalysisCommand.class)))
                .willReturn(ResumeAnalysisEvaluationFixture.withoutJd());
        given(questionGptClient.generateQuestions(any(ResumeAnalysisQuestionCallCommand.class)))
                .willReturn(ResumeAnalysisQuestionResultFixture.five());

        // when
        asyncService.run(command(analysisId, null, false));

        // then
        ResumeAnalysis found = resumeAnalysisRepository.findById(analysisId).orElseThrow();
        assertAll(
                () -> assertThat(found.getState()).isEqualTo(ResumeAnalysisState.COMPLETED),
                () -> assertThat(found.getTotalScore()).isEqualTo(78)
        );
        verify(questionBedrockClient, never()).generateQuestions(any(ResumeAnalysisQuestionCallCommand.class));
        verify(questionGptClient).generateQuestions(any(ResumeAnalysisQuestionCallCommand.class));
    }

    @Test
    void Bedrock_질문생성이_실패하면_GPT_폴백으로_질문이_완료된다() {
        // given
        Long analysisId = saveGuestAnalysis("11.22.33.87").getId();
        given(evaluationBedrockClient.evaluate(any(ResumeAnalysisCommand.class)))
                .willReturn(ResumeAnalysisEvaluationFixture.withoutJd());
        willThrow(new ExternalApiException("Bedrock 질문 생성 실패"))
                .given(questionBedrockClient).generateQuestions(any(ResumeAnalysisQuestionCallCommand.class));
        given(questionGptClient.generateQuestions(any(ResumeAnalysisQuestionCallCommand.class)))
                .willReturn(ResumeAnalysisQuestionResultFixture.five());

        // when
        asyncService.run(command(analysisId, null, false));

        // then
        assertAll(
                () -> assertThat(resumeAnalysisRepository.findById(analysisId).orElseThrow().getState())
                        .isEqualTo(ResumeAnalysisState.COMPLETED),
                () -> assertThat(generatedQuestionRepository.findByAnalysisIdOrderByQuestionOrder(analysisId))
                        .hasSize(5)
        );
    }

    @Test
    void 이미_COMPLETED된_분석에_평가_hop을_다시_실행하면_결과가_폐기된다() {
        // given
        Long analysisId = saveGuestAnalysis("11.22.33.88").getId();
        resumeAnalysisStateService.completeEvaluation(analysisId, ResumeAnalysisEvaluationFixture.withoutJd());
        resumeAnalysisStateService.completeQuestions(analysisId,
                ResumeAnalysisQuestionResultFixture.five().questions());
        given(evaluationBedrockClient.evaluate(any(ResumeAnalysisCommand.class)))
                .willReturn(ResumeAnalysisEvaluationFixture.withJd());

        // when
        asyncService.run(command(analysisId, null, false));

        // then
        ResumeAnalysis found = resumeAnalysisRepository.findById(analysisId).orElseThrow();
        assertAll(
                () -> assertThat(found.getState()).isEqualTo(ResumeAnalysisState.COMPLETED),
                () -> assertThat(found.getTotalScore()).isEqualTo(78),
                () -> assertThat(found.getJdFitScore()).isNull(),
                () -> assertThat(generatedQuestionRepository.findByAnalysisIdOrderByQuestionOrder(analysisId))
                        .hasSize(5)
        );
        verify(questionBedrockClient, never()).generateQuestions(any(ResumeAnalysisQuestionCallCommand.class));
    }

    @Test
    void 과금_대상_분석은_평가_커밋_후_토큰_5개가_차감된다() {
        // given
        Member member = saveMemberWithTokens(20);
        Long analysisId = resumeAnalysisService.saveAnalysis(member.getId(), GuestInfo.none(),
                MaterialRefs.empty(), CONTENTS, JOB_INPUT, true).getId();
        given(evaluationBedrockClient.evaluate(any(ResumeAnalysisCommand.class)))
                .willReturn(ResumeAnalysisEvaluationFixture.withoutJd());
        given(questionBedrockClient.generateQuestions(any(ResumeAnalysisQuestionCallCommand.class)))
                .willReturn(ResumeAnalysisQuestionResultFixture.five());

        // when
        asyncService.run(command(analysisId, member.getId(), false));

        // then
        Token freeToken = tokenRepository.findByMemberIdAndType(member.getId(), TokenType.FREE).orElseThrow();
        assertAll(
                () -> assertThat(freeToken.getTokenCount())
                        .isEqualTo(20 - ResumeAnalysisFacadeService.RESUME_ANALYSIS_TOKEN_COST),
                () -> assertThat(resumeAnalysisRepository.findById(analysisId).orElseThrow()
                        .getChargedTokenCount())
                        .isEqualTo(ResumeAnalysisFacadeService.RESUME_ANALYSIS_TOKEN_COST)
        );
    }

    @Test
    void 게스트_분석은_평가가_성공해도_토큰이_차감되지_않는다() {
        // given
        Member member = saveMemberWithTokens(20);
        Long analysisId = saveGuestAnalysis("11.22.33.89").getId();
        given(evaluationBedrockClient.evaluate(any(ResumeAnalysisCommand.class)))
                .willReturn(ResumeAnalysisEvaluationFixture.withoutJd());
        given(questionBedrockClient.generateQuestions(any(ResumeAnalysisQuestionCallCommand.class)))
                .willReturn(ResumeAnalysisQuestionResultFixture.five());

        // when
        asyncService.run(command(analysisId, null, false));

        // then
        Token freeToken = tokenRepository.findByMemberIdAndType(member.getId(), TokenType.FREE).orElseThrow();
        assertAll(
                () -> assertThat(freeToken.getTokenCount()).isEqualTo(20),
                () -> assertThat(resumeAnalysisRepository.findById(analysisId).orElseThrow()
                        .getChargedTokenCount()).isZero()
        );
    }

    @Test
    void 질문_hop이_종단되면_평가_직후와_질문_종단_시점에_회수_과금이_반복_호출된다() {
        // given - CAS 멱등이므로 반복 호출을 세려면 상태 서비스를 목으로 둔다
        Long analysisId = saveGuestAnalysis("11.22.33.92").getId();
        ResumeAnalysisStateService stateServiceMock = mock(ResumeAnalysisStateService.class);
        given(stateServiceMock.completeEvaluation(eq(analysisId), any(ResumeAnalysisEvaluation.class)))
                .willReturn(true);
        given(stateServiceMock.completeQuestions(eq(analysisId), anyList())).willReturn(true);
        given(evaluationBedrockClient.evaluate(any(ResumeAnalysisCommand.class)))
                .willReturn(ResumeAnalysisEvaluationFixture.withoutJd());
        given(questionBedrockClient.generateQuestions(any(ResumeAnalysisQuestionCallCommand.class)))
                .willReturn(ResumeAnalysisQuestionResultFixture.five());
        ResumeAnalysisAsyncService stateMockedAsyncService = new ResumeAnalysisAsyncService(
                resumeAnalysisService, stateServiceMock,
                evaluationBedrockClient, evaluationGptClient, questionBedrockClient, questionGptClient);

        // when
        stateMockedAsyncService.run(command(analysisId, 7L, false));

        // then
        verify(stateServiceMock, times(2)).chargeTokensIfNeeded(analysisId, 7L);
    }

    @Test
    void 질문_저장이_상태_가드로_폐기되면_실패로_기록하지_않고_회수_과금만_수행한다() {
        // given - completeQuestions가 false를 반환하는 경우를 만들려면 상태 서비스를 목으로 둔다
        Long analysisId = saveGuestAnalysis("11.22.33.96").getId();
        ResumeAnalysisStateService stateServiceMock = mock(ResumeAnalysisStateService.class);
        given(stateServiceMock.completeQuestions(eq(analysisId), anyList())).willReturn(false);
        given(questionBedrockClient.generateQuestions(any(ResumeAnalysisQuestionCallCommand.class)))
                .willReturn(ResumeAnalysisQuestionResultFixture.five());
        ResumeAnalysisAsyncService stateMockedAsyncService = new ResumeAnalysisAsyncService(
                resumeAnalysisService, stateServiceMock,
                evaluationBedrockClient, evaluationGptClient, questionBedrockClient, questionGptClient);

        // when
        stateMockedAsyncService.runQuestionHop(command(analysisId, 7L, false),
                ResumeAnalysisEvaluationFixture.withoutJd());

        // then
        verify(stateServiceMock, never())
                .failQuestions(eq(analysisId), any(ResumeAnalysisFailureReason.class));
        verify(stateServiceMock).chargeTokensIfNeeded(analysisId, 7L);
    }

    @Test
    void 평가_저장이_일시적_락_예외로_실패하면_한_번_재시도하고_종단하지_않는다() {
        // given
        Long analysisId = saveGuestAnalysis("11.22.33.93").getId();
        ResumeAnalysisStateService stateServiceMock = mock(ResumeAnalysisStateService.class);
        given(stateServiceMock.completeEvaluation(eq(analysisId), any(ResumeAnalysisEvaluation.class)))
                .willThrow(new CannotAcquireLockException("Lock wait timeout exceeded"))
                .willReturn(true);
        given(evaluationBedrockClient.evaluate(any(ResumeAnalysisCommand.class)))
                .willReturn(ResumeAnalysisEvaluationFixture.withoutJd());
        ResumeAnalysisAsyncService stateMockedAsyncService = new ResumeAnalysisAsyncService(
                resumeAnalysisService, stateServiceMock,
                evaluationBedrockClient, evaluationGptClient, questionBedrockClient, questionGptClient);

        // when
        ResumeAnalysisEvaluation returned =
                stateMockedAsyncService.runEvaluationHop(command(analysisId, null, false));

        // then
        assertThat(returned).isNotNull();
        verify(stateServiceMock, times(2))
                .completeEvaluation(eq(analysisId), any(ResumeAnalysisEvaluation.class));
        verify(stateServiceMock, never())
                .failEvaluation(eq(analysisId), any(ResumeAnalysisFailureReason.class));
    }

    @Test
    void 평가_저장이_일시적_락_예외로_계속_실패하면_재시도_상한에서_멈추고_PERSISTENCE로_종단한다() {
        // given
        Long analysisId = saveGuestAnalysis("11.22.33.95").getId();
        ResumeAnalysisStateService stateServiceMock = mock(ResumeAnalysisStateService.class);
        given(stateServiceMock.completeEvaluation(eq(analysisId), any(ResumeAnalysisEvaluation.class)))
                .willThrow(new CannotAcquireLockException("Lock wait timeout exceeded"));
        given(evaluationBedrockClient.evaluate(any(ResumeAnalysisCommand.class)))
                .willReturn(ResumeAnalysisEvaluationFixture.withoutJd());
        ResumeAnalysisAsyncService stateMockedAsyncService = new ResumeAnalysisAsyncService(
                resumeAnalysisService, stateServiceMock,
                evaluationBedrockClient, evaluationGptClient, questionBedrockClient, questionGptClient);

        // when
        ResumeAnalysisEvaluation returned =
                stateMockedAsyncService.runEvaluationHop(command(analysisId, null, false));

        // then
        assertThat(returned).isNull();
        verify(stateServiceMock, times(2))
                .completeEvaluation(eq(analysisId), any(ResumeAnalysisEvaluation.class));
        verify(stateServiceMock).failEvaluation(analysisId, ResumeAnalysisFailureReason.PERSISTENCE);
    }

    @Test
    void 평가_저장이_데이터_정합성_예외로_실패하면_재시도하지_않고_PERSISTENCE로_종단한다() {
        // given
        Long analysisId = saveGuestAnalysis("11.22.33.94").getId();
        ResumeAnalysisStateService stateServiceMock = mock(ResumeAnalysisStateService.class);
        given(stateServiceMock.completeEvaluation(eq(analysisId), any(ResumeAnalysisEvaluation.class)))
                .willThrow(new DataIntegrityViolationException("Duplicate entry"));
        given(evaluationBedrockClient.evaluate(any(ResumeAnalysisCommand.class)))
                .willReturn(ResumeAnalysisEvaluationFixture.withoutJd());
        ResumeAnalysisAsyncService stateMockedAsyncService = new ResumeAnalysisAsyncService(
                resumeAnalysisService, stateServiceMock,
                evaluationBedrockClient, evaluationGptClient, questionBedrockClient, questionGptClient);

        // when
        ResumeAnalysisEvaluation returned =
                stateMockedAsyncService.runEvaluationHop(command(analysisId, null, false));

        // then
        assertThat(returned).isNull();
        verify(stateServiceMock, times(1))
                .completeEvaluation(eq(analysisId), any(ResumeAnalysisEvaluation.class));
        verify(stateServiceMock).failEvaluation(analysisId, ResumeAnalysisFailureReason.PERSISTENCE);
    }

    @Test
    void readCommand는_원문과_부모_행에서_커맨드를_복원하고_과금하지_않는다() {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        Long analysisId = resumeAnalysisService.saveAnalysis(member.getId(), GuestInfo.none(),
                MaterialRefs.empty(), CONTENTS,
                new ResumeAnalysisJobInput("백엔드 개발자", "Java 경험자", "경력 3년"), true).getId();

        // when
        ResumeAnalysisCommand restored = asyncService.readCommand(analysisId);

        // then
        assertAll(
                () -> assertThat(restored.analysisId()).isEqualTo(analysisId),
                () -> assertThat(restored.billingMemberId()).isNull(),
                () -> assertThat(restored.jdProvided()).isTrue(),
                () -> assertThat(restored.resumeText()).isEqualTo("이력서 원문입니다."),
                () -> assertThat(restored.portfolioText()).isEqualTo("포트폴리오 원문입니다."),
                () -> assertThat(restored.jobPosition()).isEqualTo("백엔드 개발자"),
                () -> assertThat(restored.jobDescription()).isEqualTo("Java 경험자"),
                () -> assertThat(restored.jobCareer()).isEqualTo("경력 3년")
        );
    }

    @Test
    void readCommand는_원문이_없으면_예외가_발생한다() {
        // given
        ResumeAnalysis analysis = resumeAnalysisRepository.save(ResumeAnalysis.forGuest(
                UUID.randomUUID().toString(), new ClientIp("11.22.33.90"), UUID.randomUUID().toString(),
                JOB_INPUT));

        // when & then
        assertThatThrownBy(() -> asyncService.readCommand(analysis.getId()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("이력서 원문이 만료되어");
    }

    @Test
    void 질문_재시도는_readCommand로_복원한_커맨드를_쓰므로_토큰이_다시_차감되지_않는다() {
        // given - 첫 실행은 평가 성공 + 질문 2콜 모두 실패로 QUESTION_FAILED를 만든다
        Member member = saveMemberWithTokens(20);
        Long analysisId = resumeAnalysisService.saveAnalysis(member.getId(), GuestInfo.none(),
                MaterialRefs.empty(), CONTENTS, JOB_INPUT, true).getId();
        given(evaluationBedrockClient.evaluate(any(ResumeAnalysisCommand.class)))
                .willReturn(ResumeAnalysisEvaluationFixture.withoutJd());
        given(questionBedrockClient.generateQuestions(any(ResumeAnalysisQuestionCallCommand.class)))
                .willThrow(new ExternalApiException("Bedrock 질문 생성 실패"))
                .willReturn(ResumeAnalysisQuestionResultFixture.five());
        willThrow(new ExternalApiException("GPT 질문 생성 실패"))
                .given(questionGptClient).generateQuestions(any(ResumeAnalysisQuestionCallCommand.class));
        asyncService.run(command(analysisId, member.getId(), false));
        resumeAnalysisStateService.restoreForQuestionRetry(analysisId);

        // when
        asyncService.runQuestionHop(asyncService.readCommand(analysisId),
                resumeAnalysisService.readEvaluation(analysisId));

        // then
        ResumeAnalysis found = resumeAnalysisRepository.findById(analysisId).orElseThrow();
        Token freeToken = tokenRepository.findByMemberIdAndType(member.getId(), TokenType.FREE).orElseThrow();
        assertAll(
                () -> assertThat(found.getState()).isEqualTo(ResumeAnalysisState.COMPLETED),
                () -> assertThat(found.getQuestionRetryCount()).isEqualTo(1),
                () -> assertThat(found.getChargedTokenCount())
                        .isEqualTo(ResumeAnalysisFacadeService.RESUME_ANALYSIS_TOKEN_COST),
                () -> assertThat(freeToken.getTokenCount())
                        .isEqualTo(20 - ResumeAnalysisFacadeService.RESUME_ANALYSIS_TOKEN_COST),
                () -> assertThat(generatedQuestionRepository.findByAnalysisIdOrderByQuestionOrder(analysisId))
                        .hasSize(5)
        );
    }

    @Test
    void 해피패스_Bedrock_2콜을_순서대로_스터빙하면_평가와_질문이_모두_저장된다() {
        // given - BedrockConverseClient는 실물, BedrockRuntimeClient만 목으로 잡는다
        Long analysisId = saveGuestAnalysis("11.22.33.91").getId();
        BedrockRuntimeClient bedrockRuntimeClient = mock(BedrockRuntimeClient.class);
        BedrockConverseClient converseClient =
                new BedrockConverseClient(bedrockRuntimeClient, bedrockConverseProperties, objectMapper);
        ResumeAnalysisConverseResponseFixtureBuilder fixture =
                ResumeAnalysisConverseResponseFixtureBuilder.builder();
        given(bedrockRuntimeClient.converse(any(ConverseRequest.class)))
                .willReturn(fixture.buildEvaluation(false))
                .willReturn(fixture.buildQuestions());
        ResumeAnalysisAsyncService bedrockWiredAsyncService = new ResumeAnalysisAsyncService(
                resumeAnalysisService, resumeAnalysisStateService,
                new ResumeAnalysisEvaluationBedrockClient(converseClient, bedrockConverseProperties),
                evaluationGptClient,
                new ResumeAnalysisQuestionBedrockClient(converseClient, bedrockConverseProperties),
                questionGptClient);

        // when
        bedrockWiredAsyncService.run(command(analysisId, null, false));

        // then
        ArgumentCaptor<ConverseRequest> captor = ArgumentCaptor.forClass(ConverseRequest.class);
        verify(bedrockRuntimeClient, times(2)).converse(captor.capture());
        ConverseRequest evaluationRequest = captor.getAllValues().get(0);
        ConverseRequest questionRequest = captor.getAllValues().get(1);

        ResumeAnalysis found = resumeAnalysisRepository.findById(analysisId).orElseThrow();
        assertAll(
                () -> assertThat(toolNameOf(evaluationRequest)).isEqualTo(ResumeAnalysisToolNames.EVALUATION),
                () -> assertThat(toolNameOf(questionRequest))
                        .isEqualTo(ResumeAnalysisToolNames.QUESTION_GENERATION),
                () -> assertThat(evaluationRequest.inferenceConfig().maxTokens()).isEqualTo(10_000),
                () -> assertThat(evaluationRequest.inferenceConfig().temperature()).isEqualTo(0.2f),
                () -> assertThat(questionRequest.inferenceConfig().maxTokens()).isEqualTo(2_048),
                () -> assertThat(questionRequest.inferenceConfig().temperature()).isEqualTo(0.7f),
                () -> assertThat(userTextOf(evaluationRequest)).doesNotContain("<evaluation_result>"),
                () -> assertThat(userTextOf(questionRequest)).contains("<evaluation_result>"),
                () -> assertThat(found.getState()).isEqualTo(ResumeAnalysisState.COMPLETED),
                () -> assertThat(found.getTotalScore()).isEqualTo(78),
                () -> assertThat(found.getJdFitScore()).isNull(),
                () -> assertThat(generatedQuestionRepository.findByAnalysisIdOrderByQuestionOrder(analysisId))
                        .hasSize(5)
        );
    }

    private String toolNameOf(ConverseRequest request) {
        return request.toolConfig().tools().stream()
                .filter(tool -> tool.toolSpec() != null)
                .map(tool -> tool.toolSpec().name())
                .findFirst()
                .orElseThrow();
    }

    private String userTextOf(ConverseRequest request) {
        return request.messages().get(0).content().get(0).text();
    }

    private ResumeAnalysisCommand command(Long analysisId, Long billingMemberId, boolean jdProvided) {
        return new ResumeAnalysisCommand(analysisId, billingMemberId, jdProvided,
                "이력서 원문입니다.", "포트폴리오 원문입니다.", "백엔드 개발자", null, "신입");
    }

    private ResumeAnalysis saveGuestAnalysis(String guestIp) {
        return resumeAnalysisService.saveAnalysis(null,
                new GuestInfo(UUID.randomUUID().toString(), new ClientIp(guestIp),
                        UUID.randomUUID().toString()),
                MaterialRefs.empty(), CONTENTS, JOB_INPUT, false);
    }

    private Member saveMemberWithTokens(int freeTokenCount) {
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        tokenRepository.save(TokenFixtureBuilder.builder()
                .memberId(member.getId()).type(TokenType.FREE).tokenCount(freeTokenCount).build());
        tokenRepository.save(TokenFixtureBuilder.builder()
                .memberId(member.getId()).type(TokenType.PAID).tokenCount(0).build());
        return member;
    }
}
