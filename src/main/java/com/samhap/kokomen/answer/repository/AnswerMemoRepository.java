package com.samhap.kokomen.answer.repository;

import com.samhap.kokomen.answer.domain.AnswerMemo;
import com.samhap.kokomen.answer.domain.AnswerMemoState;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnswerMemoRepository extends JpaRepository<AnswerMemo, Long> {


    boolean existsByAnswerIdAndAnswerMemoState(Long answerId, AnswerMemoState answerMemoState);

    Optional<AnswerMemo> findByAnswerIdAndAnswerMemoState(Long answerId, AnswerMemoState answerMemoState);
}
