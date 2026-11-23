package com.samhap.kokomen.interview.tool;

import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignEvaluationPoint;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignHiddenRequirement;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignProblem;
import java.util.Comparator;
import java.util.List;

/**
 * LLM 호출에 넘기는 문제 문맥. 엔티티가 아니라 값만 담아 트랜잭션 밖에서 LLM을 불러도 지연 로딩이 일어나지 않는다.
 * 숨은 요구사항과 평가 포인트는 표시 순서(문제 순서)다. 판정 필드와 결과의 순서가 이 순서를 따른다.
 */
public record SystemDesignProblemContext(
        Long problemId,
        String title,
        String description,
        String difficultyComment,
        List<Requirement> requirements,
        List<EvaluationPoint> evaluationPoints
) {

    public SystemDesignProblemContext {
        requirements = List.copyOf(requirements);
        evaluationPoints = List.copyOf(evaluationPoints);
    }

    public static SystemDesignProblemContext of(SystemDesignProblem problem,
                                                List<SystemDesignHiddenRequirement> hiddenRequirements,
                                                List<SystemDesignEvaluationPoint> evaluationPoints) {
        return new SystemDesignProblemContext(problem.getId(), problem.getTitle(), problem.getDescription(),
                problem.getDifficultyComment(),
                hiddenRequirements.stream()
                        .sorted(Comparator.comparing(SystemDesignHiddenRequirement::getDisplayOrder))
                        .map(requirement -> new Requirement(requirement.getCode(), requirement.getName(),
                                requirement.getAnswer()))
                        .toList(),
                evaluationPoints.stream()
                        .sorted(Comparator.comparing(SystemDesignEvaluationPoint::getDisplayOrder))
                        .map(point -> new EvaluationPoint(point.getCode(), point.getDescription()))
                        .toList());
    }

    /**
     * 숨은 요구사항. answer는 면접관이 답할 때 근거로 삼는 정해 둔 사실이다.
     */
    public record Requirement(String code, String name, String answer) {
    }

    public record EvaluationPoint(String code, String description) {
    }
}
