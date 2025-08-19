package com.samhap.kokomen.member.service.dto;

public interface RankingProjection {
    Long getId();

    String getNickname();

    Integer getScore();

    Long getFinishedInterviewCount();
}
