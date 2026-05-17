package com.fearlauncher.app;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.*;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
// ✅ FIXED: Added missing import
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import com.fearlauncher.app.manager.AccountManager;
import com.fearlauncher.app.manager.SkinManager;
import com.fearlauncher.app.model.Account;
import com.fearlauncher.app.view.CharacterPreviewView;
import java.util.ArrayList;
import java.util.List;

public class AccountDashboardActivity extends AppCompatActivity {

    private CharacterPreviewView characterPreview;
    private RecyclerView accountsRecyclerView;
    private TextView emptyText;
    private ImageButton btnSteve, btnAlex;
    private ImageView btnMenuOptions;
    
    // ✅ Now using imported class correctly
    private FloatingActionButton btnAddAccount; 
    
    private AccountManager accountManager;
    private SkinManager skinManager;
    private AccountAdapter adapter;

    private final ActivityResultLauncher<String> pickImage = 
        registerForActivityResult(new ActivityResultContracts.GetContent(), this::handleSkinUpload);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_account_dashboard);
        
        accountManager = AccountManager.getInstance(this);        skinManager = new SkinManager(this);
        
        initViews();
        setupAdapter();
        loadAccounts();
        safeUpdatePreview();
    }

    private void initViews() {
        characterPreview = findViewById(R.id.characterPreview);
        accountsRecyclerView = findViewById(R.id.accountsRecyclerView);
        emptyText = findViewById(R.id.emptyText);
        btnSteve = findViewById(R.id.btnSteve);
        btnAlex = findViewById(R.id.btnAlex);
        btnMenuOptions = findViewById(R.id.btnMenuOptions);
        btnAddAccount = findViewById(R.id.btnAddAccount);

        if (btnSteve != null) btnSteve.setOnClickListener(v -> switchModel("steve"));
        if (btnAlex != null) btnAlex.setOnClickListener(v -> switchModel("alex"));
        if (btnMenuOptions != null) btnMenuOptions.setOnClickListener(v -> showCustomizeMenu());
        if (btnAddAccount != null) btnAddAccount.setOnClickListener(v -> showCreateDialog());
    }

    private void setupAdapter() {
        accountsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new AccountAdapter(new AccountAdapter.ClickListener() {
            @Override
            public void onSelect(Account account) {
                accountManager.selectAccount(account.getId());
                safeUpdatePreview();
                finish();
            }
            @Override
            public void onDelete(Account account) {
                accountManager.deleteAccount(account.getId());
                loadAccounts();
                safeUpdatePreview();
            }
        });
        accountsRecyclerView.setAdapter(adapter);
    }

    private void loadAccounts() {
        List<Account> list = accountManager.getAllAccounts();
        if (adapter != null) adapter.setAccounts(list);
        boolean isEmpty = (list == null || list.isEmpty());
        if (accountsRecyclerView != null) accountsRecyclerView.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
        if (emptyText != null) emptyText.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
    }
    private void safeUpdatePreview() {
        try {
            Account selected = accountManager.getSelectedAccount();
            if (selected == null || characterPreview == null) return;

            Bitmap skin = skinManager.loadSkin(selected.getId(), selected.getModelType());
            if (skin == null) {
                int defaultRes = skinManager.getDefaultSkinResId(selected.getModelType());
                skin = BitmapFactory.decodeResource(getResources(), defaultRes);
            }
            if (skin != null) characterPreview.setSkin(skin);
            updateToggleButtons(selected);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void switchModel(String type) {
        Account selected = accountManager.getSelectedAccount();
        if (selected == null) return;
        try {
            Account.ModelType newType = "alex".equals(type) ? Account.ModelType.ALEX : Account.ModelType.STEVE;
            selected.setModelType(newType);
            accountManager.updateAccount(selected);
            if (characterPreview != null) characterPreview.switchModel("models/" + type + ".json");
            safeUpdatePreview();
            Toast.makeText(this, "Switched: " + type, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void updateToggleButtons(Account selected) {
        if (selected == null) return;
        boolean isAlex = selected.getModelType() == Account.ModelType.ALEX;
        if (btnSteve != null) {
            btnSteve.setBackgroundResource(isAlex ? android.R.color.transparent : R.drawable.menu_item_bg);
        }
        if (btnAlex != null) {
            btnAlex.setBackgroundResource(isAlex ? R.drawable.menu_item_bg : android.R.color.transparent);
        }
    }

    private void showCustomizeMenu() {
        new AlertDialog.Builder(this)
            .setTitle("Customize")
            .setItems(new String[]{"Upload Skin", "Reset"}, (dialog, which) -> {
                if (which == 0) pickImage.launch("image/png");
                else resetDefault();
            }).show();    }

    private void handleSkinUpload(Uri uri) {
        if (uri == null) return;
        Account selected = accountManager.getSelectedAccount();
        if (selected == null) return;
        boolean ok = skinManager.saveSkin(uri, selected.getId(), selected.getModelType());
        if (ok) { safeUpdatePreview(); Toast.makeText(this, "Skin applied", Toast.LENGTH_SHORT).show(); }
        else Toast.makeText(this, "Invalid skin", Toast.LENGTH_LONG).show();
    }

    private void resetDefault() {
        Account selected = accountManager.getSelectedAccount();
        if (selected == null) return;
        skinManager.deleteSkin(selected.getId(), selected.getModelType());
        safeUpdatePreview();
        Toast.makeText(this, "Reset done", Toast.LENGTH_SHORT).show();
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
            if (name.length() < 3) { Toast.makeText(this, "3+ chars", Toast.LENGTH_SHORT).show(); return; }
            Account acc = new Account(name);
            if (accountManager.addAccount(acc)) {
                accountManager.selectAccount(acc.getId());
                loadAccounts();
                safeUpdatePreview();
                dialog.dismiss();
                Toast.makeText(this, "Created!", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Exists", Toast.LENGTH_SHORT).show();
            }
        });
    }

    // ===== ADAPTER =====
    private static class AccountAdapter extends RecyclerView.Adapter<AccountAdapter.VH> {
        interface ClickListener { void onSelect(Account a); void onDelete(Account a); }
        private final ClickListener listener;
        private List<Account> data = new ArrayList<>();        
        AccountAdapter(ClickListener l) { listener = l; }
        void setAccounts(List<Account> a) { data = a; notifyDataSetChanged(); }
        
        @Override public VH onCreateViewHolder(ViewGroup p, int t) {
            return new VH(LayoutInflater.from(p.getContext()).inflate(R.layout.item_account_list, p, false));
        }
        @Override public void onBindViewHolder(VH h, int i) {
            Account a = data.get(i);
            h.name.setText(a.getDisplayName());
            h.type.setText(a.getAccountTypeLabel());
            h.email.setText(a.isMicrosoft() ? a.getEmail() : "Offline");
            h.itemView.setOnClickListener(v -> listener.onSelect(a));
            h.del.setOnClickListener(v -> listener.onDelete(a));
        }
        @Override public int getItemCount() { return data.size(); }
        
        static class VH extends RecyclerView.ViewHolder {
            TextView name, type, email;
            ImageView del;
            VH(View v) {
                super(v);
                name = v.findViewById(R.id.textUsername);
                type = v.findViewById(R.id.textType);
                email = v.findViewById(R.id.textEmail);
                del = v.findViewById(R.id.iconDelete);
            }
        }
    }
}
