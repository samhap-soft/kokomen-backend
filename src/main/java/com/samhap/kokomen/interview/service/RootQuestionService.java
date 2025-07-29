package com.samhap.kokomen.interview.service;

import com.samhap.kokomen.interview.domain.RootQuestion;
import com.samhap.kokomen.interview.repository.RootQuestionRepository;
import com.samhap.kokomen.interview.service.dto.InterviewRequest;
import com.samhap.kokomen.member.domain.Member;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class RootQuestionService {

    private static final int EXCLUDED_RECENT_ROOT_QUESTION_COUNT = 50;

    private final RootQuestionRepository rootQuestionRepository;

    public RootQuestion readRandomRootQuestion(Member member, InterviewRequest interviewRequest) {
        String category = interviewRequest.category().name();

        return rootQuestionRepository.findRandomByCategoryExcludingRecent(
                member.getId(),
                category,
                EXCLUDED_RECENT_ROOT_QUESTION_COUNT
        ).orElseThrow(() -> new IllegalStateException("루트 질문 갯수가 부족합니다. category = " + category));
    }
}
