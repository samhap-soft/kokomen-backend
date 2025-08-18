package com.samhap.kokomen.interview.service;

import com.samhap.kokomen.category.domain.Category;
import com.samhap.kokomen.interview.domain.RootQuestion;
import com.samhap.kokomen.interview.domain.RootQuestionState;
import com.samhap.kokomen.interview.repository.RootQuestionRepository;
import com.samhap.kokomen.interview.service.dto.InterviewRequest;
import com.samhap.kokomen.member.domain.Member;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class RootQuestionService {

    private final RootQuestionRepository rootQuestionRepository;

    public RootQuestion findNextRootQuestionForMember(Member member, InterviewRequest interviewRequest) {
        Category category = interviewRequest.category();
        Optional<RootQuestion> firstRootQuestionNotReceived =
                rootQuestionRepository.findFirstRootQuestionMemberNotReceivedByCategory(category, member.getId(), RootQuestionState.ACTIVE);
        if (firstRootQuestionNotReceived.isPresent()) {
            return firstRootQuestionNotReceived.get();
        }

        RootQuestion lastRootQuestionReceived =
                rootQuestionRepository.findLastRootQuestionMemberReceivedByCategory(category, member.getId(), RootQuestionState.ACTIVE)
                        .orElseThrow(() -> new IllegalStateException("해당 카테고리의 질문을 찾을 수 없습니다."));

        int nextOrder = lastRootQuestionReceived.getQuestionOrder() + 1;
        return rootQuestionRepository.findRootQuestionByCategoryAndStateAndQuestionOrder(category, RootQuestionState.ACTIVE, nextOrder)
                .orElseGet(() -> findFirstRootQuestion(category));
    }

    private RootQuestion findFirstRootQuestion(Category category) {
        return rootQuestionRepository.findRootQuestionByCategoryAndStateAndQuestionOrder(category, RootQuestionState.ACTIVE, 1)
                .orElseThrow(() -> new IllegalStateException("해당 카테고리의 질문을 찾을 수 없습니다."));
    }
}
