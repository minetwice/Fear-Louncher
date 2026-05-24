package com.fearlauncher.app.service;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log; // ✅ Added Log Import
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import com.fearlauncher.app.MainActivity;
import java.io.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class JreInstallService extends Service {

    public static final String CHANNEL_ID = "FearLauncher_JRE_Channel";
    public static final int NOTIFICATION_ID = 101;
    private static final String TAG = "JreInstallService";
    
    private boolean isCancelled = false;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && "CANCEL".equals(intent.getAction())) {
            isCancelled = true;
            stopSelf();
            return START_NOT_STICKY;
        }

        startForeground(NOTIFICATION_ID, createNotification("Starting Installation...", 0, false).build());
        new Thread(() -> installJRE()).start();
        return START_STICKY;
    }
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "JRE Installation",
                NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Installing Java Runtime for Minecraft");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    private void installJRE() {
        File filesDir = getFilesDir();
        File jreDir = new File(filesDir, "jre-17");
        File javaBin = new File(jreDir, "bin/java");

        try {
            updateNotification("📦 Preparing Installer...", 5, false);
            
            // Clean up previous failed attempts
            if (jreDir.exists()) deleteRecursive(jreDir);
            
            File tempZip = new File(getCacheDir(), "jre-installer.zip");
            copyAssetToCache("components/jre-17.zip", tempZip);

            if (isCancelled) { cleanup(tempZip); return; }

            updateNotification("📂 Extracting Java Runtime...", 50, false);
            jreDir.mkdirs();
            
            unzip(tempZip, jreDir);

            if (isCancelled) { cleanup(tempZip); return; }

            updateNotification("⚙️ Fixing Permissions (Critical)...", 90, false);
            
            // Handle nested folder issue
            File firstSubDir = new File(jreDir, "jre-17");
            if (firstSubDir.exists() && firstSubDir.isDirectory()) {
                moveFiles(firstSubDir, jreDir);
                firstSubDir.delete();
            }

            if (!javaBin.exists()) {
                throw new Exception("bin/java not found after extraction.");
            }
            // ✅ CRITICAL FIX: Force Permissions via Shell Command
            // Use ProcessBuilder or Runtime.exec correctly
            Process proc = Runtime.getRuntime().exec(new String[]{"sh", "-c", "chmod -R 777 " + jreDir.getAbsolutePath()});
            proc.waitFor(); // ✅ Fixed variable name
            
            // Also set executable via Java API
            javaBin.setExecutable(true, false);
            
            // Verify if it worked
            if (!javaBin.canExecute()) {
                 Log.e(TAG, "WARNING: java bin is still not executable after chmod!"); // ✅ Fixed Log usage
                 // Try one more time with specific path
                 Runtime.getRuntime().exec(new String[]{"sh", "-c", "chmod 755 " + javaBin.getAbsolutePath()}).waitFor();
            }
            
            fixNativePermissions(new File(jreDir, "lib"));

            updateNotification("✅ Java Runtime Installed!", 100, true);
            cleanup(tempZip);
            
            Intent launchIntent = new Intent(this, MainActivity.class);
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(launchIntent);

        } catch (Exception e) {
            updateNotification("❌ Failed: " + e.getMessage(), 0, true);
            Log.e(TAG, "Installation Error", e);
        } finally {
            stopSelf();
        }
    }

    private void moveFiles(File src, File dest) throws IOException {
        File[] files = src.listFiles();
        if (files == null) return;
        for (File f : files) {
            File newFile = new File(dest, f.getName());
            if (f.isDirectory()) {
                newFile.mkdirs();
                moveFiles(f, newFile);
                f.delete();
            } else {
                renameFile(f, newFile);
            }
        }
    }

    private void renameFile(File src, File dest) throws IOException {
        if (!src.renameTo(dest)) {
            try (InputStream in = new FileInputStream(src);                 OutputStream out = new FileOutputStream(dest)) {
                byte[] buf = new byte[1024];
                int len;
                while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
            }
            src.delete();
        }
    }

    private void copyAssetToCache(String assetPath, File dest) throws IOException {
        try (InputStream in = getAssets().open(assetPath);
             FileOutputStream out = new FileOutputStream(dest)) {
            byte[] buffer = new byte[8192];
            int read;
            long total = in.available(); 
            long copied = 0;
            
            while ((read = in.read(buffer)) != -1) {
                if (isCancelled) throw new IOException("Cancelled");
                out.write(buffer, 0, read);
                copied += read;
                
                if (total > 0) {
                    int progress = 5 + (int)((copied * 45) / total); 
                    updateNotificationProgress(progress);
                }
            }
        }
    }

    private void unzip(File zipFile, File destDir) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile))) {
            ZipEntry entry;
            int fileCount = 0;
            
            while ((entry = zis.getNextEntry()) != null) {
                if (isCancelled) throw new IOException("Cancelled");
                
                File outFile = new File(destDir, entry.getName());
                if (entry.isDirectory()) {
                    outFile.mkdirs();
                } else {
                    outFile.getParentFile().mkdirs();
                    try (FileOutputStream fos = new FileOutputStream(outFile)) {
                        byte[] buffer = new byte[8192];
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            fos.write(buffer, 0, len);
                        }
                    }                }
                zis.closeEntry();
                fileCount++;
                
                if (fileCount % 50 == 0) {
                     updateNotificationProgress(50 + Math.min(40, fileCount / 10));
                }
            }
        }
    }

    private void fixNativePermissions(File libDir) {
        if (!libDir.exists()) return;
        File[] files = libDir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) fixNativePermissions(f);
            else if (f.getName().endsWith(".so")) {
                f.setExecutable(true, false);
                try {
                    Runtime.getRuntime().exec(new String[]{"sh", "-c", "chmod 755 " + f.getAbsolutePath()}).waitFor();
                } catch (Exception ignored) {}
            }
        }
    }

    private void deleteRecursive(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) for (File child : children) deleteRecursive(child);
        }
        file.delete();
    }

    private void cleanup(File tempZip) {
        if (tempZip.exists()) tempZip.delete();
    }

    private NotificationCompat.Builder createNotification(String text, int progress, boolean isDone) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("FearLauncher")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(!isDone)
            .setPriority(NotificationCompat.PRIORITY_LOW);

        if (!isDone) {
            builder.setProgress(100, progress, false);
            
            Intent cancelIntent = new Intent(this, JreInstallService.class);            cancelIntent.setAction("CANCEL");
            PendingIntent pendingCancel = PendingIntent.getService(this, 0, cancelIntent, PendingIntent.FLAG_IMMUTABLE);
            builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", pendingCancel);
        } else {
            builder.setProgress(0, 0, false);
        }
        return builder;
    }

    private void updateNotification(String text, int progress, boolean isDone) {
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) manager.notify(NOTIFICATION_ID, createNotification(text, progress, isDone).build());
    }

    private void updateNotificationProgress(int progress) {
        updateNotification("Installing JRE...", progress, false);
    }
}
