package com.fearlauncher.app.auth;

import android.content.Context;
import android.net.Uri;
import android.util.Log;
import androidx.browser.customtabs.CustomTabsIntent;
import com.fearlauncher.app.model.Account;
import okhttp3.*;
import org.json.JSONObject;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

public class MicrosoftAuth {

    private static final String TAG = "MicrosoftAuth";
    
    // 🔐 IMPORTANT: Replace with your actual Azure App Registration values
    // Get from: https://portal.azure.com → Azure AD → App registrations
    private static final String CLIENT_ID = "YOUR_AZURE_CLIENT_ID_HERE";
    private static final String REDIRECT_URI = "fearlauncher://auth/microsoft";
    
    // Microsoft OAuth2 Endpoints
    private static final String AUTH_URL = "https://login.microsoftonline.com/consumers/oauth2/v2.0/authorize";
    private static final String TOKEN_URL = "https://login.microsoftonline.com/consumers/oauth2/v2.0/token";
    private static final String XBOX_AUTH_URL = "https://user.auth.xboxlive.com/user/authenticate";
    private static final String XSTS_URL = "https://xsts.auth.xboxlive.com/xsts/authorize";
    private static final String MINECRAFT_AUTH_URL = "https://api.minecraftservices.com/authentication/login_with_xbox";
    private static final String MINECRAFT_PROFILE_URL = "https://api.minecraftservices.com/minecraft/profile";
    
    private static final String SCOPE = "XboxLive.signin%20offline_access";
    private static final OkHttpClient httpClient = new OkHttpClient.Builder()
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .build();
    
    public interface AuthCallback {
        void onSuccess(Account account);
        void onError(String error);
    }
    
    // Step 1: Open Microsoft login in Custom Tab with PKCE
    public void startLogin(Context context) {
        try {
            // Generate PKCE code verifier and challenge (for security)
            String codeVerifier = generateCodeVerifier();
            String codeChallenge = generateCodeChallenge(codeVerifier);
            
            // Store verifier for later use (in real app, use secure storage)
            // For demo, we'll skip persistence
            
            String authUrl = AUTH_URL + 
                "?client_id=" + CLIENT_ID +
                "&response_type=code" +
                "&redirect_uri=" + Uri.encode(REDIRECT_URI) +
                "&scope=" + SCOPE +
                "&response_mode=query" +
                "&code_challenge=" + codeChallenge +
                "&code_challenge_method=S256";
            
            CustomTabsIntent customTabs = new CustomTabsIntent.Builder()
                .setShowTitle(true)
                .build();
            customTabs.launchUrl(context, Uri.parse(authUrl));
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to start login: " + e.getMessage());
        }
    }
    
    // Step 2: Handle redirect and exchange code for token
    public void handleRedirect(Uri redirectUri, AuthCallback callback) {
        String code = redirectUri.getQueryParameter("code");
        String error = redirectUri.getQueryParameter("error");
        
        if (error != null) {
            callback.onError("Microsoft error: " + error);
            return;
        }
        
        if (code == null) {
            callback.onError("Authorization code not found");
            return;
        }
        
        // For demo: Skip PKCE verification
        exchangeCodeForToken(code, null, callback);
    }
    
