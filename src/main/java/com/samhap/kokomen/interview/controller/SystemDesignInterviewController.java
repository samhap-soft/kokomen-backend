package com.samhap.kokomen.interview.controller;

import static com.samhap.kokomen.interview.domain.systemdesign.SystemDesignErrorMessage.INTERVIEW_NOT_FOUND;

import com.samhap.kokomen.global.annotation.Authentication;
import com.samhap.kokomen.global.dto.MemberAuth;
import com.samhap.kokomen.global.exception.NotFoundException;
import com.samhap.kokomen.interview.service.SystemDesignInterviewFacadeService;
import com.samhap.kokomen.interview.service.dto.systemdesign.SystemDesignAnswerRequest;
import com.samhap.kokomen.interview.service.dto.systemdesign.SystemDesignAnswerResultResponse;
import com.samhap.kokomen.interview.service.dto.systemdesign.SystemDesignInterviewResponse;
import com.samhap.kokomen.interview.service.dto.systemdesign.SystemDesignInterviewStartResponse;
import com.samhap.kokomen.interview.service.dto.systemdesign.SystemDesignPublicReportResponse;
import com.samhap.kokomen.interview.service.dto.systemdesign.SystemDesignReportResponse;
import com.samhap.kokomen.interview.service.dto.systemdesign.SystemDesignRequirementAnswerResponse;
import com.samhap.kokomen.interview.service.dto.systemdesign.SystemDesignRequirementQuestionRequest;
import com.samhap.kokomen.interview.service.dto.systemdesign.SystemDesignRequirementsCompleteResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RequestMapping("/api/v1/interviews/system-design")
@RestController
public class SystemDesignInterviewController {

    private final SystemDesignInterviewFacadeService systemDesignInterviewFacadeService;

    // 요청 본문은 받지 않는다. 문제는 서버가 ACTIVE BASIC 문제 중에서 고른다.
    @PostMapping
    public ResponseEntity<SystemDesignInterviewStartResponse> startInterview(
            @Authentication MemberAuth memberAuth
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(systemDesignInterviewFacadeService.startInterview(memberAuth));
    }

    @GetMapping("/{interviewId}")
    public ResponseEntity<SystemDesignInterviewResponse> findInterview(
            @Authentication MemberAuth memberAuth,
            @PathVariable String interviewId
    ) {
        return ResponseEntity.ok(
                systemDesignInterviewFacadeService.findInterview(parseInterviewId(interviewId), memberAuth));
    }

    // 요구사항 단계에서 숨은 요구사항을 묻는다. 토큰을 쓰지 않고 면접마다 5개까지다.
    @PostMapping("/{interviewId}/requirement-questions")
    public ResponseEntity<SystemDesignRequirementAnswerResponse> askRequirementQuestion(
            @Authentication MemberAuth memberAuth,
            @PathVariable String interviewId,
            @RequestBody SystemDesignRequirementQuestionRequest request
    ) {
        return ResponseEntity.ok(systemDesignInterviewFacadeService.askRequirementQuestion(
                parseInterviewId(interviewId), request, memberAuth));
    }

    // 요청 본문은 받지 않는다. 역질문을 하나도 하지 않았어도 끝낼 수 있다.
    @PostMapping("/{interviewId}/requirements/complete")
    public ResponseEntity<SystemDesignRequirementsCompleteResponse> completeRequirements(
            @Authentication MemberAuth memberAuth,
            @PathVariable String interviewId
    ) {
        return ResponseEntity.ok(
                systemDesignInterviewFacadeService.completeRequirements(parseInterviewId(interviewId), memberAuth));
    }

    // 채점은 워커가 하고 결과는 답변 결과 조회로 폴링한다. 토큰은 답변이 저장된 뒤에 차감한다.
    @PostMapping("/{interviewId}/questions/{questionId}/answers")
    public ResponseEntity<Void> submitAnswer(
            @Authentication MemberAuth memberAuth,
            @PathVariable String interviewId,
            @PathVariable String questionId,
            @RequestBody SystemDesignAnswerRequest request
    ) {
        systemDesignInterviewFacadeService.submitAnswer(parseInterviewId(interviewId), parseQuestionOrder(questionId),
                request, memberAuth);
        return ResponseEntity.accepted().build();
    }

    @GetMapping("/{interviewId}/questions/{questionId}")
    public ResponseEntity<SystemDesignAnswerResultResponse> findAnswerResult(
            @Authentication MemberAuth memberAuth,
            @PathVariable String interviewId,
            @PathVariable String questionId
    ) {
        return ResponseEntity.ok(systemDesignInterviewFacadeService.findAnswerResult(parseInterviewId(interviewId),
                parseQuestionOrder(questionId), memberAuth));
    }

    // 끝난 면접의 리포트. 마지막 답변을 채점할 때 함께 저장한 값을 조립하므로 기다리지 않는다.
    @GetMapping("/{interviewId}/report")
    public ResponseEntity<SystemDesignReportResponse> findReport(
            @Authentication MemberAuth memberAuth,
            @PathVariable String interviewId
    ) {
        return ResponseEntity.ok(
                systemDesignInterviewFacadeService.findReport(parseInterviewId(interviewId), memberAuth));
    }

    // 다른 회원 목록에서 여는 끝난 면접의 공개 리포트. 시스템 설계 API 중 이것만 로그인하지 않아도 되고, 누가 보든 같은 응답이다.
    // memberAuth는 지금 쓰지 않지만 기존 다른 회원 결과(/api/v1/interviews/{interviewId}/result)와 같은 모양으로 두어
    // 조회수·좋아요를 더할 때 쓴다.
    @GetMapping("/{interviewId}/result")
    public ResponseEntity<SystemDesignPublicReportResponse> findPublicReport(
            @Authentication(required = false) MemberAuth memberAuth,
            @PathVariable String interviewId
    ) {
        return ResponseEntity.ok(systemDesignInterviewFacadeService.findPublicReport(parseInterviewId(interviewId)));
    }

    // 면접을 가리키지 못하는 경로 값(숫자가 아님, 빈 값, long 범위 밖)은 잘못된 입력(400)이 아니라 없는 면접(404)으로 답한다.
    private static Long parseInterviewId(String interviewId) {
        try {
            return Long.parseLong(interviewId.trim());
        } catch (NumberFormatException e) {
            throw new NotFoundException(INTERVIEW_NOT_FOUND.getMessage());
        }
    }

    // 질문 순번으로 바꾸지 못하는 경로 값은 null로 넘긴다. 답변 제출은 지금 질문이 아니라고(409), 결과 조회는 없는 질문이라고(404) 답한다.
    private static Integer parseQuestionOrder(String questionId) {
        try {
            return Integer.parseInt(questionId.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
