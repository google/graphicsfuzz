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

import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import com.google.gson.Gson;
import com.graphicsfuzz.server.thrift.FuzzerService;
import com.graphicsfuzz.server.thrift.FuzzerServiceManager;
import com.graphicsfuzz.server.thrift.GetTokenResult;
import com.graphicsfuzz.server.thrift.ImageJob;
import com.graphicsfuzz.server.thrift.ImageJobResult;
import com.graphicsfuzz.server.thrift.Job;
import com.graphicsfuzz.server.thrift.JobStatus;
import com.graphicsfuzz.server.thrift.TokenError;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.apache.thrift.TException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;

public class FuzzerServerIntegrationTests {
  @Rule
  public TemporaryFolder testFolder = new TemporaryFolder();

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  private FuzzerService.Iface fuzzerService;
  private FuzzerServiceManager.Iface fuzzerServiceManager;
  private ExecutorService executorService;

  @Before
  public void setupServices() throws IOException {
    final String processing = testFolder.newFolder("processing").toString();

    this.executorService = Executors.newCachedThreadPool();
    final FuzzerServiceImpl fuzzerServiceImpl = new FuzzerServiceImpl(processing, executorService);
    this.fuzzerService = fuzzerServiceImpl;
    this.fuzzerServiceManager = new FuzzerServiceManagerImpl(fuzzerServiceImpl,
          (command, manager) -> {
            throw new RuntimeException("TODO: Need to decide what dispatcher to "
                  + "provide for these tests.");
          });
  }

  @Test
  public void willErrorOnAMismatchedJobId() throws Exception {
    final String token = newToken();

    assertNotNull(token);

    final Job job = new Job().setImageJob(new ImageJob()).setJobId(1);
    final Future<Job> submitting = this.submitJob(token, job, 1);

    try {
      final Job otherJob = new Job().setImageJob(new ImageJob()).setJobId(2);

      final Job got = this.getAJob(token);
      assertEquals(job.getJobId(), got.getJobId());

      thrown.expect(TException.class);

      this.fuzzerService.jobDone(token, otherJob);
    } finally {
      submitting.cancel(true);
    }
  }

  @Test
  public void willGetASubmittedJob() throws Exception {
    final String token = newToken();

    assertNotNull(token);
    final Job job = new Job().setImageJob(new ImageJob()).setJobId(1);
    assertTrue(job.toString(), job.isSetImageJob());

    Future<Job> submitting = submitJob(token, job, 1);

    this.clientRuns(token, (todo) -> {
      assertTrue(todo.toString(), todo.isSetImageJob());
      assertEquals(1, todo.getJobId());
      todo.getImageJob().setResult(new ImageJobResult().setStatus(JobStatus.UNEXPECTED_ERROR));
      return todo;
    });

    Job result = submitting.get();
    assertEquals(result.getImageJob().getResult().getStatus(), JobStatus.UNEXPECTED_ERROR);
  }

  @Test
  public void willSetSkippedAfterFailures() throws Exception {

    final String token = newToken();

    assertNotNull(token);
    final Job job = new Job().setImageJob(new ImageJob()).setJobId(1);
    assertTrue(job.toString(), job.isSetImageJob());

    Future<Job> submitting = submitJob(token, job, 3);
    this.clientRepeatedlyCrashes(token, 3);
    Job result = submitting.get();
    assertFalse(result.isSetSkipJob());
    assertTrue(result.isSetImageJob());
    final ImageJobResult jobResult = result.getImageJob().getResult();
    assertEquals(JobStatus.SKIPPED, jobResult.getStatus());
    assertEquals("SKIPPED\n", jobResult.getLog());
  }

  @Test
  public void willSanitizeValueOnOldToken() throws Exception {
    String oldToken = new String("  helloworld ");
    String platformInfo = "{}";
    GetTokenResult getTokenResult = this.fuzzerService.getToken(
        platformInfo, oldToken);
    assertTrue(getTokenResult.isSetToken());
    assertEquals("helloworld", getTokenResult.getToken());
  }