    // Exchange authorization code for access token
    private void exchangeCodeForToken(String code, String codeVerifier, AuthCallback callback) {
        RequestBody.Builder formBuilder = new FormBody.Builder()
            .add("client_id", CLIENT_ID)
            .add("grant_type", "authorization_code")
            .add("code", code)
            .add("redirect_uri", REDIRECT_URI);
        
        if (codeVerifier != null) {
            formBuilder.add("code_verifier", codeVerifier);
        }
        
        Request request = new Request.Builder()
            .url(TOKEN_URL)
            .post(formBuilder.build())
            .build();
        
        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try {
                    if (!response.isSuccessful()) {
                        String errBody = response.body() != null ? response.body().string() : "Unknown error";
                        callback.onError("Token exchange failed: " + response.code() + " - " + errBody);
                        return;
                    }
                    
                    JSONObject tokenJson = new JSONObject(response.body().string());
                    String accessToken = tokenJson.getString("access_token");
                    String refreshToken = tokenJson.getString("refresh_token");
                    long expiresIn = tokenJson.optLong("expires_in", 3600);
                    
                    // Continue with Xbox Live authentication
                    authenticateWithXbox(accessToken, refreshToken, expiresIn, callback);
                    
                } catch (Exception e) {
                    Log.e(TAG, "Token parsing error: " + e.getMessage());
                    callback.onError("Token parsing error: " + e.getMessage());
                }
            }
            
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Network error: " + e.getMessage());
                callback.onError("Network error: " + e.getMessage());
            }
        });
    }
    
    // Authenticate with Xbox Live to get Minecraft account
    private void authenticateWithXbox(String msToken, String refreshToken, long expiresIn, AuthCallback callback) {
        // Step 1: Get Xbox Live User Token
        JSONObject sisuRequest = new JSONObject();
        try {
            sisuRequest.put("Properties", new JSONObject()
                .put("AuthMethod", "RPS")
                .put("SiteName", "user.auth.xboxlive.com")
                .put("RpsTicket", "d=" + msToken));
            sisuRequest.put("RelyingParty", "http://auth.xboxlive.com");
            sisuRequest.put("TokenType", "JWT");
        } catch (Exception e) {
            callback.onError("Xbox auth request error: " + e.getMessage());
            return;
        }
        
        RequestBody xboxBody = RequestBody.create(
            sisuRequest.toString(), 
            MediaType.parse("application/json")
        );
        
        Request xboxRequest = new Request.Builder()
            .url(XBOX_AUTH_URL)
            .post(xboxBody)
            .addHeader("Accept", "application/json")
            .build();
        
        httpClient.newCall(xboxRequest).enqueue(new Callback() {
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try {
                    if (!response.isSuccessful()) {
                        callback.onError("Xbox Live auth failed: " + response.code());
                        return;
                    }
                    
                    JSONObject xboxResponse = new JSONObject(response.body().string());
                    String userToken = xboxResponse
                        .getJSONObject("Token")
                        .getString("Token");
                    
                    // Step 2: Get XSTS Token
                    authenticateWithXsts(userToken, refreshToken, expiresIn, callback);
                    
                } catch (Exception e) {
                    callback.onError("Xbox response error: " + e.getMessage());
                }
            }
            
            @Override
            public void onFailure(Call call, IOException e) {
                callback.onError("Xbox network error: " + e.getMessage());
            }
        });
    }
    
    // Get XSTS token for Minecraft authentication
    private void authenticateWithXsts(String userToken, String refreshToken, long expiresIn, AuthCallback callback) {
        JSONObject xstsRequest = new JSONObject();
        try {
            xstsRequest.put("Properties", new JSONObject()
                .put("SandboxId", "RETAIL")
                .put("UserTokens", new org.json.JSONArray().put(userToken)));
            xstsRequest.put("RelyingParty", "rp://api.minecraftservices.com/");
            xstsRequest.put("TokenType", "JWT");
        } catch (Exception e) {
            callback.onError("XSTS request error: " + e.getMessage());
            return;
        }
        
        RequestBody xstsBody = RequestBody.create(
            xstsRequest.toString(),
            MediaType.parse("application/json")
        );
        
        Request xstsRequestObj = new Request.Builder()
            .url(XSTS_URL)
            .post(xstsBody)
            .addHeader("Accept", "application/json")
            .build();
        
        httpClient.newCall(xstsRequestObj).enqueue(new Callback() {
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try {
                    if (!response.isSuccessful()) {
                        callback.onError("XSTS auth failed: " + response.code());
                        return;
                    }
                    
                    JSONObject xstsResponse = new JSONObject(response.body().string());
                    String xstsToken = xstsResponse
                        .getJSONObject("Token")
                        .getString("Token");
                    String userHash = xstsResponse
                        .getJSONObject("DisplayClaims")
                        .getJSONObject("xui")
                        .getJSONArray("0")
                        .getJSONObject(0)
                        .getString("uhs");
                    
                    // Step 3: Authenticate with Minecraft services
                    authenticateWithMinecraft(xstsToken, userHash, refreshToken, expiresIn, callback);
                    
                } catch (Exception e) {
                    callback.onError("XSTS response error: " + e.getMessage());
                }
            }
            
            @Override
            public void onFailure(Call call, IOException e) {
                callback.onError("XSTS network error: " + e.getMessage());
            }
        });
    }
    
    // Final step: Get Minecraft access token and profile
    private void authenticateWithMinecraft(String xstsToken, String userHash, String refreshToken, long expiresIn, AuthCallback callback) {
        JSONObject mcRequest = new JSONObject();
        try {
            mcRequest.put("identityToken", "XBL3.0 x=" + userHash + ";" + xstsToken);
        } catch (Exception e) {
            callback.onError("Minecraft request error: " + e.getMessage());
            return;
        }
        
        RequestBody mcBody = RequestBody.create(
            mcRequest.toString(),
            MediaType.parse("application/json")
        );
        
        Request mcRequestObj = new Request.Builder()
            .url(MINECRAFT_AUTH_URL)
            .post(mcBody)
            .addHeader("Accept", "application/json")
            .build();
        
        httpClient.newCall(mcRequestObj).enqueue(new Callback() {
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try {
                    if (!response.isSuccessful()) {
                        // Check if account doesn't own Minecraft
                        if (response.code() == 403) {
                            callback.onError("This Microsoft account doesn't own Minecraft");
                            return;
                        }
                        callback.onError("Minecraft auth failed: " + response.code());
                        return;
                    }
                    
                    JSONObject mcResponse = new JSONObject(response.body().string());
                    String mcAccessToken = mcResponse.getString("access_token");
                    
                    // Step 4: Get Minecraft profile (username, UUID, skin)
                    fetchMinecraftProfile(mcAccessToken, userHash, refreshToken, expiresIn, callback);
                    
                } catch (Exception e) {
                    callback.onError("Minecraft response error: " + e.getMessage());
                }
            }
            
            @Override
            public void onFailure(Call call, IOException e) {
                callback.onError("Minecraft network error: " + e.getMessage());
            }
        });
    }
    
    // Fetch Minecraft profile for final account creation
    private void fetchMinecraftProfile(String mcToken, String userHash, String refreshToken, long expiresIn, AuthCallback callback) {
        Request profileRequest = new Request.Builder()
            .url(MINECRAFT_PROFILE_URL)
            .addHeader("Authorization", "Bearer " + mcToken)
            .addHeader("Accept", "application/json")
            .build();
        
        httpClient.newCall(profileRequest).enqueue(new Callback() {
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try {
                    if (!response.isSuccessful()) {
                        callback.onError("Profile fetch failed: " + response.code());
                        return;
                    }
                    
                    JSONObject profile = new JSONObject(response.body().string());
                    String username = profile.getString("name");
                    String uuid = profile.getString("id");
                    String skinUrl = null;
                    
                    // Optional: Extract skin URL if available
                    if (profile.has("skins") && profile.getJSONArray("skins").length() > 0) {
                        skinUrl = profile.getJSONArray("skins")
                            .getJSONObject(0)
                            .optString("url");
                    }
                    
                    // Create final Account object
                    Account account = new Account(username, userHash, mcToken, refreshToken, uuid);
                    account.setSkinUrl(skinUrl);
                    
                    callback.onSuccess(account);
                    
                } catch (Exception e) {
                    callback.onError("Profile parsing error: " + e.getMessage());
                }
            }
            
            @Override
            public void onFailure(Call call, IOException e) {
                callback.onError("Profile network error: " + e.getMessage());
            }
        });
    }
    
    // 🔐 PKCE Helper: Generate code verifier
    private String generateCodeVerifier() {
        SecureRandom secureRandom = new SecureRandom();
        byte[] codeVerifier = new byte[32];
        secureRandom.nextBytes(codeVerifier);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(codeVerifier);
    }
    
    // 🔐 PKCE Helper: Generate code challenge from verifier
    private String generateCodeChallenge(String codeVerifier) throws Exception {
        byte[] bytes = codeVerifier.getBytes();
        MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
        messageDigest.update(bytes);
        byte[] digest = messageDigest.digest();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
    }
    
    // Helper: Check if token is likely expired
    public boolean isTokenExpired(long tokenTimestamp, long expiresIn) {
        long now = System.currentTimeMillis();
        // Add 5-minute buffer
        return (now - tokenTimestamp) > ((expiresIn - 300) * 1000);
    }
    
    // Helper: Refresh token (simplified)
    public void refreshToken(String refreshToken, AuthCallback callback) {
        RequestBody formBody = new FormBody.Builder()
            .add("client_id", CLIENT_ID)
            .add("grant_type", "refresh_token")
            .add("refresh_token", refreshToken)
            .add("redirect_uri", REDIRECT_URI)
            .build();
        
        Request request = new Request.Builder()
            .url(TOKEN_URL)
            .post(formBody)
            .build();
        
        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try {
                    if (!response.isSuccessful()) {
                        callback.onError("Token refresh failed");
                        return;
                    }
                    
                    JSONObject json = new JSONObject(response.body().string());
                    String newAccessToken = json.getString("access_token");
                    String newRefreshToken = json.getString("refresh_token");
                    long expiresIn = json.optLong("expires_in", 3600);
                    
                    // Note: Caller should update Account object with new tokens
                    // For now, return null and let caller handle
                    callback.onSuccess(null);
                    
                } catch (Exception e) {
                    callback.onError("Refresh error: " + e.getMessage());
                }
            }
            
            @Override
            public void onFailure(Call call, IOException e) {
                callback.onError("Network error: " + e.getMessage());
            }
        });
    }
}
