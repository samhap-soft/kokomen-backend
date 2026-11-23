package com.samhap.kokomen.interview.tool;

import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignDiagram;
import java.util.List;

/**
 * 마지막 답변 평가와 리포트(L3) 호출 입력. interviewId는 사용량·실패 로그용이고 메시지에는 넣지 않는다.
 *
 * @param requirementTurns          이 면접의 역질문과 그때 돌려준 면접관 답(물은 순서)
 * @param previousTurns             마지막 질문 앞의 설계 질문과 답변(질문 순서). 다이어그램은 마지막 것만 넣는다
 * @param currentTurn               마지막 질문과 답변
 * @param currentDiagram            마지막 답변과 함께 낸 다이어그램
 * @param confirmedRequirementCodes 면접자가 확인한 숨은 요구사항 code(모든 역질문 행의 물은 요구사항 code 합집합). 순서와 중복은 상관없다
 */
public record SystemDesignEndCommand(
        Long interviewId,
        SystemDesignProblemContext problem,
        List<SystemDesignRequirementTurn> requirementTurns,
        List<SystemDesignAnswerTurn> previousTurns,
        SystemDesignAnswerTurn currentTurn,
        SystemDesignDiagram currentDiagram,
        List<String> confirmedRequirementCodes
) {

    public SystemDesignEndCommand {
        requirementTurns = List.copyOf(requirementTurns);
        previousTurns = List.copyOf(previousTurns);
        confirmedRequirementCodes = List.copyOf(confirmedRequirementCodes);
    }
}
