package com.samhap.kokomen.interview.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.samhap.kokomen.answer.domain.Answer;
import com.samhap.kokomen.answer.service.AnswerService;
import com.samhap.kokomen.interview.domain.Interview;
import com.samhap.kokomen.interview.domain.Question;
import com.samhap.kokomen.interview.domain.QuestionAndAnswers;
import com.samhap.kokomen.interview.external.BedrockClient;
import com.samhap.kokomen.interview.external.dto.response.AnswerFeedbackResponse;
import com.samhap.kokomen.interview.external.dto.response.LlmResponse;
import com.samhap.kokomen.interview.external.dto.response.NextQuestionResponse;
import com.samhap.kokomen.interview.external.dto.response.TotalFeedbackResponse;
import com.samhap.kokomen.interview.service.dto.InterviewProceedResponse;
import com.samhap.kokomen.member.domain.Member;
import com.samhap.kokomen.member.service.MemberService;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class InterviewProceedService {

    private final BedrockClient bedrockClient;
    private final MemberService memberService;
    private final InterviewService interviewService;
    private final AnswerService answerService;
    private final QuestionService questionService;
    private final ObjectMapper objectMapper;

    @Transactional
    public Optional<InterviewProceedResponse> proceedOrEndInterview(
            Long memberId,
            QuestionAndAnswers questionAndAnswers,
            LlmResponse llmResponse,
            Long interviewId
    ) {
        Member member = memberService.readById(memberId);
        member.useToken();
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
    public void proceedOrEndInterviewNonblockAsync(
            Long memberId,
            QuestionAndAnswers questionAndAnswers,
            LlmResponse llmResponse,
            Long interviewId
    ) {
        Member member = memberService.readById(memberId);
        member.useToken();
        Answer curAnswer = saveCurrentAnswer(questionAndAnswers, llmResponse);
        Interview interview = interviewService.readInterview(interviewId);
        if (questionAndAnswers.isProceedRequest()) {
            saveNextQuestion(llmResponse, interview);
            return;
        }
        evaluateInterview(questionAndAnswers, curAnswer, interview, llmResponse, member);
    }

    @Transactional
    public void proceedOrEndInterviewBlockAsync(
            Long memberId,
            QuestionAndAnswers questionAndAnswers,
            Long interviewId
    ) {
        LlmResponse llmResponse = bedrockClient.requestToBedrock(questionAndAnswers);
        Member member = memberService.readById(memberId);
        member.useToken();
        Answer curAnswer = saveCurrentAnswer(questionAndAnswers, llmResponse);
        Interview interview = interviewService.readInterview(interviewId);
        if (questionAndAnswers.isProceedRequest()) {
            saveNextQuestion(llmResponse, interview);
            return;
        }
        evaluateInterview(questionAndAnswers, curAnswer, interview, llmResponse, member);
    }

    private Answer saveCurrentAnswer(QuestionAndAnswers questionAndAnswers, LlmResponse llmResponse) {
        AnswerFeedbackResponse feedback = llmResponse.extractAnswerFeedbackResponse(objectMapper);
        return answerService.saveAnswer(questionAndAnswers.createCurAnswer(feedback));
    }

    private Question saveNextQuestion(LlmResponse llmResponse, Interview interview) {
        NextQuestionResponse nextQuestionResponse = llmResponse.extractNextQuestionResponse(objectMapper);
        Question nextQuestion = questionService.saveQuestion(new Question(interview, nextQuestionResponse.nextQuestion()));
        return nextQuestion;
    }

    private void evaluateInterview(QuestionAndAnswers questionAndAnswers, Answer curAnswer, Interview interview, LlmResponse llmResponse, Member member) {
        int totalScore = questionAndAnswers.calculateTotalScore(curAnswer.getAnswerRank().getScore());
        TotalFeedbackResponse totalFeedbackResponse = llmResponse.extractTotalFeedbackResponse(objectMapper);
        interview.evaluate(totalFeedbackResponse.totalFeedback(), totalScore);
        member.addScore(totalScore);
    }
}
