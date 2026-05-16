package com.fearlauncher.app.model;

import java.io.Serializable;
import java.util.UUID;

public class Account implements Serializable {
    public enum AccountType { LOCAL, MICROSOFT }

    private String id;
    private String username;
    private String email;
    private String accessToken;
    private String refreshToken;
    private AccountType type;
    private long createdAt;
    private boolean isSelected;

    public Account(String username) {
        this.id = UUID.randomUUID().toString();
        this.username = username;
        this.type = AccountType.LOCAL;
        this.createdAt = System.currentTimeMillis();
        this.isSelected = false;
    }

    public Account(String username, String email, String accessToken, String refreshToken) {
        this.id = UUID.randomUUID().toString();
        this.username = username;
        this.email = email;
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.type = AccountType.MICROSOFT;
        this.createdAt = System.currentTimeMillis();
        this.isSelected = false;
    }

    public String getId() { return id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getEmail() { return email; }
    public String getAccessToken() { return accessToken; }
    public void setAccessToken(String token) { this.accessToken = token; }
    public String getRefreshToken() { return refreshToken; }
    public AccountType getType() { return type; }
    public long getCreatedAt() { return createdAt; }
    public boolean isSelected() { return isSelected; }
    public void setSelected(boolean selected) { isSelected = selected; }
    
    public boolean isMicrosoft() { return type == AccountType.MICROSOFT; }
    public String getDisplayName() { return username + (isMicrosoft() ? " ✓" : ""); }
}
