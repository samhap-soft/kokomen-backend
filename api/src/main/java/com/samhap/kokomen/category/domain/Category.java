package com.samhap.kokomen.category.domain;

import java.util.List;
import lombok.Getter;

@Getter
public enum Category {

    ALGORITHM("알고리즘",
            """
                    알고리즘은 어떤 문제를 해결하기 위한 단계별 방법이나 절차를 의미합니다.
                    예를 들어, 숫자 목록을 크기 순서대로 정렬하는 정렬 알고리즘이나, 원하는 데이터를 빠르게 찾는 탐색 알고리즘이 있습니다.
                    또한, 그래프 알고리즘과 동적 프로그래밍 등 다양한 유형이 있으며,
                    효율적인 알고리즘을 선택하고 구현하는 것은 프로그램의 성능을 좌우하는 중요한 요소입니다.
                    """,
            "kokomen-algorithm.png"),
    DATA_STRUCTURE("자료구조",
            """
                    자료구조는 데이터를 효율적으로 저장하고 관리하기 위한 다양한 방법과 구조를 의미합니다.
                    예를 들어, 배열과 리스트는 데이터를 순서대로 저장하는 데 쓰이고, 스택과 큐는 데이터를 쌓거나 줄 세우는 방식으로 관리합니다.
                    또한, 트리와 그래프 같은 자료구조는 복잡한 관계나 연결 구조를 표현할 때 사용되며,
                    적절한 자료구조를 선택하는 것은 빠르고 효율적인 알고리즘 구현의 핵심이 됩니다.
                    """,
            "kokomen-data-structure.png"),
    DATABASE("데이터베이스",
            """
                    데이터베이스는 대량의 데이터를 효율적으로 저장하고 관리하는 시스템입니다.
                    관계형 데이터베이스(RDBMS)는 테이블 구조와 SQL을 기반으로 데이터를 관리하고,
                    NoSQL은 비정형 데이터와 대규모 분산 처리를 지원합니다.
                    적절한 인덱스 설계는 빠른 데이터 조회와 시스템 성능에 중요한 영향을 미칩니다.
                    """,
            "kokomen-database.png"),
    NETWORK("네트워크",
            """
                    네트워크는 여러 컴퓨터와 시스템이 서로 데이터를 주고받을 수 있도록 구성된 통신 구조입니다.
                    네트워크 계층 구조(OSI 7계층, TCP/IP 4계층), 프로토콜(TCP, UDP, HTTP 등),
                    라우팅, 패킷 전송 방식 등 다양한 개념을 이해하는 것이 네트워크의 핵심입니다.
                    """,
            "kokomen-network.png"),
    OPERATING_SYSTEM("운영체제",
            """
                    운영체제는 하드웨어와 소프트웨어 자원을 효율적으로 관리하고, 사용자와 응용 프로그램이 시스템을 효과적으로 사용할 수 있도록 지원하는 핵심 소프트웨어입니다.
                    프로세스 및 스레드 관리, 메모리 관리, 파일 시스템, 입출력(I/O) 제어, 그리고 CPU 스케줄링과 같은 기능을 담당합니다.
                    OS의 구조와 동작 원리를 이해하는 것은 시스템 개발 및 최적화의 기초가 됩니다.
                    """,
            "kokomen-operating-system.png"),
    ;

    private static final String BASE_URL = "https://d2ftfzru2cd49g.cloudfront.net/category-image/";

    private final String title;
    private final String description;
    private final String imageUrl;

    Category(String title, String description, String imageUrl) {
        this.title = title;
        this.description = description;
        this.imageUrl = BASE_URL + imageUrl;
    }

    private static final List<Category> CATEGORIES = List.of(values());

    public static List<Category> getCategories() {
        return CATEGORIES;
    }
}
