package com.samhap.kokomen.interview.service;

import com.samhap.kokomen.global.service.RedisService;
import com.samhap.kokomen.interview.domain.Interview;
import com.samhap.kokomen.interview.domain.Question;
import com.samhap.kokomen.interview.external.TypecastClient;
import com.samhap.kokomen.interview.external.dto.request.TypecastRequest;
import com.samhap.kokomen.interview.external.dto.response.TypecastResponse;
import com.samhap.kokomen.interview.repository.QuestionRepository;
import java.time.Duration;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class QuestionService {

    public static final String QUESTION_VOICE_URL_KEY_FORMAT = "question:%d:voice_url";
    private static final int TYPECAST_FORCED_TTL_HOURS = 24;

    private final TypecastClient typecastClient;
    private final RedisService redisService;
    private final QuestionRepository questionRepository;

    @Transactional
    public Question saveQuestion(Question question) {
        return questionRepository.save(question);
    }

    public List<Question> findByInterview(Interview interview) {
        return questionRepository.findByInterview(interview);
    }

    public List<Question> readLastTwoQuestionsByInterviewId(Long interviewId) {
        return questionRepository.findTop2ByInterviewIdOrderByIdDesc(interviewId);
    }

    public String resolveQuestionVoiceUrl(Question question) {
        String questionVoiceUrlKey = createQuestionVoiceUrlKey(question.getId());
        return redisService.get(questionVoiceUrlKey, String.class)
                .orElseGet(() -> createQuestionVoiceUrl(question));
    }

    public String createQuestionVoiceUrl(Question question) {
        TypecastResponse typecastResponse = typecastClient.request(new TypecastRequest(question.getContent()));
        String questionVoiceUrl = typecastResponse.getSpeakV2Url();
        redisService.setValue(createQuestionVoiceUrlKey(question.getId()), questionVoiceUrl, Duration.ofHours(TYPECAST_FORCED_TTL_HOURS - 1));
        return questionVoiceUrl;
    }

    public static String createQuestionVoiceUrlKey(Long questionId) {
        return QUESTION_VOICE_URL_KEY_FORMAT.formatted(questionId);
    }
}
