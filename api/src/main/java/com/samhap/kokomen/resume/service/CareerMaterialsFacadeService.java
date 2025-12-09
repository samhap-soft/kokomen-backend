package com.samhap.kokomen.resume.service;

import com.samhap.kokomen.global.dto.MemberAuth;
import com.samhap.kokomen.global.exception.BadRequestException;
import com.samhap.kokomen.global.service.RedisService;
import com.samhap.kokomen.member.domain.Member;
import com.samhap.kokomen.member.service.MemberService;
import com.samhap.kokomen.resume.domain.CareerMaterialsType;
import com.samhap.kokomen.resume.domain.PdfTextExtractor;
import com.samhap.kokomen.resume.domain.PdfValidator;
import com.samhap.kokomen.resume.domain.ResumeEvaluation;
import com.samhap.kokomen.resume.service.dto.CareerMaterialsResponse;
import com.samhap.kokomen.resume.service.dto.NonMemberResumeEvaluationData;
import com.samhap.kokomen.resume.service.dto.ResumeEvaluationAsyncRequest;
import com.samhap.kokomen.resume.service.dto.ResumeEvaluationDetailResponse;
import com.samhap.kokomen.resume.service.dto.ResumeEvaluationHistoryResponse;
import com.samhap.kokomen.resume.service.dto.ResumeEvaluationHistoryResponses;
import com.samhap.kokomen.resume.service.dto.ResumeEvaluationRequest;
import com.samhap.kokomen.resume.service.dto.ResumeEvaluationResponse;
import com.samhap.kokomen.resume.service.dto.ResumeEvaluationStateResponse;
import com.samhap.kokomen.resume.service.dto.ResumeEvaluationSubmitResponse;
import com.samhap.kokomen.resume.service.dto.ResumeSaveRequest;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class CareerMaterialsFacadeService {

    private static final String UUID_PREFIX = "uuid-";

    private final ResumeService resumeService;
    private final PortfolioService portfolioService;
    private final MemberService memberService;
    private final ResumeEvaluationService resumeEvaluationService;
    private final ResumeEvaluationPersistenceService resumeEvaluationPersistenceService;
    private final ResumeEvaluationAsyncService resumeEvaluationAsyncService;
    private final RedisService redisService;
    private final PdfValidator pdfValidator;
    private final PdfTextExtractor pdfTextExtractor;

    @Transactional(readOnly = true)
    public CareerMaterialsResponse getCareerMaterials(CareerMaterialsType type, MemberAuth memberAuth) {
        return switch (type) {
            case ALL:
                yield new CareerMaterialsResponse(
                        resumeService.getResumesByMemberId(memberAuth.memberId()),
                        portfolioService.getPortfoliosByMemberId(memberAuth.memberId())
                );
            case RESUME:
                yield new CareerMaterialsResponse(
                        resumeService.getResumesByMemberId(memberAuth.memberId()),
                        List.of()
                );
            case PORTFOLIO:
                yield new CareerMaterialsResponse(
                        List.of(),
                        portfolioService.getPortfoliosByMemberId(memberAuth.memberId())
                );
        };
    }

    @Transactional
    public void saveCareerMaterials(ResumeSaveRequest request, MemberAuth memberAuth) {
        Member member = memberService.readById(memberAuth.memberId());
        resumeService.saveResume(request.resume(), member);
        if (request.portfolio() != null) {
            portfolioService.savePortfolio(request.portfolio(), member);
        }
    }

    @Transactional
    public ResumeEvaluationResponse evaluateResume(ResumeEvaluationRequest request) {
        return resumeEvaluationService.evaluate(request);
    }

    @Transactional
    public ResumeEvaluationSubmitResponse submitResumeEvaluationAsync(ResumeEvaluationAsyncRequest request,
                                                                      MemberAuth memberAuth) {
        validatePdfFiles(request);
        String resumeText = extractResumeText(request);
        String portfolioText = pdfTextExtractor.extractText(request.getPortfolio());

        ResumeEvaluationRequest evaluationRequest = new ResumeEvaluationRequest(
                resumeText,
                portfolioText,
                request.getJobPosition(),
                request.getJobDescription(),
                request.getJobCareer()
        );

        if (memberAuth.isAuthenticated()) {
            return submitMemberResumeEvaluationAsync(request, memberAuth, evaluationRequest);
        }
        return submitNonMemberResumeEvaluationAsync(evaluationRequest);
    }

    private void validatePdfFiles(ResumeEvaluationAsyncRequest request) {
        pdfValidator.validate(request.getResume());
        if (request.getPortfolio() != null && !request.getPortfolio().isEmpty()) {
            pdfValidator.validate(request.getPortfolio());
        }
    }

    private String extractResumeText(ResumeEvaluationAsyncRequest request) {
        String resumeText = pdfTextExtractor.extractText(request.getResume());
        if (resumeText == null || resumeText.isBlank()) {
            throw new BadRequestException("이력서 PDF에서 텍스트를 추출할 수 없습니다.");
        }
        return resumeText;
    }

    private ResumeEvaluationSubmitResponse submitMemberResumeEvaluationAsync(ResumeEvaluationAsyncRequest request,
                                                                             MemberAuth memberAuth,
                                                                             ResumeEvaluationRequest evaluationRequest) {
        Member member = memberService.readById(memberAuth.memberId());
        ResumeEvaluation evaluation = new ResumeEvaluation(
                member,
                evaluationRequest.resume(),
                evaluationRequest.portfolio(),
                request.getJobPosition(),
                request.getJobDescription(),
                request.getJobCareer()
        );

        ResumeEvaluation savedEvaluation = resumeEvaluationPersistenceService.saveEvaluation(evaluation);
        uploadEvaluationPdfsToS3(request, member);
        resumeEvaluationAsyncService.evaluateMemberAsync(savedEvaluation.getId(), evaluationRequest);
        return ResumeEvaluationSubmitResponse.from(savedEvaluation.getId());
    }

    private void uploadEvaluationPdfsToS3(ResumeEvaluationAsyncRequest request, Member member) {
        resumeService.saveResume(request.getResume(), member);
        if (request.getPortfolio() != null && !request.getPortfolio().isEmpty()) {
            portfolioService.savePortfolio(request.getPortfolio(), member);
        }
    }

    private ResumeEvaluationSubmitResponse submitNonMemberResumeEvaluationAsync(
            ResumeEvaluationRequest evaluationRequest) {
        String uuid = UUID.randomUUID().toString();
        resumeEvaluationAsyncService.evaluateNonMemberAsync(uuid, evaluationRequest);
        return ResumeEvaluationSubmitResponse.fromUuid(uuid);
    }

    @Transactional(readOnly = true)
    public ResumeEvaluationStateResponse findResumeEvaluationState(String evaluationId, MemberAuth memberAuth) {
        if (isNonMemberEvaluationId(evaluationId)) {
            return findNonMemberResumeEvaluationState(evaluationId);
        }
        return findMemberResumeEvaluationState(evaluationId, memberAuth);
    }

    private boolean isNonMemberEvaluationId(String evaluationId) {
        return evaluationId != null && evaluationId.startsWith(UUID_PREFIX);
    }

    private ResumeEvaluationStateResponse findNonMemberResumeEvaluationState(String evaluationId) {
        String uuid = extractUuid(evaluationId);
        String redisKey = ResumeEvaluationAsyncService.createRedisKey(uuid);

        return redisService.get(redisKey, NonMemberResumeEvaluationData.class)
                .map(this::convertToStateResponse)
                .orElseThrow(() -> new BadRequestException("이력서 평가 결과를 찾을 수 없습니다. 만료되었거나 존재하지 않는 ID입니다."));
    }

    private String extractUuid(String evaluationId) {
        return evaluationId.substring(UUID_PREFIX.length());
    }

    private ResumeEvaluationStateResponse convertToStateResponse(NonMemberResumeEvaluationData data) {
        return switch (data.state()) {
            case PENDING -> ResumeEvaluationStateResponse.pending();
            case COMPLETED -> ResumeEvaluationStateResponse.completed(data.result());
            case FAILED -> ResumeEvaluationStateResponse.failed();
        };
    }

    private ResumeEvaluationStateResponse findMemberResumeEvaluationState(String evaluationId, MemberAuth memberAuth) {
        Long id = parseMemberEvaluationId(evaluationId);
        ResumeEvaluation evaluation = resumeEvaluationPersistenceService.readById(id);
        validateEvaluationOwner(evaluation, memberAuth.memberId());
        return ResumeEvaluationStateResponse.from(evaluation);
    }

    private Long parseMemberEvaluationId(String evaluationId) {
        try {
            return Long.parseLong(evaluationId);
        } catch (NumberFormatException e) {
            throw new BadRequestException("잘못된 평가 ID 형식입니다: " + evaluationId);
        }
    }

    @Transactional(readOnly = true)
    public ResumeEvaluationHistoryResponses findResumeEvaluationHistory(MemberAuth memberAuth, Pageable pageable) {
        Page<ResumeEvaluation> evaluationPage = resumeEvaluationPersistenceService
                .findByMemberId(memberAuth.memberId(), pageable);

        List<ResumeEvaluationHistoryResponse> evaluations = evaluationPage.stream()
                .map(ResumeEvaluationHistoryResponse::from)
                .toList();
        return ResumeEvaluationHistoryResponses.of(
                evaluations,
                evaluationPage.getNumber(),
                evaluationPage.getSize(),
                evaluationPage.getTotalElements()
        );
    }

    @Transactional(readOnly = true)
    public ResumeEvaluationDetailResponse findResumeEvaluationDetail(Long evaluationId, MemberAuth memberAuth) {
        ResumeEvaluation evaluation = resumeEvaluationPersistenceService.readById(evaluationId);
        validateEvaluationOwner(evaluation, memberAuth.memberId());
        return ResumeEvaluationDetailResponse.from(evaluation);
    }

    private void validateEvaluationOwner(ResumeEvaluation evaluation, Long memberId) {
        if (!evaluation.isOwner(memberId)) {
            throw new BadRequestException("본인의 이력서 평가만 조회할 수 있습니다.");
        }
    }
}
