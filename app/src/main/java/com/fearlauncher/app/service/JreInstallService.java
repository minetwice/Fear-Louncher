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
        startForeground(NOTIFICATION_ID, createNotification("Starting...", 0, false).build());
        new Thread(() -> installJRE()).start();
        return START_STICKY;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "JRE Install", NotificationManager.IMPORTANCE_LOW
            );
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }

    private void installJRE() {
        try {
            updateNotification("🔍 Checking JRE...", 5, false);

            if (jreDir.exists() && javaBin.exists() && javaBin.canExecute()) {
                Log.d(TAG, "✅ JRE already installed");
                updateNotification("✅ JRE already installed!", 100, true);
                Intent i = new Intent(this, MainActivity.class);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(i);
                stopSelf();
                return;
            }

            if (jreDir.exists()) {
                deleteRecursive(jreDir);
            }
            jreDir.mkdirs();

            File tempZip = new File(getCacheDir(), "jre-installer.zip");
            copyAssetToCache("components/jre-17.zip", tempZip);

            if (isCancelled) {
                cleanup(tempZip);
                return;
            }

            updateNotification("📂 Extracting...", 50, false);
            unzip(tempZip, jreDir);

            if (isCancelled) {
                cleanup(tempZip);
                return;
            }

            if (!javaBin.exists()) {
                throw new Exception("jre/bin/java not found in extracted JRE.");
            }

            updateNotification("⚙️ Fixing Permissions...", 90, false);
            setExecutableRecursive(new File(jreDir, "jre"));

            if (!javaBin.canExecute()) {
                try {
                    Runtime.getRuntime().exec("chmod -R 755 " + jreDir.getAbsolutePath()).waitFor();
                } catch (Exception e) {
                    Log.e(TAG, "chmod failed", e);
                }
                javaBin.setExecutable(true, false);
            }

            if (!javaBin.canExecute()) {
                throw new Exception("Failed to set executable permission. Device may need root.");
            }

            Log.d(TAG, "SUCCESS: Java installed at " + javaBin.getAbsolutePath());
            updateNotification("✅ JRE Ready!", 100, true);
            cleanup(tempZip);

            Intent i = new Intent(this, MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(i);
        } catch (Exception e) {
            Log.e(TAG, "JRE Install Failed", e);
            updateNotification("❌ Failed: " + e.getMessage(), 0, true);
        } finally {
            stopSelf();
        }
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

    private void copyAssetToCache(String path, File dest) throws IOException {
        try (InputStream in = getAssets().open(path);
             FileOutputStream out = new FileOutputStream(dest)) {
            byte[] b = new byte[8192];
            int r;
            long total = in.available(), copied = 0;
            while ((r = in.read(b)) != -1) {
                if (isCancelled) throw new IOException("Cancelled");
                out.write(b, 0, r);
                copied += r;
                if (total > 0) {
                    updateNotificationProgress(5 + (int)((copied * 45) / total));
                }
            }
        }
    }

    private void unzip(File zip, File dir) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zip))) {
            ZipEntry e;
            while ((e = zis.getNextEntry()) != null) {
                if (isCancelled) throw new IOException("Cancelled");
                File f = new File(dir, e.getName());
                if (e.isDirectory()) {
                    f.mkdirs();
                } else {
                    f.getParentFile().mkdirs();
                    try (FileOutputStream fos = new FileOutputStream(f)) {
                        byte[] b = new byte[8192];
                        int l;
                        while ((l = zis.read(b)) > 0) {
                            fos.write(b, 0, l);
                        }
                    }
                }
                zis.closeEntry();
            }
        }
    }

    private void deleteRecursive(File f) {
        if (f.isDirectory()) {
            File[] c = f.listFiles();
            if (c != null) {
                for (File child : c) {
                    deleteRecursive(child);
                }
            }
        }
        f.delete();
    }

    private void cleanup(File f) {
        if (f.exists()) f.delete();
    }

    private NotificationCompat.Builder createNotification(String t, int p, boolean done) {
        NotificationCompat.Builder b = new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("FearLauncher")
            .setContentText(t)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(!done);
        if (!done) {
            b.setProgress(100, p, false);
            Intent ci = new Intent(this, JreInstallService.class);
            ci.setAction("CANCEL");
            b.addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Cancel",
                PendingIntent.getService(this, 0, ci, PendingIntent.FLAG_IMMUTABLE)
            );
        }
        return b;
    }

    private void updateNotification(String t, int p, boolean d) {
        getSystemService(NotificationManager.class).notify(NOTIFICATION_ID, createNotification(t, p, d).build());
    }

    private void updateNotificationProgress(int p) {
        updateNotification("Installing...", p, false);
    }
}
