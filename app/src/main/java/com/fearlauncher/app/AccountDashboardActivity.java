package com.fearlauncher.app;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.fearlauncher.app.manager.AccountManager;
import com.fearlauncher.app.manager.SkinManager;
import com.fearlauncher.app.model.Account;
import com.fearlauncher.app.view.CharacterPreviewView;
import java.util.List;

public class AccountDashboardActivity extends AppCompatActivity {

    private CharacterPreviewView characterPreview;
    private AccountManager accountManager;
    private SkinManager skinManager;
    private RecyclerView accountsRecyclerView;
    private AccountAdapter adapter;
    private TextView emptyText;
    private Button btnAddAccount, btnSteve, btnAlex;
    private ImageView btnMenuOptions;

    private static final int REQ_SKIN = 101;
    private static final int REQ_CAPE = 102;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_account_dashboard);

        accountManager = AccountManager.getInstance(this);
        skinManager = new SkinManager(this);
        
        initViews();
        setupRecyclerView();
        loadAccounts();
        updatePreview();
    }

    private void initViews() {
        characterPreview = findViewById(R.id.characterPreview);
        accountsRecyclerView = findViewById(R.id.accountsRecyclerView);
        emptyText = findViewById(R.id.emptyText);
        btnAddAccount = findViewById(R.id.btnAddAccount);
        btnSteve = findViewById(R.id.btnSteve);
        btnAlex = findViewById(R.id.btnAlex);
        btnMenuOptions = findViewById(R.id.btnMenuOptions);

        btnAddAccount.setOnClickListener(v -> showAddAccountDialog());
        btnSteve.setOnClickListener(v -> switchModel("steve"));
        btnAlex.setOnClickListener(v -> switchModel("alex"));
        
        // 3-Line Menu -> Open Skin/Cape Panel
        btnMenuOptions.setOnClickListener(v -> showSkinCapeMenu());
    }

    private void setupRecyclerView() {
        accountsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new AccountAdapter(account -> {
            accountManager.selectAccount(account.getId());
            updatePreview();
            finish(); // Return to main
        }, account -> {
            new AlertDialog.Builder(this)
                .setTitle("Delete Account")
                .setMessage("Delete " + account.getUsername() + "?")
                .setPositiveButton("Delete", (d, w) -> {
                    accountManager.deleteAccount(account.getId());
                    loadAccounts();
                    updatePreview();
                })
                .setNegativeButton("Cancel", null)
                .show();
        });
        accountsRecyclerView.setAdapter(adapter);
    }

    private void loadAccounts() {
        List<Account> accs = accountManager.getAllAccounts();
        adapter.setAccounts(accs);
        accountsRecyclerView.setVisibility(accs.isEmpty() ? View.GONE : View.VISIBLE);
        if (emptyText != null) emptyText.setVisibility(accs.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void updatePreview() {
        Account sel = accountManager.getSelectedAccount();
        if (sel == null) return;
        
        Bitmap skin = skinManager.loadSkin(sel.getId(), sel.getModelType());
        if (skin != null) {
            characterPreview.setSkin(skin);
        } else {
            int defRes = skinManager.getDefaultSkinResId(sel.getModelType());
            characterPreview.setSkin(BitmapFactory.decodeResource(getResources(), defRes));
        }
        
        // Update model buttons
        boolean isAlex = sel.getModelType() == Account.ModelType.ALEX;
        btnSteve.setTextColor(isAlex ? getColor(R.color.text_secondary) : getColor(R.color.primary));
        btnSteve.setBackgroundResource(isAlex ? android.R.color.transparent : R.drawable.menu_item_bg);
        btnAlex.setTextColor(isAlex ? getColor(R.color.primary) : getColor(R.color.text_secondary));
        btnAlex.setBackgroundResource(isAlex ? R.drawable.menu_item_bg : android.R.color.transparent);
    }

    private void switchModel(String type) {
        Account sel = accountManager.getSelectedAccount();
        if (sel == null) return;
        
        Account.ModelType mType = type.equals("alex") ? Account.ModelType.ALEX : Account.ModelType.STEVE;
        sel.setModelType(mType);
        accountManager.updateAccount(sel);
        
        characterPreview.switchModel("models/" + type + ".json");
        updatePreview();
        Toast.makeText(this, "Switched to " + type.toUpperCase(), Toast.LENGTH_SHORT).show();
    }

    // ✅ 3-Line Menu: Skin & Cape Upload
    private void showSkinCapeMenu() {
        String[] options = {"Upload Skin (64x64 PNG)", "Upload Cape (64x32 PNG)", "Reset to Default"};
        new AlertDialog.Builder(this)
            .setTitle("Customize Character")
            .setItems(options, (dialog, which) -> {
                if (which == 0) pickImage(REQ_SKIN);
                else if (which == 1) pickImage(REQ_CAPE);
                else resetToDefault();
            })
            .show();
    }

    private void pickImage(int requestCode) {
        Intent i = new Intent(Intent.ACTION_PICK);
        i.setType("image/png");
        startActivityForResult(i, requestCode);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK && data != null) {
            Account sel = accountManager.getSelectedAccount();
            if (sel == null) return;

            if (requestCode == REQ_SKIN) {
                boolean ok = skinManager.saveSkin(data.getData(), sel.getId(), sel.getModelType());
                if (ok) {
                    updatePreview();
                    Toast.makeText(this, "Skin applied!", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "Invalid skin. Use 64x64 PNG", Toast.LENGTH_SHORT).show();
                }
            } else if (requestCode == REQ_CAPE) {
                boolean ok = skinManager.saveCape(data.getData(), sel.getId());
                if (ok) Toast.makeText(this, "Cape applied!", Toast.LENGTH_SHORT).show();
                else Toast.makeText(this, "Invalid cape. Use 64x32 PNG", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void resetToDefault() {
        Account sel = accountManager.getSelectedAccount();
        if (sel == null) return;
        skinManager.deleteSkin(sel.getId(), sel.getModelType());
        skinManager.deleteCape(sel.getId());
        updatePreview();
        Toast.makeText(this, "Reset to default", Toast.LENGTH_SHORT).show();
    }

    private void showAddAccountDialog() {
        // Same as before... (Keep your existing dialog code)
        Toast.makeText(this, "Add Account Dialog", Toast.LENGTH_SHORT).show();
    }

    // Adapter class (keep your existing AccountAdapter)
    private static class AccountAdapter extends RecyclerView.Adapter<AccountAdapter.VH> {
        // ... (your existing adapter code)
    }
}
