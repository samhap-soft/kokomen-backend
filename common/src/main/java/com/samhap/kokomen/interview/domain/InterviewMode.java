package com.samhap.kokomen.interview.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum InterviewMode {


    TEXT(1),
    VOICE(2),
    ;

    private final int requiredTokenCount;
}
