package com.samhap.kokomen.interview.service;

import com.samhap.kokomen.global.service.RedisService;
import com.samhap.kokomen.interview.domain.Interview;
import com.samhap.kokomen.interview.domain.Question;
import com.samhap.kokomen.interview.domain.QuestionVoicePathResolver;
import com.samhap.kokomen.interview.external.SupertoneClient;
import com.samhap.kokomen.interview.external.dto.request.SupertoneRequest;
import com.samhap.kokomen.interview.external.dto.response.SupertoneResponse;
import com.samhap.kokomen.interview.repository.QuestionRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@RequiredArgsConstructor
@Service
public class QuestionService {

    public static final String QUESTION_VOICE_URL_KEY_FORMAT = "question:%d:voice_url";
    private static final int TYPECAST_FORCED_TTL_HOURS = 24;

    private final S3Client s3Client;
    private final QuestionVoicePathResolver questionVoicePathResolver;
    private final SupertoneClient supertoneClient;
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
        return questionVoicePathResolver.resolveQuestionCdnPath(question.getId());
    }

    public String createAndUploadQuestionVoice(Question question) {
        SupertoneResponse supertoneResponse = supertoneClient.request(new SupertoneRequest(question.getContent()));

        PutObjectRequest s3Request = PutObjectRequest.builder()
                .bucket(QuestionVoicePathResolver.bucketName)
                .key(questionVoicePathResolver.resolveNextQuestionS3Key(question.getId()))
                .contentType("audio/wav")
                .contentLength((long) supertoneResponse.voiceData().length)
                .build();

        s3Client.putObject(s3Request, RequestBody.fromBytes(supertoneResponse.voiceData()));

        return questionVoicePathResolver.resolveQuestionCdnPath(question.getId());
    }
}
