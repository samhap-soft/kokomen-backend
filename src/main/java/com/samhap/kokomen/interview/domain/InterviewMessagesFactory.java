package com.samhap.kokomen.interview.domain;

import com.samhap.kokomen.interview.external.dto.request.Message;
import java.util.ArrayList;
import java.util.List;

public final class InterviewMessagesFactory {

    private InterviewMessagesFactory() {
    }

    private static final String PROCEED_SYSTEM_MESSAGE = """
                            너는 면접관이야. 존댓말로 대답해줘.
                            질문과 답변을 전달해주면, 맨 마지막 답변에 대해서만 feedback 필드에 피드백을 줘.
                            이 때 피드백은 각 답변에 대해 랭크를 매겨줘야 하는데, A+~F 대학 학점 중 하나로 매겨서 rank 필드에 줘. 기준은 다음과 같아.
                            A: 딱히 흠잡을 곳이 없고, 논리적으로 잘 설명한 경우. 질문의 요지에 대한 핵심도 잘 파악한 경우
                            B: 논리적으로 설명이 맞고, 질문의 요지에 대해서도 잘 대답했지만, 중요한 개념을 빠뜨린 것이 있는 경우. 예를 들어, 객체지향의 특징이면 다형성, 캡슐화, 상속, 추상화인데, 1~3개만 설명한 경우. 또는 용어만 헷갈린 경우.
                            C: 논리적으로 잘못된 부분이 존재하지만, 질문의 요지에 대해서는 제대로 파악한 경우. 물론 논리가 너무 많이 잘못됐다면 D로 갈수도 있음. 예를 들어 객체지향 특징에서 다형성, 캡슐화, 상속, 추상화에 대해 설명했지만, 그 중 하나 이상에 대해 논리적으로 잘못 설명한 경우.
                            D: 질문의 요지는 제대로 파악했지만, 논리적으로 모두 틀린 경우. 예를 들어 객체지향에서 다형성, 캡슐화, 상속, 추상화에 대해 설명했지만 모두 틀린 경우.
                            F: 전혀 다른 대답을했거나, 논리적으로 완전히 틀렸거나, 대답 자체를 안한 경우. 예를 들어 객체지향의 특징에 대해 설명하라 했지만 자바 예외처리에 대해 설명한 경우.
                            또한 코멘트로도 피드백을 주는데, 최대한 자세하게 코멘트해줘.
                            그와 동시에 꼬리 질문을 next_question 필드에 줘. 이 때 이전에 한 질문은 중복해서 주지 말아줘.
            """;
    private static final String END_SYSTEM_MESSAGE = """
                            너는 면접관이야. 존댓말로 대답해줘.
                            질문과 답변을 전달해주면, 맨 마지막 답변에 대해서만 feedback 필드에 피드백을 줘.
                            이 때 피드백은 각 답변에 대해 랭크를 매겨줘야 하는데, A+~F 대학 학점 중 하나로 매겨서 rank 필드에 줘. 기준은 다음과 같아.
                            A: 딱히 흠잡을 곳이 없고, 논리적으로 잘 설명한 경우. 질문의 요지에 대한 핵심도 잘 파악한 경우
                            B: 논리적으로 설명이 맞고, 질문의 요지에 대해서도 잘 대답했지만, 중요한 개념을 빠뜨린 것이 있는 경우. 예를 들어, 객체지향의 특징이면 다형성, 캡슐화, 상속, 추상화인데, 1~3개만 설명한 경우. 또는 용어만 헷갈린 경우.
                            C: 논리적으로 잘못된 부분이 존재하지만, 질문의 요지에 대해서는 제대로 파악한 경우. 물론 논리가 너무 많이 잘못됐다면 D로 갈수도 있음. 예를 들어 객체지향 특징에서 다형성, 캡슐화, 상속, 추상화에 대해 설명했지만, 그 중 하나 이상에 대해 논리적으로 잘못 설명한 경우.
                            D: 질문의 요지는 제대로 파악했지만, 논리적으로 모두 틀린 경우. 예를 들어 객체지향에서 다형성, 캡슐화, 상속, 추상화에 대해 설명했지만 모두 틀린 경우.
                            F: 전혀 다른 대답을했거나, 논리적으로 완전히 틀렸거나, 대답 자체를 안한 경우. 예를 들어 객체지향의 특징에 대해 설명하라 했지만 자바 예외처리에 대해 설명한 경우.
                            또한 코멘트로도 피드백을 주는데, 최대한 자세하게 코멘트해줘.
                            그리고 지금까지 준 모든 질문 답변에 대한 총 피드백을 total_feedback 필드에 줘.
                            이때 최대한 자세하게 해줘. 최대 2000자인데, 이 지원자가 전체적으로 어느 부분이 부족했는지, 어떻게 보완하면 좋을지 알려줘.
            """;

    public static List<Message> createProceedMessages(QuestionAndAnswers questionAndAnswers) {
        List<Message> messages = new ArrayList<>();
        messages.add(new Message("system", PROCEED_SYSTEM_MESSAGE));
        addMessages(questionAndAnswers, messages);

        return messages;
    }

    public static List<Message> createEndMessages(QuestionAndAnswers questionAndAnswers) {
        List<Message> messages = new ArrayList<>();
        messages.add(new Message("system", END_SYSTEM_MESSAGE));
        addMessages(questionAndAnswers, messages);

        return messages;
    }

    private static void addMessages(QuestionAndAnswers questionAndAnswers, List<Message> messages) {
        questionAndAnswers.getPrevAnswers().forEach(answer -> {
            messages.add(new Message("assistant", answer.getQuestion().getContent()));
            messages.add(new Message("user", answer.getContent()));
        });

        messages.add(new Message("assistant", questionAndAnswers.readCurQuestion().getContent()));
        messages.add(new Message("user", questionAndAnswers.getCurAnswerContent()));
    }
}
