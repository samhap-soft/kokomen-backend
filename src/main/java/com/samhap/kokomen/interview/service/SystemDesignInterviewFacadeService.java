package com.samhap.kokomen.interview.service;

import static com.samhap.kokomen.interview.domain.systemdesign.SystemDesignErrorMessage.REQUIREMENT_ANSWER_FAILED;

import com.samhap.kokomen.global.dto.MemberAuth;
import com.samhap.kokomen.global.exception.ExternalApiException;
import com.samhap.kokomen.interview.domain.InterviewMode;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignAnswer;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignDiagram;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignInterview;
import com.samhap.kokomen.interview.external.SystemDesignRequirementBedrockClient;
import com.samhap.kokomen.interview.external.dto.response.SystemDesignRequirementAnswerResult;
import com.samhap.kokomen.interview.service.dto.systemdesign.SystemDesignAnswerRequest;
import com.samhap.kokomen.interview.service.dto.systemdesign.SystemDesignAnswerResultResponse;
import com.samhap.kokomen.interview.service.dto.systemdesign.SystemDesignInterviewResponse;
import com.samhap.kokomen.interview.service.dto.systemdesign.SystemDesignInterviewStartResponse;
import com.samhap.kokomen.interview.service.dto.systemdesign.SystemDesignRequirementAnswerResponse;
import com.samhap.kokomen.interview.service.dto.systemdesign.SystemDesignRequirementQuestionRequest;
import com.samhap.kokomen.interview.service.dto.systemdesign.SystemDesignRequirementsCompleteResponse;
import com.samhap.kokomen.interview.service.systemdesign.SystemDesignAnswerAsyncService;
import com.samhap.kokomen.interview.service.systemdesign.SystemDesignAnswerService;
import com.samhap.kokomen.interview.service.systemdesign.SystemDesignAnswerService.SystemDesignAnswerDraft;
import com.samhap.kokomen.interview.service.systemdesign.SystemDesignInterviewService;
import com.samhap.kokomen.interview.service.systemdesign.SystemDesignLlmBulkhead;
import com.samhap.kokomen.interview.service.systemdesign.SystemDesignProceedLock;
import com.samhap.kokomen.interview.service.systemdesign.SystemDesignRequirementQuestionService;
import com.samhap.kokomen.interview.service.systemdesign.SystemDesignRequirementQuestionService.RequirementQuestionDraft;
import com.samhap.kokomen.token.service.TokenFacadeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 시스템 설계 면접 API의 진입점. 트랜잭션은 아래 서비스가 열고, 여기서는 진행 락처럼 트랜잭션 밖에서 해야 하는 순서를 맞춘다.
 */
@RequiredArgsConstructor
@Service
public class SystemDesignInterviewFacadeService {

    private final SystemDesignInterviewService systemDesignInterviewService;
    private final SystemDesignRequirementQuestionService systemDesignRequirementQuestionService;
    private final SystemDesignProceedLock systemDesignProceedLock;
    private final SystemDesignLlmBulkhead systemDesignLlmBulkhead;
    private final SystemDesignRequirementBedrockClient systemDesignRequirementBedrockClient;
    private final SystemDesignAnswerService systemDesignAnswerService;
    private final SystemDesignAnswerAsyncService systemDesignAnswerAsyncService;
    private final TokenFacadeService tokenFacadeService;

    // 기존 시작 API처럼 진행 락을 잡지 않는다.
    public SystemDesignInterviewStartResponse startInterview(MemberAuth memberAuth) {
        return systemDesignInterviewService.start(memberAuth.memberId());
    }

    // 진행 락을 잡지 않는다. 채점 중에 새로고침해도 같은 현재 질문과 채점 상태(cur_answer_proceed_state)를 바로 준다.
    public SystemDesignInterviewResponse findInterview(Long interviewId, MemberAuth memberAuth) {
        return systemDesignInterviewService.findInterview(interviewId, memberAuth.memberId());
    }

