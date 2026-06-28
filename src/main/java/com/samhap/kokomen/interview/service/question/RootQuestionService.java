package com.samhap.kokomen.interview.service.question;

import com.samhap.kokomen.category.domain.Category;
import com.samhap.kokomen.global.exception.NotFoundException;
import com.samhap.kokomen.global.service.S3Service;
import com.samhap.kokomen.interview.tool.QuestionVoicePathResolver;
import com.samhap.kokomen.interview.domain.RootQuestion;
import com.samhap.kokomen.interview.domain.RootQuestionState;
import com.samhap.kokomen.interview.domain.RootQuestionType;
import com.samhap.kokomen.interview.external.SupertoneClient;
import com.samhap.kokomen.interview.external.dto.request.SupertoneRequest;
import com.samhap.kokomen.interview.external.dto.response.SupertoneResponse;
import com.samhap.kokomen.interview.repository.RootQuestionRepository;
import com.samhap.kokomen.interview.service.dto.InterviewRequest;
import com.samhap.kokomen.member.domain.Member;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class RootQuestionService {

    private final S3Service s3Service;
    private final RootQuestionRepository rootQuestionRepository;
    private final SupertoneClient supertoneClient;
    private final QuestionVoicePathResolver questionVoicePathResolver;

    public RootQuestion readRandomActiveRootQuestion() {
        List<RootQuestion> rootQuestions = rootQuestionRepository.findAllByStateAndQuestionTypeAndCategoryNot(
                RootQuestionState.ACTIVE, RootQuestionType.GENERAL, Category.PERSONALITY);
        if (rootQuestions.isEmpty()) {
            throw new NotFoundException("활성화된 루트 질문이 존재하지 않습니다.");
        }
        return rootQuestions.get(ThreadLocalRandom.current().nextInt(rootQuestions.size()));
    }

    public RootQuestion findNextRootQuestionForMember(Member member, InterviewRequest interviewRequest) {
        Category category = interviewRequest.category();
        if (interviewRequest.includeLiveCoding()) {
            return readRandomActiveRootQuestionByCategory(category);
        }

        Optional<RootQuestion> firstRootQuestionNotReceived =
                rootQuestionRepository.findFirstRootQuestionMemberNotReceivedByCategory(category, member.getId(),
                        RootQuestionState.ACTIVE, RootQuestionType.GENERAL);
        if (firstRootQuestionNotReceived.isPresent()) {
            return firstRootQuestionNotReceived.get();
        }

        RootQuestion lastRootQuestionReceived =
                rootQuestionRepository.findLastRootQuestionMemberReceivedByCategory(category, member.getId(),
                                RootQuestionState.ACTIVE, RootQuestionType.GENERAL)
                        .orElseThrow(() -> new NotFoundException("해당 카테고리의 질문을 찾을 수 없습니다."));

        int nextOrder = lastRootQuestionReceived.getQuestionOrder() + 1;
        return rootQuestionRepository.findRootQuestionByCategoryAndStateAndQuestionOrder(category,
                        RootQuestionState.ACTIVE, nextOrder)
                .orElseGet(() -> findFirstRootQuestion(category));
    }

    private RootQuestion readRandomActiveRootQuestionByCategory(Category category) {
        List<RootQuestion> rootQuestions = rootQuestionRepository.findAllByCategoryAndState(category,
                RootQuestionState.ACTIVE);
        if (rootQuestions.isEmpty()) {
            throw new NotFoundException("해당 카테고리의 질문을 찾을 수 없습니다.");
        }
        return rootQuestions.get(ThreadLocalRandom.current().nextInt(rootQuestions.size()));
    }

    private RootQuestion findFirstRootQuestion(Category category) {
        return rootQuestionRepository.findRootQuestionByCategoryAndStateAndQuestionOrder(category,
                        RootQuestionState.ACTIVE, 1)
                .orElseThrow(() -> new NotFoundException("해당 카테고리의 질문을 찾을 수 없습니다."));
    }

    public boolean isRootQuestionVoiceExists(Long rootQuestionId) {
        String rootQuestionS3Key = questionVoicePathResolver.resolveRootQuestionS3Key(rootQuestionId);
        return s3Service.exists(rootQuestionS3Key);
    }

    public String createAndUploadRootQuestionVoice(Long rootQuestionId) {
        RootQuestion rootQuestion = readRootQuestion(rootQuestionId);
        SupertoneResponse supertoneResponse = supertoneClient.request(new SupertoneRequest(rootQuestion.getContent()));
        s3Service.uploadS3File(questionVoicePathResolver.resolveRootQuestionS3Key(rootQuestionId),
                supertoneResponse.voiceData(), "audio/wav");

        return questionVoicePathResolver.resolveRootQuestionCdnPath(rootQuestionId);
    }

    public String createAndUploadRootQuestionVoiceWithApiKey(Long rootQuestionId, String oneTimeApiKey) {
        RootQuestion rootQuestion = readRootQuestion(rootQuestionId);
        SupertoneResponse supertoneResponse = supertoneClient.requestWithApiKey(
                new SupertoneRequest(rootQuestion.getContent()), oneTimeApiKey);
        s3Service.uploadS3File(questionVoicePathResolver.resolveRootQuestionS3Key(rootQuestionId),
                supertoneResponse.voiceData(), "audio/wav");

        return questionVoicePathResolver.resolveRootQuestionCdnPath(rootQuestionId);
    }

    public RootQuestion readRootQuestion(Long rootQuestionId) {
        return rootQuestionRepository.findById(rootQuestionId)
                .orElseThrow(
                        () -> new NotFoundException("루트 질문이 존재하지 않습니다. rootQuestionId = " + rootQuestionId));
    }

    public List<RootQuestion> findAllRootQuestionByCategory(Category category) {
        return rootQuestionRepository.findAllByCategoryAndState(category, RootQuestionState.ACTIVE);
    }
}
