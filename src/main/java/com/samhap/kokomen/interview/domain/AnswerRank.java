package com.samhap.kokomen.interview.domain;

import lombok.Getter;

@Getter
public enum AnswerRank {
    A(20),
    B(10),
    C(0),
    D(-10),
    F(-20),
    ;

    private final int score;

    AnswerRank(int score) {
        this.score = score;
    }
}
