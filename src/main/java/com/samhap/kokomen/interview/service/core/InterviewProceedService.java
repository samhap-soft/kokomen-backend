package com.samhap.kokomen.interview.service.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.samhap.kokomen.answer.domain.Answer;
import com.samhap.kokomen.answer.service.AnswerService;
import com.samhap.kokomen.global.exception.ExternalApiException;
import com.samhap.kokomen.interview.domain.Interview;
import com.samhap.kokomen.interview.domain.InterviewMode;
import com.samhap.kokomen.interview.tool.InterviewProceedResult;
import com.samhap.kokomen.interview.domain.Question;
import com.samhap.kokomen.interview.tool.QuestionAndAnswers;
import com.samhap.kokomen.interview.external.dto.response.AnswerFeedbackResponse;
import com.samhap.kokomen.interview.external.dto.response.AnswerRankResponse;
import com.samhap.kokomen.interview.external.dto.response.LlmResponse;
import com.samhap.kokomen.interview.external.dto.response.NextQuestionResponse;
import com.samhap.kokomen.interview.external.dto.response.TotalFeedbackResponse;
import com.samhap.kokomen.interview.service.question.QuestionService;
import com.samhap.kokomen.member.domain.Member;
import com.samhap.kokomen.member.service.MemberService;
import com.samhap.kokomen.token.service.TokenFacadeService;
import jakarta.persistence.EntityManager;
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
    private final EntityManager entityManager;

    @Transactional
    public InterviewProceedResult proceedOrEndInterviewByBedrockFlowAsync(
            Long memberId,
            QuestionAndAnswers questionAndAnswers,
            LlmResponse llmResponse,
            Long interviewId
    ) {
        Member member = null;
        if (memberId != null) {
            member = memberService.readById(memberId);
        }
        Interview interview = interviewService.readInterview(interviewId);
        InterviewProceedResult result = saveProceedResult(questionAndAnswers, llmResponse, interview, member);
        if (memberId != null) {
            chargeAnswerToken(memberId);
        }
        return result;
    }

    private InterviewProceedResult saveProceedResult(QuestionAndAnswers questionAndAnswers, LlmResponse llmResponse,
                                                     Interview interview, Member member) {
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

    private void evaluateInterview(QuestionAndAnswers questionAndAnswers, Answer curAnswer, Interview interview,
                                   LlmResponse llmResponse, Member member) {
        int totalScore = questionAndAnswers.calculateTotalScore(curAnswer.getAnswerRank().getScore());
        TotalFeedbackResponse totalFeedbackResponse = llmResponse.extractTotalFeedbackResponse(objectMapper);
        String composedTotalFeedback = totalFeedbackResponse.composeTotalFeedback();
        if (composedTotalFeedback.isBlank()) {
            throw new ExternalApiException("면접 종합 피드백 응답이 비어있습니다.");
        }
        interview.evaluate(composedTotalFeedback, totalScore);
        if (member != null) {
            member.addScore(totalScore);
        }
    }

    /**
     * useTokens는 REQUIRES_NEW라 호출하는 즉시 따로 커밋되어, 이후 이 트랜잭션이 롤백돼도 돌아오지 않는다. 그래서 응답 해석과
     * 저장을 모두 끝낸 뒤 차감한다. 면접 평가처럼 커밋 때로 미뤄진 UPDATE도 flush로 먼저 실행해 저장에 실패한 진행에는
     * 과금하지 않는다. 토큰이 부족해 차감이 실패하면 저장한 진행 결과도 함께 롤백된다.
     */
    private void chargeAnswerToken(Long memberId) {
        entityManager.flush();
        tokenFacadeService.useTokens(memberId, InterviewMode.ANSWER_TOKEN_COST);
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
