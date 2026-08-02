package com.samhap.kokomen.interview.repository.dto;

/**
 * 게터명이 정본이다. getCount()로 바꾸면 안 된다 — JPQL 별칭 count는 HQL 함수명과 충돌한다.
 * 이 프로젝션을 맵으로 모으는 쪽은 getAnalysisId()/getQuestionCount()를 호출한다.
 */
public interface QuestionCountProjection {

    Long getAnalysisId();

    Long getQuestionCount();
}
