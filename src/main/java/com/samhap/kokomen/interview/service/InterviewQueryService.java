package com.samhap.kokomen.interview.service;

import com.samhap.kokomen.category.domain.Category;
import com.samhap.kokomen.global.dto.ClientIp;
import com.samhap.kokomen.global.dto.MemberAuth;
import com.samhap.kokomen.interview.domain.Interview;
import com.samhap.kokomen.interview.domain.InterviewLike;
import com.samhap.kokomen.interview.domain.InterviewMode;
import com.samhap.kokomen.interview.domain.InterviewState;
import com.samhap.kokomen.interview.external.dto.response.InterviewSummaryResponses;
import com.samhap.kokomen.interview.service.core.InterviewService;
import com.samhap.kokomen.interview.service.dto.InterviewResultResponse;
import com.samhap.kokomen.interview.service.dto.InterviewSummaryResponse;
import com.samhap.kokomen.interview.service.dto.RootQuestionResponse;
import com.samhap.kokomen.interview.service.dto.check.InterviewCheckResponse;
import com.samhap.kokomen.interview.service.question.RootQuestionService;
import com.samhap.kokomen.interview.service.social.InterviewLikeService;
import com.samhap.kokomen.member.domain.Member;
import com.samhap.kokomen.member.service.MemberService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class InterviewQueryService {

    private final InterviewService interviewService;
    private final InterviewLikeService interviewLikeService;
    private final MemberService memberService;
    private final RootQuestionService rootQuestionService;

    @Transactional
    public void likeInterview(Long interviewId, MemberAuth memberAuth) {
        Member member = memberService.readById(memberAuth.memberId());
        Interview interview = interviewService.readInterview(interviewId);
        interviewLikeService.likeInterview(new InterviewLike(member, interview));
        interviewService.increaseLikeCountModifying(
                interviewId); // X락을 사용하기 때문에 동시에 요청이 와도 올바른 likeCount 값으로 이벤트를 생성할 수 있다.
    }

    public InterviewCheckResponse checkInterview(Long interviewId, InterviewMode mode, MemberAuth memberAuth) {
        return interviewService.checkInterview(interviewId, mode, memberAuth);
    }

    public List<InterviewSummaryResponse> findMyInterviews(MemberAuth memberAuth, InterviewState state,
                                                           Pageable pageable) {
        return interviewService.findMyInterviews(memberAuth, state, pageable);
    }

    public InterviewSummaryResponses findOtherMemberInterviews(Long memberId, MemberAuth memberAuth,
                                                               Pageable pageable) {
        return interviewService.findOtherMemberInterviews(memberId, memberAuth, pageable);
    }

    public InterviewResultResponse findMyInterviewResult(Long interviewId, MemberAuth memberAuth, ClientIp clientIp) {
        return interviewService.findMyInterviewResult(interviewId, memberAuth, clientIp);
    }

    public InterviewResultResponse findOtherMemberInterviewResult(Long interviewId, MemberAuth memberAuth,
                                                                  ClientIp clientIp) {
        return interviewService.findOtherMemberInterviewResult(interviewId, memberAuth, clientIp);
    }

    @Transactional
    public void unlikeInterview(Long interviewId, MemberAuth memberAuth) {
        interviewService.unlikeInterview(interviewId, memberAuth);
    }

    public List<RootQuestionResponse> getRootQuestionsByCategory(Category category) {
        return rootQuestionService.findAllRootQuestionByCategory(category).stream()
                .map(RootQuestionResponse::from)
                .toList();
    }
}
