package com.samhap.kokomen.interview.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.samhap.kokomen.global.dto.MemberAuth;
import com.samhap.kokomen.global.exception.BadRequestException;
import com.samhap.kokomen.global.exception.ForbiddenException;
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
import com.samhap.kokomen.interview.service.dto.AnswerRequest;
import com.samhap.kokomen.interview.service.dto.InterviewProceedResponse;
import com.samhap.kokomen.member.domain.Member;
import com.samhap.kokomen.member.repository.MemberRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class InterviewProceedService {

    private final InterviewRepository interviewRepository;
    private final QuestionRepository questionRepository;
    private final AnswerRepository answerRepository;
    private final MemberRepository memberRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public QuestionAndAnswers prepareInterviewProceed(Long interviewId, Long curQuestionId, AnswerRequest answerRequest, MemberAuth memberAuth) {
        Member member = readMember(memberAuth);
        Interview interview = readInterview(interviewId);
        validateInterviewee(interview, member);
        QuestionAndAnswers questionAndAnswers = createQuestionAndAnswers(curQuestionId, answerRequest, interview);
        decreaseTokenCount(member);

        return questionAndAnswers;
    }

    private void validateInterviewee(Interview interview, Member member) {
        if (!interview.isInterviewee(member)) {
            throw new ForbiddenException("해당 인터뷰를 생성한 회원이 아닙니다.");
        }
    }

    private QuestionAndAnswers createQuestionAndAnswers(Long curQuestionId, AnswerRequest answerRequest, Interview interview) {
        List<Question> questions = questionRepository.findByInterview(interview);
        List<Answer> prevAnswers = answerRepository.findByQuestionIn(questions);
        return new QuestionAndAnswers(questions, prevAnswers, answerRequest.answer(), curQuestionId, interview);
    }

    private void decreaseTokenCount(Member member) {
        int affectedRows = memberRepository.decreaseFreeTokenCount(member);
        if (affectedRows == 0) {
            throw new BadRequestException("회원의 토큰 개수가 부족해 인터뷰를 더 이상 진행할 수 없습니다.");
        }
    }

    @Transactional
    public Optional<InterviewProceedResponse> saveInterviewProceedResult(
            Long interviewId,
            QuestionAndAnswers questionAndAnswers,
            GptResponse gptResponse,
            MemberAuth memberAuth
    ) {
        Interview interview = readInterview(interviewId);
        Member member = readMember(memberAuth);
        Answer curAnswer = saveCurrentAnswer(questionAndAnswers, gptResponse);

        if (questionAndAnswers.isProceedRequest()) {
            Question nextQuestion = saveNextQuestion(gptResponse, interview);
            return Optional.of(InterviewProceedResponse.createFollowingQuestionResponse(curAnswer, nextQuestion));
        }

        evaluateInterview(interview, questionAndAnswers, curAnswer, gptResponse, member);
        return Optional.empty();
    }

    @Transactional
    public void compensateMemberToken(MemberAuth memberAuth) {
        Member member = readMember(memberAuth);
        member.addFreeTokenCount(1);
    }

    private Member readMember(MemberAuth memberAuth) {
        return memberRepository.findById(memberAuth.memberId())
                .orElseThrow(() -> new UnauthorizedException("존재하지 않는 회원입니다."));
    }

    private Interview readInterview(Long interviewId) {
        return interviewRepository.findById(interviewId)
                .orElseThrow(() -> new BadRequestException("존재하지 않는 인터뷰입니다."));
    }

    private Answer saveCurrentAnswer(QuestionAndAnswers questionAndAnswers, GptResponse gptResponse) {
        GptFeedbackResponse feedback = gptResponse.extractGptFeedbackResponse(objectMapper);
        return answerRepository.save(questionAndAnswers.createCurAnswer(feedback));
    }

    private Question saveNextQuestion(GptResponse gptResponse, Interview interview) {
        GptNextQuestionResponse gptNextQuestionResponse = gptResponse.extractGptNextQuestionResponse(objectMapper);
        Question next = new Question(interview, gptNextQuestionResponse.nextQuestion());
        return questionRepository.save(next);
    }

    private void evaluateInterview(Interview interview, QuestionAndAnswers questionAndAnswers, Answer curAnswer, GptResponse gptResponse, Member member) {
        GptTotalFeedbackResponse gptTotalFeedbackResponse = gptResponse.extractGptTotalFeedbackResponse(objectMapper);
        int totalScore = questionAndAnswers.calculateTotalScore(curAnswer.getAnswerRank().getScore());
        interview.evaluate(gptTotalFeedbackResponse.totalFeedback(), totalScore);
        member.addScore(totalScore);
    }
}
