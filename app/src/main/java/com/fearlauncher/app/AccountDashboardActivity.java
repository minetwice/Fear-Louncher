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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_account_dashboard);

        accountManager = AccountManager.getInstance(this);
        skinManager = new SkinManager(this);
        
        initViews();
        setupRecyclerView();
        loadAccounts();
        updatePreview();    }

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
        adapter = new AccountAdapter(account -> {
            accountManager.selectAccount(account.getId());
            updatePreview();
            finish();
        }, account -> {
            accountManager.deleteAccount(account.getId());
            loadAccounts();
            updatePreview();
        });
        accountsRecyclerView.setAdapter(adapter);
    }

    private void loadAccounts() {
        List<Account> accs = accountManager.getAllAccounts();
        adapter.setAccounts(accs);
        accountsRecyclerView.setVisibility(accs.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void updatePreview() {
        Account sel = accountManager.getSelectedAccount();
        if (sel == null) return;
        
        Bitmap skin = skinManager.loadSkin(sel.getId(), sel.getModelType());
        if (skin != null) {
            characterPreview.setSkin(skin);
        } else {
            int defRes = skinManager.getDefaultSkinResId(sel.getModelType());
            Bitmap defSkin = BitmapFactory.decodeResource(getResources(), defRes);
            if (defSkin != null) characterPreview.setSkin(defSkin);
        }
        
        boolean isAlex = sel.getModelType() == Account.ModelType.ALEX;        btnSteve.setBackgroundResource(isAlex ? android.R.color.transparent : R.drawable.menu_item_bg);
        btnAlex.setBackgroundResource(isAlex ? R.drawable.menu_item_bg : android.R.color.transparent);
    }

    private void switchModel(String type) {
        Account sel = accountManager.getSelectedAccount();
        if (sel == null) return;
        sel.setModelType(type.equals("alex") ? Account.ModelType.ALEX : Account.ModelType.STEVE);
        accountManager.updateAccount(sel);
        characterPreview.switchModel("models/" + type + ".json");
        updatePreview();
        Toast.makeText(this, "Switched to " + type, Toast.LENGTH_SHORT).show();
    }

    private void showSkinCapeMenu() {
        new AlertDialog.Builder(this)
            .setTitle("Customize")
            .setItems(new String[]{"Upload Skin (64x64 PNG)", "Reset"}, (d, which) -> {
                if (which == 0) pickSkinLauncher.launch("image/png");
                else {
                    Account sel = accountManager.getSelectedAccount();
                    if (sel != null) {
                        skinManager.deleteSkin(sel.getId(), sel.getModelType());
                        updatePreview();
                        Toast.makeText(this, "Reset done", Toast.LENGTH_SHORT).show();
                    }
                }
            }).show();
    }

    private void handleSkinResult(Uri uri) {
        if (uri == null) return;
        Account sel = accountManager.getSelectedAccount();
        if (sel == null) return;
        if (skinManager.saveSkin(uri, sel.getId(), sel.getModelType())) {
            updatePreview();
            Toast.makeText(this, "Skin applied!", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Invalid skin. Use 64x64 PNG", Toast.LENGTH_LONG).show();
        }
    }

    private void showCreateAccountDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_create_account, null);
        EditText inputUsername = dialogView.findViewById(R.id.inputUsername);
        
        AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle("Create Account")
            .setView(dialogView)
            .setPositiveButton("Create", null)            .setNegativeButton("Cancel", null)
            .create();
        
        dialog.show();
        
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String username = inputUsername.getText().toString().trim();
            if (username.length() < 3) {
                Toast.makeText(this, "Username 3+ chars", Toast.LENGTH_SHORT).show();
                return;
            }
            Account newAcc = new Account(username);
            if (accountManager.addAccount(newAcc)) {
                accountManager.selectAccount(newAcc.getId());
                loadAccounts();
                updatePreview();
                Toast.makeText(this, "Account created!", Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            } else {
                Toast.makeText(this, "Username exists", Toast.LENGTH_SHORT).show();
            }
        });
    }

    // Minimal Adapter
    private static class AccountAdapter extends RecyclerView.Adapter<AccountAdapter.VH> {
        interface Listener { void onSelect(Account a); void onDelete(Account a); }
        private final Listener listener;
        private List<Account> accounts = new ArrayList<>();

        AccountAdapter(Listener l) { listener = l; }
        void setAccounts(List<Account> a) { accounts = a; notifyDataSetChanged(); }

        @Override public VH onCreateViewHolder(ViewGroup p, int t) {
            View v = LayoutInflater.from(p.getContext()).inflate(R.layout.item_account_list, p, false);
            return new VH(v);
        }

        @Override public void onBindViewHolder(VH h, int i) {
            Account a = accounts.get(i);
            h.textName.setText(a.getDisplayName());
            h.textType.setText(a.getAccountTypeLabel());
            h.textEmail.setText(a.isMicrosoft() ? a.getEmail() : "Offline");
            h.iconSelected.setVisibility(a.isSelected() ? View.VISIBLE : View.GONE);
            h.itemView.setOnClickListener(v -> listener.onSelect(a));
            h.iconDelete.setOnClickListener(v -> listener.onDelete(a));
        }

        @Override public int getItemCount() { return accounts.size(); }
        static class VH extends RecyclerView.ViewHolder {
            TextView textName, textType, textEmail;
            ImageView iconSelected, iconDelete;
            VH(View v) {
                super(v);
                textName = v.findViewById(R.id.textUsername);
                textType = v.findViewById(R.id.textType);
                textEmail = v.findViewById(R.id.textEmail);
                iconSelected = v.findViewById(R.id.iconSelected);
                iconDelete = v.findViewById(R.id.iconDelete);
            }
        }
    }
}
