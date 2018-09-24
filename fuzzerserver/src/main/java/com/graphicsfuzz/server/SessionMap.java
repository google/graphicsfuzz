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
        String token,
        String platformInfo,
        ExecutorService executorService) {
      this.platformInfo = platformInfo;
      workQueue = new WorkQueue(executorService, "WorkQueue(" + token + ")");
    }
  }

  @FunctionalInterface
  public interface SessionWorkerEx<T, E extends Throwable> {

    T go(Session session) throws E;
  }

  private final ConcurrentMap<String, Session> sessions = new ConcurrentHashMap<>();

  public Set<String> getTokenSet() {
    return Collections.unmodifiableSet(sessions.keySet());
  }

  public boolean containsToken(String token) {
    return sessions.containsKey(token);
  }

  public boolean isLive(String token) {
    Session session = this.sessions.get(token);
    if (session == null) {
      return false;
    }
    return session.isLive();
  }

  /**
   * @return true if token was absent and so has been put into the map.
   */
  public boolean putIfAbsent(String token, Session session) {
    return sessions.putIfAbsent(token, session) == null;
  }

  public void replace(String token, Session oldSession, Session session) {
    sessions.replace(token, oldSession, session);
  }

  public void remove(String token) {
    sessions.remove(token);
  }

  public WorkQueue getWorkQueue(String token) {
    return sessions.get(token).workQueue;
  }

  public <T, E extends Throwable> T lockSessionAndExecute(String token,
      SessionWorkerEx<T, E> sessionWorker) throws E {
    Session session = sessions.get(token);
    synchronized (session.mutex) {
      return sessionWorker.go(session);
    }
  }


}
