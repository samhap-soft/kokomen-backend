package com.samhap.kokomen.interview.service;

import com.samhap.kokomen.global.exception.BadRequestException;
import com.samhap.kokomen.interview.domain.Interview;
import com.samhap.kokomen.interview.domain.ResumeBasedRootQuestion;
import com.samhap.kokomen.interview.external.dto.response.GeneratedQuestionDto;
import com.samhap.kokomen.interview.repository.InterviewRepository;
import com.samhap.kokomen.interview.repository.ResumeBasedRootQuestionRepository;
import java.util.List;
import java.util.stream.IntStream;
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

        List<ResumeBasedRootQuestion> rootQuestions = IntStream.range(0, questions.size())
                .mapToObj(i -> new ResumeBasedRootQuestion(
                        interview,
                        questions.get(i).question(),
                        questions.get(i).reason(),
                        i
                ))
                .toList();

        questionRepository.saveAll(rootQuestions);
        interview.completeQuestionGeneration();
    }

    @Transactional
    public void markAsFailed(Long interviewId) {
        interviewRepository.findById(interviewId).ifPresentOrElse(
                Interview::failQuestionGeneration,
                () -> log.error("인터뷰 실패 상태 업데이트 실패: ID {}에 해당하는 인터뷰를 찾을 수 없습니다.", interviewId)
        );
    }
}
