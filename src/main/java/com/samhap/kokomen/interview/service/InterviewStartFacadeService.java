package com.samhap.kokomen.interview.service;

import com.samhap.kokomen.global.dto.MemberAuth;
import com.samhap.kokomen.global.exception.BadRequestException;
import com.samhap.kokomen.global.exception.ForbiddenException;
import com.samhap.kokomen.interview.domain.GeneratedQuestion;
import com.samhap.kokomen.interview.domain.Interview;
import com.samhap.kokomen.interview.domain.InterviewMode;
import com.samhap.kokomen.interview.domain.Question;
import com.samhap.kokomen.interview.domain.ResumeQuestionGeneration;
import com.samhap.kokomen.interview.domain.RootQuestion;
import com.samhap.kokomen.interview.tool.QuestionVoicePathResolver;
import com.samhap.kokomen.interview.service.core.InterviewService;
import com.samhap.kokomen.interview.service.dto.InterviewRequest;
import com.samhap.kokomen.interview.service.dto.RootQuestionCustomInterviewRequest;
import com.samhap.kokomen.interview.service.dto.resumebased.ResumeBasedInterviewStartRequest;
import com.samhap.kokomen.interview.service.dto.start.InterviewStartResponse;
import com.samhap.kokomen.interview.service.dto.start.InterviewStartTextModeResponse;
import com.samhap.kokomen.interview.service.dto.start.InterviewStartVoiceModeResponse;
import com.samhap.kokomen.interview.service.question.QuestionService;
import com.samhap.kokomen.interview.service.question.RootQuestionService;
import com.samhap.kokomen.interview.service.resume.ResumeBasedInterviewService;
import com.samhap.kokomen.member.domain.Member;
import com.samhap.kokomen.member.service.MemberService;
import com.samhap.kokomen.token.service.TokenFacadeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@RequiredArgsConstructor
@Service
public class InterviewStartFacadeService {

    private static final int TOKEN_NOT_REQUIRED_FOR_ROOT_QUESTION_VOICE = 1;

    private final QuestionVoicePathResolver questionVoicePathResolver;
    private final InterviewService interviewService;
    private final MemberService memberService;
    private final TokenFacadeService tokenFacadeService;
    private final RootQuestionService rootQuestionService;
    private final QuestionService questionService;
    private final ResumeBasedInterviewService resumeBasedInterviewService;

    @Transactional
    public InterviewStartResponse startInterview(InterviewRequest interviewRequest, MemberAuth memberAuth) {
        InterviewMode interviewMode = interviewRequest.mode();
        int requiredTokenCount = interviewRequest.maxQuestionCount() * interviewMode.getRequiredTokenCount()
                - TOKEN_NOT_REQUIRED_FOR_ROOT_QUESTION_VOICE;
        tokenFacadeService.validateEnoughTokens(memberAuth.memberId(), requiredTokenCount);
        Member member = memberService.readById(memberAuth.memberId());
        RootQuestion rootQuestion = rootQuestionService.findNextRootQuestionForMember(member, interviewRequest);
        Interview interview = interviewService.saveInterview(
                new Interview(member, rootQuestion, interviewRequest.maxQuestionCount(), interviewMode));
        Question question = questionService.saveQuestion(new Question(interview, rootQuestion.getContent()));

        if (interviewMode == InterviewMode.VOICE) {
            return new InterviewStartVoiceModeResponse(interview, question,
                    questionVoicePathResolver.resolveRootQuestionCdnPath(rootQuestion.getId()));
        }
        return new InterviewStartTextModeResponse(interview, question);
    }

    @Transactional
    public InterviewStartResponse startRootQuestionCustomInterview(RootQuestionCustomInterviewRequest request,
                                                                   MemberAuth memberAuth) {
        InterviewMode interviewMode = request.mode();
        int requiredTokenCount = request.maxQuestionCount() * interviewMode.getRequiredTokenCount()
                - TOKEN_NOT_REQUIRED_FOR_ROOT_QUESTION_VOICE;
        tokenFacadeService.validateEnoughTokens(memberAuth.memberId(), requiredTokenCount);
        Member member = memberService.readById(memberAuth.memberId());
        RootQuestion rootQuestion = rootQuestionService.readRootQuestion(request.rootQuestionId());
        Interview interview = interviewService.saveInterview(
                new Interview(member, rootQuestion, request.maxQuestionCount(), interviewMode));
        Question question = questionService.saveQuestion(new Question(interview, rootQuestion.getContent()));

        if (interviewMode == InterviewMode.VOICE) {
            return new InterviewStartVoiceModeResponse(interview, question,
                    questionVoicePathResolver.resolveRootQuestionCdnPath(rootQuestion.getId()));
        }
        return new InterviewStartTextModeResponse(interview, question);
    }

    @Transactional
    public InterviewStartResponse startResumeBasedInterview(
            Long generationId,
            ResumeBasedInterviewStartRequest request,
            MemberAuth memberAuth
    ) {
        Member member = memberService.readById(memberAuth.memberId());
        ResumeQuestionGeneration generation = resumeBasedInterviewService.readGeneration(generationId);
        validateGenerationOwnership(generation, memberAuth.memberId());
        validateGenerationCompleted(generation);
        GeneratedQuestion generatedQuestion = resumeBasedInterviewService.readGeneratedQuestion(
                request.generatedQuestionId(), generationId);

        InterviewMode interviewMode = request.mode();
        int requiredTokenCount = request.maxQuestionCount() * interviewMode.getRequiredTokenCount();
        tokenFacadeService.validateEnoughTokens(memberAuth.memberId(), requiredTokenCount);

        Interview interview = interviewService.saveInterview(
                new Interview(member, generatedQuestion, request.maxQuestionCount(), interviewMode));
        Question question = questionService.saveQuestion(new Question(interview, generatedQuestion.getContent()));

        if (interviewMode == InterviewMode.VOICE) {
            String voiceUrl = questionService.createAndUploadQuestionVoice(question);
            return new InterviewStartVoiceModeResponse(interview, question, voiceUrl);
        }
        return new InterviewStartTextModeResponse(interview, question);
    }

    private void validateGenerationOwnership(ResumeQuestionGeneration generation, Long memberId) {
        if (!generation.isOwner(memberId)) {
            throw new ForbiddenException("본인의 질문 생성 결과로만 면접을 시작할 수 있습니다.");
        }
    }

    private void validateGenerationCompleted(ResumeQuestionGeneration generation) {
        if (!generation.isCompleted()) {
            throw new BadRequestException("질문 생성이 완료되지 않았습니다.");
        }
    }
}
