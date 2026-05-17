package com.fearlauncher.app;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.fearlauncher.app.manager.AccountManager;
import com.fearlauncher.app.manager.SkinManager;
import com.fearlauncher.app.model.Account;
import com.fearlauncher.app.view.CharacterPreviewView;
import java.util.ArrayList;
import java.util.List;

public class AccountDashboardActivity extends AppCompatActivity {

    // UI Views
    private CharacterPreviewView characterPreview;
    private RecyclerView accountsRecyclerView;
    private TextView emptyText;
    private ImageButton btnSteve, btnAlex;
    private ImageView btnMenuOptions;
    private Button btnAddAccount;

    // Managers
    private AccountManager accountManager;
    private SkinManager skinManager;
    private AccountAdapter adapter;

    // Image Picker Launcher
    private final ActivityResultLauncher<String> pickImageLauncher = 
        registerForActivityResult(new ActivityResultContracts.GetContent(), this::handleImageResult);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_account_dashboard);

        // Initialize managers        accountManager = AccountManager.getInstance(this);
        skinManager = new SkinManager(this);

        // Initialize views
        initViews();
        setupRecyclerView();
        loadAccounts();
        updatePreview();
    }

    private void initViews() {
        characterPreview = findViewById(R.id.characterPreview);
        accountsRecyclerView = findViewById(R.id.accountsRecyclerView);
        emptyText = findViewById(R.id.emptyText);
        btnSteve = findViewById(R.id.btnSteve);
        btnAlex = findViewById(R.id.btnAlex);
        btnMenuOptions = findViewById(R.id.btnMenuOptions);
        btnAddAccount = findViewById(R.id.btnAddAccount);

        // Button click listeners
        btnAddAccount.setOnClickListener(v -> showCreateAccountDialog());
        btnSteve.setOnClickListener(v -> switchModel("steve"));
        btnAlex.setOnClickListener(v -> switchModel("alex"));
        btnMenuOptions.setOnClickListener(v -> showCustomizeMenu());
    }

    private void setupRecyclerView() {
        accountsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        
        // ✅ Proper adapter with anonymous ClickListener implementation
        adapter = new AccountAdapter(new AccountAdapter.ClickListener() {
            @Override
            public void onSelect(Account account) {
                accountManager.selectAccount(account.getId());
                updatePreview();
                finish(); // Return to MainActivity
            }

            @Override
            public void onDelete(Account account) {
                new AlertDialog.Builder(AccountDashboardActivity.this)
                    .setTitle("Delete Account")
                    .setMessage("Delete \"" + account.getUsername() + "\"?")
                    .setPositiveButton("Delete", (d, w) -> {
                        accountManager.deleteAccount(account.getId());
                        skinManager.deleteSkin(account.getId(), account.getModelType());
                        skinManager.deleteCape(account.getId());
                        loadAccounts();
                        updatePreview();
                    })                    .setNegativeButton("Cancel", null)
                    .show();
            }
        });
        
        accountsRecyclerView.setAdapter(adapter);
    }

    private void loadAccounts() {
        List<Account> accounts = accountManager.getAllAccounts();
        adapter.setAccounts(accounts);
        
        // Show/hide empty state
        boolean isEmpty = accounts.isEmpty();
        accountsRecyclerView.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
        if (emptyText != null) {
            emptyText.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        }
    }

    private void updatePreview() {
        Account selected = accountManager.getSelectedAccount();
        if (selected == null) return;

        // Load skin for preview
        Bitmap skin = skinManager.loadSkin(selected.getId(), selected.getModelType());
        if (skin != null) {
            characterPreview.setSkin(skin);
        } else {
            int defaultRes = skinManager.getDefaultSkinResId(selected.getModelType());
            Bitmap defaultSkin = BitmapFactory.decodeResource(getResources(), defaultRes);
            if (defaultSkin != null) {
                characterPreview.setSkin(defaultSkin);
            }
        }

        // Update model toggle buttons
        updateModelToggle(selected);
    }

    private void switchModel(String type) {
        Account selected = accountManager.getSelectedAccount();
        if (selected == null) {
            Toast.makeText(this, "Select an account first", Toast.LENGTH_SHORT).show();
            return;
        }

        // Update account model type
        Account.ModelType newType = type.equals("alex") ? Account.ModelType.ALEX : Account.ModelType.STEVE;
        selected.setModelType(newType);        accountManager.updateAccount(selected);

        // Update preview with new model JSON
        characterPreview.switchModel("models/" + type + ".json");
        
        // Update UI
        updateModelToggle(selected);
        updatePreview();
        
        Toast.makeText(this, "Switched to " + type.toUpperCase(), Toast.LENGTH_SHORT).show();
    }

    private void updateModelToggle(Account account) {
        if (account == null) return;
        boolean isAlex = account.getModelType() == Account.ModelType.ALEX;

        // Steve button style
        btnSteve.setBackgroundResource(isAlex ? android.R.color.transparent : R.drawable.menu_item_bg);
        btnSteve.setColorFilter(isAlex ? Color.GRAY : Color.WHITE);

        // Alex button style
        btnAlex.setBackgroundResource(isAlex ? R.drawable.menu_item_bg : android.R.color.transparent);
        btnAlex.setColorFilter(isAlex ? Color.WHITE : Color.GRAY);
    }

    private void showCustomizeMenu() {
        String[] options = {"🎨 Upload Skin (64x64 PNG)", "🔄 Reset to Default"};
        new AlertDialog.Builder(this)
            .setTitle("Customize Character")
            .setItems(options, (dialog, which) -> {
                if (which == 0) {
                    pickImageLauncher.launch("image/png");
                } else {
                    resetToDefault();
                }
            })
            .show();
    }

    private void handleImageResult(Uri uri) {
        if (uri == null) return;
        
        Account selected = accountManager.getSelectedAccount();
        if (selected == null) {
            Toast.makeText(this, "Select an account first", Toast.LENGTH_SHORT).show();
            return;
        }

        boolean success = skinManager.saveSkin(uri, selected.getId(), selected.getModelType());
        if (success) {            updatePreview();
            Toast.makeText(this, "✅ Skin applied!", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "❌ Invalid skin. Must be 64x64 PNG", Toast.LENGTH_LONG).show();
        }
    }

    private void resetToDefault() {
        Account selected = accountManager.getSelectedAccount();
        if (selected == null) return;

        skinManager.deleteSkin(selected.getId(), selected.getModelType());
        skinManager.deleteCape(selected.getId());
        updatePreview();
        Toast.makeText(this, "🔄 Reset to default", Toast.LENGTH_SHORT).show();
    }

    private void showCreateAccountDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_create_account, null);
        EditText inputUsername = dialogView.findViewById(R.id.inputUsername);

        AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle("Create Local Account")
            .setView(dialogView)
            .setPositiveButton("Create", null) // Set listener after show() to prevent auto-dismiss
            .setNegativeButton("Cancel", null)
            .create();

        dialog.show();

        // Override positive button to validate before dismissing
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String username = inputUsername.getText().toString().trim();
            
            if (username.length() < 3) {
                Toast.makeText(this, "Username must be 3+ characters", Toast.LENGTH_SHORT).show();
                return;
            }

            Account newAccount = new Account(username);
            if (accountManager.addAccount(newAccount)) {
                accountManager.selectAccount(newAccount.getId());
                loadAccounts();
                updatePreview();
                Toast.makeText(this, "✅ Account created!", Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            } else {
                Toast.makeText(this, "❌ Username already exists", Toast.LENGTH_SHORT).show();
            }
        });    }

    // ================= RECYCLER VIEW ADAPTER =================
    private static class AccountAdapter extends RecyclerView.Adapter<AccountAdapter.ViewHolder> {
        
        public interface ClickListener {
            void onSelect(Account account);
            void onDelete(Account account);
        }

        private final ClickListener listener;
        private List<Account> accounts = new ArrayList<>();

        public AccountAdapter(ClickListener listener) {
            this.listener = listener;
        }

        public void setAccounts(List<Account> accounts) {
            this.accounts = accounts;
            notifyDataSetChanged();
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_account_list, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            Account account = accounts.get(position);
            
            holder.textName.setText(account.getDisplayName());
            holder.textType.setText(account.getAccountTypeLabel());
            holder.textEmail.setText(account.isMicrosoft() ? account.getEmail() : "Offline Mode");
            holder.iconSelected.setVisibility(account.isSelected() ? View.VISIBLE : View.GONE);
            
            // Click to select
            holder.itemView.setOnClickListener(v -> listener.onSelect(account));
            
            // Delete button click
            holder.iconDelete.setOnClickListener(v -> listener.onDelete(account));
        }

        @Override
        public int getItemCount() {
            return accounts.size();
        }
        static class ViewHolder extends RecyclerView.ViewHolder {
            TextView textName, textType, textEmail;
            ImageView iconSelected, iconDelete;

            ViewHolder(View itemView) {
                super(itemView);
                textName = itemView.findViewById(R.id.textUsername);
                textType = itemView.findViewById(R.id.textType);
                textEmail = itemView.findViewById(R.id.textEmail);
                iconSelected = itemView.findViewById(R.id.iconSelected);
                iconDelete = itemView.findViewById(R.id.iconDelete);
            }
        }
    }
}
