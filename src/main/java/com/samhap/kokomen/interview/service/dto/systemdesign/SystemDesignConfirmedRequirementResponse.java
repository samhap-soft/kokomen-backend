package com.samhap.kokomen.interview.service.dto.systemdesign;

import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignConfirmedRequirement;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignRequirementSource;

/**
 * 요구사항 역질문으로 확정한 요구사항 하나. source가 SEED면 문제에 정해 둔 요구사항(숨은 요구사항)의 사실이고, ASSUMED면 문제에 정해
 * 두지 않아 면접관이 정해 답한 내용이다.
 */
public record SystemDesignConfirmedRequirementResponse(
        String name,
        String detail,
        SystemDesignRequirementSource source
) {

    public static SystemDesignConfirmedRequirementResponse from(SystemDesignConfirmedRequirement requirement) {
        return new SystemDesignConfirmedRequirementResponse(requirement.name(), requirement.detail(),
                requirement.source());
    }
}
