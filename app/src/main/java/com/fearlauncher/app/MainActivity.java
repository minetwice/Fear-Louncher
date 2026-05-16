package com.fearlauncher.app;

import android.app.Dialog;
import android.content.Intent;
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

// ✅ Correct Imports matching your package structure
import com.fearlauncher.app.auth.MicrosoftAuth;
import com.fearlauncher.app.manager.AccountManager;
import com.fearlauncher.app.model.Account;

public class MainActivity extends AppCompatActivity {

    private LinearLayout menuHome, menuPlay, menuInstall, menuMods, menuSettings, menuAccount;
    private AccountManager accountManager;
    private MicrosoftAuth microsoftAuth;
    private Dialog accountDialog;

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
        
        // Make sure activity_main.xml mein ye ID hai: android:id="@+id/menuAccount"
        menuAccount = findViewById(R.id.menuAccount);

        accountManager = AccountManager.getInstance(this);
        microsoftAuth = new MicrosoftAuth();
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
        if (menuAccount != null) menuAccount.setOnClickListener(v -> showAccountDialog());

        View playNowBtn = findViewById(R.id.btnPlayNow);
        if (playNowBtn != null) {
            playNowBtn.setOnClickListener(v -> {
                Account selected = accountManager.getSelectedAccount();
                if (selected == null) {
                    showToast("Please create or select an account first!");
                    showAccountDialog();
                } else {
                    showToast("Launching Minecraft with: " + selected.getUsername());
                }
            });
        }
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
            if (child instanceof TextView) {
                ((TextView) child).setTextColor(color);
            } else if (child instanceof ImageView) {
                ((ImageView) child).setColorFilter(color);
            }
        }
    }

    private void handleMenuAction(int viewId) {
        String name;
        if (viewId == R.id.menuHome) name = "Home";
        else if (viewId == R.id.menuPlay) name = "Play";
        else if (viewId == R.id.menuInstall) name = "Installations";
        else if (viewId == R.id.menuMods) name = "Mods";
        else if (viewId == R.id.menuSettings) name = "Settings";
        else name = "";
        
        if (!name.isEmpty()) showToast(name + " clicked");
    }

    private void showAccountDialog() {
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
            if (username.isEmpty()) {
                showError(dialog, "Please enter a username");
                return;
            }
            if (username.length() < 3) {
                showError(dialog, "Username must be at least 3 characters");
                return;
            }

            Account newAccount = new Account(username);
            if (accountManager.addAccount(newAccount)) {
                accountManager.selectAccount(newAccount.getId());
                showToast("Account created: " + username);
                dialog.dismiss();
                updateUIForSelectedAccount();
            } else {
                showError(dialog, "Username already taken");
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
                @Override
                public void onSuccess(Account account) {
                    runOnUiThread(() -> {
                        if (account != null && accountManager.addAccount(account)) {
                            accountManager.selectAccount(account.getId());
                            showToast("Microsoft account linked!");
                            updateUIForSelectedAccount();
                        }
                        if (accountDialog != null) accountDialog.dismiss();
                    });
                }

                @Override
                public void onError(String error) {
                    runOnUiThread(() -> {
                        if (accountDialog != null) {
                            showError(accountDialog, "Login failed: " + error);
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

    private void updateUIForSelectedAccount() {
        Account selected = accountManager.getSelectedAccount();
        // Optional: Update UI with selected account name
    }
}
