package com.samhap.kokomen.interview.service;

import com.samhap.kokomen.global.exception.BadRequestException;
import com.samhap.kokomen.interview.domain.Interview;
import com.samhap.kokomen.interview.domain.ResumeBasedRootQuestion;
import com.samhap.kokomen.interview.external.dto.response.GeneratedQuestionDto;
import com.samhap.kokomen.interview.repository.InterviewRepository;
import com.samhap.kokomen.interview.repository.ResumeBasedRootQuestionRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@RequiredArgsConstructor
@Service
public class InterviewStateService {

    private final InterviewRepository interviewRepository;
    private final ResumeBasedRootQuestionRepository questionRepository;

    @Transactional
    public void saveQuestionsAndComplete(Long interviewId, List<GeneratedQuestionDto> questions) {
        Interview interview = interviewRepository.findById(interviewId)
                .orElseThrow(() -> new BadRequestException("존재하지 않는 인터뷰입니다."));

        List<ResumeBasedRootQuestion> rootQuestions = questions.stream()
                .map(dto -> new ResumeBasedRootQuestion(
                        interview,
                        dto.question(),
                        dto.reason(),
                        questions.indexOf(dto)
                ))
                .toList();

        questionRepository.saveAll(rootQuestions);
        interview.completeQuestionGeneration();
    }

    @Transactional
    public void markAsFailed(Long interviewId) {
        try {
            Interview interview = interviewRepository.findById(interviewId)
                    .orElseThrow(() -> new BadRequestException("존재하지 않는 인터뷰입니다."));
            interview.failQuestionGeneration();
        } catch (Exception e) {
            log.error("인터뷰 실패 상태 업데이트 실패 - interviewId: {}", interviewId, e);
        }
    }
}
