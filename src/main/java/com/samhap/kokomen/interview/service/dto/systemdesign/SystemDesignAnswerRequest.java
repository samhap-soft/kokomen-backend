package com.samhap.kokomen.interview.service.dto.systemdesign;

import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignDiagram;

/**
 * 설계 답변 제출. 답변 길이와 다이어그램은 면접 상태를 본 뒤에 서비스·도메인이 검사한다(Bean Validation을 달지 않는다).
 */
public record SystemDesignAnswerRequest(
        String answer,
        SystemDesignDiagramRequest diagram
) {

    // 다이어그램이 없으면 null을 넘겨 SystemDesignDiagram.from이 형식 오류 문구로 알리게 한다.
    public SystemDesignDiagram.Input diagramInput() {
        return diagram == null ? null : diagram.toInput();
    }
}
