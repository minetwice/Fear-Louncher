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
        setContentView(R.layout.activity_account_dashboard);
        accountManager = AccountManager.getInstance(this);
        skinManager = new SkinManager(this);
        initViews();
        setupAdapter();
        loadAccounts();
        safeUpdatePreview();    }

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
        adapter = new AccountAdapter(a -> {
            accountManager.selectAccount(a.getId());
            safeUpdatePreview();
            finish();
        }, a -> {
            new AlertDialog.Builder(this).setTitle("Delete Account")
                .setMessage("Delete \"" + a.getUsername() + "\"?")
                .setPositiveButton("Delete", (d,w) -> {
                    accountManager.deleteAccount(a.getId());
                    skinManager.deleteSkin(a.getId(), a.getModelType());
                    loadAccounts();
                    safeUpdatePreview();
                }).setNegativeButton("Cancel", null).show();
        });
        accountsRecyclerView.setAdapter(adapter);
    }

    private void loadAccounts() {
        List<Account> list = accountManager.getAllAccounts();
        adapter.setAccounts(list);
        boolean empty = list.isEmpty();
        accountsRecyclerView.setVisibility(empty ? View.GONE : View.VISIBLE);
        if (emptyText != null) emptyText.setVisibility(empty ? View.VISIBLE : View.GONE);
    }

    private void safeUpdatePreview() {
        try {
            Account sel = accountManager.getSelectedAccount();
            if (sel == null) return;
            Bitmap skin = skinManager.loadSkin(sel.getId(), sel.getModelType());
            if (skin == null) {                int def = skinManager.getDefaultSkinResId(sel.getModelType());
                skin = BitmapFactory.decodeResource(getResources(), def);
            }
            if (skin != null && characterPreview != null) characterPreview.setSkin(skin);
            updateToggleButtons(sel);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void switchModel(String type) {
        Account sel = accountManager.getSelectedAccount();
        if (sel == null) { Toast.makeText(this, "Select account first", Toast.LENGTH_SHORT).show(); return; }
        try {
            sel.setModelType(type.equals("alex") ? Account.ModelType.ALEX : Account.ModelType.STEVE);
            accountManager.updateAccount(sel);
            if (characterPreview != null) characterPreview.switchModel("models/" + type + ".json");
            safeUpdatePreview();
            Toast.makeText(this, "✅ Switched to " + type.toUpperCase(), Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "⚠️ Model switch failed. Check assets.", Toast.LENGTH_LONG).show();
            e.printStackTrace();
        }
    }

    private void updateToggleButtons(Account sel) {
        if (sel == null) return;
        boolean alex = sel.getModelType() == Account.ModelType.ALEX;
        if (btnSteve != null) {
            btnSteve.setBackgroundResource(alex ? android.R.color.transparent : R.drawable.menu_item_bg);
            btnSteve.setColorFilter(alex ? Color.GRAY : Color.WHITE);
        }
        if (btnAlex != null) {
            btnAlex.setBackgroundResource(alex ? R.drawable.menu_item_bg : android.R.color.transparent);
            btnAlex.setColorFilter(alex ? Color.WHITE : Color.GRAY);
        }
    }

    private void showCustomizeMenu() {
        new AlertDialog.Builder(this).setTitle("Customize")
            .setItems(new String[]{"🎨 Upload Skin (64x64 PNG)", "🔄 Reset Default"}, (d, w) -> {
                if (w == 0) pickImage.launch("image/png");
                else resetDefault();
            }).show();
    }

    private void handleSkinUpload(Uri uri) {
        if (uri == null) return;
        Account sel = accountManager.getSelectedAccount();
        if (sel == null) { Toast.makeText(this, "Select account", Toast.LENGTH_SHORT).show(); return; }        boolean ok = skinManager.saveSkin(uri, sel.getId(), sel.getModelType());
        if (ok) { safeUpdatePreview(); Toast.makeText(this, "✅ Skin applied", Toast.LENGTH_SHORT).show(); }
        else Toast.makeText(this, "❌ Invalid skin. Use 64x64 PNG", Toast.LENGTH_LONG).show();
    }

    private void resetDefault() {
        Account sel = accountManager.getSelectedAccount();
        if (sel == null) return;
        skinManager.deleteSkin(sel.getId(), sel.getModelType());
        safeUpdatePreview();
        Toast.makeText(this, "🔄 Reset done", Toast.LENGTH_SHORT).show();
    }

    private void showCreateDialog() {
        View v = LayoutInflater.from(this).inflate(R.layout.dialog_create_account, null);
        EditText input = v.findViewById(R.id.inputUsername);
        AlertDialog d = new AlertDialog.Builder(this).setTitle("Create Account")
            .setView(v).setPositiveButton("Create", null).setNegativeButton("Cancel", null).create();
        d.show();
        d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(btn -> {
            String name = input.getText().toString().trim();
            if (name.length() < 3) { Toast.makeText(this, "Username 3+ chars", Toast.LENGTH_SHORT).show(); return; }
            Account acc = new Account(name);
            if (accountManager.addAccount(acc)) {
                accountManager.selectAccount(acc.getId());
                loadAccounts(); safeUpdatePreview();
                d.dismiss();
                Toast.makeText(this, "✅ Account created", Toast.LENGTH_SHORT).show();
            } else Toast.makeText(this, "❌ Username exists", Toast.LENGTH_SHORT).show();
        });
    }

    private void safeClick(View v, View.OnClickListener l) { if (v != null) v.setOnClickListener(l); }

    // Adapter (Same as before, kept minimal for stability)
    private static class AccountAdapter extends RecyclerView.Adapter<AccountAdapter.VH> {
        interface L { void onSel(Account a); void onDel(Account a); }
        private final L l; private List<Account> data = new ArrayList<>();
        AccountAdapter(L l) { this.l = l; }
        void setAccounts(List<Account> a) { data = a; notifyDataSetChanged(); }
        @Override public VH onCreateViewHolder(ViewGroup p, int t) {
            return new VH(LayoutInflater.from(p.getContext()).inflate(R.layout.item_account_list, p, false));
        }
        @Override public void onBindViewHolder(VH h, int i) {
            Account a = data.get(i);
            h.name.setText(a.getDisplayName()); h.type.setText(a.getAccountTypeLabel());
            h.email.setText(a.isMicrosoft() ? a.getEmail() : "Offline");
            h.sel.setVisibility(a.isSelected() ? View.VISIBLE : View.GONE);
            h.itemView.setOnClickListener(v -> l.onSel(a));
            h.del.setOnClickListener(v -> l.onDel(a));        }
        @Override public int getItemCount() { return data.size(); }
        static class VH extends RecyclerView.ViewHolder {
            TextView name, type, email; ImageView sel, del;
            VH(View v) { super(v); name=v.findViewById(R.id.textUsername); type=v.findViewById(R.id.textType);
                email=v.findViewById(R.id.textEmail); sel=v.findViewById(R.id.iconSelected); del=v.findViewById(R.id.iconDelete); }
        }
    }
                                                                                                                                                 }
