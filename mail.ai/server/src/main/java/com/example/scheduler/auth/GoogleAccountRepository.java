package com.example.scheduler.auth;

import org.springframework.data.jpa.repository.JpaRepository;

public interface GoogleAccountRepository extends JpaRepository<GoogleAccount, String> {
}
