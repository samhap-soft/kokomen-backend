package com.samhap.kokomen.answer.service;

import com.samhap.kokomen.answer.domain.Answer;
import com.samhap.kokomen.answer.repository.AnswerRepository;
import com.samhap.kokomen.global.exception.BadRequestException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class AnswerService {

    private final AnswerRepository answerRepository;

    public Answer readById(Long answerId) {
        return answerRepository.findById(answerId)
                .orElseThrow(() -> new BadRequestException("존재하지 않는 답변입니다."));
    }

    public void validateAnswerOwner(Long answerId, Long memberId) {
        if (!answerRepository.existsByIdAndQuestionInterviewMemberId(answerId, memberId)) {
            throw new BadRequestException("다른 회원이 작성한 답변에 메모를 추가할 수 없습니다.");
        }
    }

    @Transactional
    public void incrementLikeCountModifying(Long answerId) {
        answerRepository.incrementLikeCountModifying(answerId);
    }

    @Transactional
    public void decrementLikeCountModifying(Long answerId) {
        answerRepository.decrementLikeCountModifying(answerId);
    }

}
