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

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;

public final class SessionMap {

  public static class Session {

    public final Queue<IServerJob> jobQueue = new ArrayDeque<>();
    public String platformInfo;
    private final Object mutex = new Object();
    private volatile long touched = System.currentTimeMillis();

    private static final long FIVE_MINUTES = 5 * 60 * 1000;

    public void touch() {
      touched = System.currentTimeMillis();
    }

    public boolean isLive() {
      return System.currentTimeMillis() - touched < FIVE_MINUTES;
    }

    // Uses its own internal mutex:
    public final WorkQueue workQueue;

    /**
     * Used as dummy object.
     */
    public Session() {
      workQueue = null;
    }

    public Session(
        String worker,
        String platformInfo,
        ExecutorService executorService) {
      this.platformInfo = platformInfo;
      workQueue = new WorkQueue(executorService, "WorkQueue(" + worker + ")");
    }
  }

  @FunctionalInterface
  public interface SessionWorkerEx<T, E extends Throwable> {

    T go(Session session) throws E;
  }

  private final ConcurrentMap<String, Session> sessions = new ConcurrentHashMap<>();

  public Set<String> getWorkerSet() {
    return Collections.unmodifiableSet(sessions.keySet());
  }

  public boolean containsWorker(String worker) {
    return sessions.containsKey(worker);
  }

  public boolean isLive(String worker) {
    Session session = this.sessions.get(worker);
    if (session == null) {
      return false;
    }
    return session.isLive();
  }

  /**
   * @return true if worker was absent and so has been put into the map.
   */
  public boolean putIfAbsent(String worker, Session session) {
    return sessions.putIfAbsent(worker, session) == null;
  }

  public void replace(String worker, Session oldSession, Session session) {
    sessions.replace(worker, oldSession, session);
  }

  public void remove(String worker) {
    sessions.remove(worker);
  }

  public WorkQueue getWorkQueue(String worker) {
    return sessions.get(worker).workQueue;
  }

  public <T, E extends Throwable> T lockSessionAndExecute(String worker,
      SessionWorkerEx<T, E> sessionWorker) throws E {
    Session session = sessions.get(worker);
    synchronized (session.mutex) {
      return sessionWorker.go(session);
    }
  }


}
