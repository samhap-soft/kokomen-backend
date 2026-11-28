package com.samhap.kokomen.global.fixture.systemdesign;

import static com.samhap.kokomen.answer.domain.AnswerRank.A;
import static com.samhap.kokomen.answer.domain.AnswerRank.B;
import static com.samhap.kokomen.answer.domain.AnswerRank.C;

import com.samhap.kokomen.answer.domain.AnswerRank;
import com.samhap.kokomen.category.domain.Category;
import com.samhap.kokomen.global.fixture.interview.InterviewFixtureBuilder;
import com.samhap.kokomen.global.fixture.interview.RootQuestionFixtureBuilder;
import com.samhap.kokomen.global.fixture.member.MemberFixtureBuilder;
import com.samhap.kokomen.interview.domain.Interview;
import com.samhap.kokomen.interview.domain.InterviewState;
import com.samhap.kokomen.interview.domain.RootQuestion;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignEvaluationPoint;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignInterview;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignProblem;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignProblemQuestion;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignProblemState;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignQuestion;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignReportScore;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignRequirementAnswerType;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignRequirementItem;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignRequirementQuestion;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignRequirementSource;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignStage;
import com.samhap.kokomen.interview.repository.InterviewRepository;
import com.samhap.kokomen.interview.repository.RootQuestionRepository;
import com.samhap.kokomen.interview.repository.systemdesign.SystemDesignAnswerRepository;
import com.samhap.kokomen.interview.repository.systemdesign.SystemDesignEvaluationPointRepository;
import com.samhap.kokomen.interview.repository.systemdesign.SystemDesignEvaluationResultRepository;
import com.samhap.kokomen.interview.repository.systemdesign.SystemDesignInterviewRepository;
import com.samhap.kokomen.interview.repository.systemdesign.SystemDesignProblemQuestionRepository;
import com.samhap.kokomen.interview.repository.systemdesign.SystemDesignProblemRepository;
import com.samhap.kokomen.interview.repository.systemdesign.SystemDesignQuestionRepository;
import com.samhap.kokomen.interview.repository.systemdesign.SystemDesignRequirementQuestionRepository;
import com.samhap.kokomen.interview.tool.SystemDesignProblemContext.Requirement;
import com.samhap.kokomen.member.domain.Member;
import com.samhap.kokomen.member.repository.MemberRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * 시스템 설계 면접을 진행 상태별로 저장한다. 문제는 {@link SystemDesignProblemTestSupport}의 URL 단축기 문제(기준 질문 6개)다.
 * MySQLDatabaseCleaner가 매 테스트 전에 모든 테이블을 TRUNCATE하므로 테스트가 이것으로 만든다.
 */
@Component
public class SystemDesignInterviewTestSupport {

    public static final int OTHER_MEMBER_QUESTION_COUNT = 2;

