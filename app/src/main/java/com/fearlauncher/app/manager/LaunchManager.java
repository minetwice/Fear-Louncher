package com.fearlauncher.app.manager;

import android.content.Context;
import android.util.Log;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class LaunchManager {
    private static final String TAG = "FearLauncher_Launch";
    private final Context ctx;
    private final VersionManager versionManager;

    public interface LaunchListener {
        void onLaunchSuccess();
        void onLaunchError(String message);
        void onLog(String line); // Optional: stream game output
    }

    public LaunchManager(Context context) {
        this.ctx = context.getApplicationContext();
        this.versionManager = new VersionManager(context);
    }

    /**
     * Launch Minecraft Java Edition with proper arguments
     * @param versionId e.g. "1.20.4"
     * @param username Player name
     * @param uuid Player UUID (can be offline-mode dummy)
     * @param accessToken Auth token (offline: "0" works for cracked)
     * @param listener Callback for status
     */
    public void launchGame(String versionId, String username, String uuid, 
                          String accessToken, LaunchListener listener) {
        new Thread(() -> {
            try {
                File baseDir = versionManager.getBaseDir();
                File versionDir = new File(baseDir, "versions/" + versionId);
                File versionJson = new File(versionDir, versionId + ".json");
                
                if (!versionJson.exists()) {
                    listener.onLaunchError("Version not installed: " + versionId);
                    return;
                }

                // ✅ Build launch command (Minecraft Java Edition standard)
                List<String> command = new ArrayList<>();
                
                // 1. Java executable (MUST be ARM-compatible JVM)
                // Option A: Use system Java (rarely works on Android)
                // Option B: Use bundled JRE (PojavLauncher style)
                String javaPath = findJavaRuntime();
                if (javaPath == null) {
                    listener.onLaunchError("Java runtime not found. Please install JRE for ARM.");
                    return;
                }
                command.add(javaPath);

                // 2. JVM Arguments (standard for Minecraft)
                command.add("-Xmx2G"); // Max heap: 2GB (adjust as needed)
                command.add("-Xms1G"); // Initial heap: 1GB
                command.add("-XX:+UnlockExperimentalVMOptions");
                command.add("-XX:+UseG1GC");
                command.add("-XX:G1NewSizePercent=20");
                command.add("-XX:G1ReservePercent=20");
                command.add("-XX:MaxGCPauseMillis=50");
                command.add("-XX:G1HeapRegionSize=32M");
                
                // 3. LWJGL/OpenGL hints for Android (if using LWJGL3-Android port)
                command.add("-Dorg.lwjgl.opengl.Display.allowSoftwareOpenGL=true");
                
                // 4. Minecraft-specific properties
                command.add("-Djava.library.path=" + new File(baseDir, "natives").getAbsolutePath());
                command.add("-Dminecraft.client.jar=" + new File(versionDir, versionId + ".jar").getAbsolutePath());
                
                // 5. Main class (varies by version; 1.17+ uses net.minecraft.client.main.Main)
                command.add("net.minecraft.client.main.Main");
                
                // 6. Game arguments
                command.add("--username"); command.add(username);
                command.add("--version"); command.add(versionId);
                command.add("--gameDir"); command.add(baseDir.getAbsolutePath());
                command.add("--assetsDir"); command.add(new File(baseDir, "assets").getAbsolutePath());
                command.add("--assetIndex"); command.add(versionId); // Simplified; real: parse from version.json
                command.add("--uuid"); command.add(uuid != null ? uuid : "0");
                command.add("--accessToken"); command.add(accessToken != null ? accessToken : "0");
                command.add("--userType"); command.add("mojang");
                command.add("--versionType"); command.add("FearLauncher");

                // ✅ Execute the process
                Log.d(TAG, "Launching: " + String.join(" ", command));
                
                ProcessBuilder pb = new ProcessBuilder(command);
                pb.directory(baseDir); // Set working directory
                pb.redirectErrorStream(true); // Merge stderr into stdout
                
                Process process = pb.start();
                
                // Optional: Stream output to listener (for debugging)
                new Thread(() -> {
                    try (java.io.BufferedReader reader = new java.io.BufferedReader(
                            new java.io.InputStreamReader(process.getInputStream()))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            if (listener != null) listener.onLog(line);
                            Log.d("Minecraft_Output", line);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Output read error", e);
                    }
                }).start();

                // Wait for process to exit (optional: run async for real launcher)
                int exitCode = process.waitFor();
                if (exitCode == 0) {
                    listener.onLaunchSuccess();
                } else {
                    listener.onLaunchError("Game exited with code: " + exitCode);
                }

            } catch (IOException e) {
                Log.e(TAG, "Launch IO error", e);
                listener.onLaunchError("IO Error: " + e.getMessage());
            } catch (InterruptedException e) {
                Log.e(TAG, "Launch interrupted", e);
                listener.onLaunchError("Launch interrupted");
            } catch (Exception e) {
                Log.e(TAG, "Unexpected launch error", e);
                listener.onLaunchError("Error: " + e.getMessage());
            }
        }).start();
    }

    /**
     * Find a usable Java runtime on the device
     * Returns path to java executable, or null if none found
     */
    private String findJavaRuntime() {
        // Option 1: Check for bundled JRE (PojavLauncher style)
        File bundledJre = new File(ctx.getFilesDir(), "jre/bin/java");
        if (bundledJre.exists() && bundledJre.canExecute()) {
            return bundledJre.getAbsolutePath();
        }
        
        // Option 2: Try system java (unlikely to work on Android, but worth a try)
        if (new File("/usr/bin/java").canExecute()) return "/usr/bin/java";
        if (new File("/system/bin/java").canExecute()) return "/system/bin/java";
        
        // Option 3: Check common Termux paths (if user has Termux + JRE installed)
        if (new File("/data/data/com.termux/files/usr/bin/java").canExecute()) {
            return "/data/data/com.termux/files/usr/bin/java";
        }
        
        return null; // No suitable Java found
    }

    /**
     * Check if launch prerequisites are met
     */
    public boolean isLaunchReady(String versionId) {
        File baseDir = versionManager.getBaseDir();
        File versionDir = new File(baseDir, "versions/" + versionId);
        File versionJson = new File(versionDir, versionId + ".json");
        File gameJar = new File(versionDir, versionId + ".jar");
        File natives = new File(baseDir, "natives");
        
        return versionJson.exists() && gameJar.exists() && natives.exists() && findJavaRuntime() != null;
    }
}
