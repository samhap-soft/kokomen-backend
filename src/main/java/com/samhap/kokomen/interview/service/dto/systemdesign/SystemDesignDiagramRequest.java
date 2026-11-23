package com.samhap.kokomen.interview.service.dto.systemdesign;

import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignDiagram;
import java.util.List;
import java.util.function.Function;

/**
 * 요청의 다이어그램. 값을 그대로 옮기기만 하고 검사는 {@link SystemDesignDiagram#from(SystemDesignDiagram.Input)}이 한다.
 * kind는 String, 좌표는 Double로 받아 모르는 종류와 빠진 좌표를 Jackson 문구가 아니라 다이어그램 규칙의 문구로 알린다.
 * 모르는 필드(에디터 내부 값 등)는 전역 Jackson 설정대로 버린다.
 */
public record SystemDesignDiagramRequest(
        List<Block> blocks,
        List<Connection> connections
) {

    public SystemDesignDiagram.Input toInput() {
        return new SystemDesignDiagram.Input(mapKeepingNull(blocks, Block::toInput),
                mapKeepingNull(connections, Connection::toInput));
    }

    // null 목록과 null 원소를 그대로 넘겨 SystemDesignDiagram.from이 볼 차례에 형식 오류 문구로 알리게 한다.
    private static <T, R> List<R> mapKeepingNull(List<T> values, Function<T, R> mapper) {
        if (values == null) {
            return null;
        }
        return values.stream()
                .map(value -> value == null ? null : mapper.apply(value))
                .toList();
    }

    public record Block(
            String id,
            String kind,
            String label,
            List<Prop> props,
            Position position
    ) {

        // 위치가 없으면 좌표를 null로 넘겨 위치 규칙의 문구로 알리게 한다.
        private SystemDesignDiagram.InputBlock toInput() {
            Double x = position == null ? null : position.x();
            Double y = position == null ? null : position.y();
            return new SystemDesignDiagram.InputBlock(id, kind, label, mapKeepingNull(props, Prop::toInput), x, y);
        }
    }

    public record Prop(
            String key,
            String value
    ) {

        private SystemDesignDiagram.InputProp toInput() {
            return new SystemDesignDiagram.InputProp(key, value);
        }
    }

    public record Position(
            Double x,
            Double y
    ) {
    }

    public record Connection(
            String id,
            String source,
            String target,
            String label
    ) {

        private SystemDesignDiagram.InputConnection toInput() {
            return new SystemDesignDiagram.InputConnection(id, source, target, label);
        }
    }
}