    private static final int OTHER_MEMBER_INTERVIEW_COUNT = 6;
    private static final int REPORT_PADDING_INTERVIEW_COUNT = 7;
    private static final int REPORT_PADDING_POINT_COUNT = 12;
    private static final List<AnswerRank> FINISHED_RANKS = List.of(B, B, C, A, B, C);
    // 마지막 답변 채점이 문제의 평가 포인트 순서로 낸 판정. 5개 중 1·2·4번을 충족했다.
    private static final List<Boolean> FINISHED_MET_BY_POINT = List.of(true, true, false, true, false);
    // 질문 순번 → 직전 답변의 채점이 다듬은 문장. 1번 질문은 기준 질문 그대로다.
    private static final Map<Integer, String> FINISHED_REFINED_QUESTIONS = Map.of(
            2, "방금 그린 API 서버에서 단축 키를 어떻게 만들고, 서로 다른 URL이 같은 키를 받지 않게 하려면 어떻게 하나요?",
            3, "하루 10만 개씩 5년 동안 쌓이는 단축 URL의 저장 용량을 추정하고, 어떤 저장소에 어떤 형태로 둘지 설명해 주세요.",
            4, "조회가 생성의 100배라면 리다이렉트 경로에서 어디가 먼저 병목이 되고, 어떻게 대응하나요?",
            5, "DB 서버가 한 대라면 그 서버가 내려갔을 때 이미 보낸 링크는 어떻게 되고, 어떻게 대비하나요?",
            6, "리다이렉트 응답을 301과 302 중 무엇으로 줄지, 캐시와 통계 측면에서 장단점을 비교해 설명해 주세요.");
    private static final List<String> FINISHED_ANSWERS = List.of(
            "생성 요청은 API 서버가 받아 단축 키를 만들고 원래 URL과 함께 DB에 저장해요. "
                    + "접속 요청은 API 서버가 DB에서 원래 URL을 찾아 리다이렉트해요.",
            "DB 자동 증가 값을 Base62로 바꿔 단축 키를 만들어요. 값이 겹치지 않으니 키 충돌이 없어요.",
            "하루 10만 개씩 5년이면 약 1억 8천만 개이고, 한 건을 500바이트로 잡으면 100GB 안팎이에요. "
                    + "단축 키를 기본 키로 하는 MySQL 테이블에 둬요.",
            "조회가 많으니 API 서버 앞에 Redis 캐시를 두고 자주 쓰는 단축 키를 먼저 읽어요. API 서버는 여러 대로 늘려요.",
            "DB를 복제해 두고 주 DB가 내려가면 복제본으로 전환해요.",
            "통계 기능이 없으니 브라우저가 기억하는 301로 응답해 서버 부하를 줄여요.");
    private static final List<String> FINISHED_FEEDBACKS = List.of(
            "생성과 접속 요청의 흐름을 나눠 설명했어요. 조회가 생성보다 훨씬 많다는 점을 구조에 반영하면 더 좋아요.",
            "카운터 인코딩으로 충돌을 피하는 근거가 분명해요. 단축 키 길이 제한도 함께 확인하면 좋아요.",
            "저장 용량을 추정했지만 저장소를 고른 이유를 요구사항과 연결하지 않았어요.",
            "조회와 생성 비율을 근거로 캐시를 두고 서버를 늘린 판단이 정확해요.",
            "복제와 장애 전환을 짚었어요. 캐시가 내려갔을 때의 동작도 설명하면 좋아요.",
            "301을 고른 근거는 있지만 302와 장단점을 비교하지 않았어요.");
    private static final String FINISHED_TOTAL_FEEDBACK = "조회와 생성 비율을 근거로 캐시를 두고 카운터 인코딩으로 키 충돌을 피한 "
            + "설계가 좋았어요. 저장소를 고른 이유와 301·302 리다이렉트의 장단점 비교가 부족했어요. "
            + "요구사항의 수치를 저장소와 응답 방식 선택으로 이어 설명하는 연습을 해 보세요.";
    private static final String UNREGISTERED_REQUIREMENT_QUESTION = "로그인한 사용자만 단축 URL을 만들 수 있나요?";
    private static final String UNREGISTERED_REQUIREMENT_ANSWER = "로그인 없이도 누구나 단축 URL을 만들 수 있어요.";
    // 정해 두지 않은 내용을 물은 역질문에 면접관이 정해 답하며 적은 확정 항목
    private static final SystemDesignRequirementItem UNREGISTERED_REQUIREMENT_ITEM = new SystemDesignRequirementItem(
            "로그인 여부", "로그인 없이도 누구나 단축 URL을 만들 수 있다.", SystemDesignRequirementSource.ASSUMED);

    private final SystemDesignProblemTestSupport problemTestSupport;
    private final MemberRepository memberRepository;
    private final InterviewRepository interviewRepository;
    private final RootQuestionRepository rootQuestionRepository;
    private final SystemDesignProblemRepository problemRepository;
    private final SystemDesignProblemQuestionRepository problemQuestionRepository;
    private final SystemDesignEvaluationPointRepository evaluationPointRepository;
    private final SystemDesignInterviewRepository systemDesignInterviewRepository;
    private final SystemDesignRequirementQuestionRepository requirementQuestionRepository;
    private final SystemDesignQuestionRepository questionRepository;
    private final SystemDesignAnswerRepository answerRepository;
    private final SystemDesignEvaluationResultRepository evaluationResultRepository;

