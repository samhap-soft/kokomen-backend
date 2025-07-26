package com.samhap.kokomen.answer.service;

import com.samhap.kokomen.answer.domain.Answer;
import com.samhap.kokomen.answer.domain.AnswerLike;
import com.samhap.kokomen.answer.repository.AnswerLikeRepository;
import com.samhap.kokomen.global.exception.BadRequestException;
import com.samhap.kokomen.member.domain.Member;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class AnswerLikeService {

    private final AnswerLikeRepository answerLikeRepository;

    @Transactional
    public void likeAnswer(Member member, Answer answer) {
        validateNotAlreadyLiked(answer, member);
        answerLikeRepository.save(new AnswerLike(member, answer));
    }

    private void validateNotAlreadyLiked(Answer answer, Member member) {
        if (answerLikeRepository.existsByMemberIdAndAnswerId(member.getId(), answer.getId())) {
            throw new BadRequestException("이미 좋아요를 누른 답변입니다.");
        }
    }

    @Transactional
    public void unlikeAnswer(Long answerId, Long memberId) {
        validateAlreadyLiked(answerId, memberId);
        answerLikeRepository.deleteByAnswerIdAndMemberId(answerId, memberId);
    }

    private void validateAlreadyLiked(Long answerId, Long memberId) {
        if (!answerLikeRepository.existsByMemberIdAndAnswerId(memberId, answerId)) {
            throw new BadRequestException("좋아요를 누르지 않은 답변입니다.");
        }
    }
}
