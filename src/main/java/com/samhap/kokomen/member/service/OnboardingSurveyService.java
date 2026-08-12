package com.samhap.kokomen.member.service;

import com.samhap.kokomen.global.annotation.DistributedLock;
import com.samhap.kokomen.global.dto.MemberAuth;
import com.samhap.kokomen.member.domain.Member;
import com.samhap.kokomen.member.domain.survey.OnboardingSurvey;
import com.samhap.kokomen.member.repository.OnboardingSurveyRepository;
import com.samhap.kokomen.member.service.dto.OnboardingSurveySubmitRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class OnboardingSurveyService {

    private final OnboardingSurveyRepository onboardingSurveyRepository;
    private final MemberService memberService;

    /**
     * 회원 1명당 1행이므로 재제출은 기존 행을 덮어쓴다. 제출 버튼 더블 클릭 시 두 요청이 동시에 INSERT를 시도해 UNIQUE 제약을 위반하므로 회원 단위 분산 락으로 직렬화한다.
     */
    @DistributedLock(prefix = "onboarding-survey", key = "#memberAuth.memberId()")
    @Transactional
    public void submitOnboardingSurvey(MemberAuth memberAuth,
                                       OnboardingSurveySubmitRequest onboardingSurveySubmitRequest) {
        Member member = memberService.readById(memberAuth.memberId());
        onboardingSurveyRepository.findByMemberId(member.getId())
                .ifPresentOrElse(
                        onboardingSurvey -> updateOnboardingSurvey(onboardingSurvey, onboardingSurveySubmitRequest),
                        () -> saveOnboardingSurvey(member, onboardingSurveySubmitRequest)
                );
    }

    private void updateOnboardingSurvey(OnboardingSurvey onboardingSurvey,
                                        OnboardingSurveySubmitRequest onboardingSurveySubmitRequest) {
        onboardingSurvey.update(
                onboardingSurveySubmitRequest.careerGoal(),
                onboardingSurveySubmitRequest.prepStages(),
                onboardingSurveySubmitRequest.techTopics(),
                onboardingSurveySubmitRequest.targetCompanyType(),
                onboardingSurveySubmitRequest.interviewExperience(),
                onboardingSurveySubmitRequest.weakPoints(),
                onboardingSurveySubmitRequest.goalDescription()
        );
    }

    private void saveOnboardingSurvey(Member member, OnboardingSurveySubmitRequest onboardingSurveySubmitRequest) {
        onboardingSurveyRepository.save(new OnboardingSurvey(
                member,
                onboardingSurveySubmitRequest.careerGoal(),
                onboardingSurveySubmitRequest.prepStages(),
                onboardingSurveySubmitRequest.techTopics(),
                onboardingSurveySubmitRequest.targetCompanyType(),
                onboardingSurveySubmitRequest.interviewExperience(),
                onboardingSurveySubmitRequest.weakPoints(),
                onboardingSurveySubmitRequest.goalDescription()
        ));
    }
}
