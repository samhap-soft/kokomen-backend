package com.samhap.kokomen.resume.service;

import com.samhap.kokomen.global.exception.BadRequestException;
import com.samhap.kokomen.global.exception.NotFoundException;
import com.samhap.kokomen.member.service.MemberService;
import com.samhap.kokomen.resume.domain.DimensionScore;
import com.samhap.kokomen.resume.domain.ResumeAnalysis;
import com.samhap.kokomen.resume.domain.ResumeAnalysisEvaluation;
import com.samhap.kokomen.resume.domain.ResumeAnalysisJobInput;
import com.samhap.kokomen.resume.domain.ResumeAnalysisSourceText;
import com.samhap.kokomen.resume.repository.ResumeAnalysisRepository;
import com.samhap.kokomen.resume.repository.ResumeAnalysisSourceTextRepository;
import com.samhap.kokomen.resume.service.dto.ExtractedContents;
import com.samhap.kokomen.resume.service.dto.GuestInfo;
import com.samhap.kokomen.resume.service.dto.MaterialRefs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@RequiredArgsConstructor
@Service
public class ResumeAnalysisService {

    private final ResumeAnalysisRepository resumeAnalysisRepository;
    private final ResumeAnalysisSourceTextRepository resumeAnalysisSourceTextRepository;
    private final MemberService memberService;

    /**
     * REQUIRES_NEW로 커밋을 강제한다. 반환 시점에 행이 반드시 조회 가능해야 executor에 제출한 워커가
     * findById에 실패하지 않는다. 파사드에 @Transactional이 붙어도 이 규약은 유지된다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ResumeAnalysis saveAnalysis(Long memberId, GuestInfo guestInfo, MaterialRefs materialRefs,
                                       ExtractedContents contents, ResumeAnalysisJobInput jobInput,
                                       boolean billingRequired) {
        ResumeAnalysis analysis = memberId != null
                ? ResumeAnalysis.forMember(memberService.readById(memberId), materialRefs.memberResume(),
                materialRefs.memberPortfolio(), jobInput, billingRequired)
                : ResumeAnalysis.forGuest(guestInfo.guestToken(), guestInfo.clientIp(),
                        guestInfo.guestLockValue(), jobInput);
        ResumeAnalysis saved = resumeAnalysisRepository.save(analysis);
        resumeAnalysisSourceTextRepository.save(
                new ResumeAnalysisSourceText(saved, contents.resumeText(), contents.portfolioText()));
        return saved;
    }

    @Transactional(readOnly = true)
    public ResumeAnalysis readById(Long analysisId) {
        return resumeAnalysisRepository.findById(analysisId)
                .orElseThrow(() -> new NotFoundException("존재하지 않는 이력서 분석입니다. analysisId: " + analysisId));
    }

    /**
     * 15개 지표 컬럼에서 값객체를 복원한다. jd_fit은 jd_provided 컬럼만 보고 판단하며
     * jobDescription 문자열을 다시 검사하지 않는다.
     */
    @Transactional(readOnly = true)
    public ResumeAnalysisEvaluation readEvaluation(Long analysisId) {
        ResumeAnalysis analysis = readById(analysisId);
        if (!analysis.getState().isEvaluationRevealed()) {
            throw new BadRequestException("평가가 완료되지 않은 이력서 분석입니다. analysisId: " + analysisId);
        }
        return new ResumeAnalysisEvaluation(
                new DimensionScore(analysis.getProblemSolvingScore(), analysis.getProblemSolvingReason(),
                        analysis.getProblemSolvingImprovements()),
                new DimensionScore(analysis.getProjectExperienceScore(), analysis.getProjectExperienceReason(),
                        analysis.getProjectExperienceImprovements()),
                new DimensionScore(analysis.getTechnicalSkillsScore(), analysis.getTechnicalSkillsReason(),
                        analysis.getTechnicalSkillsImprovements()),
                new DimensionScore(analysis.getSoftSkillsScore(), analysis.getSoftSkillsReason(),
                        analysis.getSoftSkillsImprovements()),
                analysis.isJdProvided()
                        ? new DimensionScore(analysis.getJdFitScore(), analysis.getJdFitReason(),
                        analysis.getJdFitImprovements())
                        : null,
                analysis.getTotalScore(), analysis.getTotalFeedback());
    }

    @Transactional(readOnly = true)
    public ResumeAnalysisSourceText readSourceText(Long analysisId) {
        return resumeAnalysisSourceTextRepository.findByAnalysisId(analysisId)
                .orElseThrow(() -> new BadRequestException("이력서 원문이 만료되어 질문을 재생성할 수 없습니다."));
    }

    /**
     * 과금 선점 CAS. WHERE charged_token_count = 0 조건부 UPDATE라서 같은 analysisId로 몇 번 호출해도
     * 1행 갱신은 한 번뿐이다. false면 이미 다른 주체가 과금을 선점했다는 뜻이므로 차감을 시도하지 않는다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean markTokenCharged(Long analysisId, int cost) {
        return resumeAnalysisRepository.markTokenCharged(analysisId, cost) == 1;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markTokenChargeFailed(Long analysisId) {
        resumeAnalysisRepository.markTokenChargeFailed(analysisId);
    }
}
