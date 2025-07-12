package com.samhap.kokomen.answer.repository;

import com.samhap.kokomen.answer.domain.AnswerMemo;
import com.samhap.kokomen.answer.domain.AnswerMemoVisibility;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnswerMemoRepository extends JpaRepository<AnswerMemo, Long> {


    boolean existsByAnswerIdAndAnswerMemoVisibility(Long answerId, AnswerMemoVisibility answerMemoVisibility);
}
