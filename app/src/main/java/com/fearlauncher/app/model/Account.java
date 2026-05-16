package com.fearlauncher.app.model;

import java.io.Serializable;
import java.util.UUID;

public class Account implements Serializable {
    
    public enum AccountType {
        LOCAL,      // Offline account
        MICROSOFT   // Premium Microsoft account
    }
    
    private String id;
    private String username;
    private String email;
    private String passwordHash;  // For local accounts (NOT plain text!)
    private String accessToken;   // For Microsoft accounts
    private String refreshToken;
    private String uuid;          // Minecraft UUID (for premium)
    private AccountType type;
    private long createdAt;
    private long lastUsed;
    private boolean isSelected;
    private String skinUrl;       // Optional: player skin URL
    
    // Constructor for Local Account
    public Account(String username) {
        this.id = UUID.randomUUID().toString();
        this.username = username;
        this.type = AccountType.LOCAL;
        this.createdAt = System.currentTimeMillis();
        this.lastUsed = 0;
        this.isSelected = false;
    }
    
    // Constructor for Microsoft Account
    public Account(String username, String email, String accessToken, String refreshToken, String uuid) {
        this.id = UUID.randomUUID().toString();
        this.username = username;
        this.email = email;
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.uuid = uuid;
        this.type = AccountType.MICROSOFT;
        this.createdAt = System.currentTimeMillis();
        this.lastUsed = 0;
        this.isSelected = false;
    }
    
    // Getters & Setters
    public String getId() { return id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String hash) { this.passwordHash = hash; }
    public String getAccessToken() { return accessToken; }
    public void setAccessToken(String token) { this.accessToken = token; }
    public String getRefreshToken() { return refreshToken; }
    public String getUuid() { return uuid; }
    public AccountType getType() { return type; }
    public long getCreatedAt() { return createdAt; }
    public long getLastUsed() { return lastUsed; }
    public void setLastUsed(long time) { this.lastUsed = time; }
    public boolean isSelected() { return isSelected; }
    public void setSelected(boolean selected) { isSelected = selected; }
    public String getSkinUrl() { return skinUrl; }
    public void setSkinUrl(String url) { this.skinUrl = url; }
    
    // Helper methods
    public boolean isMicrosoft() { return type == AccountType.MICROSOFT; }
    public boolean isLocal() { return type == AccountType.LOCAL; }
    
    public String getDisplayName() {
        return username + (isMicrosoft() ? " ✓" : "");
    }
    
    public String getAccountTypeLabel() {
        return isMicrosoft() ? "Premium Account" : "Local Account";
    }
}
