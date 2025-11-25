package com.samhap.kokomen.answer.service;

import com.samhap.kokomen.answer.domain.Answer;
import com.samhap.kokomen.answer.service.dto.AnswerMemoCreateRequest;
import com.samhap.kokomen.answer.service.dto.AnswerMemoResponse;
import com.samhap.kokomen.answer.service.dto.AnswerMemoUpdateRequest;
import com.samhap.kokomen.global.dto.MemberAuth;
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
    public AnswerMemoResponse createAnswerMemo(Long answerId, AnswerMemoCreateRequest answerMemoCreateRequest,
                                               MemberAuth memberAuth) {
        Member member = memberService.readById(memberAuth.memberId());
        Answer answer = answerService.readById(answerId);
        answerService.validateAnswerOwner(answerId, member.getId());

        return answerMemoService.createAnswerMemo(answer, answerMemoCreateRequest);
    }

    @Transactional
    public void likeAnswer(Long answerId, MemberAuth memberAuth) {
        Member likerMember = memberService.readById(memberAuth.memberId());
        Answer answer = answerService.readById(answerId);

        answerLikeService.likeAnswer(likerMember, answer);
        answerService.incrementLikeCountModifying(answerId);
    }

    @Transactional
    public void unlikeAnswer(Long answerId, MemberAuth memberAuth) {
        Member member = memberService.readById(memberAuth.memberId());
        answerService.readById(answerId);
        answerLikeService.unlikeAnswer(answerId, member.getId());
        answerService.decrementLikeCountModifying(answerId);
    }

    @Transactional
    public void updateAnswerMemo(Long answerId, AnswerMemoUpdateRequest answerMemoUpdateRequest,
                                 MemberAuth memberAuth) {
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
