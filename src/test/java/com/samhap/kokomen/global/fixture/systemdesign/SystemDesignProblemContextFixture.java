package com.samhap.kokomen.global.fixture.systemdesign;

import com.samhap.kokomen.interview.tool.SystemDesignProblemContext;
import com.samhap.kokomen.interview.tool.SystemDesignProblemContext.EvaluationPoint;
import com.samhap.kokomen.interview.tool.SystemDesignProblemContext.Requirement;
import java.util.List;

/**
 * 실제로 출제할 URL 단축기 문제의 LLM 입력 문맥. 숨은 요구사항과 평가 포인트 문구는 SystemDesignProblemTestSupport가 DB에 저장하는
 * 값과 같은 한곳이다. 프롬프트 정본 파일(prompts/system-design-l1-url-shortener.txt)도 이 값으로 조립한 결과다.
 */
public final class SystemDesignProblemContextFixture {

    public static final List<Requirement> URL_SHORTENER_REQUIREMENTS = List.of(
            new Requirement("DAILY_CREATION_VOLUME", "하루 생성량",
                    "새로 만들어지는 단축 URL은 하루 평균 10만 개 정도예요."),
            new Requirement("READ_WRITE_RATIO", "조회와 생성 비율",
                    "단축 URL로 접속하는 조회 요청은 생성 요청의 100배쯤 된다고 보면 돼요."),
            new Requirement("RETENTION_PERIOD", "보관 기간",
                    "단축 URL은 만든 날부터 5년 동안 열려야 하고, 그 뒤에는 지워도 돼요."),
            new Requirement("SHORT_KEY_LENGTH", "단축 키 길이",
                    "단문 문자에도 넣어야 해서 단축 키는 영문 대소문자와 숫자로 7자 이하여야 해요."),
            new Requirement("CLICK_STATISTICS", "클릭 통계",
                    "링크별 클릭 수 같은 통계 기능은 이번 범위에서 빼도 돼요.")
    );

    public static final List<EvaluationPoint> URL_SHORTENER_EVALUATION_POINTS = List.of(
            new EvaluationPoint("READ_PATH_CACHE",
                    "조회가 생성보다 훨씬 많다는 점을 근거로 리다이렉트 경로에 캐시를 두었다"),
            new EvaluationPoint("KEY_GENERATION_COLLISION",
                    "단축 키 생성 방식(카운터 인코딩·해시 등)과 키 충돌을 막거나 처리하는 방법을 설명했다"),
            new EvaluationPoint("STORAGE_ESTIMATION",
                    "생성량·보관 기간·건당 크기로 전체 저장 용량을 추정하고 저장소 선택에 반영했다"),
            new EvaluationPoint("REDIRECT_AVAILABILITY",
                    "DB나 캐시에 장애가 나도 이미 보낸 링크가 계속 열리도록 대비책(복제·장애 전환 등)을 설명했다"),
            new EvaluationPoint("REDIRECT_STATUS_TRADEOFF",
                    "301과 302 리다이렉트의 장단점을 비교하고 근거를 들어 하나를 골랐다")
    );

    // 문제 id는 문맥 비교에만 쓰인다. 1이 아니게 두어 다른 행의 id와 우연히 같아지지 않게 한다.
    private static final Long URL_SHORTENER_PROBLEM_ID = 7L;

    private SystemDesignProblemContextFixture() {
    }

    public static SystemDesignProblemContext urlShortener() {
        return urlShortener(URL_SHORTENER_PROBLEM_ID);
    }

    public static SystemDesignProblemContext urlShortener(Long problemId) {
        return new SystemDesignProblemContext(problemId, SystemDesignProblemTestSupport.TITLE,
                SystemDesignProblemTestSupport.DESCRIPTION, SystemDesignProblemTestSupport.DIFFICULTY_COMMENT,
                URL_SHORTENER_REQUIREMENTS, URL_SHORTENER_EVALUATION_POINTS);
    }
}
