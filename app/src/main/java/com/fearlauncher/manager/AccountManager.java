package com.fearlouncher.manager;

import android.content.Context;
import android.content.SharedPreferences;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.fearlouncher.model.Account;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class AccountManager {
    
    private static final String PREF_NAME = "fearlouncher_accounts";
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
    
    public boolean addAccount(Account account) {
        List<Account> accounts = getAllAccounts();
        for (Account acc : accounts) {
            if (acc.getUsername().equalsIgnoreCase(account.getUsername())) return false;
        }
        accounts.add(account);
        saveAccounts(accounts);
        return true;
    }
    
    public List<Account> getAllAccounts() {
        String json = prefs.getString(KEY_ACCOUNTS, "[]");
        Type type = new TypeToken<List<Account>>() {}.getType();
        List<Account> accounts = gson.fromJson(json, type);
        return accounts != null ? accounts : new ArrayList<>();
    }
    
    public Account getSelectedAccount() {
        String selectedId = prefs.getString(KEY_SELECTED, null);
        if (selectedId == null) return null;
        for (Account acc : getAllAccounts()) {
            if (acc.getId().equals(selectedId)) return acc;
        }
        return null;
    }
    
    public boolean selectAccount(String accountId) {
        List<Account> accounts = getAllAccounts();
        boolean found = false;
        for (Account acc : accounts) {
            if (acc.getId().equals(accountId)) { acc.setSelected(true); found = true; }
            else { acc.setSelected(false); }
        }
        if (found) {
            prefs.edit().putString(KEY_SELECTED, accountId).apply();
            saveAccounts(accounts);
        }
        return found;
    }
    
    public boolean deleteAccount(String accountId) {
        List<Account> accounts = getAllAccounts();
        boolean removed = accounts.removeIf(acc -> acc.getId().equals(accountId));
        if (removed) {
            String selectedId = prefs.getString(KEY_SELECTED, null);
            if (accountId.equals(selectedId)) prefs.edit().remove(KEY_SELECTED).apply();
            saveAccounts(accounts);
        }
        return removed;
    }
    
    public boolean updateAccount(Account updatedAccount) {
        List<Account> accounts = getAllAccounts();
        for (int i = 0; i < accounts.size(); i++) {
            if (accounts.get(i).getId().equals(updatedAccount.getId())) {
                accounts.set(i, updatedAccount);
                saveAccounts(accounts);
                return true;
            }
        }
        return false;
    }
    
    private void saveAccounts(List<Account> accounts) {
        prefs.edit().putString(KEY_ACCOUNTS, gson.toJson(accounts)).apply();
    }
    
    public boolean hasAccounts() { return !getAllAccounts().isEmpty(); }
    public int getAccountCount() { return getAllAccounts().size(); }
}
