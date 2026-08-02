package com.samhap.kokomen.resume.tool;

/**
 * 신규 이력서 분석 콜의 user 메시지 단일 소스. Bedrock과 GPT가 같은 문자열을 쓰도록 여기서만 조립한다.
 * JD 미제공 시 <job_requirements> 블록 자체를 넣지 않는다(JD_POLICY_ABSENT가 "제공되지 않았다"고 선언하므로
 * 빈 태그를 보내면 프롬프트와 입력이 어긋난다).
 */
public final class ResumeAnalysisUserMessages {

    private ResumeAnalysisUserMessages() {
    }

    public static String evaluation(boolean jdProvided, String resumeText, String portfolioText,
                                    String jobPosition, String jobDescription, String jobCareer) {
        return """
                <resume>
                %s
                </resume>

                <portfolio>
                %s
                </portfolio>

                <target_position>
                %s
                </target_position>

                %s<job_career>
                %s
                </job_career>
                """.formatted(
                nullToEmpty(resumeText),
                nullToEmpty(portfolioText),
                nullToEmpty(jobPosition),
                jobRequirementsBlock(jdProvided, jobDescription),
                nullToEmpty(jobCareer));
    }

    public static String questionGeneration(String resumeText, String portfolioText, String jobPosition,
                                            String jobCareer, String evaluationResult) {
        return """
                <resume>
                %s
                </resume>

                <portfolio>
                %s
                </portfolio>

                <target_position>
                %s
                </target_position>

                <job_career>
                %s
                </job_career>

                <evaluation_result>
                %s
                </evaluation_result>
                """.formatted(
                nullToEmpty(resumeText),
                nullToEmpty(portfolioText),
                nullToEmpty(jobPosition),
                nullToEmpty(jobCareer),
                nullToEmpty(evaluationResult));
    }

    private static String jobRequirementsBlock(boolean jdProvided, String jobDescription) {
        if (!jdProvided) {
            return "";
        }
        return """
                <job_requirements>
                %s
                </job_requirements>

                """.formatted(nullToEmpty(jobDescription));
    }

    private static String nullToEmpty(String value) {
        return value != null ? value : "";
    }
}
