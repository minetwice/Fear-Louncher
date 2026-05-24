package com.fearlauncher.app.service;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import com.fearlauncher.app.MainActivity;
import java.io.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class JreInstallService extends Service {
    public static final String CHANNEL_ID = "FearLauncher_JRE_Channel";
    public static final int NOTIFICATION_ID = 101;
    private static final String TAG = "JreService";
    private boolean isCancelled = false;

    @Override
    public void onCreate() { super.onCreate(); createNotificationChannel(); }
    @Nullable @Override public IBinder onBind(Intent intent) { return null; }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && "CANCEL".equals(intent.getAction())) {
            isCancelled = true; stopSelf(); return START_NOT_STICKY;
        }
        startForeground(NOTIFICATION_ID, createNotification("Starting...", 0, false).build());
        new Thread(() -> installJRE()).start();
        return START_STICKY;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "JRE Install", NotificationManager.IMPORTANCE_LOW);
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }

    private void installJRE() {
        // ✅ USE GETFILES DIR WHICH IS PRIVATE AND EXECUTABLE
        File filesDir = getFilesDir();
        File jreDir = new File(filesDir, "jre-17");
        File javaBin = new File(jreDir, "bin/java");
        try {
            updateNotification("📦 Preparing...", 5, false);
            
            // Clean previous
            if (jreDir.exists()) deleteRecursive(jreDir);
            
            File tempZip = new File(getCacheDir(), "jre-installer.zip");
            copyAssetToCache("components/jre-17.zip", tempZip);
            if (isCancelled) { cleanup(tempZip); return; }

            updateNotification("📂 Extracting...", 50, false);
            jreDir.mkdirs();
            unzip(tempZip, jreDir);
            if (isCancelled) { cleanup(tempZip); return; }

            // ✅ FIX NESTED FOLDERS (Common Issue)
            File nested = new File(jreDir, "jre-17");
            if (nested.exists() && nested.isDirectory()) {
                Log.d(TAG, "Fixing nested folder structure...");
                moveFiles(nested, jreDir);
                nested.delete();
            }

            if (!javaBin.exists()) {
                throw new Exception("Critical Error: bin/java not found. ZIP might be corrupted.");
            }

            // ✅ THE MAGIC FIX FOR PERMISSION DENIED (Error 13)
            updateNotification("⚙️ Fixing Permissions...", 90, false);
            
            // 1. Use Shell Command with Quoted Path to handle spaces/special chars
            String chmodCmd = "chmod -R 755 '" + jreDir.getAbsolutePath() + "'";
            Log.d(TAG, "Running: " + chmodCmd);
            
            Process p = Runtime.getRuntime().exec(new String[]{"sh", "-c", chmodCmd});
            int exitCode = p.waitFor();
            
            if (exitCode != 0) {
                Log.e(TAG, "Chmod failed with code: " + exitCode);
                // Try alternative chmod without quotes if path has no spaces
                if (!jreDir.getAbsolutePath().contains(" ")) {
                     Runtime.getRuntime().exec("chmod -R 755 " + jreDir.getAbsolutePath()).waitFor();
                }
            }

            // 2. Force Java API Permission
            boolean execSet = javaBin.setExecutable(true, false);
            Log.d(TAG, "Java setExecutable result: " + execSet);
            // 3. Verify Execution Capability
            if (!javaBin.canExecute()) {
                Log.e(TAG, "WARNING: File still not executable! Trying last resort...");
                // Last resort: Copy to cache dir which is always executable
                File cacheJre = new File(getCacheDir(), "jre-17-exec");
                if (cacheJre.exists()) deleteRecursive(cacheJre);
                
                // Move entire JRE to Cache Dir (More permissive on some Android versions)
                moveFiles(jreDir, cacheJre);
                jreDir.delete();
                
                // Update paths to point to Cache Dir
                jreDir = cacheJre;
                javaBin = new File(jreDir, "bin/java");
                
                // Re-apply chmod on Cache Dir
                Runtime.getRuntime().exec(new String[]{"sh", "-c", "chmod -R 755 '" + jreDir.getAbsolutePath() + "'"}).waitFor();
                javaBin.setExecutable(true, false);
            }
            
            Log.d(TAG, "Final Path: " + javaBin.getAbsolutePath());
            Log.d(TAG, "Can Execute: " + javaBin.canExecute());

            updateNotification("✅ Installed!", 100, true);
            cleanup(tempZip);
            
            Intent i = new Intent(this, MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(i);

        } catch (Exception e) {
            Log.e(TAG, "Installation Failed", e);
            updateNotification("❌ Failed: " + e.getMessage(), 0, true);
        } finally { 
            stopSelf(); 
        }
    }

    private void copyAssetToCache(String path, File dest) throws IOException {
        try (InputStream in = getAssets().open(path); FileOutputStream out = new FileOutputStream(dest)) {
            byte[] b = new byte[8192]; int r; long total = in.available(), copied = 0;
            while ((r = in.read(b)) != -1) {
                if (isCancelled) throw new IOException("Cancelled");
                out.write(b, 0, r); copied += r;
                if (total > 0) updateNotificationProgress(5 + (int)((copied * 45) / total));
            }
        }
    }

    private void unzip(File zip, File dir) throws IOException {        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zip))) {
            ZipEntry e;
            while ((e = zis.getNextEntry()) != null) {
                if (isCancelled) throw new IOException("Cancelled");
                File f = new File(dir, e.getName());
                if (e.isDirectory()) f.mkdirs();
                else {
                    f.getParentFile().mkdirs();
                    try (FileOutputStream fos = new FileOutputStream(f)) {
                        byte[] b = new byte[8192]; int l;
                        while ((l = zis.read(b)) > 0) fos.write(b, 0, l);
                    }
                }
                zis.closeEntry();
            }
        }
    }

    private void moveFiles(File src, File dest) throws IOException {
        if (!src.exists()) return;
        File[] files = src.listFiles();
        if (files == null) return;
        
        for (File f : files) {
            File nf = new File(dest, f.getName());
            if (f.isDirectory()) { 
                nf.mkdirs(); 
                moveFiles(f, nf); 
                f.delete(); 
            } else { 
                // Use copy-delete for cross-partition moves
                try (InputStream in = new FileInputStream(f); OutputStream out = new FileOutputStream(nf)) {
                    byte[] buf = new byte[1024]; int len;
                    while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
                }
                f.delete(); 
            }
        }
    }

    private void deleteRecursive(File f) {
        if (f.isDirectory()) {
            File[] children = f.listFiles();
            if (children != null) for (File c : children) deleteRecursive(c);
        }
        f.delete();
    }
    
    private void cleanup(File f) { if (f.exists()) f.delete(); }
    private NotificationCompat.Builder createNotification(String t, int p, boolean done) {
        NotificationCompat.Builder b = new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("FearLauncher").setContentText(t)
            .setSmallIcon(android.R.drawable.stat_sys_download).setOngoing(!done);
        if (!done) {
            b.setProgress(100, p, false);
            Intent ci = new Intent(this, JreInstallService.class); ci.setAction("CANCEL");
            b.addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", 
                PendingIntent.getService(this, 0, ci, PendingIntent.FLAG_IMMUTABLE));
        }
        return b;
    }

    private void updateNotification(String t, int p, boolean d) {
        getSystemService(NotificationManager.class).notify(NOTIFICATION_ID, createNotification(t, p, d).build());
    }
    private void updateNotificationProgress(int p) { updateNotification("Installing...", p, false); }
}
