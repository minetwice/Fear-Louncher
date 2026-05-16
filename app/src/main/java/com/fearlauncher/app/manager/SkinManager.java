package com.fearlauncher.app.manager;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.Log;

// ✅ FIXED: Add this import for Account class
import com.fearlauncher.app.model.Account;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;

public class SkinManager {
    
    private static final String TAG = "SkinManager";
    private static final String SKINS_DIR = "minecraft_skins";
    private static final int MAX_SKIN_SIZE = 64; // 64x64 pixels
    private static final int MAX_FILE_SIZE = 256 * 1024; // 256 KB
    
    private final Context context;
    
    public SkinManager(Context context) {
        this.context = context.getApplicationContext();
    }
    
    // ✅ Validate and save uploaded skin
    public boolean saveSkin(Uri imageUri, String accountId, Account.ModelType modelType) {
        try {
            // 1. Validate file size
            if (!isFileSizeValid(imageUri)) {
                Log.e(TAG, "Skin file too large");
                return false;
            }
            
            // 2. Decode and validate dimensions
            Bitmap skin = decodeAndValidateSkin(imageUri);
            if (skin == null) {
                Log.e(TAG, "Invalid skin dimensions or format");
                return false;
            }
            
            // 3. Create skins directory
            File skinsDir = new File(context.getFilesDir(), SKINS_DIR);
            if (!skinsDir.exists()) skinsDir.mkdirs();
            
            // 4. Save skin with account-specific name
            String filename = accountId + "_" + modelType.name().toLowerCase() + ".png";
            File skinFile = new File(skinsDir, filename);
            
            try (FileOutputStream fos = new FileOutputStream(skinFile)) {
                skin.compress(Bitmap.CompressFormat.PNG, 100, fos);
            }
            
            Log.i(TAG, "Skin saved: " + skinFile.getAbsolutePath());
            return true;
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to save skin: " + e.getMessage());
            return false;
        }
    }
    
    // ✅ Load skin bitmap for preview
    public Bitmap loadSkin(String accountId, Account.ModelType modelType) {
        try {
            File skinsDir = new File(context.getFilesDir(), SKINS_DIR);
            String filename = accountId + "_" + modelType.name().toLowerCase() + ".png";
            File skinFile = new File(skinsDir, filename);
            
            if (skinFile.exists()) {
                return BitmapFactory.decodeFile(skinFile.getAbsolutePath());
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to load skin: " + e.getMessage());
        }
        return null;
    }
    
    // ✅ Delete skin
    public boolean deleteSkin(String accountId, Account.ModelType modelType) {
        try {
            File skinsDir = new File(context.getFilesDir(), SKINS_DIR);
            String filename = accountId + "_" + modelType.name().toLowerCase() + ".png";
            File skinFile = new File(skinsDir, filename);
            
            if (skinFile.exists()) {
                return skinFile.delete();
            }
            return true; // Already doesn't exist
        } catch (Exception e) {
            Log.e(TAG, "Failed to delete skin: " + e.getMessage());
            return false;
        }
    }
    
    // ✅ Get default skin resource ID based on model type
    public int getDefaultSkinResId(Account.ModelType modelType) {
        return modelType == Account.ModelType.ALEX 
            ? com.fearlauncher.app.R.drawable.skin_alex_default 
            : com.fearlauncher.app.R.drawable.skin_steve_default;
    }
    
    // 🔍 Private helpers
    private boolean isFileSizeValid(Uri uri) {
        try {
            InputStream is = context.getContentResolver().openInputStream(uri);
            if (is == null) return false;
            
            long size = is.available();
            is.close();
            return size <= MAX_FILE_SIZE;
        } catch (IOException e) {
            return false;
        }
    }
    
    private Bitmap decodeAndValidateSkin(Uri uri) {
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            
            InputStream is = context.getContentResolver().openInputStream(uri);
            if (is == null) return null;
            
            BitmapFactory.decodeStream(is, null, options);
            is.close();
            
            // Validate dimensions: must be 64x64 or 64x32 (legacy)
            if (options.outWidth != MAX_SKIN_SIZE || 
                (options.outHeight != MAX_SKIN_SIZE && options.outHeight != 32)) {
                return null;
            }
            
            // Decode actual bitmap
            options.inJustDecodeBounds = false;
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            
            is = context.getContentResolver().openInputStream(uri);
            Bitmap bitmap = BitmapFactory.decodeStream(is, null, options);
            if (is != null) is.close();
            
            return bitmap;
        } catch (Exception e) {
            Log.e(TAG, "Decode error: " + e.getMessage());
            return null;
        }
    }
    
    // ✅ Convert skin to base64 for storage (optional)
    public String skinToBase64(Bitmap skin) {
        // Implementation optional - use for cloud sync
        return null;
    }
}
