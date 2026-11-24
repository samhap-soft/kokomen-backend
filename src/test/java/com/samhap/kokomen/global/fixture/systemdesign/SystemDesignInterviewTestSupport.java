package com.samhap.kokomen.global.fixture.systemdesign;

import com.samhap.kokomen.answer.domain.AnswerRank;
import com.samhap.kokomen.category.domain.Category;
import com.samhap.kokomen.global.fixture.interview.InterviewFixtureBuilder;
import com.samhap.kokomen.global.fixture.interview.RootQuestionFixtureBuilder;
import com.samhap.kokomen.global.fixture.member.MemberFixtureBuilder;
import com.samhap.kokomen.interview.domain.Interview;
import com.samhap.kokomen.interview.domain.InterviewState;
import com.samhap.kokomen.interview.domain.RootQuestion;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignInterview;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignProblem;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignProblemQuestion;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignProblemState;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignQuestion;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignStage;
import com.samhap.kokomen.interview.repository.InterviewRepository;
import com.samhap.kokomen.interview.repository.RootQuestionRepository;
import com.samhap.kokomen.interview.repository.systemdesign.SystemDesignAnswerRepository;
import com.samhap.kokomen.interview.repository.systemdesign.SystemDesignInterviewRepository;
import com.samhap.kokomen.interview.repository.systemdesign.SystemDesignProblemQuestionRepository;
import com.samhap.kokomen.interview.repository.systemdesign.SystemDesignProblemRepository;
import com.samhap.kokomen.interview.repository.systemdesign.SystemDesignQuestionRepository;
import com.samhap.kokomen.member.domain.Member;
import com.samhap.kokomen.member.repository.MemberRepository;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 시스템 설계 면접을 진행 상태별로 저장한다. 문제는 {@link SystemDesignProblemTestSupport}의 URL 단축기 문제(기준 질문 6개)다.
 * MySQLDatabaseCleaner가 매 테스트 전에 모든 테이블을 TRUNCATE하므로 테스트가 이것으로 만든다.
 */
@Component
public class SystemDesignInterviewTestSupport {

    public static final int OTHER_MEMBER_QUESTION_COUNT = 2;

    private static final int OTHER_MEMBER_INTERVIEW_COUNT = 6;

    private final SystemDesignProblemTestSupport problemTestSupport;
    private final MemberRepository memberRepository;
    private final InterviewRepository interviewRepository;
    private final RootQuestionRepository rootQuestionRepository;
    private final SystemDesignProblemRepository problemRepository;
    private final SystemDesignProblemQuestionRepository problemQuestionRepository;
    private final SystemDesignInterviewRepository systemDesignInterviewRepository;
    private final SystemDesignQuestionRepository questionRepository;
    private final SystemDesignAnswerRepository answerRepository;

    public SystemDesignInterviewTestSupport(SystemDesignProblemTestSupport problemTestSupport,
                                            MemberRepository memberRepository,
                                            InterviewRepository interviewRepository,
                                            RootQuestionRepository rootQuestionRepository,
                                            SystemDesignProblemRepository problemRepository,
                                            SystemDesignProblemQuestionRepository problemQuestionRepository,
                                            SystemDesignInterviewRepository systemDesignInterviewRepository,
                                            SystemDesignQuestionRepository questionRepository,
                                            SystemDesignAnswerRepository answerRepository) {
        this.problemTestSupport = problemTestSupport;
        this.memberRepository = memberRepository;
        this.interviewRepository = interviewRepository;
        this.rootQuestionRepository = rootQuestionRepository;
        this.problemRepository = problemRepository;
        this.problemQuestionRepository = problemQuestionRepository;
        this.systemDesignInterviewRepository = systemDesignInterviewRepository;
        this.questionRepository = questionRepository;
        this.answerRepository = answerRepository;
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
            SystemDesignQuestion question = saveQuestion(systemDesignInterview, order);
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

    private SystemDesignQuestion saveQuestion(SystemDesignInterview systemDesignInterview, int questionOrder) {
        SystemDesignProblemQuestion problemQuestion = problemQuestionRepository.findByProblemAndQuestionOrder(
                systemDesignInterview.getProblem(), questionOrder).orElseThrow();
        String content = questionOrder == 1 ? problemQuestion.getContent() : "다듬은 " + questionOrder + "번 질문이에요.";
        return questionRepository.save(SystemDesignQuestionFixtureBuilder.builder()
                .systemDesignInterview(systemDesignInterview)
                .problemQuestion(problemQuestion)
                .content(content)
                .build());
    }
}
