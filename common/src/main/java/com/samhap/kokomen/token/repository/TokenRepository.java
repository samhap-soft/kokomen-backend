package com.samhap.kokomen.token.repository;

import com.samhap.kokomen.token.domain.Token;
import com.samhap.kokomen.token.domain.TokenType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TokenRepository extends JpaRepository<Token, Long> {

    Optional<Token> findByMemberIdAndType(Long memberId, TokenType type);
    
    List<Token> findByMemberId(Long memberId);
    
    void deleteByMemberIdAndType(Long memberId, TokenType type);
}