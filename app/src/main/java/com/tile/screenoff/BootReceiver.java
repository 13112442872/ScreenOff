package com.tile.screenoff;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import java.io.DataOutputStream;
import java.io.IOException;

/**
 * 开机自启动广播接收器
 * 负责在设备开机时自动启动starter.sh脚本
 */
public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            return;
        }

        // 检查是否启用了自启动功能
        SharedPreferences sp = context.getSharedPreferences("s", Context.MODE_PRIVATE);
        if (!sp.getBoolean("auto_start", false)) {
            return;
        }

        // 延迟执行，确保系统启动完成
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                startScreenController(context);
                // 启动 GlobalService
                Intent serviceIntent = new Intent(context, GlobalService.class);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent);
                } else {
                    context.startService(serviceIntent);
                }
            }
        }, 10000); // 延迟10秒启动
    }

    private void startScreenController(Context context) {
        try {
            // 首先尝试使用Root权限启动
            String command = "sh " + context.getExternalFilesDir(null).getPath() + "/starter.sh";

            Process p = Runtime.getRuntime().exec("su");
            DataOutputStream out = new DataOutputStream(p.getOutputStream());
            out.writeBytes(command + "\n");
            out.writeBytes("exit\n");
            out.flush();
            out.close();

            // 等待进程执行完成
            p.waitFor();

        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
            // 如果Root启动失败，记录到日志
            android.util.Log.e("BootReceiver", "Failed to start ScreenController: " + e.getMessage());
        }
    }
}
