package com.samhap.kokomen.global.aop;

import com.samhap.kokomen.answer.domain.Answer;
import com.samhap.kokomen.answer.domain.AnswerRank;
import com.samhap.kokomen.answer.repository.AnswerRepository;
import com.samhap.kokomen.interview.domain.Interview;
import com.samhap.kokomen.interview.domain.InterviewState;
import com.samhap.kokomen.interview.domain.Question;
import com.samhap.kokomen.interview.repository.QuestionRepository;
import com.samhap.kokomen.interview.service.InterviewService;
import com.samhap.kokomen.interview.service.dto.InterviewStartResponse;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class RootQuestionMetricAspect {

    private final InterviewService interviewService;
    private final MeterRegistry meterRegistry;
    private final QuestionRepository questionRepository;
    private final AnswerRepository answerRepository;

    @Pointcut("execution(* com.samhap.kokomen.interview.service.InterviewFacadeService.startInterview(..))")
    public void startInterviewMethod() {
    }

    @AfterReturning(pointcut = "startInterviewMethod()", returning = "result")
    public void increaseRootQuestionInterviewCount(Object result) {
        if (!(result instanceof InterviewStartResponse response)) {
            return;
        }
        Long interviewId = response.interviewId();
        if (interviewId == null) {
            return;
        }

        Interview interview = interviewService.readInterview(interviewId);
        Long rootQuestionId = interview.getRootQuestion().getId();

        meterRegistry.counter(
                "root_question_interview_count",
                "root_question_id", String.valueOf(rootQuestionId)
        ).increment();
    }

    @AfterReturning(pointcut = "execution(* com.samhap.kokomen.interview.service.InterviewFacadeService.proceedInterview(..)) && args(interviewId, curQuestionId, ..)", returning = "result")
    public void increasAnswerRankCount(Object result, Long interviewId, Long curQuestionId) {
        if (interviewId == null || curQuestionId == null) {
            return;
        }
        List<Question> questions = questionRepository.findAllByInterviewIdOrderByIdAsc(interviewId);
        if (questions.isEmpty()) {
            return;
        }
        Long firstQuestionId = questions.get(0).getId();
        boolean isFirst = curQuestionId.equals(firstQuestionId);
        Answer answer = answerRepository.findByQuestionId(curQuestionId);
        if (answer == null) {
            return;
        }
        AnswerRank answerRank = answer.getAnswerRank();
        Interview interview = interviewService.readInterview(interviewId);
        Long rootQuestionId = interview.getRootQuestion().getId();
        String metricPrefix = isFirst ? "root_question_answer_rank_count_" : "root_question_next_question_answer_rank_count_";
        String metricName = metricPrefix + answerRank.name().toLowerCase();
        meterRegistry.counter(metricName, "root_question_id", String.valueOf(rootQuestionId)).increment();
        // 인터뷰가 FINISHED 상태면 end 카운터도 증가
        if (interview.getInterviewState() == InterviewState.FINISHED) {
            meterRegistry.counter("root_question_interview_end_count_total", "root_question_id", String.valueOf(rootQuestionId)).increment();
        }
    }
}
