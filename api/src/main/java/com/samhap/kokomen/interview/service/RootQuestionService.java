package com.samhap.kokomen.interview.service;

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
        Optional<RootQuestion> firstNotReceived =
                rootQuestionRepository.findFirstQuestionMemberNotReceivedByCategory(interviewRequest.category(), member.getId(), RootQuestionState.ACTIVE);
        if (firstNotReceived.isPresent()) {
            return firstNotReceived.get();
        }

        return rootQuestionRepository.findLastQuestionMemberReceivedByCategory(interviewRequest.category(), member.getId(), RootQuestionState.ACTIVE)
                .orElseThrow(() -> new IllegalStateException("해당 카테고리의 질문을 찾을 수 없습니다."));
    }
}
