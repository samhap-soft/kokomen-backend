package com.samhap.kokomen.interview.repository.systemdesign;

import com.samhap.kokomen.interview.domain.systemdesign.SystemDesignInterview;
import com.samhap.kokomen.interview.repository.systemdesign.dto.SystemDesignInterviewListItem;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SystemDesignInterviewRepository extends JpaRepository<SystemDesignInterview, Long> {

    @EntityGraph(attributePaths = {"interview", "problem"})
    Optional<SystemDesignInterview> findByInterviewId(Long interviewId);

    // 질문·답변은 LEFT JOIN이라 질문이 없거나 답하지 않은 면접도 답변 수 0으로 나온다.
    // system_design_interview 행이 없는 면접 id는 결과에 없다.
    @Query("""
            SELECT new com.samhap.kokomen.interview.repository.systemdesign.dto.SystemDesignInterviewListItem(
                sdi.interview.id,
                p.title,
                COUNT(a.id)
            )
            FROM SystemDesignInterview sdi
            JOIN sdi.problem p
            LEFT JOIN SystemDesignQuestion q ON q.systemDesignInterview = sdi
            LEFT JOIN SystemDesignAnswer a ON a.question = q
            WHERE sdi.interview.id IN :interviewIds
            GROUP BY sdi.interview.id, p.title
            """)
    List<SystemDesignInterviewListItem> findListItemsByInterviewIdIn(
            @Param("interviewIds") Collection<Long> interviewIds);
}
