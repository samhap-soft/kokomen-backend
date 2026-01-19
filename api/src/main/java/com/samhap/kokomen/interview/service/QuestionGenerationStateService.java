package com.samhap.kokomen.interview.service;

import com.samhap.kokomen.global.exception.BadRequestException;
import com.samhap.kokomen.interview.domain.GeneratedQuestion;
import com.samhap.kokomen.interview.domain.ResumeQuestionGeneration;
import com.samhap.kokomen.interview.external.dto.response.GeneratedQuestionDto;
import com.samhap.kokomen.interview.repository.GeneratedQuestionRepository;
import com.samhap.kokomen.interview.repository.ResumeQuestionGenerationRepository;
import java.util.List;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@RequiredArgsConstructor
@Service
public class QuestionGenerationStateService {

    private final ResumeQuestionGenerationRepository generationRepository;
    private final GeneratedQuestionRepository questionRepository;

    @Transactional
    public void saveQuestionsAndComplete(Long generationId, List<GeneratedQuestionDto> questions) {
        ResumeQuestionGeneration generation = generationRepository.findById(generationId)
                .orElseThrow(() -> new BadRequestException("존재하지 않는 질문 생성 요청입니다."));

        List<GeneratedQuestion> generatedQuestions = IntStream.range(0, questions.size())
                .mapToObj(i -> new GeneratedQuestion(
                        generation,
                        questions.get(i).question(),
                        questions.get(i).reason(),
                        i
                ))
                .toList();

        questionRepository.saveAll(generatedQuestions);
        generation.complete();
    }

    @Transactional
    public void markAsFailed(Long generationId) {
        generationRepository.findById(generationId).ifPresentOrElse(
                ResumeQuestionGeneration::fail,
                () -> log.error("질문 생성 실패 상태 업데이트 실패: ID {}에 해당하는 요청을 찾을 수 없습니다.", generationId)
        );
    }
}
