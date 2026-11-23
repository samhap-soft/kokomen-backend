package com.samhap.kokomen.interview.controller;

import static com.samhap.kokomen.interview.domain.systemdesign.SystemDesignErrorMessage.INTERVIEW_NOT_FOUND;

import com.samhap.kokomen.global.annotation.Authentication;
import com.samhap.kokomen.global.dto.MemberAuth;
import com.samhap.kokomen.global.exception.NotFoundException;
import com.samhap.kokomen.interview.service.SystemDesignInterviewFacadeService;
import com.samhap.kokomen.interview.service.dto.systemdesign.SystemDesignInterviewResponse;
import com.samhap.kokomen.interview.service.dto.systemdesign.SystemDesignInterviewStartResponse;
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

    // 면접을 가리키지 못하는 경로 값(숫자가 아님, 빈 값, long 범위 밖)은 잘못된 입력(400)이 아니라 없는 면접(404)으로 답한다.
    private static Long parseInterviewId(String interviewId) {
        try {
            return Long.parseLong(interviewId.trim());
        } catch (NumberFormatException e) {
            throw new NotFoundException(INTERVIEW_NOT_FOUND.getMessage());
        }
    }
}
