package com.samhap.kokomen.answer.service;

import com.samhap.kokomen.answer.repository.AnswerLikeRepository;
import com.samhap.kokomen.answer.repository.AnswerRepository;
import com.samhap.kokomen.global.dto.MemberAuth;
import com.samhap.kokomen.global.exception.BadRequestException;
import com.samhap.kokomen.interview.domain.Answer;
import com.samhap.kokomen.interview.domain.AnswerLike;
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

    private Member readMember(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new BadRequestException("존재하지 않는 회원입니다."));
    }

    private Answer readAnswer(Long answerId) {
        return answerRepository.findById(answerId)
                .orElseThrow(() -> new BadRequestException("존재하지 않는 답변입니다."));
    }
}
