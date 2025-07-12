package com.samhap.kokomen.interview.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.samhap.kokomen.answer.domain.Answer;
import com.samhap.kokomen.answer.repository.AnswerRepository;
import com.samhap.kokomen.global.exception.BadRequestException;
import com.samhap.kokomen.global.exception.UnauthorizedException;
import com.samhap.kokomen.interview.domain.Interview;
import com.samhap.kokomen.interview.domain.Question;
import com.samhap.kokomen.interview.domain.QuestionAndAnswers;
import com.samhap.kokomen.interview.external.dto.response.AnswerFeedbackResponse;
import com.samhap.kokomen.interview.external.dto.response.LlmResponse;
import com.samhap.kokomen.interview.external.dto.response.NextQuestionResponse;
import com.samhap.kokomen.interview.external.dto.response.TotalFeedbackResponse;
import com.samhap.kokomen.interview.repository.InterviewRepository;
import com.samhap.kokomen.interview.repository.QuestionRepository;
import com.samhap.kokomen.interview.service.dto.InterviewProceedResponse;
import com.samhap.kokomen.member.domain.Member;
import com.samhap.kokomen.member.repository.MemberRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InterviewAnswerResponseService {

    private final InterviewRepository interviewRepository;
    private final AnswerRepository answerRepository;
    private final QuestionRepository questionRepository;
    private final MemberRepository memberRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public Optional<InterviewProceedResponse> handleGptResponse(
            Long memberId,
            QuestionAndAnswers questionAndAnswers,
            LlmResponse llmResponse,
            Long interviewId
    ) {
        Member member = readMember(memberId);
        member.useToken();
        Answer curAnswer = saveCurrentAnswer(questionAndAnswers, llmResponse);
        Interview interview = readInterview(interviewId);
        if (questionAndAnswers.isProceedRequest()) {
            Question nextQuestion = saveNextQuestion(llmResponse, interview);
            return Optional.of(InterviewProceedResponse.createFollowingQuestionResponse(curAnswer, nextQuestion));
        }
        evaluateInterview(questionAndAnswers, curAnswer, interview, llmResponse, member);
        return Optional.empty();
    }

    private Member readMember(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new UnauthorizedException("존재하지 않는 회원입니다."));
    }

    private Answer saveCurrentAnswer(QuestionAndAnswers questionAndAnswers, LlmResponse llmResponse) {
        AnswerFeedbackResponse feedback = llmResponse.extractAnswerFeedbackResponse(objectMapper);
        return answerRepository.save(questionAndAnswers.createCurAnswer(feedback));
    }

    private Interview readInterview(Long interviewId) {
        return interviewRepository.findById(interviewId)
                .orElseThrow(() -> new BadRequestException("존재하지 않는 인터뷰입니다."));
    }

    private Question saveNextQuestion(LlmResponse llmResponse, Interview interview) {
        NextQuestionResponse nextQuestionResponse = llmResponse.extractNextQuestionResponse(objectMapper);
        Question next = new Question(interview, nextQuestionResponse.nextQuestion());
        return questionRepository.save(next);
    }

    private void evaluateInterview(QuestionAndAnswers questionAndAnswers, Answer curAnswer, Interview interview, LlmResponse llmResponse, Member member) {
        int totalScore = questionAndAnswers.calculateTotalScore(curAnswer.getAnswerRank().getScore());
        TotalFeedbackResponse totalFeedbackResponse = llmResponse.extractTotalFeedbackResponse(objectMapper);
        interview.evaluate(totalFeedbackResponse.totalFeedback(), totalScore);
        member.addScore(totalScore);
    }
}
