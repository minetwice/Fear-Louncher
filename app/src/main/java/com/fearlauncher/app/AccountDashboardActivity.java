package com.fearlauncher.app;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.fearlauncher.app.manager.AccountManager;
import com.fearlauncher.app.model.Account;

// ✅ FIXED: Add missing imports
import java.util.ArrayList;
import java.util.List;

public class AccountDashboardActivity extends AppCompatActivity {

    private RecyclerView accountsRecyclerView;
    private AccountAdapter accountAdapter;
    private AccountManager accountManager;
    private TextView emptyText;
    private Button btnAddAccount;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_account_dashboard);

        accountManager = AccountManager.getInstance(this);
        initViews();
        setupRecyclerView();
        loadAccounts();
    }

    private void initViews() {
        accountsRecyclerView = findViewById(R.id.accountsRecyclerView);
        emptyText = findViewById(R.id.emptyText);
        btnAddAccount = findViewById(R.id.btnAddAccount);
        
        btnAddAccount.setOnClickListener(v -> showAddAccountDialog());
    }

    private void setupRecyclerView() {
        accountsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        accountAdapter = new AccountAdapter(new AccountAdapter.AccountClickListener() {
            @Override
            public void onSelect(Account account) {
                accountManager.selectAccount(account.getId());
                showToast("Selected: " + account.getUsername());
                finish();
            }

            @Override
            public void onDelete(Account account) {
                showDeleteConfirmation(account);
            }
        });
        accountsRecyclerView.setAdapter(accountAdapter);
    }

    private void loadAccounts() {
        List<Account> accounts = accountManager.getAllAccounts();
        
        if (accounts.isEmpty()) {
            emptyText.setVisibility(View.VISIBLE);
            accountsRecyclerView.setVisibility(View.GONE);
        } else {
            emptyText.setVisibility(View.GONE);
            accountsRecyclerView.setVisibility(View.VISIBLE);
            accountAdapter.setAccounts(accounts);
        }
    }

    private void showAddAccountDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Create Account");

        View dialogView = getLayoutInflater().inflate(R.layout.dialog_add_account, null);
        builder.setView(dialogView);

        EditText inputUsername = dialogView.findViewById(R.id.inputUsername);
        EditText inputEmail = dialogView.findViewById(R.id.inputEmail);
        EditText inputPassword = dialogView.findViewById(R.id.inputPassword);
        Spinner accountTypeSpinner = dialogView.findViewById(R.id.accountTypeSpinner);
        Button btnCreate = dialogView.findViewById(R.id.btnCreate);
        Button btnMicrosoft = dialogView.findViewById(R.id.btnMicrosoft);

        AlertDialog dialog = builder.create();
        dialog.show();

        btnCreate.setOnClickListener(v -> {
            String username = inputUsername.getText().toString().trim();
            String password = inputPassword.getText().toString().trim();
            String accountType = accountTypeSpinner.getSelectedItem().toString();

            if (username.isEmpty()) {
                showToast("Please enter a username");
                return;
            }
            if (username.length() < 3) {
                showToast("Username must be at least 3 characters");
                return;
            }

            Account newAccount = new Account(username);
            
            if ("Premium".equals(accountType) && !inputEmail.getText().toString().isEmpty()) {
                newAccount = new Account(username, inputEmail.getText().toString(), "", "", "");
            }
            
            if (!password.isEmpty() && newAccount.isLocal()) {
                newAccount.setPasswordHash(AccountManager.hashPassword(password));
            }

            if (accountManager.addAccount(newAccount)) {
                accountManager.selectAccount(newAccount.getId());
                showToast("Account created: " + username);
                dialog.dismiss();
                loadAccounts();
            } else {
                showToast("Username already taken");
            }
        });

        btnMicrosoft.setOnClickListener(v -> {
            showToast("Microsoft login coming soon!");
            dialog.dismiss();
        });
    }

    private void showDeleteConfirmation(Account account) {
        new AlertDialog.Builder(this)
            .setTitle("Delete Account")
            .setMessage("Are you sure you want to delete \"" + account.getUsername() + "\"?")
            .setPositiveButton("Delete", (d, which) -> {
                if (accountManager.deleteAccount(account.getId())) {
                    showToast("Account deleted");
                    loadAccounts();
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadAccounts();
    }

    // ✅ Adapter class
    private static class AccountAdapter extends RecyclerView.Adapter<AccountAdapter.ViewHolder> {
        
        public interface AccountClickListener {
            void onSelect(Account account);
            void onDelete(Account account);
        }

        private final AccountClickListener listener;
        private List<Account> accounts = new ArrayList<>(); // ✅ Now ArrayList is imported

        public AccountAdapter(AccountClickListener listener) {
            this.listener = listener;
        }

        public void setAccounts(List<Account> accounts) {
            this.accounts = accounts;
            notifyDataSetChanged();
        }

        @Override
        public ViewHolder onCreateViewHolder(android.view.ViewGroup parent, int viewType) {
            View view = android.view.LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_account_list, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            Account account = accounts.get(position);
            holder.bind(account, listener);
        }

        @Override
        public int getItemCount() {
            return accounts.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            private final TextView textUsername, textType, textEmail;
            private final ImageView iconSelected, iconDelete;
            private final View root;

            ViewHolder(View itemView) {
                super(itemView);
                root = itemView;
                textUsername = itemView.findViewById(R.id.textUsername);
                textType = itemView.findViewById(R.id.textType);
                textEmail = itemView.findViewById(R.id.textEmail);
                iconSelected = itemView.findViewById(R.id.iconSelected);
                iconDelete = itemView.findViewById(R.id.iconDelete);
            }

            void bind(Account account, AccountClickListener listener) {
                textUsername.setText(account.getDisplayName());
                textType.setText(account.getAccountTypeLabel());
                textEmail.setText(account.isMicrosoft() ? account.getEmail() : "Offline");
                iconSelected.setVisibility(account.isSelected() ? View.VISIBLE : View.GONE);
                
                root.setOnClickListener(v -> listener.onSelect(account));
                iconDelete.setOnClickListener(v -> listener.onDelete(account));
            }
        }
    }
}
