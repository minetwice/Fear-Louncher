package com.fearlouncher.auth;

import android.content.Context;
import android.net.Uri;
import android.util.Log;
import androidx.browser.customtabs.CustomTabsIntent;
import com.fearlouncher.model.Account;
import okhttp3.*;
import org.json.JSONObject;
import java.io.IOException;

public class MicrosoftAuth {
    
    private static final String CLIENT_ID = "YOUR_AZURE_CLIENT_ID_HERE";
    private static final String REDIRECT_URI = "fearlouncher://auth/microsoft";
    
    private static final String AUTH_URL = "https://login.microsoftonline.com/consumers/oauth2/v2.0/authorize";
    private static final String TOKEN_URL = "https://login.microsoftonline.com/consumers/oauth2/v2.0/token";
    private static final String SCOPE = "XboxLive.signin%20offline_access";
    private static final OkHttpClient httpClient = new OkHttpClient();
    
    public interface AuthCallback {
        void onSuccess(Account account);
        void onError(String error);
    }
    
    public void startLogin(Context context) {
        String authUrl = AUTH_URL + 
            "?client_id=" + CLIENT_ID +
            "&response_type=code" +
            "&redirect_uri=" + Uri.encode(REDIRECT_URI) +
            "&scope=" + SCOPE +
            "&response_mode=query";
        
        CustomTabsIntent customTabs = new CustomTabsIntent.Builder().build();
        customTabs.launchUrl(context, Uri.parse(authUrl));
    }
    
    public void handleRedirect(Uri redirectUri, AuthCallback callback) {
        String code = redirectUri.getQueryParameter("code");
        if (code == null) { callback.onError("Authorization code not found"); return; }
        exchangeCodeForToken(code, callback);
    }
    
    private void exchangeCodeForToken(String code, AuthCallback callback) {
        RequestBody formBody = new FormBody.Builder()
            .add("client_id", CLIENT_ID)
            .add("grant_type", "authorization_code")
            .add("code", code)
            .add("redirect_uri", REDIRECT_URI)
            .build();
        
        Request request = new Request.Builder().url(TOKEN_URL).post(formBody).build();
        
        httpClient.newCall(request).enqueue(new Callback() {
            @Override public void onResponse(Call call, Response response) throws IOException {
                try {
                    if (!response.isSuccessful()) { callback.onError("Token exchange failed"); return; }
                    JSONObject tokenJson = new JSONObject(response.body().string());
                    String accessToken = tokenJson.getString("access_token");
                    String refreshToken = tokenJson.getString("refresh_token");
                    Account account = new Account("MinecraftPlayer", "player@xbox.com", accessToken, refreshToken);
                    callback.onSuccess(account);
                } catch (Exception e) { callback.onError("Token parsing error: " + e.getMessage()); }
            }
            @Override public void onFailure(Call call, IOException e) { callback.onError("Network error: " + e.getMessage()); }
        });
    }
    
    public void refreshToken(String refreshToken, AuthCallback callback) {
        RequestBody formBody = new FormBody.Builder()
            .add("client_id", CLIENT_ID)
            .add("grant_type", "refresh_token")
            .add("refresh_token", refreshToken)
            .add("redirect_uri", REDIRECT_URI)
            .build();
        
        Request request = new Request.Builder().url(TOKEN_URL).post(formBody).build();
        httpClient.newCall(request).enqueue(new Callback() {
            @Override public void onResponse(Call call, Response response) throws IOException {
                try {
                    if (!response.isSuccessful()) { callback.onError("Token refresh failed"); return; }
                    JSONObject json = new JSONObject(response.body().string());
                    callback.onSuccess(null);
                } catch (Exception e) { callback.onError("Refresh error: " + e.getMessage()); }
            }
            @Override public void onFailure(Call call, IOException e) { callback.onError("Network error: " + e.getMessage()); }
        });
    }
}
