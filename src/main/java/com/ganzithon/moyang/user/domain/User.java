package com.ganzithon.moyang.user.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/** 서비스 사용자. 회원가입은 없고 시연용 데모 계정만 DB에 미리 넣어 둔다. */
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String loginId;

    /** BCrypt 해시. 평문 비밀번호는 저장하지 않는다. */
    @Column(nullable = false)
    private String passwordHash;

    @Column(nullable = false, length = 30)
    private String nickname;

    @Column(nullable = false)
    private boolean onboardingCompleted;

    /** 온보딩에서 고른 고민 묶음 코드(ThemeCode 이름). 온보딩 전에는 null. 온보딩 저장 API가 채운다. */
    @Column(length = 30)
    private String themeCode;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected User() {}

    public User(String loginId, String passwordHash, String nickname) {
        this.loginId = loginId;
        this.passwordHash = passwordHash;
        this.nickname = nickname;
        this.onboardingCompleted = false;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public String getLoginId() { return loginId; }
    public String getPasswordHash() { return passwordHash; }
    public String getNickname() { return nickname; }
    public boolean isOnboardingCompleted() { return onboardingCompleted; }

    public String getThemeCode() { return themeCode; }

    public void changeThemeCode(String themeCode) { this.themeCode = themeCode; }

    public void changePasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
}
