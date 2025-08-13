package com.samhap.kokomen.global.aop;

import com.samhap.kokomen.answer.domain.Answer;
import com.samhap.kokomen.answer.domain.AnswerRank;
import com.samhap.kokomen.answer.service.AnswerService;
import com.samhap.kokomen.interview.domain.Interview;
import com.samhap.kokomen.interview.domain.InterviewState;
import com.samhap.kokomen.interview.domain.QuestionAndAnswers;
import com.samhap.kokomen.interview.repository.InterviewRepository;
import com.samhap.kokomen.interview.repository.QuestionRepository;
import com.samhap.kokomen.interview.service.InterviewService;
import com.samhap.kokomen.interview.service.dto.InterviewProceedResponse;
import com.samhap.kokomen.interview.service.dto.start.InterviewStartResponse;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
@Order(3)
@Aspect
public class RootQuestionMetricAspect {

    private final MeterRegistry meterRegistry;
    private final InterviewService interviewService;
    private final AnswerService answerService;
    private final InterviewRepository interviewRepository;
    private final QuestionRepository questionRepository;

    @Pointcut("execution(* com.samhap.kokomen.interview.service.InterviewFacadeService.startInterview(..))")
    public void startInterviewMethod() {
    }

    @AfterReturning(pointcut = "startInterviewMethod()", returning = "result")
    public void increaseRootQuestionInterviewCount(InterviewStartResponse result) {
        Long interviewId = result.interviewId();
        Long rootQuestionId = interviewRepository.findRootQuestionIdByInterviewId(interviewId);

        meterRegistry.counter(
                "root_question_interview_count",
                "root_question_id", String.valueOf(rootQuestionId)
        ).increment();
    }

    @Pointcut("execution(* com.samhap.kokomen.interview.service.InterviewFacadeService.proceedInterview(..)) && args(interviewId, curQuestionId, ..)")
    public void proceedInterviewPointcut(Long interviewId, Long curQuestionId) {
    }

    @AfterReturning(pointcut = "proceedInterviewPointcut(interviewId, curQuestionId)", returning = "result")
    public void increaseRootQuestionInterviewEndCount(Optional<InterviewProceedResponse> result, Long interviewId, Long curQuestionId) {
        boolean isInterviewEnded = result.isEmpty();
        if (isInterviewEnded) {
            Long rootQuestionId = interviewRepository.findRootQuestionIdByInterviewId(interviewId);
            meterRegistry.counter(
                    "root_question_interview_end_count_total",
                    "root_question_id", String.valueOf(rootQuestionId)
            ).increment();
        }
    }

    @AfterReturning(pointcut = "proceedInterviewPointcut(interviewId, curQuestionId)", returning = "result")
    public void increaseRootQuestionAnswerRankCount(Optional<InterviewProceedResponse> result, Long interviewId, Long curQuestionId) {
        Long firstQuestionId = questionRepository.findFirstQuestionIdByInterviewIdOrderByIdAsc(interviewId);
        boolean isRootQuestionAnswer = Objects.equals(curQuestionId, firstQuestionId);
        if (isRootQuestionAnswer) {
            AnswerRank answerRank = result.get().curAnswerRank();
            Long rootQuestionId = interviewRepository.findRootQuestionIdByInterviewId(interviewId);
            meterRegistry.counter(
                    "root_question_answer_rank_count_" + answerRank.name().toLowerCase(),
                    "root_question_id", String.valueOf(rootQuestionId)
            ).increment();
        }
    }

    @Pointcut("execution(* com.samhap.kokomen.interview.service.InterviewProceedBlockAsyncService.proceedOrEndInterviewBlockAsync(..)) && args(memberId, questionAndAnswers, interviewId)")
    public void asyncProceedInterviewPointcut(Long memberId, QuestionAndAnswers questionAndAnswers, Long interviewId) {
    }

    // TODO: 테스트 코드 작성
    @AfterReturning(pointcut = "asyncProceedInterviewPointcut(memberId, questionAndAnswers, interviewId)")
    public void increaseRootQuestionInterviewEndCountByAsyncProceed(Long memberId, QuestionAndAnswers questionAndAnswers, Long interviewId) {
        Interview interview = interviewService.readInterview(interviewId);
        if (interview.getInterviewState() == InterviewState.FINISHED) {
            Long rootQuestionId = interviewRepository.findRootQuestionIdByInterviewId(interviewId);
            meterRegistry.counter(
                    "root_question_interview_end_count_total",
                    "root_question_id", String.valueOf(rootQuestionId)
            ).increment();
        }
    }

    // TODO: 테스트 코드 작성
    @AfterReturning(pointcut = "asyncProceedInterviewPointcut(memberId, questionAndAnswers, interviewId)")
    public void increaseRootQuestionAnswerRankCountByAsyncProceed(Long memberId, QuestionAndAnswers questionAndAnswers, Long interviewId) {
        Long curQuestionId = questionAndAnswers.readCurQuestion().getId();
        Long firstQuestionId = questionRepository.findFirstQuestionIdByInterviewIdOrderByIdAsc(interviewId);
        boolean isRootQuestionAnswer = Objects.equals(curQuestionId, firstQuestionId);
        if (isRootQuestionAnswer) {
            Answer answer = answerService.readByQuestionId(curQuestionId);
            AnswerRank answerRank = answer.getAnswerRank();
            Long rootQuestionId = interviewRepository.findRootQuestionIdByInterviewId(interviewId);
            meterRegistry.counter(
                    "root_question_answer_rank_count_" + answerRank.name().toLowerCase(),
                    "root_question_id", String.valueOf(rootQuestionId)
            ).increment();
        }
    }
}
