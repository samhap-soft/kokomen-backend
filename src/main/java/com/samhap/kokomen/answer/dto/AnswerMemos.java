package com.samhap.kokomen.answer.dto;

import com.samhap.kokomen.answer.domain.AnswerMemo;
import java.util.Optional;

public record AnswerMemos(
        String submittedAnswerMemo,
        String tempAnswerMemo
) {

    public static AnswerMemos createMine(AnswerMemo submittedAnswerMemo, AnswerMemo tempAnswerMemo) {
        return new AnswerMemos(
                Optional.ofNullable(submittedAnswerMemo)
                        .map(AnswerMemo::getContent)
                        .orElse(""),
                Optional.ofNullable(tempAnswerMemo)
                        .map(AnswerMemo::getContent)
                        .orElse("")
        );
    }

    public static AnswerMemos createOfOtherMember(AnswerMemo submittedAnswerMemo) {
        return new AnswerMemos(
                Optional.ofNullable(submittedAnswerMemo)
                        .map(AnswerMemo::getContent)
                        .orElse(""),
                null
        );
    }
}
