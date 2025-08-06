package com.samhap.kokomen.interview.service;

import com.samhap.kokomen.interview.domain.Interview;
import com.samhap.kokomen.interview.domain.Question;
import com.samhap.kokomen.interview.repository.QuestionRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class QuestionService {

    private final QuestionRepository questionRepository;

    @Transactional
    public Question saveQuestion(Question question) {
        return questionRepository.save(question);
    }

    public List<Question> findByInterview(Interview interview) {
        return questionRepository.findByInterview(interview);
    }

    public Question readLastQuestionByInterviewId(Long interviewId) {
        return questionRepository.findFirstByInterviewIdOrderByIdDesc(interviewId)
                .orElseThrow(() -> new IllegalStateException("다음 인터뷰의 마지막 질문이 존재하지 않습니다. interviewId: " + interviewId));
    }
}
