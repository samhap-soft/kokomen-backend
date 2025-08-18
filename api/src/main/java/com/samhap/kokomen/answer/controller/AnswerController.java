package com.samhap.kokomen.answer.controller;

import com.samhap.kokomen.answer.service.AnswerFacadeService;
import com.samhap.kokomen.answer.service.dto.AnswerMemoCreateRequest;
import com.samhap.kokomen.answer.service.dto.AnswerMemoResponse;
import com.samhap.kokomen.answer.service.dto.AnswerMemoUpdateRequest;
import com.samhap.kokomen.global.annotation.Authentication;
import com.samhap.kokomen.global.dto.MemberAuth;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RequestMapping("/api/v1/answers")
@RestController
public class AnswerController {

    private final AnswerFacadeService answerFacadeService;

    @PostMapping("/{answerId}/like")
    public ResponseEntity<Void> likeAnswer(
            @PathVariable Long answerId,
            @Authentication MemberAuth memberAuth
    ) {
        answerFacadeService.likeAnswer(answerId, memberAuth);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{answerId}/memo")
    public ResponseEntity<AnswerMemoResponse> createAnswerMemo(
            @PathVariable Long answerId,
            @RequestBody @Valid AnswerMemoCreateRequest answerMemoCreateRequest,
            @Authentication MemberAuth memberAuth
    ) {
        AnswerMemoResponse answerMemoResponse = answerFacadeService.createAnswerMemo(answerId, answerMemoCreateRequest, memberAuth);
        return ResponseEntity.ok(answerMemoResponse);
    }

    @PatchMapping("/{answerId}/memo")
    public ResponseEntity<Void> updateAnswerMemo(
            @PathVariable Long answerId,
            @RequestBody @Valid AnswerMemoUpdateRequest answerMemoUpdateRequest,
            @Authentication MemberAuth memberAuth
    ) {
        answerFacadeService.updateAnswerMemo(answerId, answerMemoUpdateRequest, memberAuth);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{answerId}/like")
    public ResponseEntity<Void> unlikeAnswer(
            @PathVariable Long answerId,
            @Authentication MemberAuth memberAuth
    ) {
        answerFacadeService.unlikeAnswer(answerId, memberAuth);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{answerId}/memo")
    public ResponseEntity<Void> deleteAnswerMemo(
            @PathVariable Long answerId,
            @Authentication MemberAuth memberAuth
    ) {
        answerFacadeService.deleteAnswerMemo(answerId, memberAuth);
        return ResponseEntity.noContent().build();
    }
}
