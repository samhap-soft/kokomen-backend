package com.samhap.kokomen.interview.external.dto.response;

public record TotalFeedbackResponse(
        OverallSummary overallSummary
) {

    public String composeTotalFeedback() {
        if (overallSummary == null) {
            return "";
        }
        return overallSummary.toParagraph();
    }

    public record OverallSummary(
            String strengths,
            String improvements,
            String learningDirection
    ) {

        public String toParagraph() {
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
}
