package com.samhap.kokomen.global.fixture.interview;

import com.samhap.kokomen.answer.domain.AnswerRank;
import com.samhap.kokomen.interview.external.dto.response.Choice;
import com.samhap.kokomen.interview.external.dto.response.GptFunctionCall;
import com.samhap.kokomen.interview.external.dto.response.GptResponse;
import com.samhap.kokomen.interview.external.dto.response.Message;
import com.samhap.kokomen.interview.external.dto.response.ToolCall;
import java.util.List;

public class GptResponseFixtureBuilder {

    private AnswerRank answerRank;
    private String reasoning;
    private String feedback;
    private String nextQuestion;
    private String strengths;
    private String improvements;
    private String learningDirection;

    public static GptResponseFixtureBuilder builder() {
        return new GptResponseFixtureBuilder();
    }

    public GptResponseFixtureBuilder answerRank(AnswerRank answerRank) {
        this.answerRank = answerRank;
        return this;
    }

    public GptResponseFixtureBuilder reasoning(String reasoning) {
        this.reasoning = reasoning;
        return this;
    }

    public GptResponseFixtureBuilder feedback(String feedback) {
        this.feedback = feedback;
        return this;
    }

    public GptResponseFixtureBuilder nextQuestion(String nextQuestion) {
        this.nextQuestion = nextQuestion;
        return this;
    }

    public GptResponseFixtureBuilder strengths(String strengths) {
        this.strengths = strengths;
        return this;
    }

    public GptResponseFixtureBuilder improvements(String improvements) {
        this.improvements = improvements;
        return this;
    }

    public GptResponseFixtureBuilder learningDirection(String learningDirection) {
        this.learningDirection = learningDirection;
        return this;
    }

    public GptResponse buildProceed() {
        String arguments = """
                {
                  "reasoning": "%s",
                  "rank": "%s",
                  "feedback": "%s",
                  "next_question": "%s"
                }
                """.formatted(
                reasoning != null ? reasoning : "답변 평가 근거와 다음 질문 설계 근거입니다.",
                answerRank != null ? answerRank.name() : "A",
                feedback != null ? feedback : "좋은 답변입니다.",
                nextQuestion != null ? nextQuestion : "스레드 안전하다는 것은 무엇인가요?"
        );
        return create(arguments, "generate_feedback");
    }

    public GptResponse buildEnd() {
        String arguments = """
                {
                  "reasoning": "%s",
                  "rank": "%s",
                  "feedback": "%s",
                  "strengths": "%s",
                  "improvements": "%s",
                  "learning_direction": "%s"
                }
                """.formatted(
                reasoning != null ? reasoning : "마지막 답변 평가 근거와 전체 면접 종합 평가 근거입니다.",
                answerRank != null ? answerRank.name() : "A",
                feedback != null ? feedback : "좋은 답변입니다.",
                strengths != null ? strengths : "전체적으로 답변이 명확합니다.",
                improvements != null ? improvements : "구체 사례를 더 보강하면 좋겠습니다.",
                learningDirection != null ? learningDirection : "기초 개념 심화 학습을 권장합니다."
        );
        return create(arguments, "generate_total_feedback");
    }

    private GptResponse create(String arguments, String gptFunctionCallName) {
        return new GptResponse(
                List.of(new Choice(
                        new Message(
                                List.of(new ToolCall(new GptFunctionCall(
                                        gptFunctionCallName,
                                        arguments
                                )))
                        )
                )));
    }
}
