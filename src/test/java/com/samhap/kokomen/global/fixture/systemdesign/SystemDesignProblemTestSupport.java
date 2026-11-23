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
import com.samhap.kokomen.interview.tool.SystemDesignProblemContext.EvaluationPoint;
import com.samhap.kokomen.interview.tool.SystemDesignProblemContext.Requirement;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.springframework.stereotype.Component;

/**
 * 시스템 설계 문제 하나를 숨은 요구사항 5개, 평가 포인트 5개, 기준 질문 6개(1~3 HIGH_LEVEL, 4~6 DEEP_DIVE)까지 저장한다.
 * 문구는 실제로 출제할 URL 단축기 문제와 같다. 숨은 요구사항과 평가 포인트는 SystemDesignProblemContextFixture의 값을 저장한다.
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
        List<Requirement> requirements = SystemDesignProblemContextFixture.URL_SHORTENER_REQUIREMENTS;
        hiddenRequirementRepository.saveAll(IntStream.range(0, requirements.size())
                .mapToObj(index -> requirement(problem, index + 1, requirements.get(index)))
                .toList());
        List<EvaluationPoint> points = SystemDesignProblemContextFixture.URL_SHORTENER_EVALUATION_POINTS;
        evaluationPointRepository.saveAll(IntStream.range(0, points.size())
                .mapToObj(index -> point(problem, index + 1, points.get(index)))
                .toList());
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

    private static SystemDesignHiddenRequirement requirement(SystemDesignProblem problem, int displayOrder,
                                                             Requirement requirement) {
        return SystemDesignHiddenRequirementFixtureBuilder.builder()
                .problem(problem)
                .displayOrder(displayOrder)
                .code(requirement.code())
                .name(requirement.name())
                .answer(requirement.answer())
                .build();
    }

    private static SystemDesignEvaluationPoint point(SystemDesignProblem problem, int displayOrder,
                                                     EvaluationPoint point) {
        return SystemDesignEvaluationPointFixtureBuilder.builder()
                .problem(problem)
                .displayOrder(displayOrder)
                .code(point.code())
                .description(point.description())
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
