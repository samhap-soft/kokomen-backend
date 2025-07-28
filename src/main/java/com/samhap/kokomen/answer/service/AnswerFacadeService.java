package com.samhap.kokomen.answer.service;

import com.samhap.kokomen.answer.domain.Answer;
import com.samhap.kokomen.answer.service.dto.AnswerMemoCreateRequest;
import com.samhap.kokomen.answer.service.dto.AnswerMemoResponse;
import com.samhap.kokomen.answer.service.dto.AnswerMemoUpdateRequest;
import com.samhap.kokomen.answer.service.event.AnswerLikedEvent;
import com.samhap.kokomen.global.dto.MemberAuth;
import com.samhap.kokomen.interview.domain.Interview;
import com.samhap.kokomen.member.domain.Member;
import com.samhap.kokomen.member.service.MemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class AnswerFacadeService {

    private final AnswerService answerService;
    private final AnswerLikeService answerLikeService;
    private final AnswerMemoService answerMemoService;
    private final MemberService memberService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public AnswerMemoResponse createAnswerMemo(Long answerId, AnswerMemoCreateRequest answerMemoCreateRequest, MemberAuth memberAuth) {
        Member member = memberService.readById(memberAuth.memberId());
        Answer answer = answerService.readById(answerId);
        answerService.validateAnswerOwner(answerId, member.getId());

        return answerMemoService.createAnswerMemo(answer, answerMemoCreateRequest);
    }

    @Transactional
    public void likeAnswer(Long answerId, MemberAuth memberAuth) {
        Member likerMember = memberService.readById(memberAuth.memberId());
        Answer answer = answerService.readById(answerId);
        Interview interview = answer.getQuestion().getInterview();
        Member interviewee = interview.getMember();

        answerLikeService.likeAnswer(likerMember, answer);
        answerService.incrementLikeCountModifying(answerId);
        answer = answerService.readById(answerId); // @Modifying에서 영속성 컨텍스트를 비운 뒤, 다시 조회

        eventPublisher.publishEvent(
                new AnswerLikedEvent(answerId, interview.getId(), likerMember.getId(), interviewee.getId(), answer.getLikeCount()));
    }

    @Transactional
    public void unlikeAnswer(Long answerId, MemberAuth memberAuth) {
        Member member = memberService.readById(memberAuth.memberId());
        answerService.readById(answerId);
        answerLikeService.unlikeAnswer(answerId, member.getId());
        answerService.decrementLikeCountModifying(answerId);
    }

    @Transactional
    public void updateAnswerMemo(Long answerId, AnswerMemoUpdateRequest answerMemoUpdateRequest, MemberAuth memberAuth) {
        Member member = memberService.readById(memberAuth.memberId());
        answerService.validateAnswerOwner(answerId, member.getId());
        answerMemoService.updateAnswerMemo(answerId, answerMemoUpdateRequest);
    }

    @Transactional
    public void deleteAnswerMemo(Long answerId, MemberAuth memberAuth) {
        Member member = memberService.readById(memberAuth.memberId());
        answerService.validateAnswerOwner(answerId, member.getId());
        answerMemoService.deleteAnswerMemo(answerId);
    }
}
