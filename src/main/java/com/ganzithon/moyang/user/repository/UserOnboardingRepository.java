package com.ganzithon.moyang.user.repository;

import com.ganzithon.moyang.user.domain.UserOnboarding;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserOnboardingRepository extends JpaRepository<UserOnboarding, Long> {
}
