/*
 * Copyright 2018 The GraphicsFuzz Project Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.graphicsfuzz.glesworker;

import android.app.ActivityManager;
import android.app.ActivityManager.RunningTaskInfo;
import android.app.Notification;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.support.v7.app.NotificationCompat;
import android.util.Log;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class AndroidService extends Service {

  private MyRunnable myRunnable;
  private Process logcatProcess;
  private StringBuilder logcatErr;
  private Thread logcatThread;

  private static class MyLogReader implements Runnable {

    private StringBuilder logcatErr;
    private Process process;

    MyLogReader(Process process, StringBuilder logcatErr) {
      this.process = process;
      this.logcatErr = logcatErr;
    }

    @Override
    public void run() {
      try {
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        String line;
        while ((line = reader.readLine()) != null) {
          if (line.contains("RESET_LAUNCHER_LOG")) {
            synchronized (logcatErr) {
              logcatErr.setLength(0);
            }
          }
          // filter out regular info, to keep only warnings, error and fatal
          if (line.contains(" I ") == false) {
            synchronized (logcatErr) {
              logcatErr.append(line + "\n");
            }
          }
          if (Thread.interrupted()) {
            return;
          }
        }
      } catch (Exception err) {
        Log.w("Service", "issue when trying to read logcat");
      }
    }
  }

  private static class MyRunnable implements Runnable {

    public boolean active = true;
    public final Object mutex = new Object();
    private ActivityManager activityManager;
    private Context context;
    private StringBuilder logcatErr;
    private PersistentData persistentData;

    public MyRunnable(ActivityManager activityManager, Context context, StringBuilder logcatErr) {
      this.activityManager = activityManager;
      this.context = context;
      this.logcatErr = logcatErr;
      persistentData = new PersistentData(new AndroidPersistentFile(context));
    }

    @Override
    public void run() {

      while(true) {

        // sleep
        try {
          Thread.sleep(1000);
        }
        catch (InterruptedException e) {
          Log.i("LaunchServiceActivity", "Thread interrupted.");
          break;
        }

        // exit
        synchronized (mutex) {
          if(!active) {
            Log.i("LaunchServiceActivity", "Thread stopping.");
            break;
          }
        }

        boolean foundOurTask = false;

        for(RunningTaskInfo i: activityManager.getRunningTasks(Integer.MAX_VALUE)) {
          if("AndroidLauncher"
              .equals(i.baseActivity.getClassName())) {
            activityManager.moveTaskToFront(i.id, ActivityManager.MOVE_TASK_WITH_HOME);
            foundOurTask = true;
          }
        }

        if(!foundOurTask) {
          // Before starting the task, gather logcat errors and reset the error buffer.
          String errMsg;
          synchronized (logcatErr) {
            errMsg = logcatErr.toString();
            logcatErr.setLength(0);
          }
          persistentData.appendErrMsg("### LOGCAT START ###\n"
              + errMsg + "\n"
              + "### LOGCAT END ###");

          Intent intent = new Intent(context, AndroidLauncher.class);
          intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
          Log.v("Service", "Start worker activity");
          context.startActivity(intent);
        }
      }
    }
  }

  @Override
  public void onLowMemory() {
    Log.d("AndroidService", "onLowMemory");
    super.onLowMemory();
  }

  @Override
  public void onTaskRemoved(Intent rootIntent) {
    Log.d("AndroidService", "onTaskRemoved");
    super.onTaskRemoved(rootIntent);
    if(myRunnable != null) {
      synchronized (myRunnable.mutex) {
        myRunnable.active = false;
      }
    }
    if (logcatThread != null) {
      logcatThread.interrupt();
    }
    if (logcatProcess != null) {
      logcatProcess.destroy();
    }
    stopSelf();
  }

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    Log.d("AndroidService", "onStartCommand");
    return super.onStartCommand(intent, flags, startId);
  }

  @Override
  public void onCreate() {
    Log.d("AndroidService", "onCreate");
    super.onCreate();

    // Logcat reader thread
    logcatErr = new StringBuilder();
    try {
      logcatProcess = Runtime.getRuntime().exec("logcat *:I");
    } catch (IOException err) {
      Log.e("Service", "Error when starting logcat reader");
    }
    logcatThread = new Thread(new MyLogReader(logcatProcess, logcatErr));
    logcatThread.start();

    ActivityManager activityManager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
    myRunnable = new MyRunnable(activityManager, this, logcatErr);
    new Thread(myRunnable).start();

    Notification notification = new NotificationCompat.Builder(this)
        .setContentTitle("OGLTesting")
        .setContentText("Running.")
        .build();

    startForeground(1, notification);

  }

  @Override
  public void onDestroy() {
    Log.d("AndroidService", "onDestroy");
    super.onDestroy();

    if(myRunnable != null) {
      synchronized (myRunnable.mutex) {
        myRunnable.active = false;
      }
    }
    if (logcatThread != null) {
      logcatThread.interrupt();
    }
    if (logcatProcess != null) {
      logcatProcess.destroy();
    }
  }

  @Override
  public IBinder onBind(Intent intent) {
    Log.d("AndroidService", "onBind");
    return null;
  }

}
