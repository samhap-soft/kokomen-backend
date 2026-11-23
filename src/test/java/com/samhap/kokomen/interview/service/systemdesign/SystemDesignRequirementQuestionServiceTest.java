package com.samhap.kokomen.interview.service.systemdesign;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.samhap.kokomen.global.BaseTest;
import com.samhap.kokomen.global.exception.BadRequestException;
import com.samhap.kokomen.global.exception.ConflictException;
import com.samhap.kokomen.global.fixture.interview.InterviewFixtureBuilder;
import com.samhap.kokomen.global.fixture.member.MemberFixtureBuilder;
import com.samhap.kokomen.global.fixture.systemdesign.SystemDesignHiddenRequirementFixtureBuilder;
import com.samhap.kokomen.global.fixture.systemdesign.SystemDesignInterviewFixtureBuilder;
import com.samhap.kokomen.global.fixture.systemdesign.SystemDesignProblemContextFixture;
import com.samhap.kokomen.global.fixture.systemdesign.SystemDesignProblemFixtureBuilder;
import com.samhap.kokomen.global.fixture.systemdesign.SystemDesignProblemTestSupport;
import com.samhap.kokomen.global.fixture.systemdesign.SystemDesignRequirementQuestionFixtureBuilder;
import com.samhap.kokomen.global.fixture.token.TokenFixtureBuilder;
import com.samhap.kokomen.interview.domain.Interview;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignInterview;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignProblem;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignProblemState;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignRequirementQuestion;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignStage;
import com.samhap.kokomen.interview.external.dto.response.SystemDesignRequirementAnswerResult;
import com.samhap.kokomen.interview.repository.InterviewRepository;
import com.samhap.kokomen.interview.repository.systemdesign.SystemDesignHiddenRequirementRepository;
import com.samhap.kokomen.interview.repository.systemdesign.SystemDesignInterviewRepository;
import com.samhap.kokomen.interview.repository.systemdesign.SystemDesignProblemRepository;
import com.samhap.kokomen.interview.repository.systemdesign.SystemDesignRequirementQuestionRepository;
import com.samhap.kokomen.interview.service.dto.systemdesign.SystemDesignRequirementAnswerResponse;
import com.samhap.kokomen.interview.service.systemdesign.SystemDesignRequirementQuestionService.RequirementQuestionDraft;
import com.samhap.kokomen.interview.tool.SystemDesignRequirementTurn;
import com.samhap.kokomen.member.domain.Member;
import com.samhap.kokomen.member.repository.MemberRepository;
import com.samhap.kokomen.token.domain.TokenType;
import com.samhap.kokomen.token.repository.TokenRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class SystemDesignRequirementQuestionServiceTest extends BaseTest {

    private static final String DEFAULT_ANSWER = "등록되지 않은 내용이에요. 가정하고 진행하세요.";
    private static final String DAILY_CREATION_VOLUME_ANSWER = "새로 만들어지는 단축 URL은 하루 평균 10만 개 정도예요.";

    @Autowired
    private SystemDesignRequirementQuestionService requirementQuestionService;
    @Autowired
    private SystemDesignInterviewService systemDesignInterviewService;
    @Autowired
    private SystemDesignProblemTestSupport problemTestSupport;
    @Autowired
    private SystemDesignProblemRepository problemRepository;
    @Autowired
    private SystemDesignHiddenRequirementRepository hiddenRequirementRepository;
    @Autowired
    private SystemDesignInterviewRepository systemDesignInterviewRepository;
    @Autowired
    private SystemDesignRequirementQuestionRepository requirementQuestionRepository;
    @Autowired
    private InterviewRepository interviewRepository;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private TokenRepository tokenRepository;

    // TRUNCATE 뒤 테이블마다 id가 1부터 다시 시작해 interview·system_design_interview·문제·역질문의 id가 같아지지 않게,
    // 다른 회원이 다른 문제(숨은 요구사항 code가 다르다)로 역질문까지 한 면접을 먼저 둔다. 면접이나 문제를 다른 id로 찾으면
    // 그 회원의 문제 문맥이나 역질문이 섞여 드러난다. 문제는 INACTIVE라 시작 API가 고르지 않는다.
    @BeforeEach
    void setUpOtherMemberInterview() {
        Member otherMember = memberRepository.save(MemberFixtureBuilder.builder().nickname("기록이 있는 회원").build());
        SystemDesignProblem retiredProblem = problemRepository.save(SystemDesignProblemFixtureBuilder.builder()
                .state(SystemDesignProblemState.INACTIVE)
                .title("지난 문제")
                .build());
        hiddenRequirementRepository.save(SystemDesignHiddenRequirementFixtureBuilder.builder()
                .problem(retiredProblem)
                .code("RETIRED_REQUIREMENT")
                .name("지난 요구사항")
                .answer("지난 문제의 정해 둔 사실이에요.")
                .displayOrder(1)
                .build());
        SystemDesignInterview otherInterview = saveSystemDesignInterview(otherMember, retiredProblem,
                SystemDesignStage.REQUIREMENTS);
        requirementQuestionRepository.save(SystemDesignRequirementQuestionFixtureBuilder.builder()
                .systemDesignInterview(otherInterview)
                .questionOrder(1)
                .content("지난 문제의 역질문")
                .answer("지난 문제의 답")
                .askedRequirementCodes(List.of("RETIRED_REQUIREMENT"))
                .build());
    }

    @Test
    void 준비는_이전_역질문과_문제_문맥으로_command를_만든다() {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        SystemDesignProblem problem = problemTestSupport.saveProblem();
        SystemDesignInterview systemDesignInterview = saveSystemDesignInterview(member, problem,
                SystemDesignStage.REQUIREMENTS);
        saveRequirementQuestion(systemDesignInterview, 1, "하루에 몇 개나 만들어지나요?", "하루 평균 10만 개 정도예요.");
        saveRequirementQuestion(systemDesignInterview, 2, "그럼 조회는요?", "조회는 생성의 100배쯤 돼요.");
        Long interviewId = systemDesignInterview.getInterview().getId();

        // when
        RequirementQuestionDraft draft = requirementQuestionService.prepare(interviewId, member.getId(),
                " 얼마나 오래 보관하나요? ");

        // then
        assertAll(
                () -> assertThat(draft.interviewId()).isEqualTo(interviewId),
                () -> assertThat(draft.content()).isEqualTo("얼마나 오래 보관하나요?"),
                () -> assertThat(draft.command().interviewId()).isEqualTo(interviewId),
                () -> assertThat(draft.command().currentQuestion()).isEqualTo("얼마나 오래 보관하나요?"),
                () -> assertThat(draft.command().previousTurns()).containsExactly(
                        new SystemDesignRequirementTurn("하루에 몇 개나 만들어지나요?", "하루 평균 10만 개 정도예요."),
                        new SystemDesignRequirementTurn("그럼 조회는요?", "조회는 생성의 100배쯤 돼요.")),
                () -> assertThat(draft.command().problem())
                        .isEqualTo(SystemDesignProblemContextFixture.urlShortener(problem.getId()))
        );
    }

    @Test
    void 물은_요구사항_code_목록을_역질문_행에_저장한다() {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        SystemDesignInterview systemDesignInterview = saveSystemDesignInterview(member,
                problemTestSupport.saveProblem(), SystemDesignStage.REQUIREMENTS);
        saveRequirementQuestion(systemDesignInterview, 1, "단축 키는 몇 자까지 되나요?", "7자 이하여야 해요.");
        RequirementQuestionDraft draft = requirementQuestionService.prepare(
                systemDesignInterview.getInterview().getId(), member.getId(), "하루 생성량과 보관 기간을 알려 주세요.");

        // when
        SystemDesignRequirementAnswerResponse response = requirementQuestionService.save(draft,
                new SystemDesignRequirementAnswerResult(List.of("DAILY_CREATION_VOLUME", "RETENTION_PERIOD"),
                        "하루 평균 10만 개가 만들어지고, 만든 날부터 5년 동안 열려야 해요."));

        // then
        assertAll(
                () -> assertThat(response.question()).isEqualTo("하루 생성량과 보관 기간을 알려 주세요."),
                () -> assertThat(response.answer()).isEqualTo("하루 평균 10만 개가 만들어지고, 만든 날부터 5년 동안 열려야 해요."),
                () -> assertThat(response.remainingCount()).isEqualTo(3),
                () -> assertThat(readRequirementQuestions(systemDesignInterview))
                        .extracting(SystemDesignRequirementQuestion::getQuestionOrder,
                                SystemDesignRequirementQuestion::getContent,
                                SystemDesignRequirementQuestion::getAskedRequirementCodes)
                        .containsExactly(
                                tuple(1, "단축 키는 몇 자까지 되나요?", List.of("DAILY_CREATION_VOLUME")),
                                tuple(2, "하루 생성량과 보관 기간을 알려 주세요.",
                                        List.of("DAILY_CREATION_VOLUME", "RETENTION_PERIOD")))
        );
    }

    @Test
    void 물은_요구사항이_없으면_정해진_문구를_저장한다() {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        SystemDesignInterview systemDesignInterview = saveSystemDesignInterview(member,
                problemTestSupport.saveProblem(), SystemDesignStage.REQUIREMENTS);
        RequirementQuestionDraft draft = requirementQuestionService.prepare(
                systemDesignInterview.getInterview().getId(), member.getId(), "요구사항을 전부 알려 주세요.");

        // when
        SystemDesignRequirementAnswerResponse response = requirementQuestionService.save(draft,
                new SystemDesignRequirementAnswerResult(List.of(), "하루 평균 10만 개가 만들어져요."));

        // then
        assertAll(
                () -> assertThat(response.answer()).isEqualTo(DEFAULT_ANSWER),
                () -> assertThat(response.remainingCount()).isEqualTo(4),
                () -> assertThat(readRequirementQuestions(systemDesignInterview)).singleElement()
                        .extracting(SystemDesignRequirementQuestion::getAnswer,
                                SystemDesignRequirementQuestion::getAskedRequirementCodes)
                        .containsExactly(DEFAULT_ANSWER, List.of())
        );
    }

    // 면접자가 직접 계산할 값(초당 요청 수)을 LLM이 대신 계산한 답은 시드 답으로 바꿔 저장한다. 물은 요구사항 code는 그대로 남긴다.
    @Test
    void 시드에_없는_숫자를_쓴_답은_시드_답으로_저장한다() {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        SystemDesignInterview systemDesignInterview = saveSystemDesignInterview(member,
                problemTestSupport.saveProblem(), SystemDesignStage.REQUIREMENTS);
        RequirementQuestionDraft draft = requirementQuestionService.prepare(
                systemDesignInterview.getInterview().getId(), member.getId(), "하루에 몇 개나 만들어지나요?");

        // when
        SystemDesignRequirementAnswerResponse response = requirementQuestionService.save(draft,
                new SystemDesignRequirementAnswerResult(List.of("DAILY_CREATION_VOLUME"),
                        "하루 평균 10만 개, 초당 약 1.2건 정도예요."));

        // then
        assertAll(
                () -> assertThat(response.answer()).isEqualTo(DAILY_CREATION_VOLUME_ANSWER),
                () -> assertThat(readRequirementQuestions(systemDesignInterview)).singleElement()
                        .extracting(SystemDesignRequirementQuestion::getAnswer,
                                SystemDesignRequirementQuestion::getAskedRequirementCodes)
                        .containsExactly(DAILY_CREATION_VOLUME_ANSWER, List.of("DAILY_CREATION_VOLUME"))
        );
    }

    // U+3000(전각 공백)은 JS trim()이 지우는 공백이다. 프론트가 센 글자 수와 서버가 센 글자 수가 같아야 한다.
    @Test
    void 질문_앞뒤의_공백과_전각_공백을_지우고_저장한다() {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        SystemDesignInterview systemDesignInterview = saveSystemDesignInterview(member,
                problemTestSupport.saveProblem(), SystemDesignStage.REQUIREMENTS);
        String paddedQuestion = "　 \t하루에 몇 개나　만들어지나요?\n　";

        // when
        RequirementQuestionDraft draft = requirementQuestionService.prepare(
                systemDesignInterview.getInterview().getId(), member.getId(), paddedQuestion);
        SystemDesignRequirementAnswerResponse response = requirementQuestionService.save(draft,
                new SystemDesignRequirementAnswerResult(List.of(), ""));

        // then
        assertAll(
                () -> assertThat(response.question()).isEqualTo("하루에 몇 개나　만들어지나요?"),
                () -> assertThat(readRequirementQuestions(systemDesignInterview)).singleElement()
                        .extracting(SystemDesignRequirementQuestion::getContent)
                        .isEqualTo("하루에 몇 개나　만들어지나요?")
        );
    }

    @Test
    void 검사_순서는_단계_길이_개수다() {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        SystemDesignProblem problem = problemTestSupport.saveProblem();
        Long finishedRequirementsInterviewId = saveSystemDesignInterview(member, problem,
                SystemDesignStage.HIGH_LEVEL).getInterview().getId();
        SystemDesignInterview fullInterview = saveSystemDesignInterview(member, problem,
                SystemDesignStage.REQUIREMENTS);
        for (int order = 1; order <= SystemDesignRequirementQuestion.MAX_COUNT; order++) {
            saveRequirementQuestion(fullInterview, order, "역질문 " + order, "답 " + order);
        }
        String tooLongQuestion = "가".repeat(201);

        // when & then
        assertAll(
                () -> assertThatThrownBy(() -> requirementQuestionService.prepare(finishedRequirementsInterviewId,
                        member.getId(), tooLongQuestion))
                        .isInstanceOf(ConflictException.class)
                        .hasMessage("요구사항 단계가 끝났어요."),
                () -> assertThatThrownBy(() -> requirementQuestionService.prepare(
                        fullInterview.getInterview().getId(), member.getId(), tooLongQuestion))
                        .isInstanceOf(BadRequestException.class)
                        .hasMessage("질문은 1~200자로 적어 주세요."),
                () -> assertThatThrownBy(() -> requirementQuestionService.prepare(
                        fullInterview.getInterview().getId(), member.getId(), "하루에 몇 개나 만들어지나요?"))
                        .isInstanceOf(BadRequestException.class)
                        .hasMessage("역질문은 5개까지 할 수 있어요.")
        );
    }

    // 진행 락이 막는 경쟁이지만, 락이 풀린 뒤 늦게 끝난 요청처럼 준비와 저장 사이에 5번째가 먼저 저장된 경우를 다시 막는다.
    @Test
    void 저장할_때_개수를_다시_확인한다() {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        SystemDesignInterview systemDesignInterview = saveSystemDesignInterview(member,
                problemTestSupport.saveProblem(), SystemDesignStage.REQUIREMENTS);
        for (int order = 1; order < SystemDesignRequirementQuestion.MAX_COUNT; order++) {
            saveRequirementQuestion(systemDesignInterview, order, "역질문 " + order, "답 " + order);
        }
        RequirementQuestionDraft draft = requirementQuestionService.prepare(
                systemDesignInterview.getInterview().getId(), member.getId(), "다섯 번째 역질문");
        saveRequirementQuestion(systemDesignInterview, SystemDesignRequirementQuestion.MAX_COUNT, "먼저 저장된 역질문",
                "먼저 저장된 답");

        // when & then
        assertThatThrownBy(() -> requirementQuestionService.save(draft,
                new SystemDesignRequirementAnswerResult(List.of(), "")))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("역질문은 5개까지 할 수 있어요.");
        assertThat(readRequirementQuestions(systemDesignInterview))
                .extracting(SystemDesignRequirementQuestion::getContent)
                .doesNotContain("다섯 번째 역질문")
                .hasSize(SystemDesignRequirementQuestion.MAX_COUNT);
    }

    @Test
    void 저장할_때_단계를_다시_확인한다() {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        SystemDesignInterview systemDesignInterview = saveSystemDesignInterview(member,
                problemTestSupport.saveProblem(), SystemDesignStage.REQUIREMENTS);
        Long interviewId = systemDesignInterview.getInterview().getId();
        RequirementQuestionDraft draft = requirementQuestionService.prepare(interviewId, member.getId(),
                "하루에 몇 개나 만들어지나요?");
        systemDesignInterviewService.completeRequirements(interviewId, member.getId());

        // when & then
        assertThatThrownBy(() -> requirementQuestionService.save(draft,
                new SystemDesignRequirementAnswerResult(List.of("DAILY_CREATION_VOLUME"), "하루 평균 10만 개 정도예요.")))
                .isInstanceOf(ConflictException.class)
                .hasMessage("요구사항 단계가 끝났어요.");
        assertThat(readRequirementQuestions(systemDesignInterview)).isEmpty();
    }

    @Test
    void 역질문은_토큰을_검증하지도_차감하지도_않는다() {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        tokenRepository.save(TokenFixtureBuilder.builder()
                .memberId(member.getId()).type(TokenType.FREE).tokenCount(0).build());
        tokenRepository.save(TokenFixtureBuilder.builder()
                .memberId(member.getId()).type(TokenType.PAID).tokenCount(0).build());
        SystemDesignInterview systemDesignInterview = saveSystemDesignInterview(member,
                problemTestSupport.saveProblem(), SystemDesignStage.REQUIREMENTS);

        // when
        RequirementQuestionDraft draft = requirementQuestionService.prepare(
                systemDesignInterview.getInterview().getId(), member.getId(), "하루에 몇 개나 만들어지나요?");
        requirementQuestionService.save(draft, new SystemDesignRequirementAnswerResult(
                List.of("DAILY_CREATION_VOLUME"), "하루 평균 10만 개 정도예요."));

        // then
        assertAll(
                () -> assertThat(readRequirementQuestions(systemDesignInterview)).hasSize(1),
                () -> assertThat(readTokenCount(member, TokenType.FREE)).isZero(),
                () -> assertThat(readTokenCount(member, TokenType.PAID)).isZero()
        );
    }

    private SystemDesignInterview saveSystemDesignInterview(Member member, SystemDesignProblem problem,
                                                            SystemDesignStage stage) {
        Interview interview = interviewRepository.save(InterviewFixtureBuilder.systemDesign().member(member).build());
        return systemDesignInterviewRepository.save(SystemDesignInterviewFixtureBuilder.builder()
                .interview(interview)
                .problem(problem)
                .stage(stage)
                .build());
    }

    private void saveRequirementQuestion(SystemDesignInterview systemDesignInterview, int questionOrder,
                                         String content, String answer) {
        requirementQuestionRepository.save(SystemDesignRequirementQuestionFixtureBuilder.builder()
                .systemDesignInterview(systemDesignInterview)
                .questionOrder(questionOrder)
                .content(content)
                .answer(answer)
                .build());
    }

    private int readTokenCount(Member member, TokenType type) {
        return tokenRepository.findByMemberIdAndType(member.getId(), type).orElseThrow().getTokenCount();
    }

    private List<SystemDesignRequirementQuestion> readRequirementQuestions(
            SystemDesignInterview systemDesignInterview) {
        return requirementQuestionRepository.findBySystemDesignInterviewOrderByQuestionOrderAsc(systemDesignInterview);
    }
}
