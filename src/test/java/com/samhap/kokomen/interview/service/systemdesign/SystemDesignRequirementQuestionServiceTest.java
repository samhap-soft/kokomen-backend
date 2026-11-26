package com.samhap.kokomen.interview.service.systemdesign;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.samhap.kokomen.category.domain.Category;
import com.samhap.kokomen.global.BaseTest;
import com.samhap.kokomen.global.exception.BadRequestException;
import com.samhap.kokomen.global.exception.ConflictException;
import com.samhap.kokomen.global.exception.ForbiddenException;
import com.samhap.kokomen.global.fixture.interview.InterviewFixtureBuilder;
import com.samhap.kokomen.global.fixture.interview.RootQuestionFixtureBuilder;
import com.samhap.kokomen.global.fixture.member.MemberFixtureBuilder;
import com.samhap.kokomen.global.fixture.systemdesign.SystemDesignHiddenRequirementFixtureBuilder;
import com.samhap.kokomen.global.fixture.systemdesign.SystemDesignInterviewFixtureBuilder;
import com.samhap.kokomen.global.fixture.systemdesign.SystemDesignProblemContextFixture;
import com.samhap.kokomen.global.fixture.systemdesign.SystemDesignProblemFixtureBuilder;
import com.samhap.kokomen.global.fixture.systemdesign.SystemDesignProblemTestSupport;
import com.samhap.kokomen.global.fixture.systemdesign.SystemDesignRequirementQuestionFixtureBuilder;
import com.samhap.kokomen.global.fixture.token.TokenFixtureBuilder;
import com.samhap.kokomen.interview.domain.Interview;
import com.samhap.kokomen.interview.domain.RootQuestion;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignInterview;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignProblem;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignProblemState;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignRequirementQuestion;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignStage;
import com.samhap.kokomen.interview.external.dto.response.SystemDesignRequirementAnswerResult;
import com.samhap.kokomen.interview.repository.InterviewRepository;
import com.samhap.kokomen.interview.repository.RootQuestionRepository;
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
import org.springframework.jdbc.core.JdbcTemplate;

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
    private RootQuestionRepository rootQuestionRepository;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private TokenRepository tokenRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    // TRUNCATE 뒤에는 테이블마다 id가 1부터 다시 시작한다. 테이블마다 다른 수의 행(회원 1, 문제 2, 시스템 설계 면접 3, 면접 4,
    // 역질문 1)을 먼저 두어, 각 테스트가 만드는 회원·문제·시스템 설계 면접·면접의 id가 서로 같아지지 않게 한다. 면접이나 문제를
    // 다른 테이블의 id로 찾으면 다른 회원의 문제 문맥·역질문이 섞이거나 행을 못 찾아 드러난다. 문제는 INACTIVE라 시작 API가 고르지 않는다.
    @BeforeEach
    void setUpOtherMemberInterviews() {
        Member otherMember = memberRepository.save(MemberFixtureBuilder.builder().nickname("기록이 있는 회원").build());
        SystemDesignProblem retiredProblem = saveRetiredProblem("지난 문제", "RETIRED_REQUIREMENT");
        saveRetiredProblem("더 지난 문제", "OLDER_RETIRED_REQUIREMENT");
        SystemDesignInterview otherInterview = saveSystemDesignInterview(otherMember, retiredProblem,
                SystemDesignStage.REQUIREMENTS);
        saveSystemDesignInterview(otherMember, retiredProblem, SystemDesignStage.HIGH_LEVEL);
        saveSystemDesignInterview(otherMember, retiredProblem, SystemDesignStage.DEEP_DIVE);
        requirementQuestionRepository.save(SystemDesignRequirementQuestionFixtureBuilder.builder()
                .systemDesignInterview(otherInterview)
                .questionOrder(1)
                .content("지난 문제의 역질문")
                .answer("지난 문제의 답")
                .askedRequirementCodes(List.of("RETIRED_REQUIREMENT"))
                .build());
        // 테스트가 기본값(OPERATING_SYSTEM, 순번 1)으로 루트 질문을 또 저장해도 (카테고리, 순번) 유니크와 겹치지 않게 한다.
        RootQuestion rootQuestion = rootQuestionRepository.save(RootQuestionFixtureBuilder.builder()
                .category(Category.NETWORK)
                .build());
        interviewRepository.save(InterviewFixtureBuilder.builder()
                .member(otherMember)
                .rootQuestion(rootQuestion)
                .build());
    }

    @Test
    void 선행_데이터_덕분에_회원_문제_시스템_설계_면접_면접의_id가_서로_다르다() {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        SystemDesignProblem problem = problemTestSupport.saveProblem();

        // when
        SystemDesignInterview systemDesignInterview = saveSystemDesignInterview(member, problem,
                SystemDesignStage.REQUIREMENTS);

        // then
        assertThat(List.of(member.getId(), problem.getId(), systemDesignInterview.getId(),
                systemDesignInterview.getInterview().getId())).doesNotHaveDuplicates();
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
        String paddedQuestion = "\u3000 \t하루에 몇 개나\u3000만들어지나요?\n\u3000";

        // when
        RequirementQuestionDraft draft = requirementQuestionService.prepare(
                systemDesignInterview.getInterview().getId(), member.getId(), paddedQuestion);
        SystemDesignRequirementAnswerResponse response = requirementQuestionService.save(draft,
                new SystemDesignRequirementAnswerResult(List.of(), ""));

        // then
        assertAll(
                () -> assertThat(response.question()).isEqualTo("하루에 몇 개나\u3000만들어지나요?"),
                () -> assertThat(readRequirementQuestions(systemDesignInterview)).singleElement()
                        .extracting(SystemDesignRequirementQuestion::getContent)
                        .isEqualTo("하루에 몇 개나\u3000만들어지나요?")
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

    @Test
    void 다섯_번째_역질문을_저장하면_남은_개수가_0이다() {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        SystemDesignInterview systemDesignInterview = saveSystemDesignInterview(member,
                problemTestSupport.saveProblem(), SystemDesignStage.REQUIREMENTS);
        for (int order = 1; order < SystemDesignRequirementQuestion.MAX_COUNT; order++) {
            saveRequirementQuestion(systemDesignInterview, order, "역질문 " + order, "답 " + order);
        }
        RequirementQuestionDraft draft = requirementQuestionService.prepare(
                systemDesignInterview.getInterview().getId(), member.getId(), "다섯 번째 역질문");

        // when
        SystemDesignRequirementAnswerResponse response = requirementQuestionService.save(draft,
                new SystemDesignRequirementAnswerResult(List.of(), ""));

        // then
        assertAll(
                () -> assertThat(response.remainingCount()).isZero(),
                () -> assertThat(readRequirementQuestions(systemDesignInterview))
                        .extracting(SystemDesignRequirementQuestion::getQuestionOrder,
                                SystemDesignRequirementQuestion::getContent)
                        .hasSize(SystemDesignRequirementQuestion.MAX_COUNT)
                        .last()
                        .isEqualTo(tuple(SystemDesignRequirementQuestion.MAX_COUNT, "다섯 번째 역질문"))
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

    // 응답을 받지 못해 같은 질문을 다시 보낸 경우다. LLM 입력 대신 직전 행의 질문·답·남은 개수를 담아, 파사드가 LLM을 부르지 않고 그대로 준다.
    @Test
    void 직전_역질문과_다듬은_내용이_같고_60초_안이면_LLM_입력_대신_직전_행의_응답을_담는다() {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        SystemDesignInterview systemDesignInterview = saveSystemDesignInterview(member,
                problemTestSupport.saveProblem(), SystemDesignStage.REQUIREMENTS);
        saveRequirementQuestion(systemDesignInterview, 1, "하루에 몇 개나 만들어지나요?", "하루 평균 10만 개 정도예요.");
        saveRequirementQuestion(systemDesignInterview, 2, "단축 키는 몇 자까지 되나요?", "7자 이하여야 해요.");

        // when
        RequirementQuestionDraft draft = requirementQuestionService.prepare(
                systemDesignInterview.getInterview().getId(), member.getId(), "\u3000단축 키는 몇 자까지 되나요? ");

        // then
        assertAll(
                () -> assertThat(draft.isRetry()).isTrue(),
                () -> assertThat(draft.command()).isNull(),
                () -> assertThat(draft.retriedResponse()).isEqualTo(
                        new SystemDesignRequirementAnswerResponse("단축 키는 몇 자까지 되나요?", "7자 이하여야 해요.", 3))
        );
    }

    @Test
    void 직전_역질문과_같아도_저장한_지_60초가_지났으면_새_역질문으로_준비한다() {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        SystemDesignInterview systemDesignInterview = saveSystemDesignInterview(member,
                problemTestSupport.saveProblem(), SystemDesignStage.REQUIREMENTS);
        saveRequirementQuestion(systemDesignInterview, 1, "하루에 몇 개나 만들어지나요?", "하루 평균 10만 개 정도예요.");
        moveSavedAtBack(systemDesignInterview, 1, 61);

        // when
        RequirementQuestionDraft draft = requirementQuestionService.prepare(
                systemDesignInterview.getInterview().getId(), member.getId(), "하루에 몇 개나 만들어지나요?");

        // then
        assertAll(
                () -> assertThat(draft.isRetry()).isFalse(),
                () -> assertThat(draft.retriedResponse()).isNull(),
                () -> assertThat(draft.command().currentQuestion()).isEqualTo("하루에 몇 개나 만들어지나요?")
        );
    }

    // 저장 시각을 DB에서 옮겨 시간이 지난 것처럼 만든다. 60초 안쪽이면 재시도, 바깥이면 새 역질문이어야 경계를 실제 왕복(JPA로 읽은
    // 저장 시각과 서비스의 현재 시각 비교)으로 확인한 것이다.
    @Test
    void 저장한_지_55초인_직전_역질문과_같은_질문은_아직_재시도다() {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        SystemDesignInterview systemDesignInterview = saveSystemDesignInterview(member,
                problemTestSupport.saveProblem(), SystemDesignStage.REQUIREMENTS);
        saveRequirementQuestion(systemDesignInterview, 1, "하루에 몇 개나 만들어지나요?", "하루 평균 10만 개 정도예요.");
        moveSavedAtBack(systemDesignInterview, 1, 55);

        // when
        RequirementQuestionDraft draft = requirementQuestionService.prepare(
                systemDesignInterview.getInterview().getId(), member.getId(), "하루에 몇 개나 만들어지나요?");

        // then
        assertThat(draft.isRetry()).isTrue();
    }

    // 직전 역질문만 본다. 더 앞의 역질문과 같은 질문은 다시 물은 것으로 보고 새로 답한다.
    @Test
    void 직전보다_앞의_역질문과_같은_질문은_새_역질문으로_준비한다() {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        SystemDesignInterview systemDesignInterview = saveSystemDesignInterview(member,
                problemTestSupport.saveProblem(), SystemDesignStage.REQUIREMENTS);
        saveRequirementQuestion(systemDesignInterview, 1, "하루에 몇 개나 만들어지나요?", "하루 평균 10만 개 정도예요.");
        saveRequirementQuestion(systemDesignInterview, 2, "단축 키는 몇 자까지 되나요?", "7자 이하여야 해요.");

        // when
        RequirementQuestionDraft draft = requirementQuestionService.prepare(
                systemDesignInterview.getInterview().getId(), member.getId(), "하루에 몇 개나 만들어지나요?");

        // then
        assertThat(draft.isRetry()).isFalse();
    }

    // 같은 회원의 다른 면접에 같은 질문이 방금 저장됐어도 이 면접에는 직전 역질문이 없으니 새로 묻는다.
    @Test
    void 다른_면접의_직전_역질문과_같은_질문은_재시도가_아니다() {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        SystemDesignProblem problem = problemTestSupport.saveProblem();
        SystemDesignInterview askedInterview = saveSystemDesignInterview(member, problem,
                SystemDesignStage.REQUIREMENTS);
        SystemDesignInterview otherInterview = saveSystemDesignInterview(member, problem,
                SystemDesignStage.REQUIREMENTS);
        saveRequirementQuestion(askedInterview, 1, "하루에 몇 개나 만들어지나요?", "하루 평균 10만 개 정도예요.");

        // when
        RequirementQuestionDraft draft = requirementQuestionService.prepare(
                otherInterview.getInterview().getId(), member.getId(), "하루에 몇 개나 만들어지나요?");

        // then
        assertThat(draft.isRetry()).isFalse();
    }

    // 재시도 판단은 개수 검사보다 앞이다. 5번째 역질문의 응답을 받지 못해 다시 보내도 400이 아니라 저장된 응답을 받는다.
    @Test
    void 다섯_개를_다_쓴_뒤에도_직전_역질문의_재시도는_개수_오류가_아니다() {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        SystemDesignInterview systemDesignInterview = saveSystemDesignInterview(member,
                problemTestSupport.saveProblem(), SystemDesignStage.REQUIREMENTS);
        for (int order = 1; order <= SystemDesignRequirementQuestion.MAX_COUNT; order++) {
            saveRequirementQuestion(systemDesignInterview, order, "역질문 " + order, "답 " + order);
        }
        Long interviewId = systemDesignInterview.getInterview().getId();

        // when
        RequirementQuestionDraft draft = requirementQuestionService.prepare(interviewId, member.getId(), "역질문 5");

        // then
        assertAll(
                () -> assertThat(draft.retriedResponse())
                        .isEqualTo(new SystemDesignRequirementAnswerResponse("역질문 5", "답 5", 0)),
                () -> assertThatThrownBy(() -> requirementQuestionService.prepare(interviewId, member.getId(),
                        "여섯 번째 역질문"))
                        .isInstanceOf(BadRequestException.class)
                        .hasMessage("역질문은 5개까지 할 수 있어요.")
        );
    }

    // 재시도 판단은 소유자와 단계 검사 뒤다. 다른 회원은 같은 질문을 보내도 403이고, 요구사항 정리가 끝났으면 409다.
    @Test
    void 다른_회원이거나_요구사항_정리가_끝났으면_같은_질문이어도_재시도가_아니라_오류다() {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        Member otherMember = memberRepository.save(MemberFixtureBuilder.builder().nickname("다른 회원").build());
        SystemDesignProblem problem = problemTestSupport.saveProblem();
        SystemDesignInterview requirementStageInterview = saveSystemDesignInterview(member, problem,
                SystemDesignStage.REQUIREMENTS);
        SystemDesignInterview designStageInterview = saveSystemDesignInterview(member, problem,
                SystemDesignStage.HIGH_LEVEL);
        saveRequirementQuestion(requirementStageInterview, 1, "하루에 몇 개나 만들어지나요?", "하루 평균 10만 개 정도예요.");
        saveRequirementQuestion(designStageInterview, 1, "하루에 몇 개나 만들어지나요?", "하루 평균 10만 개 정도예요.");

        // when & then
        assertAll(
                () -> assertThatThrownBy(() -> requirementQuestionService.prepare(
                        requirementStageInterview.getInterview().getId(), otherMember.getId(), "하루에 몇 개나 만들어지나요?"))
                        .isInstanceOf(ForbiddenException.class)
                        .hasMessage("본인의 면접만 볼 수 있어요."),
                () -> assertThatThrownBy(() -> requirementQuestionService.prepare(
                        designStageInterview.getInterview().getId(), member.getId(), "하루에 몇 개나 만들어지나요?"))
                        .isInstanceOf(ConflictException.class)
                        .hasMessage("요구사항 단계가 끝났어요.")
        );
    }

    @Test
    void 재시도로_준비한_역질문은_저장하지_않는다() {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        SystemDesignInterview systemDesignInterview = saveSystemDesignInterview(member,
                problemTestSupport.saveProblem(), SystemDesignStage.REQUIREMENTS);
        saveRequirementQuestion(systemDesignInterview, 1, "하루에 몇 개나 만들어지나요?", "하루 평균 10만 개 정도예요.");
        RequirementQuestionDraft draft = requirementQuestionService.prepare(
                systemDesignInterview.getInterview().getId(), member.getId(), "하루에 몇 개나 만들어지나요?");

        // when & then
        assertThatThrownBy(() -> requirementQuestionService.save(draft,
                new SystemDesignRequirementAnswerResult(List.of(), "")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(readRequirementQuestions(systemDesignInterview)).hasSize(1);
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

    private SystemDesignProblem saveRetiredProblem(String title, String requirementCode) {
        SystemDesignProblem problem = problemRepository.save(SystemDesignProblemFixtureBuilder.builder()
                .state(SystemDesignProblemState.INACTIVE)
                .title(title)
                .build());
        hiddenRequirementRepository.save(SystemDesignHiddenRequirementFixtureBuilder.builder()
                .problem(problem)
                .code(requirementCode)
                .name(title + "의 요구사항")
                .answer(title + "의 정해 둔 사실이에요.")
                .displayOrder(1)
                .build());
        return problem;
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

    // 저장 시각(created_at)을 DB 안에서 seconds초 앞으로 옮겨 그만큼 시간이 지난 것처럼 만든다. 자바의 LocalDateTime을 바로 넣으면
    // JPA와 시간대 변환이 달라(JVM이 UTC이고 연결은 Asia/Seoul) 9시간이 더 지난 값으로 읽힌다.
    private void moveSavedAtBack(SystemDesignInterview systemDesignInterview, int questionOrder, long seconds) {
        SystemDesignRequirementQuestion question = readRequirementQuestions(systemDesignInterview).stream()
                .filter(requirementQuestion -> requirementQuestion.getQuestionOrder() == questionOrder)
                .findFirst()
                .orElseThrow();
        jdbcTemplate.update("UPDATE system_design_requirement_question "
                        + "SET created_at = DATE_SUB(created_at, INTERVAL ? SECOND) WHERE id = ?",
                seconds, question.getId());
    }

    private int readTokenCount(Member member, TokenType type) {
        return tokenRepository.findByMemberIdAndType(member.getId(), type).orElseThrow().getTokenCount();
    }

    private List<SystemDesignRequirementQuestion> readRequirementQuestions(
            SystemDesignInterview systemDesignInterview) {
        return requirementQuestionRepository.findBySystemDesignInterviewOrderByQuestionOrderAsc(systemDesignInterview);
    }
}
