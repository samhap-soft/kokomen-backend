package com.samhap.kokomen.interview.repository.dto;

/**
 * 게터명이 정본이다. getCount()로 바꾸면 안 된다 — JPQL 별칭 count는 HQL 함수명과 충돌한다.
 * Task 10의 readQuestionCounts는 getAnalysisId()/getQuestionCount()를 호출해야 한다.
 */
public interface QuestionCountProjection {

    Long getAnalysisId();

    Long getQuestionCount();
}
