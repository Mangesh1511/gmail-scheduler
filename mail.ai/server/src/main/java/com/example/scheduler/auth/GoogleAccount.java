package com.example.scheduler.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "google_accounts")
public class GoogleAccount {
    @Id
    @Column(name = "owner_id", nullable = false, updatable = false)
    private String ownerId;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false, length = 4096)
    private String encryptedRefreshToken;

    @Column(nullable = false)
    private Instant connectedAt;

    protected GoogleAccount() {}

    public GoogleAccount(String ownerId, String email, String encryptedRefreshToken) {
        this.ownerId = ownerId;
        this.email = email;
        this.encryptedRefreshToken = encryptedRefreshToken;
        this.connectedAt = Instant.now();
    }

    public String getOwnerId() { return ownerId; }
    public String getEmail() { return email; }
    public String getEncryptedRefreshToken() { return encryptedRefreshToken; }
    public Instant getConnectedAt() { return connectedAt; }
}
