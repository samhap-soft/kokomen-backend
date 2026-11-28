package com.samhap.kokomen.interview.tool;

import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignConfirmedRequirement;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignDiagram;
import java.util.List;

/**
 * 답변 평가(L2) 호출 입력. 1~5번째 답변을 평가하고 다음 질문을 만든다. interviewId는 사용량·실패 로그용이고 메시지에는 넣지 않는다.
 *
 * @param requirementTurns      이 면접의 역질문과 그때 돌려준 면접관 답(물은 순서)
 * @param confirmedRequirements 역질문으로 확정한 요구사항(SystemDesignConfirmedRequirement.collect). 리포트의
 *                              confirmed_requirements와 같은 목록이다
 * @param previousTurns         이번 질문 앞의 설계 질문과 답변(질문 순서). 다이어그램은 이번 것만 넣는다
 * @param currentTurn           평가할 질문과 답변
 * @param currentDiagram        이번 답변과 함께 낸 다이어그램
 * @param nextBaseQuestion      다음 질문의 기준 질문
 */
public record SystemDesignAnswerCommand(
        Long interviewId,
        SystemDesignProblemContext problem,
        List<SystemDesignRequirementTurn> requirementTurns,
        List<SystemDesignConfirmedRequirement> confirmedRequirements,
        List<SystemDesignAnswerTurn> previousTurns,
        SystemDesignAnswerTurn currentTurn,
        SystemDesignDiagram currentDiagram,
        SystemDesignNextBaseQuestion nextBaseQuestion
) {

    public SystemDesignAnswerCommand {
        requirementTurns = List.copyOf(requirementTurns);
        confirmedRequirements = List.copyOf(confirmedRequirements);
        previousTurns = List.copyOf(previousTurns);
    }
}