    public SystemDesignInterviewTestSupport(SystemDesignProblemTestSupport problemTestSupport,
                                            MemberRepository memberRepository,
                                            InterviewRepository interviewRepository,
                                            RootQuestionRepository rootQuestionRepository,
                                            SystemDesignProblemRepository problemRepository,
                                            SystemDesignProblemQuestionRepository problemQuestionRepository,
                                            SystemDesignEvaluationPointRepository evaluationPointRepository,
                                            SystemDesignInterviewRepository systemDesignInterviewRepository,
                                            SystemDesignRequirementQuestionRepository requirementQuestionRepository,
                                            SystemDesignQuestionRepository questionRepository,
                                            SystemDesignAnswerRepository answerRepository,
                                            SystemDesignEvaluationResultRepository evaluationResultRepository) {
        this.problemTestSupport = problemTestSupport;
        this.memberRepository = memberRepository;
        this.interviewRepository = interviewRepository;
        this.rootQuestionRepository = rootQuestionRepository;
        this.problemRepository = problemRepository;
        this.problemQuestionRepository = problemQuestionRepository;
        this.evaluationPointRepository = evaluationPointRepository;
        this.systemDesignInterviewRepository = systemDesignInterviewRepository;
        this.requirementQuestionRepository = requirementQuestionRepository;
        this.questionRepository = questionRepository;
        this.answerRepository = answerRepository;
        this.evaluationResultRepository = evaluationResultRepository;
    }

    /**
     * TRUNCATE로 테이블마다 id가 1부터 다시 시작해도 값이 우연히 같아지지 않게 다른 회원의 기록을 먼저 둔다.
     * 질문 {@value #OTHER_MEMBER_QUESTION_COUNT}개와 답변 1개가 있어 뒤에 만드는 면접의 질문 PK는 순번과 다르다(순번 대신 PK로
     * 찾는 실수가 드러난다). 면접 {@value #OTHER_MEMBER_INTERVIEW_COUNT}개가 있어 뒤에 만드는 면접의 id는 질문 순번(1~6)보다 크고,
     * 출제하지 않는 문제 2개로 문제 id와 system_design_interview id도 어긋나게 한다. 루트 질문은 다른 테스트가 쓰는 카테고리와
     * 겹치지 않게 JAVA_SPRING으로 둔다.
     */
    public void saveOtherMemberRecords() {
        Member otherMember = memberRepository.save(MemberFixtureBuilder.builder().nickname("기록이 있는 회원").build());
        SystemDesignProblem retiredProblem = problemRepository.save(SystemDesignProblemFixtureBuilder.builder()
                .state(SystemDesignProblemState.INACTIVE)
                .build());
        problemRepository.save(SystemDesignProblemFixtureBuilder.builder()
                .state(SystemDesignProblemState.INACTIVE)
                .build());
        Interview otherInterview = interviewRepository.save(InterviewFixtureBuilder.systemDesign()
                .member(otherMember)
                .build());
        SystemDesignInterview otherSystemDesignInterview = systemDesignInterviewRepository.save(
                SystemDesignInterviewFixtureBuilder.builder()
                        .interview(otherInterview)
                        .problem(retiredProblem)
                        .stage(SystemDesignStage.HIGH_LEVEL)
                        .build());
        for (int order = 1; order <= OTHER_MEMBER_QUESTION_COUNT; order++) {
            SystemDesignProblemQuestion retiredQuestion = problemQuestionRepository.save(
                    SystemDesignProblemQuestionFixtureBuilder.builder()
                            .problem(retiredProblem)
                            .questionOrder(order)
                            .stage(SystemDesignStage.HIGH_LEVEL)
                            .build());
            SystemDesignQuestion question = questionRepository.save(SystemDesignQuestionFixtureBuilder.builder()
                    .systemDesignInterview(otherSystemDesignInterview)
                    .problemQuestion(retiredQuestion)
                    .build());
            if (order < OTHER_MEMBER_QUESTION_COUNT) {
                answerRepository.save(SystemDesignAnswerFixtureBuilder.builder().question(question).build());
            }
        }
        RootQuestion rootQuestion = rootQuestionRepository.save(RootQuestionFixtureBuilder.builder()
                .category(Category.JAVA_SPRING)
                .build());
        for (int i = 1; i < OTHER_MEMBER_INTERVIEW_COUNT; i++) {
            interviewRepository.save(InterviewFixtureBuilder.builder()
                    .member(otherMember)
                    .rootQuestion(rootQuestion)
                    .build());
        }
    }

