package com.samhap.kokomen.global.fixture.systemdesign;

import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignDiagram;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignEvaluationPoint;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignHiddenRequirement;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignProblem;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignProblemQuestion;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignStage;
import com.samhap.kokomen.interview.repository.systemdesign.SystemDesignEvaluationPointRepository;
import com.samhap.kokomen.interview.repository.systemdesign.SystemDesignHiddenRequirementRepository;
import com.samhap.kokomen.interview.repository.systemdesign.SystemDesignProblemQuestionRepository;
import com.samhap.kokomen.interview.repository.systemdesign.SystemDesignProblemRepository;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

/**
 * 시스템 설계 문제 하나를 숨은 요구사항 5개, 평가 포인트 5개, 기준 질문 6개(1~3 HIGH_LEVEL, 4~6 DEEP_DIVE)까지 저장한다.
 * 문구는 실제로 출제할 URL 단축기 문제와 같다.
 * MySQLDatabaseCleaner가 매 테스트 전에 모든 테이블을 TRUNCATE해 Flyway 시드가 남지 않으므로 테스트는 문제를 이것으로 만든다.
 */
@Component
public class SystemDesignProblemTestSupport {

    public static final String TITLE = "URL 단축기를 설계해 보세요";
    public static final String DESCRIPTION = "문자나 메신저 메시지에 넣을 짧은 링크를 만들어 주는 서비스예요. "
            + "긴 URL을 받아 짧은 URL을 돌려주고, 누군가 짧은 URL을 누르면 원래 주소로 보내 줘요. "
            + "많은 기업이 단체 문자에 이 링크를 넣어 보내기 때문에, 발송 직후에는 접속이 짧은 시간에 몰려요.";
    public static final String DIFFICULTY_COMMENT = "리다이렉트 경로를 빠르게 하는 방법과 단축 키 생성·충돌 처리를 "
            + "요구사항과 연결해 설명하면 좋은 평가를 받아요. "
            + "저장 용량은 가정한 수치로 대략 계산하면 되고, 장애 대응은 핵심 대비책만 짚어도 충분해요.";

    // system_design_problem.code가 UNIQUE라 부를 때마다 다른 code를 쓴다.
    private static final AtomicInteger CODE_SEQUENCE = new AtomicInteger();

    private final SystemDesignProblemRepository problemRepository;
    private final SystemDesignHiddenRequirementRepository hiddenRequirementRepository;
    private final SystemDesignEvaluationPointRepository evaluationPointRepository;
    private final SystemDesignProblemQuestionRepository problemQuestionRepository;

    public SystemDesignProblemTestSupport(SystemDesignProblemRepository problemRepository,
                                          SystemDesignHiddenRequirementRepository hiddenRequirementRepository,
                                          SystemDesignEvaluationPointRepository evaluationPointRepository,
                                          SystemDesignProblemQuestionRepository problemQuestionRepository) {
        this.problemRepository = problemRepository;
        this.hiddenRequirementRepository = hiddenRequirementRepository;
        this.evaluationPointRepository = evaluationPointRepository;
        this.problemQuestionRepository = problemQuestionRepository;
    }

    public SystemDesignProblem saveProblem() {
        return saveProblem(SystemDesignDiagramFixture.urlShortenerStarter());
    }

