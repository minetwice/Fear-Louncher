package com.fearlauncher.app.manager;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.fearlauncher.app.model.Account;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class AccountManager {
    
    private static final String TAG = "AccountManager";
    private static final String PREF_NAME = "fearlauncher_accounts";
    private static final String KEY_ACCOUNTS = "accounts_list";
    private static final String KEY_SELECTED = "selected_account_id";
    
    private static AccountManager instance;
    private final SharedPreferences prefs;
    private final Gson gson;
    
    private AccountManager(Context context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        gson = new Gson();
    }
    
    public static synchronized AccountManager getInstance(Context context) {
        if (instance == null) {
            instance = new AccountManager(context.getApplicationContext());
        }
        return instance;
    }
    
    // ✅ Create new account
    public boolean addAccount(Account account) {
        List<Account> accounts = getAllAccounts();
        
        // Check for duplicate username (case-insensitive)
        for (Account acc : accounts) {
            if (acc.getUsername().equalsIgnoreCase(account.getUsername())) {
                Log.w(TAG, "Username already exists: " + account.getUsername());
                return false;
            }
        }
        
        accounts.add(account);
        saveAccounts(accounts);
        Log.i(TAG, "Account added: " + account.getUsername());
        return true;
    }
    
    // ✅ Get all accounts (sorted by last used)
    public List<Account> getAllAccounts() {
        String json = prefs.getString(KEY_ACCOUNTS, "[]");
        Type type = new TypeToken<List<Account>>() {}.getType();
        List<Account> accounts = gson.fromJson(json, type);
        
        if (accounts == null) accounts = new ArrayList<>();
        
        // Sort: selected first, then by last used
        Collections.sort(accounts, new Comparator<Account>() {
            @Override
            public int compare(Account a, Account b) {
                if (a.isSelected() && !b.isSelected()) return -1;
                if (!a.isSelected() && b.isSelected()) return 1;
                return Long.compare(b.getLastUsed(), a.getLastUsed());
            }
        });
        
        return accounts;
    }
    
    // ✅ Get currently selected account
    public Account getSelectedAccount() {
        String selectedId = prefs.getString(KEY_SELECTED, null);
        if (selectedId == null) return null;
        
        for (Account acc : getAllAccounts()) {
            if (acc.getId().equals(selectedId)) {
                return acc;
            }
        }
        return null;
    }
    
    // ✅ Select an account (auto-deselect others)
    public boolean selectAccount(String accountId) {
        List<Account> accounts = getAllAccounts();
        boolean found = false;
        
        for (Account acc : accounts) {
            if (acc.getId().equals(accountId)) {
                acc.setSelected(true);
                acc.setLastUsed(System.currentTimeMillis());
                found = true;
            } else {
                acc.setSelected(false);
            }
        }
        
        if (found) {
            prefs.edit().putString(KEY_SELECTED, accountId).apply();
            saveAccounts(accounts);
            Log.i(TAG, "Account selected: " + accountId);
        }
        return found;
    }
    
    // ✅ Delete account
    public boolean deleteAccount(String accountId) {
        List<Account> accounts = getAllAccounts();
        boolean removed = accounts.removeIf(acc -> acc.getId().equals(accountId));
        
        if (removed) {
            // If deleted account was selected, clear selection
            String selectedId = prefs.getString(KEY_SELECTED, null);
            if (accountId.equals(selectedId)) {
                prefs.edit().remove(KEY_SELECTED).apply();
            }
            saveAccounts(accounts);
            Log.i(TAG, "Account deleted: " + accountId);
        }
        return removed;
    }
    
    // ✅ Update account (for token refresh, skin update, etc.)
    public boolean updateAccount(Account updatedAccount) {
        List<Account> accounts = getAllAccounts();
        for (int i = 0; i < accounts.size(); i++) {
            if (accounts.get(i).getId().equals(updatedAccount.getId())) {
                accounts.set(i, updatedAccount);
                saveAccounts(accounts);
                Log.i(TAG, "Account updated: " + updatedAccount.getUsername());
                return true;
            }
        }
        return false;
    }
    
    // ✅ Check if any account exists
    public boolean hasAccounts() {
        return !getAllAccounts().isEmpty();
    }
    
    // ✅ Get account count
    public int getAccountCount() {
        return getAllAccounts().size();
    }
    
    // ✅ Clear all accounts (for testing)
    public void clearAllAccounts() {
        prefs.edit().clear().apply();
        Log.w(TAG, "All accounts cleared");
    }
    
    // 🔒 Private: Save list to SharedPreferences
    private void saveAccounts(List<Account> accounts) {
        String json = gson.toJson(accounts);
        prefs.edit().putString(KEY_ACCOUNTS, json).apply();
    }
    
    // 🔐 Optional: Hash password for local accounts (simple SHA-256)
    public static String hashPassword(String password) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(password.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            return password; // Fallback (not secure, but won't crash)
        }
    }
}
