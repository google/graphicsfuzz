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
import com.graphicsfuzz.server.thrift.GetWorkerNameResult;
import com.graphicsfuzz.server.thrift.ImageJob;
import com.graphicsfuzz.server.thrift.ImageJobResult;
import com.graphicsfuzz.server.thrift.Job;
import com.graphicsfuzz.server.thrift.JobStatus;
import com.graphicsfuzz.server.thrift.WorkerNameError;
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
    final String worker = newWorkerName();

    assertNotNull(worker);

    final Job job = new Job().setImageJob(new ImageJob()).setJobId(1);
    final Future<Job> submitting = this.submitJob(worker, job, 1);

    try {
      final Job otherJob = new Job().setImageJob(new ImageJob()).setJobId(2);

      final Job got = this.getAJob(worker);
      assertEquals(job.getJobId(), got.getJobId());

      thrown.expect(TException.class);

      this.fuzzerService.jobDone(worker, otherJob);
    } finally {
      submitting.cancel(true);
    }
  }

  @Test
  public void willGetASubmittedJob() throws Exception {
    final String worker = newWorkerName();

    assertNotNull(worker);
    final Job job = new Job().setImageJob(new ImageJob()).setJobId(1);
    assertTrue(job.toString(), job.isSetImageJob());

    Future<Job> submitting = submitJob(worker, job, 1);

    this.clientRuns(worker, (todo) -> {
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

    final String worker = newWorkerName();

    assertNotNull(worker);
    final Job job = new Job().setImageJob(new ImageJob()).setJobId(1);
    assertTrue(job.toString(), job.isSetImageJob());

    Future<Job> submitting = submitJob(worker, job, 3);
    this.clientRepeatedlyCrashes(worker, 3);
    Job result = submitting.get();
    assertFalse(result.isSetSkipJob());
    assertTrue(result.isSetImageJob());
    final ImageJobResult jobResult = result.getImageJob().getResult();
    assertEquals(JobStatus.SKIPPED, jobResult.getStatus());
    assertEquals("SKIPPED\n", jobResult.getLog());
  }

  @Test
  public void willSanitizeValueOnOldWorkerName() throws Exception {
    String oldWorkerName = new String("  helloworld ");
    String platformInfo = "{}";
    GetWorkerNameResult getWorkerNameResult = this.fuzzerService.getWorkerName(
        platformInfo, oldWorkerName);
    assertTrue(getWorkerNameResult.isSetWorkerName());
    assertEquals("helloworld", getWorkerNameResult.getWorkerName());
  }

  @Test
  public void willRejectWorkerNameWithChangedPlatformInfo() throws Exception {
    String oldWorkerName = this.newWorkerName();
    String platformInfo = "{\"bogus\": \"key\"}";
    GetWorkerNameResult getWorkerNameResult = this.fuzzerService.getWorkerName(
        platformInfo, oldWorkerName);
    assertFalse(getWorkerNameResult.isSetWorkerName());
    assertEquals(getWorkerNameResult.getError(), WorkerNameError.PLATFORM_INFO_CHANGED);
  }

  @Test
  public void willRejectAnInvalidWorkerName() throws Exception {
    String platformInfo = "{}";
    GetWorkerNameResult getWorkerNameResult = this.fuzzerService.getWorkerName(
        platformInfo,
        new String("hello world")
    );
    assertFalse(getWorkerNameResult.isSetWorkerName());
    assertEquals(getWorkerNameResult.getError(), WorkerNameError.INVALID_PROVIDED_WORKER);
  }

  @Test
  public void willRejectANonObjectPlatformInfo() throws Exception {
    String platformInfo = "";
    GetWorkerNameResult getWorkerNameResult = this.fuzzerService.getWorkerName(
        platformInfo,
        ""
    );
    assertFalse(getWorkerNameResult.isSetWorkerName());
    assertEquals(getWorkerNameResult.getError(), WorkerNameError.INVALID_PLATFORM_INFO);
  }

  @Test
  public void willPutManufacturerAndModelIntoWorkerName() throws Exception {
    Gson gson = new Gson();
    Map<String, String> platformInfoData = new HashMap<>();
    platformInfoData.put("manufacturer", "ABCD");
    platformInfoData.put("model", "12345");
    String platformInfo = gson.toJson(
        platformInfoData
    );

    GetWorkerNameResult getWorkerNameResult =
        this.fuzzerService.getWorkerName(platformInfo, "");

    assertTrue(getWorkerNameResult.isSetWorkerName());
    assertTrue(getWorkerNameResult.getWorkerName().contains("ABCD"));
    assertTrue(getWorkerNameResult.getWorkerName().contains("12345"));
  }

  // Helper methods below here
  private Future<Job> submitJob(String worker, Job job, int retryLimit) {
    return this.submit(() ->
        this.fuzzerServiceManager.submitJob(
            job, worker, retryLimit
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

  private void clientRepeatedlyCrashes(String worker, int maxCrashes) throws Exception {
    for (int i = 0; i <= maxCrashes; i++) {
      boolean gotJob = false;
      for (int j = 0; j < 500; j++) {
        Job todo = this.fuzzerService.getJob(worker).deepCopy();
        if (todo.isSetNoJob()) {
          Thread.sleep(1);
          continue;
        }
        if (todo.isSetSkipJob()) {
          this.fuzzerService.jobDone(worker, todo.deepCopy());
          return;
        }
        gotJob = true;
        break;
      }
      assertTrue("On try " + i + " did not get a job despite waiting 500ms", gotJob);
    }
  }

  private Job getAJob(String worker) throws Exception {
    while (true) {
      Job todo = this.fuzzerService.getJob(worker).deepCopy();
      if (todo.isSetNoJob()) {
        Thread.sleep(1);
        continue;
      }
      return todo.deepCopy();
    }
  }

  private void clientRuns(String worker, ClientAction client) throws Exception {
    Job todo = this.getAJob(worker);
    Job result = client.run(todo.deepCopy());
    this.fuzzerService.jobDone(worker, result);
  }

  private String newWorkerName() throws TException {
    String platformInfo = "{}";
    return this.fuzzerService.getWorkerName(
        platformInfo, ""
    ).getWorkerName();
  }

  interface ThriftCallable<T> {
    T callThrift() throws Exception;
  }

  interface ClientAction {
    Job run(Job job) throws Exception;
  }
}
