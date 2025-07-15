package com.samhap.kokomen.answer.service;

import com.samhap.kokomen.answer.domain.Answer;
import com.samhap.kokomen.answer.domain.AnswerLike;
import com.samhap.kokomen.answer.domain.AnswerMemo;
import com.samhap.kokomen.answer.domain.AnswerMemoState;
import com.samhap.kokomen.answer.repository.AnswerLikeRepository;
import com.samhap.kokomen.answer.repository.AnswerMemoRepository;
import com.samhap.kokomen.answer.repository.AnswerRepository;
import com.samhap.kokomen.answer.service.dto.AnswerMemoCreateRequest;
import com.samhap.kokomen.answer.service.dto.AnswerMemoResponse;
import com.samhap.kokomen.answer.service.dto.AnswerMemoUpdateRequest;
import com.samhap.kokomen.global.dto.MemberAuth;
import com.samhap.kokomen.global.exception.BadRequestException;
import com.samhap.kokomen.member.domain.Member;
import com.samhap.kokomen.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class AnswerService {

    private final MemberRepository memberRepository;
    private final AnswerRepository answerRepository;
    private final AnswerLikeRepository answerLikeRepository;
    private final AnswerMemoRepository answerMemoRepository;

    @Transactional
    public void likeAnswer(Long answerId, MemberAuth memberAuth) {
        Member member = readMember(memberAuth.memberId());
        Answer answer = readAnswer(answerId);
        if (answerLikeRepository.existsByMemberIdAndAnswerId(member.getId(), answer.getId())) {
            throw new BadRequestException("이미 좋아요를 누른 답변입니다.");
        }
        answerLikeRepository.save(new AnswerLike(member, answer));
        answerRepository.incrementLikeCount(answerId);
    }

    @Transactional
    public void unlikeAnswer(Long answerId, MemberAuth memberAuth) {
        Member member = readMember(memberAuth.memberId());
        readAnswer(answerId);
        int affectedRows = answerLikeRepository.deleteByAnswerIdAndMemberId(answerId, member.getId());
        if (affectedRows == 0) {
            throw new BadRequestException("좋아요를 누르지 않은 답변입니다.");
        }
        answerRepository.decrementLikeCount(answerId);
    }

    @Transactional
    public AnswerMemoResponse createAnswerMemo(Long answerId, AnswerMemoCreateRequest answerMemoCreateRequest, MemberAuth memberAuth) {
        Member member = readMember(memberAuth.memberId());
        Answer answer = readAnswer(answerId);
        validateAnswerOwner(answerId, member);
        validateSubmittedAnswerMemoAlreadyExists(answerId);
        AnswerMemo answerMemo = new AnswerMemo(answerMemoCreateRequest.content(), answer, answerMemoCreateRequest.visibility());
        answerMemoRepository.save(answerMemo);

        return new AnswerMemoResponse(answerMemo);
    }

    private Member readMember(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new BadRequestException("존재하지 않는 회원입니다."));
    }

    private void validateSubmittedAnswerMemoAlreadyExists(Long answerId) {
        if (answerMemoRepository.existsByAnswerIdAndAnswerMemoState(answerId, AnswerMemoState.SUBMITTED)) {
            throw new BadRequestException("이미 해당 답변에 메모가 존재합니다.");
        }
    }

    @Transactional
    public void updateAnswerMemo(Long answerId, AnswerMemoUpdateRequest answerMemoUpdateRequest, MemberAuth memberAuth) {
        Member member = readMember(memberAuth.memberId());
        validateAnswerOwner(answerId, member);
        AnswerMemo answerMemo = readSubmittedAnswerMemo(answerId);

        answerMemo.updateContent(answerMemoUpdateRequest.content());
        answerMemo.updateVisibility(answerMemoUpdateRequest.visibility());
    }

    @Transactional
    public void deleteAnswerMemo(Long answerId, MemberAuth memberAuth) {
        Member member = readMember(memberAuth.memberId());
        validateAnswerOwner(answerId, member);
        AnswerMemo answerMemo = readSubmittedAnswerMemo(answerId);
        answerMemoRepository.delete(answerMemo);
    }

    private Answer readAnswer(Long answerId) {
        return answerRepository.findById(answerId)
                .orElseThrow(() -> new BadRequestException("존재하지 않는 답변입니다."));
    }

    private void validateAnswerOwner(Long answerId, Member member) {
        if (!answerRepository.existsByIdAndQuestionInterviewMemberId(answerId, member.getId())) {
            throw new BadRequestException("다른 회원이 작성한 답변에 메모를 추가할 수 없습니다.");
        }
    }

    private AnswerMemo readSubmittedAnswerMemo(Long answerId) {
        return answerMemoRepository.findByAnswerIdAndAnswerMemoState(answerId, AnswerMemoState.SUBMITTED)
                .orElseThrow(() -> new BadRequestException("제출된 답변 메모가 없습니다."));
    }
}
