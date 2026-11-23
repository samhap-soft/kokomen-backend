package com.samhap.kokomen.interview.service.systemdesign;

import static com.samhap.kokomen.interview.domain.systemdesign.SystemDesignErrorMessage.INTERVIEW_NOT_FOUND;

import com.samhap.kokomen.global.exception.NotFoundException;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignInterview;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignProblem;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignRequirementQuestion;
import com.samhap.kokomen.interview.external.dto.response.SystemDesignRequirementAnswerResult;
import com.samhap.kokomen.interview.repository.systemdesign.SystemDesignEvaluationPointRepository;
import com.samhap.kokomen.interview.repository.systemdesign.SystemDesignHiddenRequirementRepository;
import com.samhap.kokomen.interview.repository.systemdesign.SystemDesignInterviewRepository;
import com.samhap.kokomen.interview.repository.systemdesign.SystemDesignRequirementQuestionRepository;
import com.samhap.kokomen.interview.service.dto.systemdesign.SystemDesignRequirementAnswerResponse;
import com.samhap.kokomen.interview.tool.SystemDesignProblemContext;
import com.samhap.kokomen.interview.tool.SystemDesignRequirementAnswerCommand;
import com.samhap.kokomen.interview.tool.SystemDesignRequirementAnswerComposer;
import com.samhap.kokomen.interview.tool.SystemDesignRequirementTurn;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 요구사항 역질문의 준비와 저장. LLM 호출은 두 트랜잭션 사이(트랜잭션 밖)에서 파사드가 한다. 역질문은 토큰을 검증하지도 차감하지도 않는다.
 */
@RequiredArgsConstructor
@Service
public class SystemDesignRequirementQuestionService {

    private final SystemDesignInterviewService systemDesignInterviewService;
    private final SystemDesignInterviewRepository systemDesignInterviewRepository;
    private final SystemDesignRequirementQuestionRepository requirementQuestionRepository;
    private final SystemDesignHiddenRequirementRepository hiddenRequirementRepository;
    private final SystemDesignEvaluationPointRepository evaluationPointRepository;

    /**
     * 소유자(404·403) → 단계(409) → 질문 길이(400) → 개수(400) 순서로 검사하고 LLM 입력을 값만으로 만든다.
     * 진행 락을 잡기 전에 흔한 오류를 빨리 돌려주려고 한 번, 락 안에서 최신 상태로 한 번 더 부른다.
     */
    @Transactional(readOnly = true)
    public RequirementQuestionDraft prepare(Long interviewId, Long memberId, String rawQuestion) {
        SystemDesignInterview systemDesignInterview =
                systemDesignInterviewService.readOwnedInterview(interviewId, memberId);
        systemDesignInterview.validateRequirementStage();
        String content = SystemDesignRequirementQuestion.normalizeContent(rawQuestion);
        List<SystemDesignRequirementQuestion> previousQuestions = requirementQuestionRepository
                .findBySystemDesignInterviewOrderByQuestionOrderAsc(systemDesignInterview);
        SystemDesignRequirementQuestion.validateCount(previousQuestions.size());
        List<SystemDesignRequirementTurn> previousTurns = previousQuestions.stream()
                .map(question -> new SystemDesignRequirementTurn(question.getContent(), question.getAnswer()))
                .toList();
        SystemDesignRequirementAnswerCommand command = new SystemDesignRequirementAnswerCommand(interviewId,
                readProblemContext(systemDesignInterview.getProblem()), previousTurns, content);
        return new RequirementQuestionDraft(interviewId, content, command);
    }

    private SystemDesignProblemContext readProblemContext(SystemDesignProblem problem) {
        return SystemDesignProblemContext.of(problem,
                hiddenRequirementRepository.findByProblemOrderByDisplayOrderAsc(problem),
                evaluationPointRepository.findByProblemOrderByDisplayOrderAsc(problem));
    }

    /**
     * LLM 호출 뒤 단계와 개수를 다시 확인하고, 최종 답을 정해 역질문 행을 저장한다. 실패하면 아무것도 남지 않는다.
     */
    @Transactional
    public SystemDesignRequirementAnswerResponse save(RequirementQuestionDraft draft,
                                                      SystemDesignRequirementAnswerResult result) {
        SystemDesignInterview systemDesignInterview = systemDesignInterviewRepository
                .findByInterviewId(draft.interviewId())
                .orElseThrow(() -> new NotFoundException(INTERVIEW_NOT_FOUND.getMessage()));
        systemDesignInterview.validateRequirementStage();
        int savedCount = Math.toIntExact(requirementQuestionRepository.countBySystemDesignInterview(
                systemDesignInterview));
        SystemDesignRequirementQuestion.validateCount(savedCount);
        String answer = SystemDesignRequirementAnswerComposer.compose(draft.command().problem().requirements(),
                result.askedRequirementCodes(), result.answer());
        SystemDesignRequirementQuestion requirementQuestion = requirementQuestionRepository.save(
                new SystemDesignRequirementQuestion(systemDesignInterview, savedCount + 1, draft.content(), answer,
                        result.askedRequirementCodes()));
        return new SystemDesignRequirementAnswerResponse(requirementQuestion.getContent(),
                requirementQuestion.getAnswer(),
                SystemDesignRequirementQuestion.MAX_COUNT - requirementQuestion.getQuestionOrder());
    }

    /**
     * 준비한 역질문. content는 정규화한 역질문이고 command는 LLM 입력이다.
     */
    public record RequirementQuestionDraft(
            Long interviewId,
            String content,
            SystemDesignRequirementAnswerCommand command
    ) {
    }
}
