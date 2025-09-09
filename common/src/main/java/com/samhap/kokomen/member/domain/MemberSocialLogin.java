package com.samhap.kokomen.member.domain;

import com.samhap.kokomen.global.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "member_social_login",
        indexes = {
                @Index(name = "idx_member_social_login_member_id", columnList = "member_id")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_member_social_login_provider_social_id",
                        columnNames = {"provider", "social_id"})
        })
public class MemberSocialLogin extends BaseEntity {

    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false)
    private SocialProvider provider;

    @Column(name = "social_id", nullable = false)
    private String socialId;

    public MemberSocialLogin(Member member, SocialProvider provider, String socialId) {
        this.member = member;
        this.provider = provider;
        this.socialId = socialId;
    }

    public Long getMemberId() {
        return member.getId();
    }
}
