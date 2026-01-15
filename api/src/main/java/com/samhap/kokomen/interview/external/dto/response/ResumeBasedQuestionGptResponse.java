package com.samhap.kokomen.interview.external.dto.response;

import java.util.List;

public record ResumeBasedQuestionGptResponse(
        List<ResumeBasedQuestionGptChoice> choices
) {
}
