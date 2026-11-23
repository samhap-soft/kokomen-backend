package com.samhap.kokomen.interview.service.dto.systemdesign;

import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignBlockKind;
import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignDiagram;
import java.util.List;

/**
 * 저장된 다이어그램의 정규화 값 그대로다. 연결 label이 null이면 전역 non_null 설정으로 키가 빠진다.
 */
public record SystemDesignDiagramResponse(
        List<Block> blocks,
        List<Connection> connections
) {

    public static SystemDesignDiagramResponse from(SystemDesignDiagram diagram) {
        return new SystemDesignDiagramResponse(
                diagram.blocks().stream().map(Block::from).toList(),
                diagram.connections().stream().map(Connection::from).toList());
    }

    public record Block(
            String id,
            SystemDesignBlockKind kind,
            String label,
            List<Prop> props,
            Position position
    ) {

        private static Block from(SystemDesignDiagram.Block block) {
            return new Block(block.id(), block.kind(), block.label(),
                    block.props().stream().map(Prop::from).toList(),
                    new Position(block.position().x(), block.position().y()));
        }
    }

    public record Prop(
            String key,
            String value
    ) {

        private static Prop from(SystemDesignDiagram.Prop prop) {
            return new Prop(prop.key(), prop.value());
        }
    }

    public record Position(
            double x,
            double y
    ) {
    }

    public record Connection(
            String id,
            String source,
            String target,
            String label
    ) {

        private static Connection from(SystemDesignDiagram.Connection connection) {
            return new Connection(connection.id(), connection.source(), connection.target(), connection.label());
        }
    }
}
