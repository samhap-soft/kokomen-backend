package com.samhap.kokomen.global.aop;

import com.samhap.kokomen.interview.repository.InterviewRepository;
import com.samhap.kokomen.interview.service.dto.start.InterviewStartResponse;
import io.micrometer.core.instrument.MeterRegistry;
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
    private final InterviewRepository interviewRepository;

    @Pointcut("execution(* com.samhap.kokomen.interview.service.InterviewStartFacadeService.startInterview(..))")
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
}
