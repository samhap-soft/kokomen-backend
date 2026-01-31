package com.samhap.kokomen.interview.service.dto.resumebased;

import java.util.List;
import org.springframework.data.domain.Page;

public record ResumeQuestionGenerationPageResponse(
        List<ResumeQuestionGenerationResponse> data,
        int currentPage,
        long totalCount,
        int totalPages,
        boolean hasNext
) {
    public static ResumeQuestionGenerationPageResponse of(
            List<ResumeQuestionGenerationResponse> data,
            Page<?> page
    ) {
        return new ResumeQuestionGenerationPageResponse(
                data,
                page.getNumber(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.hasNext()
        );
    }
}
