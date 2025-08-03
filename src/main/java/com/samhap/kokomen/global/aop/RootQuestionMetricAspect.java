package com.samhap.kokomen.global.aop;

import com.samhap.kokomen.answer.domain.AnswerRank;
import com.samhap.kokomen.interview.repository.InterviewRepository;
import com.samhap.kokomen.interview.repository.QuestionRepository;
import com.samhap.kokomen.interview.service.dto.InterviewProceedResponse;
import com.samhap.kokomen.interview.service.dto.InterviewStartResponse;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;

@Aspect
@Component
@RequiredArgsConstructor
public class RootQuestionMetricAspect {

    private final MeterRegistry meterRegistry;
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
    public void increaseInterviewEndCount(Optional<InterviewProceedResponse> result, Long interviewId, Long curQuestionId) {
        if (result.isEmpty()) {
            Long rootQuestionId = interviewRepository.findRootQuestionIdByInterviewId(interviewId);
            meterRegistry.counter("root_question_interview_end_count_total", "root_question_id", String.valueOf(rootQuestionId)).increment();
        }
    }

    @AfterReturning(pointcut = "proceedInterviewPointcut(interviewId, curQuestionId)", returning = "result")
    public void increaseAnswerRankCount(Optional<InterviewProceedResponse> result, Long interviewId, Long curQuestionId) {
        Long firstQuestionId = questionRepository.findFirstQuestionIdByInterviewIdOrderByIdAsc(interviewId);
        boolean isFirst = Objects.equals(curQuestionId, firstQuestionId);
        if (isFirst) {
            AnswerRank answerRank = result.get().curAnswerRank();
            Long rootQuestionId = interviewRepository.findRootQuestionIdByInterviewId(interviewId);
            String metricName = "root_question_answer_rank_count_" + answerRank.name().toLowerCase();
            meterRegistry.counter(metricName, "root_question_id", String.valueOf(rootQuestionId)).increment();
        }
    }
}
