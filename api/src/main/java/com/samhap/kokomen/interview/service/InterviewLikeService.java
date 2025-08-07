package com.samhap.kokomen.interview.service;

import com.samhap.kokomen.global.exception.BadRequestException;
import com.samhap.kokomen.interview.domain.Interview;
import com.samhap.kokomen.interview.domain.InterviewLike;
import com.samhap.kokomen.interview.repository.InterviewLikeRepository;
import com.samhap.kokomen.member.domain.Member;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class InterviewLikeService {

    private final InterviewLikeRepository interviewLikeRepository;

    @Transactional
    public void likeInterview(InterviewLike interviewLike) {
        validateNotAlreadyLiked(interviewLike.getInterview(), interviewLike.getMember());
        interviewLikeRepository.save(interviewLike);
    }

    private void validateNotAlreadyLiked(Interview interview, Member member) {
        if (interviewLikeRepository.existsByInterviewIdAndMemberId(interview.getId(), member.getId())) {
            throw new BadRequestException("이미 좋아요를 누른 인터뷰입니다.");
        }
    }
}
