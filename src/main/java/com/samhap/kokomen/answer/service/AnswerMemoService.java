package com.samhap.kokomen.answer.service;

import com.samhap.kokomen.answer.domain.Answer;
import com.samhap.kokomen.answer.domain.AnswerMemo;
import com.samhap.kokomen.answer.domain.AnswerMemoState;
import com.samhap.kokomen.answer.repository.AnswerMemoRepository;
import com.samhap.kokomen.answer.service.dto.AnswerMemoCreateRequest;
import com.samhap.kokomen.answer.service.dto.AnswerMemoResponse;
import com.samhap.kokomen.answer.service.dto.AnswerMemoUpdateRequest;
import com.samhap.kokomen.global.exception.BadRequestException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class AnswerMemoService {

    private final AnswerMemoRepository answerMemoRepository;

    @Transactional
    public AnswerMemoResponse createAnswerMemo(Answer answer, AnswerMemoCreateRequest answerMemoCreateRequest) {
        validateSubmittedAnswerMemoAlreadyExists(answer.getId());
        AnswerMemo answerMemo = answerMemoRepository.save(new AnswerMemo(answerMemoCreateRequest.content(), answer, answerMemoCreateRequest.visibility()));

        return new AnswerMemoResponse(answerMemo);
    }

    private void validateSubmittedAnswerMemoAlreadyExists(Long answerId) {
        if (existsByAnswerIdAndAnswerMemoState(answerId, AnswerMemoState.SUBMITTED)) {
            throw new BadRequestException("이미 해당 답변에 메모가 존재합니다.");
        }
    }

    public boolean existsByAnswerIdAndAnswerMemoState(Long answerId, AnswerMemoState state) {
        return answerMemoRepository.existsByAnswerIdAndAnswerMemoState(answerId, state);
    }

    @Transactional
    public void updateAnswerMemo(Long answerId, AnswerMemoUpdateRequest answerMemoUpdateRequest) {
        AnswerMemo answerMemo = readByAnswerIdAndAnswerMemoState(answerId, AnswerMemoState.SUBMITTED);
        answerMemo.updateContent(answerMemoUpdateRequest.content());
        answerMemo.updateVisibility(answerMemoUpdateRequest.visibility());
    }

    @Transactional
    public void deleteAnswerMemo(Long answerId) {
        AnswerMemo answerMemo = readByAnswerIdAndAnswerMemoState(answerId, AnswerMemoState.SUBMITTED);
        answerMemoRepository.delete(answerMemo);
    }

    private AnswerMemo readByAnswerIdAndAnswerMemoState(Long answerId, AnswerMemoState state) {
        return answerMemoRepository.findByAnswerIdAndAnswerMemoState(answerId, state)
                .orElseThrow(() -> new BadRequestException("제출된 답변 메모가 없습니다."));
    }
}
