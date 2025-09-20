package com.samhap.kokomen.token.repository;

import com.samhap.kokomen.token.domain.Token;
import com.samhap.kokomen.token.domain.TokenType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TokenRepository extends JpaRepository<Token, Long> {

    Optional<Token> findByMemberIdAndType(Long memberId, TokenType type);

    @Modifying
    @Query("""
            UPDATE Token t 
            SET t.tokenCount = t.tokenCount + :count 
            WHERE t.memberId = :memberId 
            AND t.type = :type
            """)
    int incrementTokenCountModifying(@Param("memberId") Long memberId, @Param("type") TokenType type, @Param("count") int count);

    @Modifying
    @Query("""
            UPDATE Token t 
            SET t.tokenCount = t.tokenCount - :count 
            WHERE t.memberId = :memberId 
            AND t.type = :type
            AND t.tokenCount >= :count
            """)
    int decrementTokenCountModifying(@Param("memberId") Long memberId, @Param("type") TokenType type, @Param("count") int count);

    @Modifying
    @Query("""
            UPDATE Token t 
            SET t.tokenCount = :tokenCount 
            WHERE t.type = :type
            """)
    int updateAllMembersFreeTokens(@Param("tokenCount") int tokenCount, @Param("type") TokenType type);
}