    /**
     * 리포트 테스트용 다른 회원의 기록. {@link #saveOtherMemberRecords()}에 더해 다른 회원의 끝난 면접(리포트 데이터까지, 단축 키 길이와
     * 클릭 통계를 물었다), 그 회원의 기존 면접 {@value #REPORT_PADDING_INTERVIEW_COUNT}개, 출제하지 않는 문제의 평가 포인트
     * {@value #REPORT_PADDING_POINT_COUNT}개를 둔다. 다른 회원의 끝난 면접은 다른 문제를 풀었으므로, 면접을 가리지 않고 읽은 역질문·답변이
     * 리포트에 섞이면 드러나지만 같은 문제를 푼 면접의 판정이 섞이는 것은 드러나지 않는다. 그 경우는 같은 문제로 끝난 면접 둘을
     * {@link #saveFinishedInterview(Member, SystemDesignProblem, List, List, List)}로 저장해 확인한다.
     * 뒤에 저장하는 회원과 {@link #saveFinishedInterview} 면접의 id는 회원 < 문제 < 질문 PK < 면접 < 평가 포인트 순으로 서로 겹치지
     * 않는다. 아무 기록 없이 시작하는 테스트와 SystemDesignInterviewControllerTest의 @BeforeEach 기록 위에서 모두 그렇다.
     */
    public void saveOtherMemberReportRecords() {
        saveOtherMemberRecords();
        Member otherMember = memberRepository.save(MemberFixtureBuilder.builder().nickname("리포트가 있는 회원").build());
        saveFinishedInterview(otherMember, List.of(List.of("SHORT_KEY_LENGTH", "CLICK_STATISTICS")),
                List.of(A, A, A, A, A, A));
        RootQuestion rootQuestion = rootQuestionRepository.save(RootQuestionFixtureBuilder.builder()
                .category(Category.DATABASE)
                .build());
        for (int i = 0; i < REPORT_PADDING_INTERVIEW_COUNT; i++) {
            interviewRepository.save(InterviewFixtureBuilder.builder()
                    .member(otherMember)
                    .rootQuestion(rootQuestion)
                    .build());
        }
        SystemDesignProblem retiredProblem = problemRepository.save(SystemDesignProblemFixtureBuilder.builder()
                .state(SystemDesignProblemState.INACTIVE)
                .build());
        for (int i = 0; i < REPORT_PADDING_POINT_COUNT; i++) {
            evaluationPointRepository.save(SystemDesignEvaluationPointFixtureBuilder.builder()
                    .problem(retiredProblem)
                    .build());
        }
    }

    /**
     * 앞의 질문 ranks.size()개에 답이 저장된 면접. 6개 모두 답했으면 끝난 면접이고, 아니면 그다음 질문이 현재 질문이다.
     * 1번 질문은 기준 질문 그대로이고 2번부터는 "다듬은 n번 질문이에요."다. 답변은 "n번 답변이에요."이고 다이어그램은 비어 있다.
     * 끝난 면접의 등급 점수 합·종합 피드백·리포트 점수는 채우지 않는다.
     */
    public SystemDesignInterview saveInterviewWithAnswers(Member member, List<AnswerRank> ranks) {
        int answeredCount = ranks.size();
        boolean finished = answeredCount == SystemDesignInterview.MAX_QUESTION_COUNT;
        Interview interview = interviewRepository.save(InterviewFixtureBuilder.systemDesign()
                .member(member)
                .interviewState(finished ? InterviewState.FINISHED : InterviewState.IN_PROGRESS)
                .build());
        SystemDesignInterview systemDesignInterview = systemDesignInterviewRepository.save(
                SystemDesignInterviewFixtureBuilder.builder()
                        .interview(interview)
                        .problem(problemTestSupport.saveProblem())
                        .stage(finished ? SystemDesignStage.DEEP_DIVE
                                : SystemDesignStage.ofQuestionOrder(answeredCount + 1))
                        .build());
        int questionCount = finished ? SystemDesignInterview.MAX_QUESTION_COUNT : answeredCount + 1;
        for (int order = 1; order <= questionCount; order++) {
            SystemDesignQuestion question = saveQuestion(systemDesignInterview, order,
                    "다듬은 " + order + "번 질문이에요.");
            if (order <= answeredCount) {
                answerRepository.save(SystemDesignAnswerFixtureBuilder.builder()
                        .question(question)
                        .content(order + "번 답변이에요.")
                        .answerRank(ranks.get(order - 1))
                        .build());
            }
        }
        return systemDesignInterview;
    }

