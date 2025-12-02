package com.samhap.kokomen.resume.external.dto;

import java.util.List;

public record ResumeGptResponse(
        List<ResumeGptChoice> choices
) {
}
