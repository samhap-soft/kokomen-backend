package com.samhap.kokomen.interview.service;

import com.samhap.kokomen.answer.domain.Answer;
import com.samhap.kokomen.answer.domain.AnswerMemo;
import com.samhap.kokomen.answer.domain.AnswerMemoState;
import com.samhap.kokomen.answer.domain.AnswerMemoVisibility;
import com.samhap.kokomen.answer.dto.AnswerMemos;
import com.samhap.kokomen.answer.repository.AnswerLikeRepository;
import com.samhap.kokomen.answer.repository.AnswerMemoRepository;
import com.samhap.kokomen.answer.repository.AnswerRepository;
import com.samhap.kokomen.global.dto.ClientIp;
import com.samhap.kokomen.global.dto.MemberAuth;
import com.samhap.kokomen.global.exception.BadRequestException;
import com.samhap.kokomen.global.exception.ForbiddenException;
import com.samhap.kokomen.global.exception.UnauthorizedException;
import com.samhap.kokomen.interview.domain.Interview;
import com.samhap.kokomen.interview.domain.InterviewMode;
import com.samhap.kokomen.interview.domain.InterviewState;
import com.samhap.kokomen.interview.domain.Question;
import com.samhap.kokomen.interview.external.dto.response.InterviewSummaryResponses;
import com.samhap.kokomen.interview.repository.InterviewLikeRepository;
import com.samhap.kokomen.interview.repository.InterviewRepository;
import com.samhap.kokomen.interview.repository.QuestionRepository;
import com.samhap.kokomen.interview.service.dto.FeedbackResponse;
import com.samhap.kokomen.interview.service.dto.InterviewResponse;
import com.samhap.kokomen.interview.service.dto.InterviewResultResponse;
import com.samhap.kokomen.interview.service.dto.InterviewSummaryResponse;
import com.samhap.kokomen.member.domain.Member;
import com.samhap.kokomen.member.repository.MemberRepository;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class InterviewService {

    private final InterviewRepository interviewRepository;
    private final QuestionRepository questionRepository;
    private final AnswerRepository answerRepository;
    private final MemberRepository memberRepository;
    private final InterviewLikeRepository interviewLikeRepository;
    private final AnswerLikeRepository answerLikeRepository;
    private final AnswerMemoRepository answerMemoRepository;
    private final InterviewViewCountService interviewViewCountService;

    @Transactional
    public Interview saveInterview(Interview interview) {
        return interviewRepository.save(interview);
    }

    @Transactional(readOnly = true)
    public InterviewResponse checkInterview(Long interviewId, MemberAuth memberAuth) {
        Interview interview = readInterview(interviewId);
        validateInterviewee(interviewId, memberAuth.memberId());
        List<Question> questions = questionRepository.findByInterviewOrderById(interview);
        List<Answer> answers = answerRepository.findByQuestionInOrderById(questions);

        return InterviewResponse.of(interview, questions, answers);
    }

    @Transactional(readOnly = true)
    public List<InterviewSummaryResponse> findMyInterviews(MemberAuth memberAuth, InterviewState state, Pageable pageable) {
        Member member = readMember(memberAuth.memberId());
        List<Interview> interviews = findInterviews(member, state, pageable);
        List<Long> finishedInterviewIds = interviews.stream()
                .filter(interview -> !interview.isInProgress())
                .map(Interview::getId)
                .toList();
        Set<Long> likedInterviewIds = interviewLikeRepository.findLikedInterviewIds(member.getId(), finishedInterviewIds);

        return interviews.stream()
                .map(interview ->
                        InterviewSummaryResponse.createMine(interview, countCurAnswers(interview), interviewViewCountService.findViewCount(interview),
                                likedInterviewIds.contains(interview.getId()), countSubmittedAnswerMemos(interview), hasTempAnswerMemo(interview)))
                .toList();
    }

    private int countCurAnswers(Interview interview) {
        int qurQuestionCount = questionRepository.countByInterview(interview);

        // TODO: 해당 로직 적절한 도메인에 부여하기
        if (interview.isInProgress()) {
            return qurQuestionCount - 1;
        }
        return qurQuestionCount;
    }

    private int countSubmittedAnswerMemos(Interview interview) {
        return Math.toIntExact(answerMemoRepository.countByAnswerQuestionInterviewAndAnswerMemoState(interview, AnswerMemoState.SUBMITTED));
    }

    private Boolean hasTempAnswerMemo(Interview interview) {
        return answerMemoRepository.existsByAnswerQuestionInterviewAndAnswerMemoState(interview, AnswerMemoState.TEMP);
    }

    @Transactional(readOnly = true)
    public InterviewSummaryResponses findOtherMemberInterviews(Long targetMemberId, MemberAuth memberAuth, Pageable pageable) {
        Member interviewee = readMember(targetMemberId);
        long intervieweeRank = memberRepository.findRankByScore(interviewee.getScore());
        long totalMemberCount = memberRepository.count();
        long totalPageCount = calculateTotalPageCount(interviewee, pageable);

        List<Interview> finishedInterviews = findInterviews(interviewee, InterviewState.FINISHED, pageable);
        Map<Long, Long> viewCounts = finishedInterviews.stream()
                .collect(Collectors.toMap(Interview::getId, interviewViewCountService::findViewCount));
        Map<Long, Integer> submittedAnswerMemoCounts = finishedInterviews.stream()
                .collect(Collectors.toMap(Interview::getId, this::countSubmittedAndPublicAnswerMemos));
        if (memberAuth.isAuthenticated()) {
            Member readerMember = readMember(memberAuth.memberId());
            List<Long> finishedInterviewIds = finishedInterviews.stream().map(Interview::getId).toList();
            Set<Long> likedInterviewIds = interviewLikeRepository.findLikedInterviewIds(readerMember.getId(), finishedInterviewIds);

            return InterviewSummaryResponses.createOfOtherMemberForAuthorized(interviewee.getNickname(), totalMemberCount, intervieweeRank, finishedInterviews,
                    likedInterviewIds, viewCounts, submittedAnswerMemoCounts, totalPageCount);
        }
        return InterviewSummaryResponses.createOfOtherMemberForUnAuthorized(interviewee.getNickname(), totalMemberCount, intervieweeRank, finishedInterviews,
                viewCounts, submittedAnswerMemoCounts, totalPageCount);
    }

    private Long calculateTotalPageCount(Member member, Pageable pageable) {
        int pageSize = pageable.getPageSize();
        long interviewCount = interviewRepository.countByMemberAndInterviewState(member, InterviewState.FINISHED);

        if (interviewCount % pageSize == 0) {
            return interviewCount / pageSize;
        }

        return interviewCount / pageSize + 1;
    }

    // TODO: 동적 쿼리 개선하기
    private List<Interview> findInterviews(Member member, InterviewState state, Pageable pageable) {
        if (state == null) {
            return interviewRepository.findByMember(member, pageable);
        }
        return interviewRepository.findByMemberAndInterviewState(member, state, pageable);
    }

    private int countSubmittedAndPublicAnswerMemos(Interview interview) {
        return Math.toIntExact(answerMemoRepository.countByAnswerQuestionInterviewAndAnswerMemoStateAndAnswerMemoVisibility(
                interview, AnswerMemoState.SUBMITTED, AnswerMemoVisibility.PUBLIC));
    }

    // TODO: 인터뷰 안 끝나면 예외 던지기
    @Transactional(readOnly = true)
    public InterviewResultResponse findMyInterviewResult(Long interviewId, MemberAuth memberAuth) {
        validateInterviewee(interviewId, memberAuth.memberId());
        Member member = readMember(memberAuth.memberId());
        Interview interview = readInterview(interviewId);
        validateInterviewFinished(interview);
        List<Answer> answers = answerRepository.findByQuestionIn(questionRepository.findByInterview(interview));
        List<FeedbackResponse> feedbackResponses = FeedbackResponse.createMine(answers, findAnswerMemos(answers));

        return InterviewResultResponse.createMine(feedbackResponses, interview, member);
    }

    @Transactional(readOnly = true)
    public void validateInterviewMode(Long interviewId, InterviewMode interviewMode) {
        Interview interview = readInterview(interviewId);
        if (interview.getInterviewMode() != interviewMode) {
            throw new BadRequestException("인터뷰 모드가 일치하지 않습니다. 현재 인터뷰 모드: " + interview.getInterviewMode() + ", 요청한 인터뷰 모드: " + interviewMode);
        }
    }

    @Transactional(readOnly = true)
    public void validateInterviewee(Long interviewId, Long memberId) {
        Interview interview = readInterview(interviewId);
        if (!interview.isInterviewee(memberId)) {
            throw new ForbiddenException("해당 인터뷰를 생성한 회원이 아닙니다.");
        }
    }

    private Map<Long, AnswerMemos> findAnswerMemos(List<Answer> answers) {
        return answers.stream()
                .collect(Collectors.toMap(
                        Answer::getId,
                        answer -> AnswerMemos.createMine(findAnswerMemo(answer, AnswerMemoState.SUBMITTED), findAnswerMemo(answer, AnswerMemoState.TEMP))));
    }

    private AnswerMemo findAnswerMemo(Answer answer, AnswerMemoState state) {
        return answerMemoRepository.findByAnswerAndAnswerMemoState(answer, state)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public InterviewResultResponse findOtherMemberInterviewResult(Long interviewId, MemberAuth memberAuth, ClientIp clientIp) {
        Interview interview = readInterview(interviewId);
        Member interviewee = interview.getMember();
        long totalMemberCount = memberRepository.count();
        long intervieweeRank = memberRepository.findRankByScore(interviewee.getScore());

        validateInterviewFinished(interview);
        long viewCount = increaseViewCountIfNotInterviewee(interview, memberAuth, clientIp);
        List<Answer> answers = answerRepository.findByQuestionIn(questionRepository.findByInterview(interview));
        Map<Long, AnswerMemos> answerMemos = findPublicSubmittedAnswerMemos(answers);
        if (memberAuth.isAuthenticated()) {
            Member readerMember = readMember(memberAuth.memberId());
            boolean interviewAlreadyLiked = interviewLikeRepository.existsByInterviewIdAndMemberId(interview.getId(), readerMember.getId());
            List<Long> answerIds = answers.stream().map(Answer::getId).toList();
            Set<Long> likedAnswerIds = answerLikeRepository.findLikedAnswerIds(readerMember.getId(), answerIds);

            return InterviewResultResponse.createOfOtherMemberForAuthorized(answers, likedAnswerIds, interview, viewCount, interviewAlreadyLiked,
                    interview.getMember().getNickname(), totalMemberCount, intervieweeRank, answerMemos);
        }

        return InterviewResultResponse.createOfOtherMemberForUnauthorized(answers, interview, viewCount, interviewee.getNickname(), totalMemberCount,
                intervieweeRank, answerMemos);
    }

    private Map<Long, AnswerMemos> findPublicSubmittedAnswerMemos(List<Answer> answers) {
        return answers.stream()
                .collect(Collectors.toMap(
                        Answer::getId,
                        answer -> AnswerMemos.createOfOtherMember(findAnswerMemo(answer, AnswerMemoState.SUBMITTED, AnswerMemoVisibility.PUBLIC))));
    }

    private AnswerMemo findAnswerMemo(Answer answer, AnswerMemoState answerMemoState, AnswerMemoVisibility answerMemoVisibility) {
        return answerMemoRepository.findByAnswerAndAnswerMemoStateAndAnswerMemoVisibility(
                        answer, answerMemoState, answerMemoVisibility)
                .orElse(null);
    }

    private void validateInterviewFinished(Interview interview) {
        if (interview.isInProgress()) {
            throw new BadRequestException("해당 인터뷰는 아직 진행 중입니다. 인터뷰가 종료된 후 결과를 조회할 수 있습니다.");
        }
    }

    private Long increaseViewCountIfNotInterviewee(Interview interview, MemberAuth memberAuth, ClientIp clientIp) {
        return interviewViewCountService.incrementViewCount(interview, memberAuth, clientIp);
    }

    @Transactional
    public void unlikeInterview(Long interviewId, MemberAuth memberAuth) {
        Member member = readMember(memberAuth.memberId());
        Interview interview = readInterview(interviewId);
        int affectedRows = interviewLikeRepository.deleteByMemberAndInterview(member, interview);
        if (affectedRows == 0) {
            throw new BadRequestException("좋아요를 누르지 않은 인터뷰입니다.");
        }
        interviewRepository.decreaseLikeCountModifying(interviewId);
    }

    private Member readMember(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new UnauthorizedException("존재하지 않는 회원입니다."));
    }

    public Interview readInterview(Long interviewId) {
        return interviewRepository.findById(interviewId)
                .orElseThrow(() -> new BadRequestException("존재하지 않는 인터뷰입니다."));
    }

    public void increaseLikeCountModifying(Long interviewId) {
        interviewRepository.increaseLikeCountModifying(interviewId);
    }
}
