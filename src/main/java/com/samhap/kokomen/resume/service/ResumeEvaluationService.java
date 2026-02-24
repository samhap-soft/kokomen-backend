package com.samhap.kokomen.resume.service;

import com.samhap.kokomen.global.exception.BadRequestException;
import com.samhap.kokomen.resume.domain.MemberPortfolio;
import com.samhap.kokomen.resume.domain.MemberResume;
import com.samhap.kokomen.resume.domain.ResumeEvaluation;
import com.samhap.kokomen.resume.repository.ResumeEvaluationRepository;
import com.samhap.kokomen.resume.service.dto.ResumeEvaluationResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@RequiredArgsConstructor
@Service
public class ResumeEvaluationService {

    private final ResumeEvaluationRepository resumeEvaluationRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ResumeEvaluation saveEvaluation(ResumeEvaluation evaluation) {
        return resumeEvaluationRepository.save(evaluation);
    }

    @Transactional(readOnly = true)
    public ResumeEvaluation readById(Long id) {
        return resumeEvaluationRepository.findById(id)
                .orElseThrow(() -> new BadRequestException("이력서 평가를 찾을 수 없습니다. id: " + id));
    }

    @Transactional(readOnly = true)
    public Page<ResumeEvaluation> findByMemberId(Long memberId, Pageable pageable) {
        return resumeEvaluationRepository.findByMemberIdOrderByCreatedAtDesc(memberId, pageable);
    }

    @Transactional
    public void updateCompleted(Long evaluationId, ResumeEvaluationResponse response) {
        ResumeEvaluation evaluation = resumeEvaluationRepository.findById(evaluationId)
                .orElseThrow(() -> new BadRequestException("이력서 평가를 찾을 수 없습니다. id: " + evaluationId));
        evaluation.complete(
                response.technicalSkills().score(),
                response.technicalSkills().reason(),
                response.technicalSkills().improvements(),
                response.projectExperience().score(),
                response.projectExperience().reason(),
                response.projectExperience().improvements(),
                response.problemSolving().score(),
                response.problemSolving().reason(),
                response.problemSolving().improvements(),
                response.careerGrowth().score(),
                response.careerGrowth().reason(),
                response.careerGrowth().improvements(),
                response.documentation().score(),
                response.documentation().reason(),
                response.documentation().improvements(),
                response.totalScore(),
                response.totalFeedback()
        );
    }

    @Transactional
    public void updateMemberResume(Long evaluationId, MemberResume memberResume, MemberPortfolio memberPortfolio) {
        ResumeEvaluation evaluation = resumeEvaluationRepository.findById(evaluationId)
                .orElseThrow(() -> new BadRequestException("이력서 평가를 찾을 수 없습니다. id: " + evaluationId));
        evaluation.updateMemberResume(memberResume, memberPortfolio);
    }

    @Transactional
    public void updateFailed(Long evaluationId) {
        ResumeEvaluation evaluation = resumeEvaluationRepository.findById(evaluationId)
                .orElseThrow(() -> new BadRequestException("이력서 평가를 찾을 수 없습니다. id: " + evaluationId));
        evaluation.fail();
    }
}
