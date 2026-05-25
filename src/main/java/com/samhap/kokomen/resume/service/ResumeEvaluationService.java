package com.samhap.kokomen.resume.service;

import com.samhap.kokomen.global.exception.BadRequestException;
import com.samhap.kokomen.resume.domain.MemberPortfolio;
import com.samhap.kokomen.resume.domain.MemberResume;
import com.samhap.kokomen.resume.domain.ResumeEvaluation;
import com.samhap.kokomen.resume.external.dto.ResumeEvaluationLlmResponse;
import com.samhap.kokomen.resume.external.dto.ResumeEvaluationLlmResponse.CategoryScore;
import com.samhap.kokomen.resume.repository.ResumeEvaluationRepository;
import java.util.List;
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
    public void updateCompleted(Long evaluationId, ResumeEvaluationLlmResponse response) {
        ResumeEvaluation evaluation = resumeEvaluationRepository.findById(evaluationId)
                .orElseThrow(() -> new BadRequestException("이력서 평가를 찾을 수 없습니다. id: " + evaluationId));
        evaluation.complete(
                scoreOf(response.technicalSkills()),
                reasonOf(response.technicalSkills()),
                improvementsOf(response.technicalSkills()),
                scoreOf(response.projectExperience()),
                reasonOf(response.projectExperience()),
                improvementsOf(response.projectExperience()),
                scoreOf(response.problemSolving()),
                reasonOf(response.problemSolving()),
                improvementsOf(response.problemSolving()),
                scoreOf(response.careerGrowth()),
                reasonOf(response.careerGrowth()),
                improvementsOf(response.careerGrowth()),
                scoreOf(response.documentation()),
                reasonOf(response.documentation()),
                improvementsOf(response.documentation()),
                response.totalScore(),
                response.totalFeedback()
        );
    }

    private static int scoreOf(CategoryScore category) {
        return category != null ? category.score() : 0;
    }

    private static List<String> reasonOf(CategoryScore category) {
        return category != null ? category.reason() : List.of();
    }

    private static List<String> improvementsOf(CategoryScore category) {
        return category != null ? category.improvements() : List.of();
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
