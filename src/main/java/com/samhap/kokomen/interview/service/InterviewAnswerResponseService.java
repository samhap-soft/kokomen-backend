package com.samhap.kokomen.interview.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.samhap.kokomen.global.exception.BadRequestException;
import com.samhap.kokomen.global.exception.UnauthorizedException;
import com.samhap.kokomen.interview.domain.Answer;
import com.samhap.kokomen.interview.domain.Interview;
import com.samhap.kokomen.interview.domain.Question;
import com.samhap.kokomen.interview.domain.QuestionAndAnswers;
import com.samhap.kokomen.interview.external.dto.response.GptFeedbackResponse;
import com.samhap.kokomen.interview.external.dto.response.GptNextQuestionResponse;
import com.samhap.kokomen.interview.external.dto.response.GptResponse;
import com.samhap.kokomen.interview.external.dto.response.GptTotalFeedbackResponse;
import com.samhap.kokomen.interview.repository.AnswerRepository;
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
            GptResponse gptResponse,
            Long interviewId
    ) {
        Member member = readMember(memberId);
        member.useToken();
        Answer curAnswer = saveCurrentAnswer(questionAndAnswers, gptResponse);
        Interview interview = readInterview(interviewId);
        if (questionAndAnswers.isProceedRequest()) {
            Question nextQuestion = saveNextQuestion(gptResponse, interview);
            return Optional.of(InterviewProceedResponse.createFollowingQuestionResponse(curAnswer, nextQuestion));
        }
        evaluateInterview(questionAndAnswers, curAnswer, interview, gptResponse, member);
        return Optional.empty();
    }

    private Member readMember(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new UnauthorizedException("존재하지 않는 회원입니다."));
    }

    private Answer saveCurrentAnswer(QuestionAndAnswers questionAndAnswers, GptResponse gptResponse) {
        GptFeedbackResponse feedback = gptResponse.extractGptFeedbackResponse(objectMapper);
        return answerRepository.save(questionAndAnswers.createCurAnswer(feedback));
    }

    private Interview readInterview(Long interviewId) {
        return interviewRepository.findById(interviewId)
                .orElseThrow(() -> new BadRequestException("존재하지 않는 인터뷰입니다."));
    }

    private Question saveNextQuestion(GptResponse gptResponse, Interview interview) {
        GptNextQuestionResponse gptNextQuestionResponse = gptResponse.extractGptNextQuestionResponse(objectMapper);
        Question next = new Question(interview, gptNextQuestionResponse.nextQuestion());
        return questionRepository.save(next);
    }

    private void evaluateInterview(QuestionAndAnswers questionAndAnswers, Answer curAnswer, Interview interview, GptResponse gptResponse, Member member) {
        int totalScore = questionAndAnswers.calculateTotalScore(curAnswer.getAnswerRank().getScore());
        GptTotalFeedbackResponse gptTotalFeedbackResponse = gptResponse.extractGptTotalFeedbackResponse(objectMapper);
        interview.evaluate(gptTotalFeedbackResponse.totalFeedback(), totalScore);
        member.addScore(totalScore);
    }
}
