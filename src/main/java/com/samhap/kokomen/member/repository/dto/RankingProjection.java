package com.samhap.kokomen.member.repository.dto;

public interface RankingProjection {
    Long getId();

    String getNickname();

    Integer getScore();

    Long getFinishedInterviewCount();
}
