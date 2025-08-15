package com.samhap.kokomen.interview.service;

import com.samhap.kokomen.global.service.S3Service;
import com.samhap.kokomen.interview.domain.QuestionVoicePathResolver;
import com.samhap.kokomen.interview.domain.RootQuestion;
import com.samhap.kokomen.interview.external.SupertoneClient;
import com.samhap.kokomen.interview.external.dto.request.SupertoneRequest;
import com.samhap.kokomen.interview.external.dto.response.SupertoneResponse;
import com.samhap.kokomen.interview.repository.RootQuestionRepository;
import com.samhap.kokomen.interview.service.dto.InterviewRequest;
import com.samhap.kokomen.member.domain.Member;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class RootQuestionService {

    private static final int EXCLUDED_RECENT_ROOT_QUESTION_COUNT = 50;

    private final S3Service s3Service;
    private final RootQuestionRepository rootQuestionRepository;
    private final SupertoneClient supertoneClient;
    private final QuestionVoicePathResolver questionVoicePathResolver;

    public RootQuestion readRandomRootQuestion(Member member, InterviewRequest interviewRequest) {
        String category = interviewRequest.category().name();

        return rootQuestionRepository.findRandomByCategoryExcludingRecent(
                member.getId(),
                category,
                EXCLUDED_RECENT_ROOT_QUESTION_COUNT
        ).orElseThrow(() -> new IllegalStateException("루트 질문 갯수가 부족합니다. category = " + category));
    }

    public boolean isRootQuestionVoiceExists(Long rootQuestionId) {
        String rootQuestionS3Key = questionVoicePathResolver.resolveRootQuestionS3Key(rootQuestionId);
        return s3Service.exists(rootQuestionS3Key);
    }

    public String createAndUploadRootQuestionVoice(Long rootQuestionId) {
        RootQuestion rootQuestion = readRootQuestion(rootQuestionId);
        SupertoneResponse supertoneResponse = supertoneClient.request(new SupertoneRequest(rootQuestion.getContent()));
        s3Service.uploadS3File(questionVoicePathResolver.resolveRootQuestionS3Key(rootQuestionId), supertoneResponse.voiceData(), "audio/wav");

        return questionVoicePathResolver.resolveRootQuestionCdnPath(rootQuestionId);
    }

    public RootQuestion readRootQuestion(Long rootQuestionId) {
        return rootQuestionRepository.findById(rootQuestionId)
                .orElseThrow(() -> new IllegalArgumentException("루트 질문이 존재하지 않습니다. rootQuestionId = " + rootQuestionId));
    }
}
