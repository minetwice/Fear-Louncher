package com.fearlauncher.app;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
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
    private AccountManager accountManager;
    private SkinManager skinManager;
    private RecyclerView accountsRecyclerView;
    private AccountAdapter adapter;
    private ImageButton btnSteve, btnAlex;
    private ImageView btnMenuOptions;
    private Button btnAddAccount;

    private final ActivityResultLauncher<String> pickSkinLauncher = 
        registerForActivityResult(new ActivityResultContracts.GetContent(), this::handleSkinResult);
    private final ActivityResultLauncher<String> pickCapeLauncher = 
        registerForActivityResult(new ActivityResultContracts.GetContent(), this::handleCapeResult);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_account_dashboard);

        accountManager = AccountManager.getInstance(this);
        skinManager = new SkinManager(this);
        
        initViews();
        setupRecyclerView();        loadAccounts();
        updatePreview();
    }

    private void initViews() {
        characterPreview = findViewById(R.id.characterPreview);
        accountsRecyclerView = findViewById(R.id.accountsRecyclerView);
        btnSteve = findViewById(R.id.btnSteve);
        btnAlex = findViewById(R.id.btnAlex);
        btnMenuOptions = findViewById(R.id.btnMenuOptions);
        btnAddAccount = findViewById(R.id.btnAddAccount);

        btnAddAccount.setOnClickListener(v -> showCreateAccountDialog());
        btnSteve.setOnClickListener(v -> switchModel("steve"));
        btnAlex.setOnClickListener(v -> switchModel("alex"));
        btnMenuOptions.setOnClickListener(v -> showSkinCapeMenu());
    }

    private void setupRecyclerView() {
        accountsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new AccountAdapter(new AccountAdapter.ClickListener() {
            @Override public void onSelect(Account account) {
                accountManager.selectAccount(account.getId());
                updatePreview();
                finish();
            }
            @Override public void onDelete(Account account) {
                new AlertDialog.Builder(AccountDashboardActivity.this)
                    .setTitle("Delete Account")
                    .setMessage("Delete \"" + account.getUsername() + "\"?")
                    .setPositiveButton("Delete", (d, w) -> {
                        accountManager.deleteAccount(account.getId());
                        loadAccounts();
                        updatePreview();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
            }
        });
        accountsRecyclerView.setAdapter(adapter);
    }

    private void loadAccounts() {
        List<Account> accs = accountManager.getAllAccounts();
        adapter.setAccounts(accs);
        accountsRecyclerView.setVisibility(accs.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void updatePreview() {
        Account sel = accountManager.getSelectedAccount();        if (sel == null) return;
        
        Bitmap skin = skinManager.loadSkin(sel.getId(), sel.getModelType());
        if (skin != null) {
            characterPreview.setSkin(skin);
        } else {
            int defRes = skinManager.getDefaultSkinResId(sel.getModelType());
            Bitmap defSkin = BitmapFactory.decodeResource(getResources(), defRes);
            if (defSkin != null) characterPreview.setSkin(defSkin);
        }
        
        boolean isAlex = sel.getModelType() == Account.ModelType.ALEX;
        btnSteve.setBackgroundResource(isAlex ? android.R.color.transparent : R.drawable.menu_item_bg);
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

    private void showSkinCapeMenu() {
        String[] options = {"🎨 Upload Skin (64x64 PNG)", "🧥 Upload Cape (64x32 PNG)", "🔄 Reset"};
        new AlertDialog.Builder(this)
            .setTitle("Customize Character")
            .setItems(options, (dialog, which) -> {
                if (which == 0) pickSkinLauncher.launch("image/png");
                else if (which == 1) pickCapeLauncher.launch("image/png");
                else resetToDefault();
            })
            .show();
    }

    private void handleSkinResult(Uri uri) {
        if (uri == null) return;
        Account sel = accountManager.getSelectedAccount();
        if (sel == null) return;

        boolean ok = skinManager.saveSkin(uri, sel.getId(), sel.getModelType());
        if (ok) {
            updatePreview();
            Toast.makeText(this, "✅ Skin applied!", Toast.LENGTH_SHORT).show();        } else {
            Toast.makeText(this, "❌ Invalid skin. Use 64x64 PNG", Toast.LENGTH_LONG).show();
        }
    }

    private void handleCapeResult(Uri uri) {
        if (uri == null) return;
        Account sel = accountManager.getSelectedAccount();
        if (sel == null) return;

        boolean ok = skinManager.saveCape(uri, sel.getId());
        if (ok) Toast.makeText(this, "✅ Cape applied!", Toast.LENGTH_SHORT).show();
        else Toast.makeText(this, "❌ Invalid cape. Use 64x32 PNG", Toast.LENGTH_LONG).show();
    }

    private void resetToDefault() {
        Account sel = accountManager.getSelectedAccount();
        if (sel == null) return;
        skinManager.deleteSkin(sel.getId(), sel.getModelType());
        skinManager.deleteCape(sel.getId());
        updatePreview();
        Toast.makeText(this, "🔄 Reset to default", Toast.LENGTH_SHORT).show();
    }

    // ✅ FULLY WORKING ACCOUNT CREATION DIALOG
    private void showCreateAccountDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_create_account, null);
        EditText inputUsername = dialogView.findViewById(R.id.inputUsername);
        
        new AlertDialog.Builder(this)
            .setTitle("Create Local Account")
            .setView(dialogView)
            .setPositiveButton("Create", null) // Set listener after to prevent auto-dismiss
            .setNegativeButton("Cancel", null)
            .show()
            .getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String username = inputUsername.getText().toString().trim();
                if (username.length() < 3) {
                    Toast.makeText(this, "Username must be 3+ characters", Toast.LENGTH_SHORT).show();
                    return;
                }
                Account newAcc = new Account(username);
                if (accountManager.addAccount(newAcc)) {
                    accountManager.selectAccount(newAcc.getId());
                    loadAccounts();
                    updatePreview();
                    Toast.makeText(this, "✅ Account created!", Toast.LENGTH_SHORT).show();
                    // Auto-dismiss dialog
                    ((AlertDialog) ((View) v.getParent()).getParent()).dismiss();
                } else {                    Toast.makeText(this, "❌ Username already exists", Toast.LENGTH_SHORT).show();
                }
            });
    }

    // Minimal Adapter
    private static class AccountAdapter extends RecyclerView.Adapter<AccountAdapter.VH> {
        public interface ClickListener { void onSelect(Account a); void onDelete(Account a); }
        private final ClickListener listener;
        private List<Account> accounts = new ArrayList<>();

        AccountAdapter(ClickListener l) { listener = l; }
        void setAccounts(List<Account> a) { accounts = a; notifyDataSetChanged(); }

        @Override public VH onCreateViewHolder(ViewGroup p, int t) {
            return new VH(LayoutInflater.from(p.getContext()).inflate(R.layout.item_account_list, p, false));
        }

        @Override public void onBindViewHolder(VH h, int i) {
            Account a = accounts.get(i);
            h
