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

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.Disposable;
import com.graphicsfuzz.repackaged.org.apache.http.impl.client.CloseableHttpClient;
import com.graphicsfuzz.repackaged.org.apache.http.impl.client.HttpClients;
import com.graphicsfuzz.repackaged.org.apache.thrift.TException;
import com.graphicsfuzz.repackaged.org.apache.thrift.protocol.TBinaryProtocol;
import com.graphicsfuzz.repackaged.org.apache.thrift.protocol.TProtocol;
import com.graphicsfuzz.repackaged.org.apache.thrift.transport.THttpClient;
import com.graphicsfuzz.repackaged.org.apache.thrift.transport.TTransport;
import com.graphicsfuzz.server.thrift.FuzzerService;
import com.graphicsfuzz.server.thrift.GetTokenResult;
import com.graphicsfuzz.server.thrift.Job;

public class JobGetter implements Disposable {

  private static final String REQUEST_PATH = "/request";

  private CloseableHttpClient httpClient;
  private TTransport transport;
  public FuzzerService.Iface fuzzerServiceProxy;
  public String token;

  // The latest job that has been gotten.
  private Job latestJob;

  // Used to ensure mutual exclusion when replying to jobs.
  private Object replyMutex;

  public JobGetter(String url) throws TException {
    Gdx.app.log("JobGetter", "Creating JobGetter");
    httpClient = HttpClients.createDefault();
    transport = new THttpClient(url + REQUEST_PATH, httpClient);
    transport.open();
    TProtocol protocol = new TBinaryProtocol(transport);

    fuzzerServiceProxy = new FuzzerService.Client(protocol);

    this.latestJob = null;
    this.replyMutex = new Object();

    Gdx.app.log("JobGetter", "JobGetter created");
  }

  public boolean setToken(String token, String platformInfo) throws TException {

    GetTokenResult getTokenResult = fuzzerServiceProxy.getToken(
        platformInfo,
        token);

    if (getTokenResult.isSetToken()) {
      this.token = getTokenResult.getToken();
      Gdx.app.log("JobGetter", "Token set: " + this.token);
      return true;
    } else {
      Gdx.app.log("JobGetter", "Failed to set token");
      return false;
    }
  }

  public Job getJob() throws TException {

    Job job = fuzzerServiceProxy.getJob(token);

    Gdx.app.log("JobGetter", "Got a job.");

    synchronized (replyMutex) {
      checkJobNotSet();
      latestJob = job;
    }

    return job;

  }

  public void clearJob() {
    checkJobIsSet();
    latestJob = null;
  }

  /**
   * Tries to send a reply for the given job.  If a reply has already been sent, the call is a
   * no-op.  Return value indicates whether a reply was sent.
   * @param job The job to be replied to
   * @throws TException Thrift exception might be thrown
   * @return true if and only if a reply was sent.
   */
  public boolean replyJob(Job job) throws TException {

    synchronized (replyMutex) {

      if (latestJob == null) {
        // A reply has already been sent for the job.
        Gdx.app.log("JobGetter", "jobDone(): " +
            "A reply has already been sent for the job");
        return false;
      }

      if (job.isSetImageJob()) {
        // TODO: Hugues: We should have a dedicated Thrift struct for
        // job replies, to avoid to have to reset the fragment source.
        job.getImageJob().setFragmentSource("");
      }

      Gdx.app.log("JobGetter", "Sending jobDone.");

      fuzzerServiceProxy.jobDone(token, job);

      // We have sent a reply, so nullify latestJob.
      latestJob = null;
      return true;

    }

  }

  @Override
  public void dispose() {

  }

  private void checkJobNotSet() {
    if (latestJob != null) {
      throw new RuntimeException("Job should not be set at this time.");
    }
  }

  private void checkJobIsSet() {
    if (latestJob == null) {
      throw new RuntimeException("Job should be set at this time.");
    }
  }

}
