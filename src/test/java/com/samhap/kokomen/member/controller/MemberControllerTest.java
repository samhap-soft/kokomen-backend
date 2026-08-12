package com.samhap.kokomen.member.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.headers.HeaderDocumentation.requestHeaders;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.requestFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.queryParameters;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.samhap.kokomen.category.domain.Category;
import com.samhap.kokomen.global.BaseControllerTest;
import com.samhap.kokomen.global.fixture.interview.InterviewFixtureBuilder;
import com.samhap.kokomen.global.fixture.interview.RootQuestionFixtureBuilder;
import com.samhap.kokomen.global.fixture.member.MemberFixtureBuilder;
import com.samhap.kokomen.global.fixture.member.OnboardingSurveyFixtureBuilder;
import com.samhap.kokomen.interview.domain.InterviewState;
import com.samhap.kokomen.interview.domain.RootQuestion;
import com.samhap.kokomen.interview.repository.InterviewRepository;
import com.samhap.kokomen.interview.repository.RootQuestionRepository;
import com.samhap.kokomen.member.domain.Admin;
import com.samhap.kokomen.member.domain.Member;
import com.samhap.kokomen.member.domain.survey.CareerGoal;
import com.samhap.kokomen.member.domain.survey.InterviewExperience;
import com.samhap.kokomen.member.domain.survey.OnboardingSurvey;
import com.samhap.kokomen.member.domain.survey.PrepStage;
import com.samhap.kokomen.member.domain.survey.TargetCompanyType;
import com.samhap.kokomen.member.domain.survey.WeakPoint;
import com.samhap.kokomen.member.repository.AdminRepository;
import com.samhap.kokomen.member.repository.MemberRepository;
import com.samhap.kokomen.member.repository.OnboardingSurveyRepository;
import com.samhap.kokomen.token.service.TokenService;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;

class MemberControllerTest extends BaseControllerTest {

    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private RootQuestionRepository rootQuestionRepository;
    @Autowired
    private InterviewRepository interviewRepository;
    @Autowired
    private TokenService tokenService;
    @Autowired
    private AdminRepository adminRepository;
    @Autowired
    private OnboardingSurveyRepository onboardingSurveyRepository;

