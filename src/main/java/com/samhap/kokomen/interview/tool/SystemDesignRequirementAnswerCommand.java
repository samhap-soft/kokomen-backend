package com.samhap.kokomen.interview.tool;

import java.util.List;
import java.util.Objects;

/**
 * 역질문 답(L1) 호출 입력. interviewId는 사용량·실패 로그용이고 메시지에는 넣지 않는다.
 *
 * @param previousTurns         이 면접에서 이미 저장된 역질문과 답(물은 순서)
 * @param confirmedRequirements 이전 역질문들로 이미 확정한 요구사항
 * @param currentQuestion       정규화한 이번 역질문
 */
public record SystemDesignRequirementAnswerCommand(
        Long interviewId,
        SystemDesignProblemContext problem,
        List<SystemDesignRequirementTurn> previousTurns,
        SystemDesignConfirmedRequirements confirmedRequirements,
        String currentQuestion
) {

    public SystemDesignRequirementAnswerCommand {
        previousTurns = List.copyOf(previousTurns);
        Objects.requireNonNull(confirmedRequirements, "confirmedRequirements");
    }
}
