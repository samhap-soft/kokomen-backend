package com.samhap.kokomen.answer.repository;

import com.samhap.kokomen.answer.domain.Answer;
import com.samhap.kokomen.answer.domain.AnswerMemo;
import com.samhap.kokomen.answer.domain.AnswerMemoState;
import com.samhap.kokomen.answer.domain.AnswerMemoVisibility;
import com.samhap.kokomen.interview.domain.Interview;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnswerMemoRepository extends JpaRepository<AnswerMemo, Long> {

    Long countByAnswerQuestionInterviewAndAnswerMemoState(Interview interview, AnswerMemoState answerMemoState);

    Long countByAnswerQuestionInterviewAndAnswerMemoStateAndAnswerMemoVisibility(
            Interview interview, AnswerMemoState answerMemoState, AnswerMemoVisibility answerMemoVisibility);

    boolean existsByAnswerIdAndAnswerMemoState(Long answerId, AnswerMemoState answerMemoState);
    Boolean existsByAnswerQuestionInterviewAndAnswerMemoState(Interview interview, AnswerMemoState answerMemoState);

    Optional<AnswerMemo> findByAnswerIdAndAnswerMemoState(Long answerId, AnswerMemoState answerMemoState);
    Optional<AnswerMemo> findByAnswerAndAnswerMemoState(Answer answer, AnswerMemoState answerMemoState);

    Optional<AnswerMemo> findByAnswerAndAnswerMemoStateAndAnswerMemoVisibility(Answer answer, AnswerMemoState answerMemoState,
                                                                               AnswerMemoVisibility answerMemoVisibility);
}
