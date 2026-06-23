package com.samhap.kokomen.interview.external.dto.response;

public record TotalFeedbackResponse(
        String strengths,
        String improvements,
        String learningDirection
) {

    public String composeTotalFeedback() {
        return joinNonBlank(strengths, improvements, learningDirection);
    }

    private static String joinNonBlank(String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part == null || part.isBlank()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(part.replaceAll("\\s+", " ").trim());
        }
        return sb.toString();
    }
}
