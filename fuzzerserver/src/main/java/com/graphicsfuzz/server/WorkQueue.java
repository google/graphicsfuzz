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

package com.graphicsfuzz.server;

import com.graphicsfuzz.server.thrift.CommandInfo;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

public class WorkQueue {

  private static final Logger LOGGER = LoggerFactory.getLogger(WorkQueue.class);

  private final ExecutorService executor;
  private final ArrayList<Runnable> workQueue = new ArrayList<>();
  private final Object mutex = new Object();
  private final String name;

  private final MyWorker myWorker = new MyWorker();
  private boolean running;
  private Future future = null;
  private final int timeLimit = 60;
  private final int consecutiveFailureLimit = 5;

  public WorkQueue(ExecutorService executor, String name) {
    this.executor = executor;
    this.name = name;
  }

  private class MyWorker implements Runnable {

    @Override
    public void run() {
      Runnable runnable = null;
      while (true) {
        synchronized (mutex) {
          assert running;
          // Remove previously completed (or failed) work item r.
          if (runnable != null) {
            // Edge case:
            if (workQueue.isEmpty()) {
              LOGGER.info(name
                  + ": front work item failed but queue is now empty. "
                  + "The queue was probably interrupted and cleared. "
                  + "Stopping.");
              running = false;
              return;
            }
            Runnable removed = workQueue.remove(0);
            assert removed == runnable;
            // If r failed, add to back of queue.
          }
          // Main exit condition.
          if (workQueue.isEmpty()) {
            LOGGER.info(name + " queue is empty. Stopping.");
            running = false;
            return;
          }
          runnable = workQueue.get(0);
        }
        // Must execute runnable outside of synchronized block, as
        // runnables may add work.
        // Should not use "return" below:
        try {
          MDC.put("token", name + ":" + runnable.toString());
          LOGGER.info("Dequeued work item. Running it now.");
          runnable.run();
        } catch (Throwable ex) {
          LOGGER.error("Throwable", ex);
        } finally {
          MDC.remove("token");
        }
      }
    }
  }

  public void addNext(Runnable runnable) {
    add(runnable, 1);
  }

  public void add(Runnable runnable) {
    add(runnable, -1);
  }

  public void add(Runnable runnable, int pos) {
    // boolean startWorker = false;
    synchronized (mutex) {
      if (pos < 0 || workQueue.isEmpty()) {
        workQueue.add(runnable);
      } else {
        workQueue.add(pos, runnable);
      }
      if (!running) {
        running = true;
        // startWorker = true;
        future = executor.submit(myWorker);
      }
    }

//    if(startWorker) {
//      executor.execute(myWorker);
//    }
  }

  public String queueToString() {
    StringBuffer sb = new StringBuffer();
    for (String s : queueToStringList()) {
      sb.append(s);
      sb.append(System.lineSeparator());
    }
    return sb.toString();
  }

  public List<String> queueToStringList() {
    List<String> res;
    synchronized (mutex) {
      res = workQueue.stream().map(Object::toString).collect(Collectors.toList());
    }
    return res;
  }

  public List<CommandInfo> getQueueAsCommandInfoList() {
    List<CommandInfo> res = new ArrayList<>();
    List<Runnable> queue = getQueueCopy();
    for (Runnable item : queue) {
      if (item instanceof CommandRunnable) {
        CommandRunnable cr = (CommandRunnable) item;
        res.add(
            new CommandInfo()
              .setName(cr.getName())
              .setCommand(cr.getCommand())
              .setLogFile(cr.getLogFile())
        );
      } else {
        res.add(new CommandInfo().setName(item.toString()));
      }
    }
    return res;
  }

  public List<Runnable> getQueueCopy() {
    synchronized (mutex) {
      return new ArrayList<>(workQueue);
    }
  }

  public void clearQueue() throws InterruptedException {
    Future theFuture;
    synchronized (mutex) {
      workQueue.clear();
      theFuture = future;
      if (theFuture != null) {
        theFuture.cancel(true);
      }

    }

    try {
      if (theFuture != null) {
        theFuture.get();
      }
    } catch (ExecutionException exception) {
      LOGGER.info("Error waiting for work queue to stop:", exception);
    } catch (CancellationException exception) {
      // ignore
    }

  }

}