  @Test
  public void willRejectTokenWithChangedPlatformInfo() throws Exception {
    String oldToken = this.newToken();
    String platformInfo = "{\"bogus\": \"key\"}";
    GetTokenResult getTokenResult = this.fuzzerService.getToken(
        platformInfo, oldToken);
    assertFalse(getTokenResult.isSetToken());
    assertEquals(getTokenResult.getError(), TokenError.PLATFORM_INFO_CHANGED);
  }

  @Test
  public void willRejectAnInvalidToken() throws Exception {
    String platformInfo = "{}";
    GetTokenResult getTokenResult = this.fuzzerService.getToken(
        platformInfo,
        new String("hello world")
    );
    assertFalse(getTokenResult.isSetToken());
    assertEquals(getTokenResult.getError(), TokenError.INVALID_PROVIDED_TOKEN);
  }

  @Test
  public void willRejectANonObjectPlatformInfo() throws Exception {
    String platformInfo = "";
    GetTokenResult getTokenResult = this.fuzzerService.getToken(
        platformInfo,
        ""
    );
    assertFalse(getTokenResult.isSetToken());
    assertEquals(getTokenResult.getError(), TokenError.INVALID_PLATFORM_INFO);
  }

  @Test
  public void willPutManufacturerAndModelIntoToken() throws Exception {
    Gson gson = new Gson();
    Map<String, String> platformInfoData = new HashMap<>();
    platformInfoData.put("manufacturer", "ABCD");
    platformInfoData.put("model", "12345");
    String platformInfo = gson.toJson(
        platformInfoData
    );

    GetTokenResult getTokenResult =
        this.fuzzerService.getToken(platformInfo, "");

    assertTrue(getTokenResult.isSetToken());
    assertTrue(getTokenResult.getToken().contains("ABCD"));
    assertTrue(getTokenResult.getToken().contains("12345"));
  }

  // Helper methods below here
  private Future<Job> submitJob(String token, Job job, int retryLimit) {
    return this.submit(() ->
        this.fuzzerServiceManager.submitJob(
            job, token, retryLimit
        )
    );
  }

  private <T> Future<T> submit(ThriftCallable<T> callable) {
    return this.executorService.submit(() -> {
      try {
        return callable.callThrift();
      } catch (TException t) {
        throw new RuntimeException(t);
      }
    });
  }

  private void clientRepeatedlyCrashes(String token, int maxCrashes) throws Exception {
    for (int i = 0; i <= maxCrashes; i++) {
      boolean gotJob = false;
      for (int j = 0; j < 500; j++) {
        Job todo = this.fuzzerService.getJob(token).deepCopy();
        if (todo.isSetNoJob()) {
          Thread.sleep(1);
          continue;
        }
        if (todo.isSetSkipJob()) {
          this.fuzzerService.jobDone(token, todo.deepCopy());
          return;
        }
        gotJob = true;
        break;
      }
      assertTrue("On try " + i + " did not get a job despite waiting 500ms", gotJob);
    }
  }

  private Job getAJob(String token) throws Exception {
    while (true) {
      Job todo = this.fuzzerService.getJob(token).deepCopy();
      if (todo.isSetNoJob()) {
        Thread.sleep(1);
        continue;
      }
      return todo.deepCopy();
    }
  }

  private void clientRuns(String token, ClientAction client) throws Exception {
    Job todo = this.getAJob(token);
    Job result = client.run(todo.deepCopy());
    this.fuzzerService.jobDone(token, result);
  }

  private String newToken() throws TException {
    String platformInfo = "{}";
    return this.fuzzerService.getToken(
        platformInfo, ""
    ).getToken();
  }

  interface ThriftCallable<T> {
    T callThrift() throws Exception;
  }

  interface ClientAction {
    Job run(Job job) throws Exception;
  }
}