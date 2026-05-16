package com.fearlauncher.app.model;

import java.io.Serializable;
import java.util.UUID;

public class Account implements Serializable {
    
    public enum AccountType { LOCAL, MICROSOFT }
    public enum ModelType { STEVE, ALEX } // Classic vs Slim arms
    
    private String id;
    private String username;
    private String email;
    private String passwordHash;
    private String accessToken;
    private String refreshToken;
    private String uuid;
    private AccountType type;
    private ModelType modelType; // ✅ NEW: Steve or Alex
    private String skinPath;     // ✅ NEW: Local path to skin PNG
    private String skinUrl;      // ✅ NEW: Remote skin URL (for premium)
    private long createdAt;
    private long lastUsed;
    private boolean isSelected;
    
    public Account(String username) {
        this.id = UUID.randomUUID().toString();
        this.username = username;
        this.type = AccountType.LOCAL;
        this.modelType = ModelType.STEVE; // Default
        this.createdAt = System.currentTimeMillis();
        this.lastUsed = 0;
        this.isSelected = false;
    }
    
    public Account(String username, String email, String accessToken, String refreshToken, String uuid) {
        this.id = UUID.randomUUID().toString();
        this.username = username;
        this.email = email;
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.uuid = uuid;
        this.type = AccountType.MICROSOFT;
        this.modelType = ModelType.STEVE;
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
    
    // ✅ NEW: Model Type
    public ModelType getModelType() { return modelType; }
    public void setModelType(ModelType type) { this.modelType = type; }
    public boolean isSlimModel() { return modelType == ModelType.ALEX; }
    
    // ✅ NEW: Skin Management
    public String getSkinPath() { return skinPath; }
    public void setSkinPath(String path) { this.skinPath = path; }
    public String getSkinUrl() { return skinUrl; }
    public void setSkinUrl(String url) { this.skinUrl = url; }
    public boolean hasCustomSkin() { return skinPath != null || skinUrl != null; }
    
    public long getCreatedAt() { return createdAt; }
    public long getLastUsed() { return lastUsed; }
    public void setLastUsed(long time) { this.lastUsed = time; }
    public boolean isSelected() { return isSelected; }
    public void setSelected(boolean selected) { isSelected = selected; }
    
    public boolean isMicrosoft() { return type == AccountType.MICROSOFT; }
    public boolean isLocal() { return type == AccountType.LOCAL; }
    
    public String getDisplayName() {
        return username + (isMicrosoft() ? " ✓" : "");
    }
    
    public String getAccountTypeLabel() {
        return isMicrosoft() ? "Premium Account" : "Local Account";
    }
    
    public String getModelLabel() {
        return modelType == ModelType.ALEX ? "Alex (Slim)" : "Steve (Classic)";
    }
}
