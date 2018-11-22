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

package com.graphicsfuzz.glesworker.desktop;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import com.graphicsfuzz.glesworker.Constants;
import com.graphicsfuzz.glesworker.IWatchdog;
import com.graphicsfuzz.glesworker.PersistentData;
import org.zeroturnaround.process.PidProcess;
import org.zeroturnaround.process.PidUtil;
import org.zeroturnaround.process.Processes;

public class DesktopWatchdog implements IWatchdog {


  private static class ForcefulKill implements Runnable {

    @Override
    public void run() {
      try {
        Thread.sleep(5000);
      }
      catch (InterruptedException e) {
      }

      PidProcess proc = Processes.newPidProcess(PidUtil.getMyPid());
      try {
        proc.destroyForcefully();
      }
      catch (InterruptedException|IOException e) {
      }
    }
  }

  private static class DWRunnable implements Runnable {

    private final String name;
    private final long waitTime;
    private PersistentData persistentData;
    private final Object watchdogMutex;

    public DWRunnable(Object watchdogMutex, String name, long waitTime, PersistentData persistentData) {
      this.name = name;
      this.waitTime = waitTime;
      this.persistentData = persistentData;
      this.watchdogMutex = watchdogMutex;
    }

    @Override
    public void run() {
      try {
        long start = System.currentTimeMillis();
        long end;
        do {
          Thread.sleep(waitTime);
          end = System.currentTimeMillis();
        } while(end - start < waitTime);
        synchronized (watchdogMutex) {
          persistentData.setBool(Constants.PERSISTENT_KEY_TIMEOUT, true);
          persistentData.appendErrMsg("Our DesktopWatchdog(" + name + ") exceeded "
                  + waitTime + " milliseconds: KILLING PROCESS");
          System.err.println("Our DesktopWatchdog(" + name + ") exceeded " + waitTime
              + " milliseconds: KILLING PROCESS");
          new Thread(new ForcefulKill()).start();
        }
        System.exit(Constants.TIMEOUT_EXIT_CODE);
      }
      catch (InterruptedException e) {
        // Hopefully we will be interrupted.
      }
    }
  }

  private Thread thread;

  @Override
  public void start(Object watchdogMutex, String name, long seconds,
      PersistentData persistentData) {
    if(thread != null) {
      throw new IllegalStateException("Watchdog was in illegal state.");
    }
    thread = new Thread(new DWRunnable(watchdogMutex, name, TimeUnit.SECONDS.toMillis(seconds),
        persistentData));
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
    System.err.println("Our DesktopWatchdog was instructed to exit process.");
    new Thread(new ForcefulKill()).start();
    System.exit(1);
  }

}