    /**
     * 리포트 데이터까지 저장한 끝난 면접. 마지막 답변을 저장할 때처럼 등급 점수 합·종합 피드백·리포트 점수·평가 포인트 판정을 채운다.
     * 답변 6개의 등급은 B·B·C·A·B·C(등급 점수 합 50)이고, 평가 포인트 5개 중 1·2·4번을 충족해 리포트 점수는 65다.
     * 1~3번 답변은 시작 다이어그램, 4~6번 답변은 캐시를 더한 다이어그램과 함께 냈고, 마지막 답변의 다이어그램이 현재 다이어그램이다.
     *
     * @param askedRequirementCodes 역질문마다 물은 숨은 요구사항 code(역질문 1번부터). 비어 있으면 정해 두지 않은 내용을 물은 역질문이다.
     *                              확정 항목은 정해 두지 않은 내용을 물은 역질문에만 하나 있다(시드 항목은 적지 않은 행)
     */
    public SystemDesignInterview saveFinishedInterview(Member member, List<List<String>> askedRequirementCodes) {
        return saveFinishedInterview(member, askedRequirementCodes, FINISHED_RANKS);
    }

    /**
     * 답변 등급을 정해 {@link #saveFinishedInterview(Member, List)}처럼 저장한다. 등급 점수 합(interview.total_score)과 리포트 점수는
     * 이 등급으로 계산한다.
     */
    public SystemDesignInterview saveFinishedInterview(Member member, List<List<String>> askedRequirementCodes,
                                                       List<AnswerRank> ranks) {
        return saveFinishedInterview(member, problemTestSupport.saveProblem(), askedRequirementCodes, ranks,
                FINISHED_MET_BY_POINT);
    }

    /**
     * 이미 있는 URL 단축기 문제로 {@link #saveFinishedInterview(Member, List)}처럼 저장한다. 같은 문제를 푼 면접끼리는 평가 포인트 행을
     * 함께 쓴다.
     *
     * @param problem    {@link SystemDesignProblemTestSupport#saveProblem()}으로 저장한 문제
     * @param metByPoint 문제의 평가 포인트 순서로 마지막 답변 채점이 낸 판정
     */
    public SystemDesignInterview saveFinishedInterview(Member member, SystemDesignProblem problem,
                                                       List<List<String>> askedRequirementCodes,
                                                       List<AnswerRank> ranks, List<Boolean> metByPoint) {
        List<SystemDesignEvaluationPoint> points =
                evaluationPointRepository.findByProblemOrderByDisplayOrderAsc(problem);
        int metCount = Math.toIntExact(metByPoint.stream().filter(Boolean::booleanValue).count());
        Interview interview = interviewRepository.save(InterviewFixtureBuilder.systemDesign()
                .member(member)
                .interviewState(InterviewState.FINISHED)
                .totalScore(ranks.stream().mapToInt(AnswerRank::getScore).sum())
                .totalFeedback(FINISHED_TOTAL_FEEDBACK)
                .finishedAt(LocalDateTime.now())
                .build());
        SystemDesignInterview systemDesignInterview = systemDesignInterviewRepository.save(
                SystemDesignInterviewFixtureBuilder.builder()
                        .interview(interview)
                        .problem(problem)
                        .stage(SystemDesignStage.DEEP_DIVE)
                        .currentDiagram(SystemDesignDiagramFixture.withCache())
                        .reportScore(SystemDesignReportScore.calculate(metCount, points.size(), ranks))
                        .build());
        for (int index = 0; index < askedRequirementCodes.size(); index++) {
            requirementQuestionRepository.save(requirementQuestion(systemDesignInterview, index + 1,
                    askedRequirementCodes.get(index)));
        }
        for (int order = 1; order <= SystemDesignInterview.MAX_QUESTION_COUNT; order++) {
            SystemDesignQuestion question = saveQuestion(systemDesignInterview, order,
                    FINISHED_REFINED_QUESTIONS.get(order));
            answerRepository.save(SystemDesignAnswerFixtureBuilder.builder()
                    .question(question)
                    .content(FINISHED_ANSWERS.get(order - 1))
                    .diagram(order <= SystemDesignInterview.HIGH_LEVEL_QUESTION_COUNT
                            ? SystemDesignDiagramFixture.urlShortenerStarter() : SystemDesignDiagramFixture.withCache())
                    .answerRank(ranks.get(order - 1))
                    .feedback(FINISHED_FEEDBACKS.get(order - 1))
                    .build());
        }
        // 판정 결과 id가 평가 포인트 id와 같은 순서로 붙지 않게 포인트의 역순으로 저장한다. id를 헷갈려 짝지으면 판정이 어긋난다.
        for (int index = points.size() - 1; index >= 0; index--) {
            evaluationResultRepository.save(SystemDesignEvaluationResultFixtureBuilder.builder()
                    .systemDesignInterview(systemDesignInterview)
                    .evaluationPoint(points.get(index))
                    .met(metByPoint.get(index))
                    .build());
        }
        return systemDesignInterview;
    }