    /**
     * 사전 준비(소유자 → 단계 → 길이 → 개수) → 진행 락 → 락 안에서 다시 준비 → 동시 실행 허가 → LLM 호출(트랜잭션 밖) → 저장.
     * 락은 finally 한 곳에서 풀고, 저장 트랜잭션은 락을 풀기 전에 커밋되므로 다음 요청은 늘어난 개수를 본다. 토큰은 건드리지 않는다.
     */
    public SystemDesignRequirementAnswerResponse askRequirementQuestion(Long interviewId,
                                                                        SystemDesignRequirementQuestionRequest request,
                                                                        MemberAuth memberAuth) {
        Long memberId = memberAuth.memberId();
        systemDesignRequirementQuestionService.prepare(interviewId, memberId, request.question());
        String lockValue = systemDesignProceedLock.acquire(memberId);
        try {
            RequirementQuestionDraft draft = systemDesignRequirementQuestionService.prepare(interviewId, memberId,
                    request.question());
            SystemDesignRequirementAnswerResult result = answerRequirement(draft);
            return systemDesignRequirementQuestionService.save(draft, result);
        } finally {
            systemDesignProceedLock.release(memberId, lockValue);
        }
    }

    // LLM 호출 실패(전송·응답 검증)만 사용자 문구의 500으로 바꾼다. 동시 실행 상한의 503(global.exception)은 그대로 내보낸다.
    private SystemDesignRequirementAnswerResult answerRequirement(RequirementQuestionDraft draft) {
        try {
            return systemDesignLlmBulkhead.execute(
                    () -> systemDesignRequirementBedrockClient.answerRequirement(draft.command()));
        } catch (ExternalApiException e) {
            throw new ExternalApiException(REQUIREMENT_ANSWER_FAILED.getMessage(), e);
        }
    }

    // 사전 검사(소유자 → 단계) → 진행 락 → 완료. 완료 트랜잭션은 락을 풀기 전에 커밋되므로 다음 요청은 바뀐 단계를 본다.
    public SystemDesignRequirementsCompleteResponse completeRequirements(Long interviewId, MemberAuth memberAuth) {
        Long memberId = memberAuth.memberId();
        systemDesignInterviewService.validateRequirementsCompletable(interviewId, memberId);
        String lockValue = systemDesignProceedLock.acquire(memberId);
        try {
            return systemDesignInterviewService.completeRequirements(interviewId, memberId);
        } finally {
            systemDesignProceedLock.release(memberId, lockValue);
        }
    }

    /**
     * 사전 검사(소유자 → 끝난 면접 → 요구사항 단계 → 질문 순번) → 답변 길이 → 다이어그램 → 토큰 1개 보유 → 진행 락 → 락 안에서 준비 →
     * 워커 제출 순서다. 상태를 입력과 토큰보다 먼저 봐서, 응답을 받지 못한 마지막 답변을 다시 보내면 마지막 토큰을 썼더라도
     * 토큰 부족이 아니라 끝난 면접으로 알린다. 다만 차감(REQUIRES_NEW)은 답변 저장이 커밋되기 직전에 따로 커밋되므로, 그 사이(수 ms)에
     * 다시 보낸 요청은 토큰 부족(400)을 받을 수 있다. 락 안의 준비가 실패하면 여기서 락을 풀고, 워커에 넘긴 뒤에는 워커가 푼다.
     * 토큰은 워커가 답변 저장에 성공한 뒤에 차감한다.
     */
    public void submitAnswer(Long interviewId, Integer questionOrder, SystemDesignAnswerRequest request,
                             MemberAuth memberAuth) {
        Long memberId = memberAuth.memberId();
        int currentOrder = systemDesignAnswerService.precheck(interviewId, memberId, questionOrder);
        String content = SystemDesignAnswer.normalizeContent(request.answer());
        SystemDesignDiagram diagram = SystemDesignDiagram.from(request.diagramInput());
        tokenFacadeService.validateEnoughTokens(memberId, InterviewMode.TEXT.calculateRequiredTokenCountToAnswer(
                currentOrder < SystemDesignInterview.MAX_QUESTION_COUNT));
        String lockValue = systemDesignProceedLock.acquire(memberId);
        SystemDesignAnswerDraft draft;
        try {
            draft = systemDesignAnswerService.prepare(interviewId, memberId, questionOrder, content, diagram);
        } catch (RuntimeException e) {
            systemDesignProceedLock.release(memberId, lockValue);
            throw e;
        }
        systemDesignAnswerAsyncService.submit(draft, lockValue);
    }

    // 진행 락을 잡지 않는다. 채점 중에도 결과를 물을 수 있어야 한다.
    public SystemDesignAnswerResultResponse findAnswerResult(Long interviewId, Integer questionOrder,
                                                             MemberAuth memberAuth) {
        return systemDesignAnswerService.findAnswerResult(interviewId, memberAuth.memberId(), questionOrder);
    }
}
