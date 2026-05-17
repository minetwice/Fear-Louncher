package com.fearlauncher.app;

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
    private Button btnAddAccount, btnSteve, btnAlex;
    private ImageView btnMenuOptions;

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
        setupRecyclerView();
        loadAccounts();
        updatePreview();    }

    private void initViews() {
        characterPreview = findViewById(R.id.characterPreview);
        accountsRecyclerView = findViewById(R.id.accountsRecyclerView);
        btnAddAccount = findViewById(R.id.btnAddAccount);
        btnSteve = findViewById(R.id.btnSteve);
        btnAlex = findViewById(R.id.btnAlex);
        btnMenuOptions = findViewById(R.id.btnMenuOptions);

        btnAddAccount.setOnClickListener(v -> showAddAccountDialog());
        btnSteve.setOnClickListener(v -> switchModel("steve"));
        btnAlex.setOnClickListener(v -> switchModel("alex"));
        btnMenuOptions.setOnClickListener(v -> showSkinCapeMenu());
    }

    private void setupRecyclerView() {
        accountsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        // ✅ FIXED: Proper adapter construction with ClickListener interface
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
            updatePreview();            Toast.makeText(this, "✅ Skin applied!", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "❌ Invalid skin. Use 64x64 PNG", Toast.LENGTH_LONG).show();
        }
    }

    private void handleCapeResult(Uri uri) {
        if (uri == null) return;
        Account sel = accountManager.getSelectedAccount();
        if (sel == null) return;

        boolean ok = skinManager.saveCape(uri, sel.getId());
        if (ok) {
            Toast.makeText(this, "✅ Cape applied!", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "❌ Invalid cape. Use 64x32 PNG", Toast.LENGTH_LONG).show();
        }
    }

    private void resetToDefault() {
        Account sel = accountManager.getSelectedAccount();
        if (sel == null) return;
        skinManager.deleteSkin(sel.getId(), sel.getModelType());
        skinManager.deleteCape(sel.getId());
        updatePreview();
        Toast.makeText(this, "🔄 Reset to default", Toast.LENGTH_SHORT).show();
    }

    private void showAddAccountDialog() {
        Toast.makeText(this, "Add Account (Implement dialog here)", Toast.LENGTH_SHORT).show();
    }

    // ================= ADAPTER =================
    private static class AccountAdapter extends RecyclerView.Adapter<AccountAdapter.VH> {
        public interface ClickListener {
            void onSelect(Account account);
            void onDelete(Account account);
        }
        private final ClickListener listener;
        private List<Account> accounts = new ArrayList<>();

        AccountAdapter(ClickListener listener) { 
            this.listener = listener; 
        }

        void setAccounts(List<Account> accounts) {
            this.accounts = accounts;
            notifyDataSetChanged();
        }
        @Override public VH onCreateViewHolder(ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_account_list, parent, false);
            return new VH(v);
        }

        @Override public void onBindViewHolder(VH holder, int position) {
            Account acc = accounts.get(position);
            holder.textName.setText(acc.getDisplayName());
            holder.textType.setText(acc.getAccountTypeLabel());
            holder.textEmail.setText(acc.isMicrosoft() ? acc.getEmail() : "Offline Mode");
            holder.iconSelected.setVisibility(acc.isSelected() ? View.VISIBLE : View.GONE);
            
            holder.itemView.setOnClickListener(v -> listener.onSelect(acc));
            holder.iconDelete.setOnClickListener(v -> listener.onDelete(acc));
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
