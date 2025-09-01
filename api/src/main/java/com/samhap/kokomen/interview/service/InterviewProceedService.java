package com.samhap.kokomen.interview.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.samhap.kokomen.answer.domain.Answer;
import com.samhap.kokomen.answer.service.AnswerService;
import com.samhap.kokomen.interview.domain.Interview;
import com.samhap.kokomen.interview.domain.InterviewMode;
import com.samhap.kokomen.interview.domain.InterviewProceedResult;
import com.samhap.kokomen.interview.domain.Question;
import com.samhap.kokomen.interview.domain.QuestionAndAnswers;
import com.samhap.kokomen.interview.external.dto.response.AnswerFeedbackResponse;
import com.samhap.kokomen.interview.external.dto.response.AnswerRankResponse;
import com.samhap.kokomen.interview.external.dto.response.LlmResponse;
import com.samhap.kokomen.interview.external.dto.response.NextQuestionResponse;
import com.samhap.kokomen.interview.external.dto.response.TotalFeedbackResponse;
import com.samhap.kokomen.interview.service.dto.InterviewProceedResponse;
import com.samhap.kokomen.member.domain.Member;
import com.samhap.kokomen.member.service.MemberService;
import com.samhap.kokomen.token.service.TokenFacadeService;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class InterviewProceedService {

    private final MemberService memberService;
    private final InterviewService interviewService;
    private final AnswerService answerService;
    private final QuestionService questionService;
    private final TokenFacadeService tokenFacadeService;
    private final ObjectMapper objectMapper;

    @Transactional
    public Optional<InterviewProceedResponse> proceedOrEndInterview(
            Long memberId,
            QuestionAndAnswers questionAndAnswers,
            LlmResponse llmResponse,
            Long interviewId
    ) {
        Member member = memberService.readById(memberId);
        tokenFacadeService.useToken(memberId);
        Answer curAnswer = saveCurrentAnswer(questionAndAnswers, llmResponse);
        Interview interview = interviewService.readInterview(interviewId);
        if (questionAndAnswers.isProceedRequest()) {
            Question nextQuestion = saveNextQuestion(llmResponse, interview);
            return Optional.of(InterviewProceedResponse.createFollowingQuestionResponse(curAnswer, nextQuestion));
        }
        evaluateInterview(questionAndAnswers, curAnswer, interview, llmResponse, member);
        return Optional.empty();
    }

    @Transactional
    public InterviewProceedResult proceedOrEndInterviewByBedrockFlowAsync(
            Long memberId,
            QuestionAndAnswers questionAndAnswers,
            LlmResponse llmResponse,
            Long interviewId
    ) {
        Member member = memberService.readById(memberId);
        tokenFacadeService.useToken(memberId);
        Interview interview = interviewService.readInterview(interviewId);
        if (questionAndAnswers.isProceedRequest()) {
            Question nextQuestion = saveNextQuestion(llmResponse, interview);
            Answer curAnswer = saveCurrentAnswerWithoutFeedback(questionAndAnswers, llmResponse);
            return InterviewProceedResult.createInProgress(curAnswer, nextQuestion);
        }
        Answer curAnswer = saveCurrentAnswer(questionAndAnswers, llmResponse);
        evaluateInterview(questionAndAnswers, curAnswer, interview, llmResponse, member);
        return InterviewProceedResult.createFinished(curAnswer);
    }

    private Question saveNextQuestion(LlmResponse llmResponse, Interview interview) {
        NextQuestionResponse nextQuestionResponse = llmResponse.extractNextQuestionResponse(objectMapper);
        return questionService.saveQuestion(new Question(interview, nextQuestionResponse.nextQuestion()));
    }

    private Answer saveCurrentAnswerWithoutFeedback(QuestionAndAnswers questionAndAnswers, LlmResponse llmResponse) {
        AnswerRankResponse answerRankResponse = llmResponse.extractAnswerRankResponse(objectMapper);
        return answerService.saveAnswer(questionAndAnswers.createCurAnswerWithoutFeedback(answerRankResponse));
    }

    private Answer saveCurrentAnswer(QuestionAndAnswers questionAndAnswers, LlmResponse llmResponse) {
        AnswerFeedbackResponse feedback = llmResponse.extractAnswerFeedbackResponse(objectMapper);
        return answerService.saveAnswer(questionAndAnswers.createCurAnswer(feedback));
    }

    private void evaluateInterview(QuestionAndAnswers questionAndAnswers, Answer curAnswer, Interview interview, LlmResponse llmResponse, Member member) {
        int totalScore = questionAndAnswers.calculateTotalScore(curAnswer.getAnswerRank().getScore());
        TotalFeedbackResponse totalFeedbackResponse = llmResponse.extractTotalFeedbackResponse(objectMapper);
        interview.evaluate(totalFeedbackResponse.totalFeedback(), totalScore);
        member.addScore(totalScore);
    }

    public boolean isVoiceMode(Long interviewId) {
        Interview interview = interviewService.readInterview(interviewId);
        return interview.getInterviewMode() == InterviewMode.VOICE;
    }

    @Transactional
    public void saveAnswerFeedback(Long answerId, String answerFeedback) {
        Answer answer = answerService.readById(answerId);
        answer.giveFeedback(answerFeedback);
    }
}
