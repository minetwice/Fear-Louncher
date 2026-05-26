package com.fearlauncher.app.service;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
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
    private File jreDir;
    private File javaBin;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        this.jreDir = new File("/data/local/tmp/fearlauncher_jre");
        this.javaBin = new File(this.jreDir, "jre/bin/java");
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
        startForeground(NOTIFICATION_ID, createNotification("Preparing JRE...", 0, false).build());
        new Thread(this::installJRE).start();
        return START_STICKY;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "JRE Installation",
                NotificationManager.IMPORTANCE_LOW
            );
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }

    private void installJRE() {
        try {
            updateNotification("🔍 Checking JRE...", 5, false);

            // Check if JRE already exists and is valid
            if (jreDir.exists() && javaBin.exists() && javaBin.canExecute()) {
                Log.d(TAG, "✅ JRE already installed at: " + javaBin.getAbsolutePath());
                updateNotification("✅ JRE Ready!", 100, true);
                startMainActivity();
                return;
            }

            // Clean up old JRE if it exists
            if (jreDir.exists()) {
                deleteRecursive(jreDir);
            }

            // Create directory
            if (!jreDir.mkdirs()) {
                throw new IOException("Failed to create JRE directory");
            }

            // Copy JRE from assets
            File tempZip = new File(getCacheDir(), "jre-installer.zip");
            copyAssetToCache("components/jre-17.zip", tempZip);

            if (isCancelled) {
                cleanup(tempZip);
                return;
            }

            updateNotification("📂 Extracting JRE...", 50, false);
            unzip(tempZip, jreDir);

            if (isCancelled) {
                cleanup(tempZip);
                return;
            }

            // Verify java binary exists
            if (!javaBin.exists()) {
                throw new FileNotFoundException("jre/bin/java not found in extracted JRE");
            }

            // Set executable permissions
            updateNotification("⚙️ Setting Permissions...", 90, false);
            setExecutableRecursive(new File(jreDir, "jre"));

            // Verify permissions
            if (!javaBin.canExecute()) {
                try {
                    // Try chmod as fallback
                    Process chmodProcess = Runtime.getRuntime().exec(
                        new String[]{"chmod", "755", javaBin.getAbsolutePath()}
                    );
                    chmodProcess.waitFor();
                } catch (Exception e) {
                    Log.w(TAG, "chmod failed, trying setExecutable", e);
                }

                // Final attempt
                javaBin.setExecutable(true, false);
            }

            if (!javaBin.canExecute()) {
                throw new SecurityException("Failed to set executable permission for java binary");
            }

            Log.d(TAG, "✅ JRE installed successfully at: " + javaBin.getAbsolutePath());
            updateNotification("✅ JRE Ready!", 100, true);
            cleanup(tempZip);
            startMainActivity();

        } catch (Exception e) {
            Log.e(TAG, "JRE installation failed", e);
            updateNotification("❌ JRE Install Failed: " + e.getMessage(), 0, true);
        } finally {
            stopSelf();
        }
    }

    private void startMainActivity() {
        Intent i = new Intent(this, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(i);
    }

    private void setExecutableRecursive(File file) {
        if (file.isDirectory()) {
            File[] files = file.listFiles();
            if (files != null) {
                for (File child : files) {
                    setExecutableRecursive(child);
                }
            }
        }
        file.setExecutable(true, false);
        file.setReadable(true, false);
        file.setWritable(false, false);
    }

    private void copyAssetToCache(String assetPath, File dest) throws IOException {
        try (InputStream in = getAssets().open(assetPath);
             FileOutputStream out = new FileOutputStream(dest)) {
            byte[] buffer = new byte[8192];
            int length;
            long total = in.available();
            long copied = 0;

            while ((length = in.read(buffer)) != -1) {
                if (isCancelled) throw new IOException("Installation cancelled");
                out.write(buffer, 0, length);
                copied += length;

                if (total > 0) {
                    int progress = (int) ((copied * 45) / total);
                    updateNotificationProgress(5 + progress);
                }
            }
        }
    }

    private void unzip(File zipFile, File destDir) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (isCancelled) throw new IOException("Installation cancelled");

                File outputFile = new File(destDir, entry.getName());

                if (entry.isDirectory()) {
                    outputFile.mkdirs();
                } else {
                    outputFile.getParentFile().mkdirs();
                    try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                        byte[] buffer = new byte[8192];
                        int length;
                        while ((length = zis.read(buffer)) > 0) {
                            fos.write(buffer, 0, length);
                        }
                    }
                }
                zis.closeEntry();
            }
        }
    }

    private void deleteRecursive(File file) {
        if (file.isDirectory()) {
            File[] files = file.listFiles();
            if (files != null) {
                for (File child : files) {
                    deleteRecursive(child);
                }
            }
        }
        file.delete();
    }

    private void cleanup(File file) {
        if (file != null && file.exists()) {
            file.delete();
        }
    }

    private NotificationCompat.Builder createNotification(String title, int progress, boolean done) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("FearLauncher")
            .setContentText(title)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(!done);

        if (!done) {
            builder.setProgress(100, progress, false);

            Intent cancelIntent = new Intent(this, JreInstallService.class);
            cancelIntent.setAction("CANCEL");
            PendingIntent cancelPendingIntent = PendingIntent.getService(
                this, 0, cancelIntent, PendingIntent.FLAG_IMMUTABLE
            );

            builder.addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Cancel",
                cancelPendingIntent
            );
        }
        return builder;
    }

    private void updateNotification(String text, int progress, boolean done) {
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.notify(NOTIFICATION_ID, createNotification(text, progress, done).build());
    }

    private void updateNotificationProgress(int progress) {
        updateNotification("Installing JRE...", progress, false);
    }
}
