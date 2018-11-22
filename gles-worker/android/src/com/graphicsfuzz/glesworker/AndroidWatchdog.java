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

import android.os.Process;
import android.util.Log;

import java.util.concurrent.TimeUnit;

public class AndroidWatchdog implements IWatchdog {

  private static class AWRunnable implements Runnable {

    private final String name;
    private final long waitTimeNs;
    private final long waitTimeMs;
    private PersistentData persistentData;
    private final Object watchdogMutex;
    private final boolean quiet;

    public AWRunnable(Object watchdogMutex, String name, long waitTimeMs, PersistentData persistentData, boolean quiet) {
      this.name = name;
      this.waitTimeMs = waitTimeMs;
      this.waitTimeNs = waitTimeMs * 1000 * 1000;
      this.persistentData = persistentData;
      this.watchdogMutex = watchdogMutex;
      this.quiet = quiet;
    }

    @Override
    public void run() {
      try {
        if (!quiet) {
          Log.i("OurAndroidWatchdog", "START " + name + ": " + waitTimeMs + " ms");
        }
        long start = System.nanoTime();
        long end;
        synchronized (watchdogMutex) {
          if (!quiet) {
            persistentData.appendErrMsg(start + " WATCHDOG " + name + " START: "
                + waitTimeMs + " ms");
          }
        }
        do {
          Thread.sleep(waitTimeMs);
          end = System.nanoTime();
        } while(end - start < waitTimeNs);
        // Here we make the hypothesis that the watchdog will not be blocked on acquiring the mutex
        synchronized (watchdogMutex) {
          persistentData.appendErrMsg(System.nanoTime() + " WATCHDOG " + name + " END");
          persistentData.setBool(Constants.PERSISTENT_KEY_TIMEOUT, true);
          persistentData.appendErrMsg("OurAndroidWatchdog: " + name + " exceeded " + waitTimeMs
              + " milliseconds: KILLING PROCESS");
        }
        // Timeout expired, kill the current process.
        Log.i("OurAndroidWatchdog",
            name + " exceeded " + waitTimeMs + " milliseconds: KILLING PROCESS");
        // sleep a little before to propagate changes before killing ourselves
        Thread.sleep(300);
        Process.killProcess(Process.myPid());

      } catch (InterruptedException e) {
        synchronized (watchdogMutex) {
          if (!quiet) {
            Log.i("OurAndroidWatchdog", System.nanoTime() + " WATCHDOG " + name + " INTERRUPTED");
            persistentData.appendErrMsg(System.nanoTime() + " WATCHDOG " + name + " INTERRUPTED");
          }
        }
      }
    }
  }

  private Thread thread;
  private final boolean quiet;

  public AndroidWatchdog() {
    this(false);
  }

  public AndroidWatchdog(final boolean quiet) {
    this.quiet = quiet;
  }

  @Override
  public void start(Object watchdogMutex, String name, long seconds,
      PersistentData persistentData) {
    if(thread != null) {
      throw new IllegalStateException("Watchdog was in illegal state.");
    }
    thread = new Thread(new AWRunnable(watchdogMutex, name, TimeUnit.SECONDS.toMillis(seconds),
        persistentData, quiet));
    thread.start();
  }

  @Override
  public void stop() {
    if(thread == null) {
      return;
    }
    thread.interrupt();
    try {
      thread.join();
    }
    catch (InterruptedException e) {
      // ignore
    }
    thread = null;
  }

  @Override
  public void killNow() {
    stop();
    Log.i("OurAndroidWatchdog", "KILLING PROCESS IMMEDIATELY");
    Process.killProcess(Process.myPid());
  }

}
