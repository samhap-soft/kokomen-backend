package com.samhap.kokomen.resume.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.samhap.kokomen.global.dto.MemberAuth;
import com.samhap.kokomen.global.exception.BadRequestException;
import com.samhap.kokomen.global.service.RedisService;
import com.samhap.kokomen.member.domain.Member;
import com.samhap.kokomen.member.service.MemberService;
import com.samhap.kokomen.resume.domain.CareerMaterialsType;
import com.samhap.kokomen.resume.domain.MemberPortfolio;
import com.samhap.kokomen.resume.domain.MemberResume;
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
import com.samhap.kokomen.resume.service.dto.ResumeFileData;
import com.samhap.kokomen.resume.service.dto.ResumeSaveRequest;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@RequiredArgsConstructor
@Service
public class CareerMaterialsFacadeService {

    private static final String UUID_PREFIX = "uuid-";

    private final CareerMaterialsService careerMaterialsService;
    private final MemberService memberService;
    private final ResumeEvaluationService resumeEvaluationService;
    private final ResumeEvaluationAsyncService resumeEvaluationAsyncService;
    private final RedisService redisService;
    private final PdfValidator pdfValidator;
    private final PdfUploadService pdfUploadService;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public CareerMaterialsResponse getCareerMaterials(CareerMaterialsType type, MemberAuth memberAuth) {
        return careerMaterialsService.getCareerMaterials(type, memberAuth);
    }

    @Transactional
    public ResumeEvaluationSubmitResponse submitResumeEvaluationAsync(
            ResumeEvaluationAsyncRequest request,
            MemberAuth memberAuth
    ) {
        if (memberAuth.isAuthenticated()) {
            return submitMemberEvaluation(request, memberAuth);
        }
        return submitNonMemberEvaluation(request);
    }

    private ResumeEvaluationSubmitResponse submitMemberEvaluation(
            ResumeEvaluationAsyncRequest request,
            MemberAuth memberAuth
    ) {
        validatePdfFiles(request);
        Member member = memberService.readById(memberAuth.memberId());

        ResumeFileData resumeFileData = getResumeFileData(request);
        MemberResume memberResume = getMemberResume(request, memberAuth);
        ResumeFileData portfolioFileData = getPortfolioFileData(request);
        MemberPortfolio memberPortfolio = getMemberPortfolio(request, memberAuth);

        ResumeEvaluation savedEvaluation = saveEvaluation(member, memberResume, memberPortfolio, request);
        startMemberAsyncEvaluation(savedEvaluation, member, resumeFileData, portfolioFileData,
                memberResume, memberPortfolio, request);
        return ResumeEvaluationSubmitResponse.from(savedEvaluation.getId());
    }

    private ResumeEvaluationSubmitResponse submitNonMemberEvaluation(ResumeEvaluationAsyncRequest request) {
        validatePdfFiles(request);
        String uuid = UUID.randomUUID().toString();
        ResumeFileData resumeFileData = createResumeFileData(request.resume());
        ResumeFileData portfolioFileData = createResumeFileData(request.portfolio());

        startNonMemberAsyncEvaluation(uuid, resumeFileData, portfolioFileData, request);
        return ResumeEvaluationSubmitResponse.fromUuid(uuid);
    }

    private void validatePdfFiles(ResumeEvaluationAsyncRequest request) {
        if (hasFile(request.resume())) {
            pdfValidator.validate(request.resume());
        }
        if (hasFile(request.portfolio())) {
            pdfValidator.validate(request.portfolio());
        }
    }

    private ResumeFileData getResumeFileData(ResumeEvaluationAsyncRequest request) {
        if (hasFile(request.resume())) {
            return createResumeFileData(request.resume());
        }
        return null;
    }

    private MemberResume getMemberResume(ResumeEvaluationAsyncRequest request, MemberAuth memberAuth) {
        if (hasFile(request.resume())) {
            return null;
        }
        if (request.resumeId() == null) {
            return null;
        }
        return careerMaterialsService.getResumeByIdAndMemberId(request.resumeId(), memberAuth.memberId());
    }

    private ResumeFileData getPortfolioFileData(ResumeEvaluationAsyncRequest request) {
        if (hasFile(request.portfolio())) {
            return createResumeFileData(request.portfolio());
        }
        return null;
    }

