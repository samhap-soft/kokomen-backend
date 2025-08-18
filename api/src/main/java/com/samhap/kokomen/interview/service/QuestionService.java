package com.samhap.kokomen.interview.service;

import com.samhap.kokomen.global.service.S3Service;
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

@RequiredArgsConstructor
@Service
public class QuestionService {

    private final S3Service s3Service;
    private final SupertoneClient supertoneClient;
    private final QuestionVoicePathResolver questionVoicePathResolver;
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
        return questionVoicePathResolver.resolveNextQuestionCdnPath(question.getId());
    }

    public String createAndUploadQuestionVoice(Question question) {
        SupertoneResponse supertoneResponse = supertoneClient.request(new SupertoneRequest(question.getContent()));
        s3Service.uploadS3File(questionVoicePathResolver.resolveNextQuestionS3Key(question.getId()), supertoneResponse.voiceData(), "audio/wav");

        return questionVoicePathResolver.resolveNextQuestionCdnPath(question.getId());
    }
}
