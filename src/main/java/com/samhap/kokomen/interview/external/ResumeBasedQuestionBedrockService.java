package com.samhap.kokomen.interview.external;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.samhap.kokomen.global.exception.ExternalApiException;
import com.samhap.kokomen.interview.external.dto.response.GeneratedQuestionDto;
import com.samhap.kokomen.interview.external.dto.response.QuestionResponseWrapper;
import com.samhap.kokomen.resume.external.ResumeBasedQuestionBedrockClient;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class ResumeBasedQuestionBedrockService {

    private final ResumeBasedQuestionBedrockClient bedrockClient;
    private final ResumeBasedQuestionGptClient gptClient;
    private final ObjectMapper objectMapper;

    public ResumeBasedQuestionBedrockService(
            ResumeBasedQuestionBedrockClient bedrockClient,
            ResumeBasedQuestionGptClient gptClient,
            ObjectMapper objectMapper
    ) {
        this.bedrockClient = bedrockClient;
        this.gptClient = gptClient;
        this.objectMapper = objectMapper;
    }

    public List<GeneratedQuestionDto> generateQuestions(
            String resumeText,
            String portfolioText,
            String jobCareer
    ) {
        try {
            return bedrockClient.generateQuestions(resumeText, portfolioText, jobCareer);
        } catch (Exception e) {
            log.error("Bedrock 질문 생성 실패, GPT 폴백 시도", e);
            return generateQuestionsWithGpt(resumeText, portfolioText, jobCareer);
        }
    }

    private List<GeneratedQuestionDto> generateQuestionsWithGpt(
            String resumeText,
            String portfolioText,
            String jobCareer
    ) {
        String jsonResponse = gptClient.generateQuestions(resumeText, portfolioText, jobCareer);
        return parseQuestionResponse(jsonResponse);
    }

    private List<GeneratedQuestionDto> parseQuestionResponse(String jsonResponse) {
        try {
            String cleanedJson = cleanJsonContent(jsonResponse);
            QuestionResponseWrapper wrapper = objectMapper.readValue(cleanedJson, QuestionResponseWrapper.class);
            return wrapper.questions();
        } catch (JsonProcessingException e) {
            log.error("GPT 질문 응답 파싱 실패: {}", jsonResponse, e);
            throw new ExternalApiException("질문 응답을 파싱하는데 실패했습니다.");
        }
    }

    private String cleanJsonContent(String rawText) {
        if (rawText == null || rawText.isEmpty()) {
            return rawText;
        }
        String cleaned = rawText
                .replace("```json", "")
                .replace("```", "")
                .replace("`", "")
                .trim();

        if (cleaned.startsWith("\"") && cleaned.endsWith("\"")) {
            String unwrapped = cleaned.substring(1, cleaned.length() - 1);
            return unwrapped.replace("\\\"", "\"").replace("\\n", "\n");
        }
        return cleaned;
    }
}