    private MemberPortfolio getMemberPortfolio(ResumeEvaluationAsyncRequest request, MemberAuth memberAuth) {
        if (hasFile(request.portfolio())) {
            return null;
        }
        if (request.portfolioId() == null) {
            return null;
        }
        return careerMaterialsService.getPortfolioByIdAndMemberId(request.portfolioId(), memberAuth.memberId());
    }

    private boolean hasFile(MultipartFile file) {
        return file != null && !file.isEmpty();
    }

    private ResumeEvaluation saveEvaluation(
            Member member,
            MemberResume memberResume,
            MemberPortfolio memberPortfolio,
            ResumeEvaluationAsyncRequest request
    ) {
        ResumeEvaluation evaluation = new ResumeEvaluation(
                member, memberResume, memberPortfolio,
                request.jobPosition(), request.jobDescription(), request.jobCareer()
        );
        return resumeEvaluationService.saveEvaluation(evaluation);
    }

    private void startMemberAsyncEvaluation(
            ResumeEvaluation evaluation,
            Member member,
            ResumeFileData resumeFileData,
            ResumeFileData portfolioFileData,
            MemberResume memberResume,
            MemberPortfolio memberPortfolio,
            ResumeEvaluationAsyncRequest request
    ) {
        resumeEvaluationAsyncService.processAndEvaluateMixedAsync(
                evaluation.getId(), member,
                resumeFileData, portfolioFileData,
                memberResume, memberPortfolio,
                request.jobPosition(), request.jobDescription(), request.jobCareer()
        );
    }

    private void startNonMemberAsyncEvaluation(
            String uuid,
            ResumeFileData resumeFileData,
            ResumeFileData portfolioFileData,
            ResumeEvaluationAsyncRequest request
    ) {
        resumeEvaluationAsyncService.processAndEvaluateNonMemberAsync(
                uuid, resumeFileData, portfolioFileData,
                request.jobPosition(), request.jobDescription(), request.jobCareer()
        );
    }

    private ResumeFileData createResumeFileData(MultipartFile file) {
        try {
            return ResumeFileData.from(file);
        } catch (IOException e) {
            throw new BadRequestException("파일을 읽는 중 오류가 발생했습니다.");
        }
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

        return redisService.get(redisKey, String.class)
                .map(this::parseNonMemberEvaluationData)
                .map(this::convertToStateResponse)
                .orElseThrow(() -> new BadRequestException("이력서 평가 결과를 찾을 수 없습니다. 만료되었거나 존재하지 않는 ID입니다."));
    }

    private String extractUuid(String evaluationId) {
        return evaluationId.substring(UUID_PREFIX.length());
    }

    private NonMemberResumeEvaluationData parseNonMemberEvaluationData(String jsonData) {
        try {
            return objectMapper.readValue(jsonData, NonMemberResumeEvaluationData.class);
        } catch (JsonProcessingException e) {
            throw new BadRequestException("비회원 평가 데이터 파싱에 실패했습니다.");
        }
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
        ResumeEvaluation evaluation = resumeEvaluationService.readById(id);
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
        Page<ResumeEvaluation> evaluationPage = resumeEvaluationService
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
        ResumeEvaluation evaluation = resumeEvaluationService.readById(evaluationId);
        validateEvaluationOwner(evaluation, memberAuth.memberId());
        return ResumeEvaluationDetailResponse.from(evaluation);
    }

    private void validateEvaluationOwner(ResumeEvaluation evaluation, Long memberId) {
        if (!evaluation.isOwner(memberId)) {
            throw new BadRequestException("본인의 이력서 평가만 조회할 수 있습니다.");
        }
    }

    // TODO: 이력서 평가가 비동기로 전환 완료되면 삭제하기
    @Transactional
    public ResumeEvaluationResponse evaluateResume(ResumeEvaluationRequest request) {
        return resumeEvaluationService.evaluate(request);
    }

    // TODO: 이력서 평가가 비동기로 전환 완료되면 삭제하기
    @Transactional
    public void saveCareerMaterials(ResumeSaveRequest request, MemberAuth memberAuth) {
        Member member = memberService.readById(memberAuth.memberId());
        pdfUploadService.saveResume(request.resume(), member);
        if (request.portfolio() != null) {
            pdfUploadService.savePortfolio(request.portfolio(), member);
        }
    }
}
