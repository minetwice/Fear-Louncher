package com.fearlauncher.app;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
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

    private static final String TAG = "AccountDashboard";

    private CharacterPreviewView characterPreview;
    private RecyclerView accountsRecyclerView;
    private TextView emptyText;
    private ImageButton btnSteve, btnAlex;
    private ImageView btnMenuOptions;
    private Button btnAddAccount;
    
    private AccountManager accountManager;
    private SkinManager skinManager;
    private AccountAdapter adapter;

    private final ActivityResultLauncher<String> pickImage = 
        registerForActivityResult(new ActivityResultContracts.GetContent(), this::handleSkinUpload);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            setContentView(R.layout.activity_account_dashboard);
            accountManager = AccountManager.getInstance(this);            skinManager = new SkinManager(this);
            initViews();
            setupAdapter();
            loadAccounts();
            safeUpdatePreview();
        } catch (Exception e) {
            Log.e(TAG, "Crash on start: ", e);
            Toast.makeText(this, "⚠️ Failed to load dashboard", Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private void initViews() {
        characterPreview = findViewById(R.id.characterPreview);
        accountsRecyclerView = findViewById(R.id.accountsRecyclerView);
        emptyText = findViewById(R.id.emptyText);
        btnSteve = findViewById(R.id.btnSteve);
        btnAlex = findViewById(R.id.btnAlex);
        btnMenuOptions = findViewById(R.id.btnMenuOptions);
        btnAddAccount = findViewById(R.id.btnAddAccount);

        safeClick(btnSteve, v -> switchModel("steve"));
        safeClick(btnAlex, v -> switchModel("alex"));
        safeClick(btnMenuOptions, v -> showCustomizeMenu());
        safeClick(btnAddAccount, v -> showCreateDialog());
    }

    private void setupAdapter() {
        accountsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new AccountAdapter(new AccountAdapter.ClickListener() {
            @Override public void onSelect(Account account) {
                accountManager.selectAccount(account.getId());
                safeUpdatePreview();
                finish();
            }
            @Override public void onDelete(Account account) {
                new AlertDialog.Builder(AccountDashboardActivity.this)
                    .setTitle("Delete Account")
                    .setMessage("Delete \"" + account.getUsername() + "\"?")
                    .setPositiveButton("Delete", (d, w) -> {
                        accountManager.deleteAccount(account.getId());
                        skinManager.deleteSkin(account.getId(), account.getModelType());
                        loadAccounts();
                        safeUpdatePreview();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
            }
        });
        accountsRecyclerView.setAdapter(adapter);    }

    private void loadAccounts() {
        List<Account> list = accountManager.getAllAccounts();
        adapter.setAccounts(list);
        boolean isEmpty = list.isEmpty();
        accountsRecyclerView.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
        if (emptyText != null) emptyText.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
    }

    // ✅ PNG CRASH FIX: Safe image loading with fallback
    private void safeUpdatePreview() {
        try {
            Account selected = accountManager.getSelectedAccount();
            if (selected == null) return;

            Bitmap skin = skinManager.loadSkin(selected.getId(), selected.getModelType());
            
            // If no custom skin, load default safely
            if (skin == null) {
                try {
                    int defaultRes = skinManager.getDefaultSkinResId(selected.getModelType());
                    skin = BitmapFactory.decodeResource(getResources(), defaultRes);
                } catch (Exception e) {
                    Log.e(TAG, "Default skin load failed", e);
                }
            }

            if (skin != null && characterPreview != null) {
                characterPreview.setSkin(skin);
            }
            updateToggleButtons(selected);
        } catch (Exception e) {
            Log.e(TAG, "Preview update failed", e);
        }
    }

    private void switchModel(String type) {
        Account selected = accountManager.getSelectedAccount();
        if (selected == null) {
            Toast.makeText(this, "Select account first", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            Account.ModelType newType = type.equals("alex") ? Account.ModelType.ALEX : Account.ModelType.STEVE;
            selected.setModelType(newType);
            accountManager.updateAccount(selected);

            if (characterPreview != null) {
                characterPreview.switchModel("models/" + type + ".json");            }
            safeUpdatePreview();
            Toast.makeText(this, "✅ Switched to " + type.toUpperCase(), Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.e(TAG, "Model switch failed", e);
            Toast.makeText(this, "⚠️ Check assets/models/ JSON files", Toast.LENGTH_LONG).show();
        }
    }

    private void updateToggleButtons(Account selected) {
        if (selected == null) return;
        boolean isAlex = selected.getModelType() == Account.ModelType.ALEX;
        
        if (btnSteve != null) {
            btnSteve.setBackgroundResource(isAlex ? android.R.color.transparent : R.drawable.menu_item_bg);
            btnSteve.setColorFilter(isAlex ? Color.GRAY : Color.WHITE);
        }
        if (btnAlex != null) {
            btnAlex.setBackgroundResource(isAlex ? R.drawable.menu_item_bg : android.R.color.transparent);
            btnAlex.setColorFilter(isAlex ? Color.WHITE : Color.GRAY);
        }
    }

    private void showCustomizeMenu() {
        new AlertDialog.Builder(this)
            .setTitle("Customize")
            .setItems(new String[]{"🎨 Upload Skin (64x64 PNG)", "🔄 Reset Default"}, (dialog, which) -> {
                if (which == 0) pickImage.launch("image/png");
                else resetDefault();
            }).show();
    }

    private void handleSkinUpload(Uri uri) {
        if (uri == null) return;
        Account selected = accountManager.getSelectedAccount();
        if (selected == null) {
            Toast.makeText(this, "Select account first", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            boolean success = skinManager.saveSkin(uri, selected.getId(), selected.getModelType());
            if (success) {
                safeUpdatePreview();
                Toast.makeText(this, "✅ Skin applied!", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "❌ Invalid skin. Use 64x64 PNG", Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            Log.e(TAG, "Skin upload crash", e);
            Toast.makeText(this, "⚠️ Upload failed. File may be corrupt.", Toast.LENGTH_LONG).show();        }
    }

    private void resetDefault() {
        Account selected = accountManager.getSelectedAccount();
        if (selected == null) return;
        skinManager.deleteSkin(selected.getId(), selected.getModelType());
        safeUpdatePreview();
        Toast.makeText(this, "🔄 Reset to default", Toast.LENGTH_SHORT).show();
    }

    private void showCreateDialog() {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_create_account, null);
        EditText input = view.findViewById(R.id.inputUsername);
        
        AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle("Create Account")
            .setView(view)
            .setPositiveButton("Create", null)
            .setNegativeButton("Cancel", null)
            .create();
            
        dialog.show();
        
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String name = input.getText().toString().trim();
            if (name.length() < 3) {
                Toast.makeText(this, "Username 3+ chars", Toast.LENGTH_SHORT).show();
                return;
            }
            Account acc = new Account(name);
            if (accountManager.addAccount(acc)) {
                accountManager.selectAccount(acc.getId());
                loadAccounts();
                safeUpdatePreview();
                dialog.dismiss();
                Toast.makeText(this, "✅ Account created", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "❌ Username exists", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void safeClick(View v, View.OnClickListener listener) {
        if (v != null) v.setOnClickListener(listener);
    }

    // ================= ADAPTER =================
    private static class AccountAdapter extends RecyclerView.Adapter<AccountAdapter.VH> {
        public interface ClickListener {            void onSelect(Account account);
            void onDelete(Account account);
        }
        private final ClickListener listener;
        private List<Account> data = new ArrayList<>();

        public AccountAdapter(ClickListener listener) { this.listener = listener; }
        public void setAccounts(List<Account> accounts) { data = accounts; notifyDataSetChanged(); }

        @Override public VH onCreateViewHolder(ViewGroup p, int t) {
            return new VH(LayoutInflater.from(p.getContext()).inflate(R.layout.item_account_list, p, false));
        }

        @Override public void onBindViewHolder(VH h, int i) {
            Account a = data.get(i);
            h.name.setText(a.getDisplayName());
            h.type.setText(a.getAccountTypeLabel());
            h.email.setText(a.isMicrosoft() ? a.getEmail() : "Offline");
            h.sel.setVisibility(a.isSelected() ? View.VISIBLE : View.GONE);
            h.itemView.setOnClickListener(v -> listener.onSelect(a));
            h.del.setOnClickListener(v -> listener.onDelete(a));
        }

        @Override public int getItemCount() { return data.size(); }

        static class VH extends RecyclerView.ViewHolder {
            TextView name, type, email;
            ImageView sel, del;
            VH(View v) {
                super(v);
                name = v.findViewById(R.id.textUsername);
                type = v.findViewById(R.id.textType);
                email = v.findViewById(R.id.textEmail);
                sel = v.findViewById(R.id.iconSelected);
                del = v.findViewById(R.id.iconDelete);
            }
        }
    }
                }
