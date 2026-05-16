package com.fearlauncher.app.manager;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.Log;
import com.fearlauncher.app.R;
import com.fearlauncher.app.model.Account;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;

public class SkinManager {
    
    private static final String TAG = "SkinManager";
    private static final String DATA_DIR = "fearlauncher_data";
    private static final int MAX_FILE_SIZE = 2 * 1024 * 1024; // 2MB
    
    private final Context context;
    
    public SkinManager(Context context) {
        this.context = context.getApplicationContext();
    }
    
    // ✅ Save Skin (64x64)
    public boolean saveSkin(Uri imageUri, String accountId, Account.ModelType modelType) {
        Bitmap bmp = validateAndDecode(imageUri, 64, 64);
        if (bmp == null) return false;
        
        String filename = accountId + "_skin_" + modelType.name().toLowerCase() + ".png";
        return saveBitmapInternal(bmp, filename);
    }
    
    // ✅ Save Cape (64x32)
    public boolean saveCape(Uri imageUri, String accountId) {
        Bitmap bmp = validateAndDecode(imageUri, 64, 32);
        if (bmp == null) return false;
        
        String filename = accountId + "_cape.png";
        return saveBitmapInternal(bmp, filename);
    }
    
    // ✅ Load Skin
    public Bitmap loadSkin(String accountId, Account.ModelType modelType) {
        String filename = accountId + "_skin_" + modelType.name().toLowerCase() + ".png";
        return loadBitmapInternal(filename);
    }
    
    // ✅ Load Cape
    public Bitmap loadCape(String accountId) {
        String filename = accountId + "_cape.png";
        return loadBitmapInternal(filename);
    }
    
    // ✅ Delete Skin
    public boolean deleteSkin(String accountId, Account.ModelType modelType) {
        String filename = accountId + "_skin_" + modelType.name().toLowerCase() + ".png";
        return deleteInternal(filename);
    }
    
    // ✅ Delete Cape
    public boolean deleteCape(String accountId) {
        return deleteInternal(accountId + "_cape.png");
    }
    
    // ✅ Get Default Skin Resource
    public int getDefaultSkinResId(Account.ModelType modelType) {
        return modelType == Account.ModelType.ALEX 
            ? R.drawable.skin_alex_default 
            : R.drawable.skin_steve_default;
    }
    
    // ================= PRIVATE HELPERS =================
    
    private Bitmap validateAndDecode(Uri uri, int reqWidth, int reqHeight) {
        try {
            // 1. Check file size
            InputStream is = context.getContentResolver().openInputStream(uri);
            if (is == null) return null;
            long size = is.available();
            is.close();
            if (size > MAX_FILE_SIZE) {
                Log.w(TAG, "File too large: " + size);
                return null;
            }
            
            // 2. Check dimensions without loading full bitmap
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            is = context.getContentResolver().openInputStream(uri);
            BitmapFactory.decodeStream(is, null, opts);
            if (is != null) is.close();
            
            if (opts.outWidth != reqWidth || opts.outHeight != reqHeight) {
                Log.w(TAG, "Invalid dimensions: " + opts.outWidth + "x" + opts.outHeight);
                return null;
            }
            
            // 3. Decode actual bitmap
            opts.inJustDecodeBounds = false;
            opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
            is = context.getContentResolver().openInputStream(uri);
            Bitmap bmp = BitmapFactory.decodeStream(is, null, opts);
            if (is != null) is.close();
            
            return bmp;
        } catch (Exception e) {
            Log.e(TAG, "Decode error: " + e.getMessage());
            return null;
        }
    }
    
    private boolean saveBitmapInternal(Bitmap bmp, String filename) {
        try {
            File dir = new File(context.getFilesDir(), DATA_DIR);
            if (!dir.exists()) dir.mkdirs();
            
            File file = new File(dir, filename);
            try (FileOutputStream fos = new FileOutputStream(file)) {
                bmp.compress(Bitmap.CompressFormat.PNG, 100, fos);
            }
            bmp.recycle(); // Free memory
            Log.i(TAG, "Saved: " + file.getAbsolutePath());
            return true;
        } catch (IOException e) {
            Log.e(TAG, "Save error: " + e.getMessage());
            return false;
        }
    }
    
    private Bitmap loadBitmapInternal(String filename) {
        try {
            File dir = new File(context.getFilesDir(), DATA_DIR);
            File file = new File(dir, filename);
            if (file.exists()) {
                return BitmapFactory.decodeFile(file.getAbsolutePath());
            }
        } catch (Exception e) {
            Log.e(TAG, "Load error: " + e.getMessage());
        }
        return null;
    }
    
    private boolean deleteInternal(String filename) {
        try {
            File dir = new File(context.getFilesDir(), DATA_DIR);
            File file = new File(dir, filename);
            if (file.exists()) return file.delete();
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Delete error: " + e.getMessage());
            return false;
        }
    }
}
