package com.samhap.kokomen.interview.service.dto.systemdesign;

import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignProblem;

public record SystemDesignTopicResponse(
        String title,
        String description
) {

    public static SystemDesignTopicResponse from(SystemDesignProblem problem) {
        return new SystemDesignTopicResponse(problem.getTitle(), problem.getDescription());
    }
}
