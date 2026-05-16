package com.fearlauncher.app;

import android.app.Dialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import com.fearlauncher.app.auth.MicrosoftAuth;
import com.fearlauncher.app.manager.AccountManager;
import com.fearlauncher.app.manager.SkinManager;
import com.fearlauncher.app.model.Account;
import com.fearlauncher.app.model.Account.ModelType;
import com.fearlauncher.app.view.CharacterPreviewView;

public class MainActivity extends AppCompatActivity {

    // Menu Views
    private LinearLayout menuHome, menuPlay, menuInstall, menuMods, menuSettings, menuAccount;
    
    // Managers
    private AccountManager accountManager;
    private MicrosoftAuth microsoftAuth;
    private SkinManager skinManager;
    
    // Dialog
    private Dialog accountDialog;

    // Character Preview Views
    private TextView textUsername, textCharacterName, textSkinInfo;
    private CharacterPreviewView characterPreview;
    private Button btnModelSteve, btnModelAlex, btnUploadSkin;
    private static final int REQUEST_PICK_SKIN = 1001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        setupClickListeners();
        selectMenuItem(menuHome);
        updateUIForSelectedAccount();
    }

    private void initViews() {
        menuHome = findViewById(R.id.menuHome);
        menuPlay = findViewById(R.id.menuPlay);
        menuInstall = findViewById(R.id.menuInstall);
        menuMods = findViewById(R.id.menuMods);
        menuSettings = findViewById(R.id.menuSettings);
        menuAccount = findViewById(R.id.menuAccount);

        // Character Preview UI
        textUsername = findViewById(R.id.textUsername);
        textCharacterName = findViewById(R.id.textCharacterName);
        characterPreview = findViewById(R.id.characterPreview);
        btnModelSteve = findViewById(R.id.btnModelSteve);
        btnModelAlex = findViewById(R.id.btnModelAlex);
        btnUploadSkin = findViewById(R.id.btnUploadSkin);
        textSkinInfo = findViewById(R.id.textSkinInfo);

        accountManager = AccountManager.getInstance(this);
        microsoftAuth = new MicrosoftAuth();
        skinManager = new SkinManager(this);
    }

    private void setupClickListeners() {
        View.OnClickListener menuClick = v -> {
            resetAllMenus();
            selectMenuItem((LinearLayout) v);
            handleMenuAction(v.getId());
        };

        if (menuHome != null) menuHome.setOnClickListener(menuClick);
        if (menuPlay != null) menuPlay.setOnClickListener(menuClick);
        if (menuInstall != null) menuInstall.setOnClickListener(menuClick);
        if (menuMods != null) menuMods.setOnClickListener(menuClick);
        if (menuSettings != null) menuSettings.setOnClickListener(menuClick);
        if (menuAccount != null) menuAccount.setOnClickListener(v -> openAccountDashboard());

        View playNowBtn = findViewById(R.id.btnPlayNow);
        if (playNowBtn != null) playNowBtn.setOnClickListener(v -> launchMinecraft());

        // ✅ Model Toggles
        btnModelSteve.setOnClickListener(v -> setModelType(ModelType.STEVE));
        btnModelAlex.setOnClickListener(v -> setModelType(ModelType.ALEX));

        // ✅ Skin Upload
        btnUploadSkin.setOnClickListener(v -> pickSkinImage());
    }

    private void selectMenuItem(LinearLayout selected) {
        if (selected == null) return;
        selected.setBackgroundResource(R.drawable.menu_item_bg);
        selected.setSelected(true);
        updateMenuColors(selected, true);
    }

    private void resetAllMenus() {
        LinearLayout[] menus = {menuHome, menuPlay, menuInstall, menuMods, menuSettings};
        for (LinearLayout menu : menus) {
            if (menu != null) {
                menu.setBackgroundResource(android.R.color.transparent);
                menu.setSelected(false);
                updateMenuColors(menu, false);
            }
        }
    }

    private void updateMenuColors(LinearLayout menu, boolean isSelected) {
        int color = isSelected ?
                ContextCompat.getColor(this, R.color.primary) :
                ContextCompat.getColor(this, R.color.text_secondary);

        for (int i = 0; i < menu.getChildCount(); i++) {
            View child = menu.getChildAt(i);
            if (child instanceof TextView) ((TextView) child).setTextColor(color);
            else if (child instanceof ImageView) ((ImageView) child).setColorFilter(color);
        }
    }

    private void handleMenuAction(int viewId) {
        String name;
        if (viewId == R.id.menuHome) name = getString(R.string.menu_home);
        else if (viewId == R.id.menuPlay) name = getString(R.string.menu_play);
        else if (viewId == R.id.menuInstall) name = getString(R.string.menu_install);
        else if (viewId == R.id.menuMods) name = getString(R.string.menu_mods);
        else if (viewId == R.id.menuSettings) name = getString(R.string.menu_settings);
        else name = "";
        if (!name.isEmpty()) showToast(name + " clicked");
    }

    private void openAccountDashboard() {
        Intent intent = new Intent(this, AccountDashboardActivity.class);
        startActivity(intent);
    }

    private void launchMinecraft() {
        Account selected = accountManager.getSelectedAccount();
        if (selected == null) {
            showToast(getString(R.string.select_account_first));
            openAccountDashboard();
            return;
        }
        selected.setLastUsed(System.currentTimeMillis());
        accountManager.updateAccount(selected);
        showToast(getString(R.string.play_with_account, selected.getUsername()));
        showToast(getString(R.string.launching));
    }

    // ✅ Update UI based on selected account
    private void updateUIForSelectedAccount() {
        Account selected = accountManager.getSelectedAccount();
        String displayName = selected != null ? selected.getDisplayName() : "FearUser";
        
        if (textUsername != null) textUsername.setText(displayName);
        if (textCharacterName != null) textCharacterName.setText(displayName);
        
        if (characterPreview != null && selected != null) {
            loadCharacterPreview(selected);
            updateModelToggle(selected);
        }
    }

    private void loadCharacterPreview(Account account) {
        Bitmap skin = skinManager.loadSkin(account.getId(), account.getModelType());
        if (skin != null) {
            characterPreview.setSkin(skin, account.getModelType());
            textSkinInfo.setText("Custom skin loaded ✓");
        } else {
            int defaultRes = skinManager.getDefaultSkinResId(account.getModelType());
            Bitmap defaultSkin = BitmapFactory.decodeResource(getResources(), defaultRes);
            characterPreview.setSkin(defaultSkin, account.getModelType());
            textSkinInfo.setText("Using default " + account.getModelLabel());
        }
    }

    private void setModelType(ModelType type) {
        Account selected = accountManager.getSelectedAccount();
        if (selected == null) return;
        
        selected.setModelType(type);
        accountManager.updateAccount(selected);
        updateModelToggle(selected);
        loadCharacterPreview(selected);
        showToast("Model changed: " + selected.getModelLabel());
    }

    private void updateModelToggle(Account account) {
        if (account == null) return;
        boolean isAlex = account.getModelType() == ModelType.ALEX;
        
        btnModelSteve.setTextColor(isAlex ? getColor(R.color.text_secondary) : getColor(R.color.primary));
        btnModelSteve.setBackgroundResource(isAlex ? android.R.color.transparent : R.drawable.menu_item_bg);
        
        btnModelAlex.setTextColor(isAlex ? getColor(R.color.primary) : getColor(R.color.text_secondary));
        btnModelAlex.setBackgroundResource(isAlex ? R.drawable.menu_item_bg : android.R.color.transparent);
    }

    private void pickSkinImage() {
        Account selected = accountManager.getSelectedAccount();
        if (selected == null) {
            showToast("Please select an account first");
            return;
        }
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType("image/png");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"image/png"});
        startActivityForResult(intent, REQUEST_PICK_SKIN);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_PICK_SKIN && resultCode == RESULT_OK && data != null) {
            Account selected = accountManager.getSelectedAccount();
            if (selected != null) {
                boolean success = skinManager.saveSkin(data.getData(), selected.getId(), selected.getModelType());
                if (success) {
                    loadCharacterPreview(selected);
                    showToast("Skin uploaded successfully!");
                } else {
                    showToast("Invalid skin. Must be 64x64 PNG");
                }
            }
        }
    }

    private void showQuickAccountDialog() {
        if (accountDialog != null && accountDialog.isShowing()) return;
        accountDialog = new Dialog(this);
        accountDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        accountDialog.setContentView(R.layout.dialog_account_form);
        accountDialog.setCancelable(true);
        Window window = accountDialog.getWindow();
        if (window != null) {
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);
            window.setGravity(Gravity.BOTTOM);
            window.setWindowAnimations(R.style.SlideUpAnimation);
        }
        setupAccountDialogViews(accountDialog);
        accountDialog.show();
    }

    private void setupAccountDialogViews(Dialog dialog) {
        dialog.findViewById(R.id.btnClose).setOnClickListener(v -> dialog.dismiss());
        Button tabLocal = dialog.findViewById(R.id.tabLocal);
        Button tabMicrosoft = dialog.findViewById(R.id.tabMicrosoft);
        LinearLayout formLocal = dialog.findViewById(R.id.formLocal);
        LinearLayout formMicrosoft = dialog.findViewById(R.id.formMicrosoft);

        tabLocal.setOnClickListener(v -> {
            tabLocal.setTextColor(getColor(R.color.primary));
            tabLocal.setBackgroundResource(R.drawable.menu_item_bg);
            tabMicrosoft.setTextColor(getColor(R.color.text_secondary));
            tabMicrosoft.setBackgroundResource(android.R.color.transparent);
            formLocal.setVisibility(View.VISIBLE);
            formMicrosoft.setVisibility(View.GONE);
        });

        tabMicrosoft.setOnClickListener(v -> {
            tabMicrosoft.setTextColor(getColor(R.color.primary));
            tabMicrosoft.setBackgroundResource(R.drawable.menu_item_bg);
            tabLocal.setTextColor(getColor(R.color.text_secondary));
            tabLocal.setBackgroundResource(android.R.color.transparent);
            formMicrosoft.setVisibility(View.VISIBLE);
            formLocal.setVisibility(View.GONE);
        });

        EditText inputUsername = dialog.findViewById(R.id.inputUsername);
        dialog.findViewById(R.id.btnCreateLocal).setOnClickListener(v -> {
            String username = inputUsername.getText().toString().trim();
            if (username.isEmpty()) { showError(dialog, getString(R.string.username_required)); return; }
            if (username.length() < 3) { showError(dialog, getString(R.string.username_min_length)); return; }

            Account newAccount = new Account(username);
            if (accountManager.addAccount(newAccount)) {
                accountManager.selectAccount(newAccount.getId());
                showToast(getString(R.string.account_created, username));
                dialog.dismiss();
                updateUIForSelectedAccount();
            } else {
                showError(dialog, getString(R.string.account_exists));
            }
        });

        dialog.findViewById(R.id.btnSignInMicrosoft).setOnClickListener(v -> {
            dialog.findViewById(R.id.progressLoading).setVisibility(View.VISIBLE);
            dialog.findViewById(R.id.btnSignInMicrosoft).setEnabled(false);
            microsoftAuth.startLogin(this);
        });
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        Uri data = intent.getData();
        if (data != null && "auth".equals(data.getHost()) && "/microsoft".equals(data.getPath())) {
            microsoftAuth.handleRedirect(data, new MicrosoftAuth.AuthCallback() {
                @Override public void onSuccess(Account account) {
                    runOnUiThread(() -> {
                        if (account != null && accountManager.addAccount(account)) {
                            accountManager.selectAccount(account.getId());
                            showToast(getString(R.string.microsoft_success));
                            updateUIForSelectedAccount();
                        }
                        if (accountDialog != null) accountDialog.dismiss();
                    });
                }
                @Override public void onError(String error) {
                    runOnUiThread(() -> {
                        if (accountDialog != null) {
                            showError(accountDialog, getString(R.string.microsoft_failed, error));
                            accountDialog.findViewById(R.id.progressLoading).setVisibility(View.GONE);
                            accountDialog.findViewById(R.id.btnSignInMicrosoft).setEnabled(true);
                        }
                    });
                }
            });
        }
    }

    private void showError(Dialog dialog, String message) {
        TextView textError = dialog.findViewById(R.id.textError);
        if (textError != null) {
            textError.setText(message);
            textError.setVisibility(View.VISIBLE);
            textError.postDelayed(() -> textError.setVisibility(View.GONE), 5000);
        }
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateUIForSelectedAccount();
    }
}