    @Test
    void 멤버_프로필_조회() throws Exception {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        tokenService.createTokensForNewMember(member.getId());
        adminRepository.save(new Admin(member));
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("MEMBER_ID", member.getId());

        String responseJson = """
                {
                    "id": %d,
                    "nickname": %s,
                    "score": %d,
                    "total_member_count": 1,
                    "rank": 1,
                    "token_count": 20,
                    "profile_completed": %s,
                    "is_admin": true
                }
                """.formatted(member.getId(), member.getNickname(), member.getScore(), member.getProfileCompleted());

        // when & then
        mockMvc.perform(get("/api/v1/members/me/profile")
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session)
                )
                .andExpect(status().isOk())
                .andExpect(content().json(responseJson))
                .andDo(document("member-findMyProfile",
                        requestHeaders(
                                headerWithName("Cookie").description("로그인 세션을 위한 JSESSIONID 쿠키")
                        ),
                        responseFields(
                                fieldWithPath("id").description("회원 id"),
                                fieldWithPath("nickname").description("회원 닉네임"),
                                fieldWithPath("score").description("현재 회원 점수"),
                                fieldWithPath("total_member_count").description("전체 회원 수"),
                                fieldWithPath("rank").description("회원 등수"),
                                fieldWithPath("token_count").description("현재 회원 토큰 개수"),
                                fieldWithPath("profile_completed").description("프로필 완성 여부"),
                                fieldWithPath("is_admin").description("어드민 유저 여부")
                        )
                ));
    }

    @Test
    void 프로필을_변경한다() throws Exception {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("MEMBER_ID", member.getId());

        String newNickname = "새로운닉네임";
        String requestJson = """
                {
                  "nickname": "%s"
                }
                """.formatted(newNickname);

        // when & then
        mockMvc.perform(patch("/api/v1/members/me/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson)
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session)
                )
                .andExpect(status().isOk())
                .andDo(document("member-updateProfile",
                        requestHeaders(
                                headerWithName("Cookie").description("로그인 세션을 위한 JSESSIONID 쿠키")
                        ),
                        requestFields(
                                fieldWithPath("nickname").description("변경할 닉네임")
                        )
                ));

        Member updatedMember = memberRepository.findById(member.getId()).orElseThrow();
        assertThat(updatedMember.getNickname()).isEqualTo(newNickname);
    }

    @Test
    void 닉네임이_공백이면_프로필_변경에_실패한다() throws Exception {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("MEMBER_ID", member.getId());

        String invalidNickname = "  ";
        String requestJson = """
                {
                  "nickname": "%s"
                }
                """.formatted(invalidNickname);

        // when & then
        mockMvc.perform(patch("/api/v1/members/me/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson)
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session)
                )
                .andExpect(status().isBadRequest())
                .andDo(document("member-updateProfile-error",
                        requestHeaders(
                                headerWithName("Cookie").description("로그인 세션을 위한 JSESSIONID 쿠키")
                        ),
                        requestFields(
                                fieldWithPath("nickname").description("변경할 닉네임")
                        )
                ));
    }

    @Test
    void 온보딩_설문을_제출한다() throws Exception {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("MEMBER_ID", member.getId());

        String requestJson = """
                {
                  "career_goal": "BACKEND",
                  "prep_stages": ["JOB_SEEKING", "GRADUATING"],
                  "tech_topics": ["JAVA_SPRING", "DATABASE"],
                  "target_company_type": "BIG_TECH",
                  "interview_experience": "ONE_TO_THREE",
                  "weak_points": ["CS", "MENTAL"],
                  "goal_description": "6개월 안에 백엔드 신입으로 취업하고 싶습니다."
                }
                """;

        // when & then
        mockMvc.perform(post("/api/v1/members/me/onboarding-survey")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson)
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session)
                )
                .andExpect(status().isOk())
                .andDo(document("member-submitOnboardingSurvey",
                        requestHeaders(
                                headerWithName("Cookie").description("로그인 세션을 위한 JSESSIONID 쿠키")
                        ),
                        requestFields(
                                fieldWithPath("career_goal").description(
                                        "커리어 목표 - BACKEND, FRONTEND, AI_DATA, MOBILE, CAREER_SWITCH, EXPLORING"),
                                fieldWithPath("prep_stages").description(
                                        "취업 준비 단계(복수) - BEGINNER, JOB_SEEKING, GRADUATING, SWITCHING. 최소 1개"),
                                fieldWithPath("tech_topics").description(
                                        "관심 기술 분야(복수) - Category 이름 중 인성 면접(PERSONALITY)을 제외한 값. 최소 1개"),
                                fieldWithPath("target_company_type").description(
                                        "지원 희망 기업 형태 - BIG_TECH, SME, STARTUP, ANY"),
                                fieldWithPath("interview_experience").description(
                                        "기술 면접 경험 - NONE, ONE_TO_THREE, FOUR_PLUS"),
                                fieldWithPath("weak_points").description(
                                        "면접에서 어려운 부분(복수) - CS, PROJECT_QA, COMMUNICATION, MENTAL. 최소 1개"),
                                fieldWithPath("goal_description").description(
                                        "꼬꼬면을 통해 이루고 싶은 목표 - 선택 항목, 최대 1000자").optional()
                        )
                ));

        OnboardingSurvey onboardingSurvey = onboardingSurveyRepository.findByMemberId(member.getId()).orElseThrow();
        assertThat(onboardingSurvey.getCareerGoal()).isEqualTo(CareerGoal.BACKEND);
        assertThat(onboardingSurvey.getPrepStages()).containsExactly(PrepStage.JOB_SEEKING, PrepStage.GRADUATING);
        assertThat(onboardingSurvey.getTechTopics()).containsExactly(Category.JAVA_SPRING, Category.DATABASE);
        assertThat(onboardingSurvey.getTargetCompanyType()).isEqualTo(TargetCompanyType.BIG_TECH);
        assertThat(onboardingSurvey.getInterviewExperience()).isEqualTo(InterviewExperience.ONE_TO_THREE);
        assertThat(onboardingSurvey.getWeakPoints()).containsExactly(WeakPoint.CS, WeakPoint.MENTAL);
        assertThat(onboardingSurvey.getGoalDescription()).isEqualTo("6개월 안에 백엔드 신입으로 취업하고 싶습니다.");
    }

    @Test
    void 이미_제출한_회원이_다시_제출하면_기존_응답을_덮어쓴다() throws Exception {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        OnboardingSurvey savedOnboardingSurvey = onboardingSurveyRepository.save(
                OnboardingSurveyFixtureBuilder.builder()
                        .member(member)
                        .careerGoal(CareerGoal.BACKEND)
                        .prepStages(List.of(PrepStage.BEGINNER))
                        .techTopics(List.of(Category.JAVA_SPRING))
                        .weakPoints(List.of(WeakPoint.CS))
                        .goalDescription("이전 목표")
                        .build());
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("MEMBER_ID", member.getId());

        String requestJson = """
                {
                  "career_goal": "FRONTEND",
                  "prep_stages": ["SWITCHING"],
                  "tech_topics": ["REACT"],
                  "target_company_type": "STARTUP",
                  "interview_experience": "FOUR_PLUS",
                  "weak_points": ["MENTAL"]
                }
                """;

        // when & then
        mockMvc.perform(post("/api/v1/members/me/onboarding-survey")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson)
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session)
                )
                .andExpect(status().isOk());

        assertThat(onboardingSurveyRepository.count()).isEqualTo(1);
        OnboardingSurvey updatedOnboardingSurvey = onboardingSurveyRepository.findByMemberId(member.getId())
                .orElseThrow();
        assertThat(updatedOnboardingSurvey.getId()).isEqualTo(savedOnboardingSurvey.getId());
        assertThat(updatedOnboardingSurvey.getCareerGoal()).isEqualTo(CareerGoal.FRONTEND);
        assertThat(updatedOnboardingSurvey.getPrepStages()).containsExactly(PrepStage.SWITCHING);
        assertThat(updatedOnboardingSurvey.getTechTopics()).containsExactly(Category.REACT);
        assertThat(updatedOnboardingSurvey.getTargetCompanyType()).isEqualTo(TargetCompanyType.STARTUP);
        assertThat(updatedOnboardingSurvey.getInterviewExperience()).isEqualTo(InterviewExperience.FOUR_PLUS);
        assertThat(updatedOnboardingSurvey.getWeakPoints()).containsExactly(WeakPoint.MENTAL);
        assertThat(updatedOnboardingSurvey.getGoalDescription()).isNull();
    }

    @Test
    void 로그인하지_않으면_온보딩_설문_제출에_실패한다() throws Exception {
        // given
        String requestJson = """
                {
                  "career_goal": "BACKEND",
                  "prep_stages": ["JOB_SEEKING"],
                  "tech_topics": ["JAVA_SPRING"],
                  "target_company_type": "BIG_TECH",
                  "interview_experience": "NONE",
                  "weak_points": ["CS"]
                }
                """;

        // when & then
        mockMvc.perform(post("/api/v1/members/me/onboarding-survey")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson)
                )
                .andExpect(status().isUnauthorized());

        assertThat(onboardingSurveyRepository.count()).isZero();
    }

    @Test
    void 필수_항목이_누락되면_온보딩_설문_제출에_실패한다() throws Exception {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("MEMBER_ID", member.getId());

        String requestJson = """
                {
                  "prep_stages": ["JOB_SEEKING"],
                  "tech_topics": ["JAVA_SPRING"],
                  "target_company_type": "BIG_TECH",
                  "interview_experience": "NONE",
                  "weak_points": ["CS"]
                }
                """;

        // when & then
        mockMvc.perform(post("/api/v1/members/me/onboarding-survey")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson)
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session)
                )
                .andExpect(status().isBadRequest())
                .andExpect(content().json("""
                        {
                          "message": "career_goal은 null일 수 없습니다."
                        }
                        """))
                .andDo(document("member-submitOnboardingSurvey-error",
                        requestHeaders(
                                headerWithName("Cookie").description("로그인 세션을 위한 JSESSIONID 쿠키")
                        ),
                        responseFields(
                                fieldWithPath("message").description("에러 메시지")
                        )
                ));

        assertThat(onboardingSurveyRepository.count()).isZero();
    }

    @Test
    void 복수_선택_항목이_빈_배열이면_온보딩_설문_제출에_실패한다() throws Exception {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("MEMBER_ID", member.getId());

        String requestJson = """
                {
                  "career_goal": "BACKEND",
                  "prep_stages": [],
                  "tech_topics": ["JAVA_SPRING"],
                  "target_company_type": "BIG_TECH",
                  "interview_experience": "NONE",
                  "weak_points": ["CS"]
                }
                """;

        // when & then
        mockMvc.perform(post("/api/v1/members/me/onboarding-survey")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson)
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session)
                )
                .andExpect(status().isBadRequest())
                .andExpect(content().json("""
                        {
                          "message": "prep_stages는 최소 1개를 선택해야 합니다."
                        }
                        """));

        assertThat(onboardingSurveyRepository.count()).isZero();
    }

    @Test
    void 정의되지_않은_enum_값이면_온보딩_설문_제출에_실패한다() throws Exception {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("MEMBER_ID", member.getId());

        String requestJson = """
                {
                  "career_goal": "DEVOPS",
                  "prep_stages": ["JOB_SEEKING"],
                  "tech_topics": ["JAVA_SPRING"],
                  "target_company_type": "BIG_TECH",
                  "interview_experience": "NONE",
                  "weak_points": ["CS"]
                }
                """;

        // when & then
        mockMvc.perform(post("/api/v1/members/me/onboarding-survey")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson)
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session)
                )
                .andExpect(status().isBadRequest())
                .andExpect(content().json("""
                        {
                          "message": "JSON 파싱 오류: 'career_goal' 필드에 유효하지 않은 값이 전달되었습니다. (전달된 값: 'DEVOPS')"
                        }
                        """));

        assertThat(onboardingSurveyRepository.count()).isZero();
    }

    @Test
    void 복수_선택_항목에_정의되지_않은_enum_값이_있으면_온보딩_설문_제출에_실패한다() throws Exception {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("MEMBER_ID", member.getId());

        String requestJson = """
                {
                  "career_goal": "BACKEND",
                  "prep_stages": ["JOB_SEEKING"],
                  "tech_topics": ["JAVA_SPRING", "GOLANG"],
                  "target_company_type": "BIG_TECH",
                  "interview_experience": "NONE",
                  "weak_points": ["CS"]
                }
                """;

        // when & then
        mockMvc.perform(post("/api/v1/members/me/onboarding-survey")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson)
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session)
                )
                .andExpect(status().isBadRequest())
                .andExpect(content().json("""
                        {
                          "message": "JSON 파싱 오류: 'tech_topics' 필드에 유효하지 않은 값이 전달되었습니다. (전달된 값: 'GOLANG')"
                        }
                        """));

        assertThat(onboardingSurveyRepository.count()).isZero();
    }

    @Test
    void 인성_면접을_관심_기술_분야로_보내면_온보딩_설문_제출에_실패한다() throws Exception {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("MEMBER_ID", member.getId());

        String requestJson = """
                {
                  "career_goal": "BACKEND",
                  "prep_stages": ["JOB_SEEKING"],
                  "tech_topics": ["JAVA_SPRING", "PERSONALITY"],
                  "target_company_type": "BIG_TECH",
                  "interview_experience": "NONE",
                  "weak_points": ["CS"]
                }
                """;

        // when & then
        mockMvc.perform(post("/api/v1/members/me/onboarding-survey")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson)
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session)
                )
                .andExpect(status().isBadRequest())
                .andExpect(content().json("""
                        {
                          "message": "tech_topics에는 기술 카테고리만 선택할 수 있습니다."
                        }
                        """));

        assertThat(onboardingSurveyRepository.count()).isZero();
    }

    @Test
    void 랭킹_조회에_성공한다() throws Exception {
        // given
        memberRepository.save(MemberFixtureBuilder.builder().nickname("100점 회원").score(100).build());
        Member member2 = memberRepository.save(MemberFixtureBuilder.builder().nickname("200점 회원").score(200).build());
        Member member3 = memberRepository.save(MemberFixtureBuilder.builder().nickname("300점 회원").score(300).build());

        RootQuestion rootQuestion = rootQuestionRepository.save(RootQuestionFixtureBuilder.builder().build());

        interviewRepository.save(InterviewFixtureBuilder.builder().member(member3).rootQuestion(rootQuestion)
                .interviewState(InterviewState.FINISHED).build());
        interviewRepository.save(InterviewFixtureBuilder.builder().member(member3).rootQuestion(rootQuestion)
                .interviewState(InterviewState.FINISHED).build());
        interviewRepository.save(InterviewFixtureBuilder.builder().member(member2).rootQuestion(rootQuestion)
                .interviewState(InterviewState.FINISHED).build());

        String responseJson = """
                [
                  {
                    "id": %d,
                    "nickname": "300점 회원",
                    "score": 300,
                    "finished_interview_count": 2
                  },
                  {
                    "id": %d,
                    "nickname": "200점 회원",
                    "score": 200,
                    "finished_interview_count": 1
                  }
                ]
                """.formatted(member3.getId(), member2.getId());

        // when & then
        mockMvc.perform(get("/api/v1/members/ranking")
                        .param("page", "0")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(content().json(responseJson))
                .andDo(document("member-findRanking",
                        queryParameters(
                                parameterWithName("page").description("페이지 번호 (0부터 시작)"),
                                parameterWithName("size").description("한 페이지 크기")
                        ),
                        responseFields(
                                fieldWithPath("[].id").description("회원 ID"),
                                fieldWithPath("[].nickname").description("회원 닉네임"),
                                fieldWithPath("[].score").description("회원 점수"),
                                fieldWithPath("[].finished_interview_count").description("회원의 완료한 인터뷰 수")
                        )
                ));
    }

    @Test
    void 랭킹_조회에_성공한다_V2() throws Exception {
        // given
        memberRepository.save(
                MemberFixtureBuilder.builder().nickname("100점 회원").score(100).build());
        Member member2 = memberRepository.save(
                MemberFixtureBuilder.builder().nickname("200점 회원").score(200).build());
        Member member3 = memberRepository.save(
                MemberFixtureBuilder.builder().nickname("300점 회원").score(300).build());

        RootQuestion rootQuestion = rootQuestionRepository.save(RootQuestionFixtureBuilder.builder().build());

        interviewRepository.save(InterviewFixtureBuilder.builder().member(member3).rootQuestion(rootQuestion)
                .interviewState(InterviewState.FINISHED).build());
        interviewRepository.save(InterviewFixtureBuilder.builder().member(member3).rootQuestion(rootQuestion)
                .interviewState(InterviewState.FINISHED).build());
        interviewRepository.save(InterviewFixtureBuilder.builder().member(member2).rootQuestion(rootQuestion)
                .interviewState(InterviewState.FINISHED).build());

        String responseJson = """
                {
                  "data": [
                    {
                      "id": %d,
                      "nickname": "300점 회원",
                      "score": 300,
                      "finished_interview_count": 2
                    },
                    {
                      "id": %d,
                      "nickname": "200점 회원",
                      "score": 200,
                      "finished_interview_count": 1
                    }
                  ],
                  "current_page": 0,
                  "total_ranking_count": 3,
                  "total_pages": 2,
                  "has_next": true
                }
                """.formatted(member3.getId(), member2.getId());

        // when & then
        mockMvc.perform(get("/api/v1/members/v2/ranking")
                        .param("page", "0")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(content().json(responseJson))
                .andDo(document("member-findRankingV2",
                        queryParameters(
                                parameterWithName("page").description("페이지 번호 (0부터 시작)"),
                                parameterWithName("size").description("한 페이지 크기")
                        ),
                        responseFields(
                                fieldWithPath("data[]").description("랭킹 데이터 배열"),
                                fieldWithPath("data[].id").description("회원 ID"),
                                fieldWithPath("data[].nickname").description("회원 닉네임"),
                                fieldWithPath("data[].score").description("회원 점수"),
                                fieldWithPath("data[].finished_interview_count").description("회원의 완료한 인터뷰 수"),
                                fieldWithPath("current_page").description("현재 페이지 번호 (0부터 시작)"),
                                fieldWithPath("total_ranking_count").description("전체 랭킹(회원) 수"),
                                fieldWithPath("total_pages").description("전체 페이지 수"),
                                fieldWithPath("has_next").description("다음 페이지 존재 여부")
                        )
                ));
    }

    @Test
    void 멤버_스트릭_조회에_성공한다() throws Exception {
        // given
        Member member = memberRepository.save(MemberFixtureBuilder.builder().build());
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("MEMBER_ID", member.getId());

        RootQuestion rootQuestion = rootQuestionRepository.save(RootQuestionFixtureBuilder.builder().build());

        // 2024-01-01과 2024-01-03에 완료된 인터뷰 생성 (1일 건너뛰어서 연속성 테스트)
        interviewRepository.save(InterviewFixtureBuilder.builder()
                .member(member)
                .rootQuestion(rootQuestion)
                .interviewState(InterviewState.FINISHED)
                .totalFeedback("피드백1")
                .totalScore(85)
                .finishedAt(LocalDateTime.of(2024, 1, 1, 12, 0, 0))
                .build());
        interviewRepository.save(InterviewFixtureBuilder.builder()
                .member(member)
                .rootQuestion(rootQuestion)
                .interviewState(InterviewState.FINISHED)
                .totalFeedback("피드백2")
                .totalScore(90)
                .finishedAt(LocalDateTime.of(2024, 1, 3, 12, 0, 0))
                .build());

        String responseJson = """
                {
                    "daily_counts": [
                        {
                            "date": "2024-01-01",
                            "count": 1
                        },
                        {
                            "date": "2024-01-03",
                            "count": 1
                        }
                    ],
                    "max_streak": 1,
                    "current_streak": 0
                }
                """;

        // when & then
        mockMvc.perform(get("/api/v1/members/me/streaks")
                        .param("start_date", "2024-01-01")
                        .param("end_date", "2024-12-31")
                        .header("Cookie", "JSESSIONID=" + session.getId())
                        .session(session)
                )
                .andExpect(status().isOk())
                .andExpect(content().json(responseJson))
                .andDo(document("member-findMemberStreaks",
                        requestHeaders(
                                headerWithName("Cookie").description("로그인 세션을 위한 JSESSIONID 쿠키")
                        ),
                        queryParameters(
                                parameterWithName("start_date").description("조회 시작 날짜 (YYYY-MM-DD 형식)"),
                                parameterWithName("end_date").description("조회 종료 날짜 (YYYY-MM-DD 형식)")
                        ),
                        responseFields(
                                fieldWithPath("daily_counts").description("일별 인터뷰 완료 횟수 목록"),
                                fieldWithPath("daily_counts[].date").description("날짜 (YYYY-MM-DD 형식)"),
                                fieldWithPath("daily_counts[].count").description("해당 날짜의 완료된 인터뷰 수"),
                                fieldWithPath("max_streak").description("최대 연속 스트릭 일수"),
                                fieldWithPath("current_streak").description("현재 연속 스트릭 일수")
                        )
                ));
    }
}