    // 물은 요구사항의 이름으로 역질문을, 그 요구사항들의 정해 둔 답을 문제 순서로 이어 면접관 답을 만든다(시드 답으로 답할 때와 같은 모양).
    // 이 행은 확정 항목을 적지 않아 리포트가 정해 둔 사실을 폴백으로 넣는다. 물은 요구사항이 없으면 숨은 요구사항에 없는 내용을 물어
    // 면접관이 정해 답하고 확정 항목 하나(로그인 여부)를 적은 역질문이다.
    private static SystemDesignRequirementQuestion requirementQuestion(SystemDesignInterview systemDesignInterview,
                                                                       int questionOrder, List<String> askedCodes) {
        List<Requirement> askedRequirements = SystemDesignProblemContextFixture.URL_SHORTENER_REQUIREMENTS.stream()
                .filter(requirement -> askedCodes.contains(requirement.code()))
                .toList();
        SystemDesignRequirementQuestionFixtureBuilder builder = SystemDesignRequirementQuestionFixtureBuilder.builder()
                .systemDesignInterview(systemDesignInterview)
                .questionOrder(questionOrder)
                .askedRequirementCodes(askedCodes);
        if (askedRequirements.isEmpty()) {
            return builder.content(UNREGISTERED_REQUIREMENT_QUESTION)
                    .answer(UNREGISTERED_REQUIREMENT_ANSWER)
                    .answerType(SystemDesignRequirementAnswerType.ASSUMED)
                    .requirementItems(List.of(UNREGISTERED_REQUIREMENT_ITEM))
                    .build();
        }
        return builder.content(askedRequirements.stream()
                        .map(Requirement::name)
                        .collect(Collectors.joining(", ")) + "에 대해 알려 주세요.")
                .answer(askedRequirements.stream()
                        .map(Requirement::answer)
                        .collect(Collectors.joining(" ")))
                .build();
    }

    // 1번 질문은 요구사항 정리 완료가 낸 기준 질문 그대로이고, 2번부터는 refinedContent(직전 답변의 채점이 다듬은 문장)다.
    private SystemDesignQuestion saveQuestion(SystemDesignInterview systemDesignInterview, int questionOrder,
                                              String refinedContent) {
        SystemDesignProblemQuestion problemQuestion = problemQuestionRepository.findByProblemAndQuestionOrder(
                systemDesignInterview.getProblem(), questionOrder).orElseThrow();
        String content = questionOrder == 1 ? problemQuestion.getContent() : refinedContent;
        return questionRepository.save(SystemDesignQuestionFixtureBuilder.builder()
                .systemDesignInterview(systemDesignInterview)
                .problemQuestion(problemQuestion)
                .content(content)
                .build());
    }

    /**
     * 리포트를 조립할 때 찾는 id: 면접 id, 문제 id, 면접자 id, 질문 PK, 문제의 평가 포인트 id. 테스트가 서로 겹치지 않는지 확인할 때 쓴다.
     */
    public List<Long> readReportIds(SystemDesignInterview systemDesignInterview) {
        Interview interview = systemDesignInterview.getInterview();
        List<Long> ids = new ArrayList<>(List.of(interview.getId(), systemDesignInterview.getProblem().getId(),
                interview.getMember().getId()));
        questionRepository.findBySystemDesignInterviewOrderByQuestionOrderAsc(systemDesignInterview)
                .forEach(question -> ids.add(question.getId()));
        evaluationPointRepository.findByProblemOrderByDisplayOrderAsc(systemDesignInterview.getProblem())
                .forEach(point -> ids.add(point.getId()));
        return ids;
    }
}