    public SystemDesignProblem saveProblem(SystemDesignDiagram starterDiagram) {
        SystemDesignProblem problem = problemRepository.save(SystemDesignProblemFixtureBuilder.builder()
                .code("URL_SHORTENER_" + CODE_SEQUENCE.incrementAndGet())
                .title(TITLE)
                .description(DESCRIPTION)
                .difficultyComment(DIFFICULTY_COMMENT)
                .starterDiagram(starterDiagram)
                .build());
        hiddenRequirementRepository.saveAll(List.of(
                requirement(problem, 1, "DAILY_CREATION_VOLUME", "하루 생성량",
                        "새로 만들어지는 단축 URL은 하루 평균 10만 개 정도예요."),
                requirement(problem, 2, "READ_WRITE_RATIO", "조회와 생성 비율",
                        "단축 URL로 접속하는 조회 요청은 생성 요청의 100배쯤 된다고 보면 돼요."),
                requirement(problem, 3, "RETENTION_PERIOD", "보관 기간",
                        "단축 URL은 만든 날부터 5년 동안 열려야 하고, 그 뒤에는 지워도 돼요."),
                requirement(problem, 4, "SHORT_KEY_LENGTH", "단축 키 길이",
                        "단문 문자에도 넣어야 해서 단축 키는 영문 대소문자와 숫자로 7자 이하여야 해요."),
                requirement(problem, 5, "CLICK_STATISTICS", "클릭 통계",
                        "링크별 클릭 수 같은 통계 기능은 이번 범위에서 빼도 돼요.")
        ));
        evaluationPointRepository.saveAll(List.of(
                point(problem, 1, "READ_PATH_CACHE",
                        "조회가 생성보다 훨씬 많다는 점을 근거로 리다이렉트 경로에 캐시를 두었다"),
                point(problem, 2, "KEY_GENERATION_COLLISION",
                        "단축 키 생성 방식(카운터 인코딩·해시 등)과 키 충돌을 막거나 처리하는 방법을 설명했다"),
                point(problem, 3, "STORAGE_ESTIMATION",
                        "생성량·보관 기간·건당 크기로 전체 저장 용량을 추정하고 저장소 선택에 반영했다"),
                point(problem, 4, "REDIRECT_AVAILABILITY",
                        "DB나 캐시에 장애가 나도 이미 보낸 링크가 계속 열리도록 대비책(복제·장애 전환 등)을 설명했다"),
                point(problem, 5, "REDIRECT_STATUS_TRADEOFF",
                        "301과 302 리다이렉트의 장단점을 비교하고 근거를 들어 하나를 골랐다")
        ));
        problemQuestionRepository.saveAll(List.of(
                question(problem, 1, SystemDesignStage.HIGH_LEVEL, "READ_PATH_CACHE",
                        "확인한 요구사항을 바탕으로 전체 구조를 그리고, 단축 URL을 만드는 요청과 단축 URL로 접속하는 요청이 각각 어떤 순서로 처리되는지 "
                                + "설명해 주세요."),
                question(problem, 2, SystemDesignStage.HIGH_LEVEL, "KEY_GENERATION_COLLISION",
                        "단축 키는 어떤 방식으로 만들고, 서로 다른 URL이 같은 키를 받지 않게 하려면 어떻게 하나요?"),
                question(problem, 3, SystemDesignStage.HIGH_LEVEL, "STORAGE_ESTIMATION",
                        "데이터가 얼마나 쌓일지 추정해 보고, 그에 맞춰 단축 키와 원래 URL을 어떤 저장소에 어떤 형태로 둘지 설명해 주세요."),
                question(problem, 4, SystemDesignStage.DEEP_DIVE, "READ_PATH_CACHE",
                        "리다이렉트 요청이 지금보다 크게 늘면 어디가 먼저 병목이 되고, 어떻게 대응하나요?"),
                question(problem, 5, SystemDesignStage.DEEP_DIVE, "REDIRECT_AVAILABILITY",
                        "DB 서버 한 대가 갑자기 내려가면 이미 메시지로 보낸 링크는 어떻게 되고, 어떻게 대비하나요?"),
                question(problem, 6, SystemDesignStage.DEEP_DIVE, "REDIRECT_STATUS_TRADEOFF",
                        "리다이렉트 응답을 301(영구 이동)과 302(임시 이동) 중 무엇으로 줄지, 두 방식의 장단점을 비교해 설명해 주세요.")
        ));
        return problem;
    }

    private static SystemDesignHiddenRequirement requirement(SystemDesignProblem problem, int displayOrder, String code,
                                                             String name, String answer) {
        return SystemDesignHiddenRequirementFixtureBuilder.builder()
                .problem(problem)
                .displayOrder(displayOrder)
                .code(code)
                .name(name)
                .answer(answer)
                .build();
    }

    private static SystemDesignEvaluationPoint point(SystemDesignProblem problem, int displayOrder, String code,
                                                     String description) {
        return SystemDesignEvaluationPointFixtureBuilder.builder()
                .problem(problem)
                .displayOrder(displayOrder)
                .code(code)
                .description(description)
                .build();
    }

    private static SystemDesignProblemQuestion question(SystemDesignProblem problem, int questionOrder,
                                                        SystemDesignStage stage, String targetPointCode,
                                                        String content) {
        return SystemDesignProblemQuestionFixtureBuilder.builder()
                .problem(problem)
                .questionOrder(questionOrder)
                .stage(stage)
                .content(content)
                .targetPointCodes(List.of(targetPointCode))
                .build();
    }
}
