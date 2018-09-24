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

import com.graphicsfuzz.server.thrift.ImageJobResult;
import com.graphicsfuzz.server.thrift.Job;
import com.graphicsfuzz.server.thrift.JobStatus;
import com.graphicsfuzz.server.thrift.ResultConstant;
import com.graphicsfuzz.server.thrift.SkipJob;
import java.util.concurrent.atomic.AtomicLong;

public class SingleJob implements IServerJob {

  @FunctionalInterface
  public interface ISingleJobCompleter {

    void completeJob(Job job);
  }

  public final Job job;
  private Job skipJob;
  private ISingleJobCompleter completer;

  private final AtomicLong skipJobIdCounter;

  private int counter;
  private final int limit;

  public SingleJob(Job job, ISingleJobCompleter completer, AtomicLong skipJobIdCounter,
      int retryLimit) {
    this.job = job;
    this.completer = completer;
    this.skipJobIdCounter = skipJobIdCounter;
    this.limit = retryLimit + 1;
  }

  @Override
  public Job getJob() throws ServerJobException {
    if (counter + 1 >= limit) {
      skipJob = new Job()
          .setJobId(skipJobIdCounter.incrementAndGet())
          .setSkipJob(new SkipJob());
      job.getImageJob()
          .setResult(
              new ImageJobResult()
                  .setStatus(JobStatus.SKIPPED)
                  .setLog(ResultConstant.SKIPPED.toString() + "\n"));
      return skipJob;
    } else {
      ++counter;
    }
    return job;
  }

  @Override
  public boolean finishJob(Job returnedJob) throws ServerJobException {

    if (skipJob != null) {
      if (returnedJob.getJobId() != skipJob.getJobId()) {
        throw new ServerJobException("Client tried to finish a job that did not match"
            + "the current skip job.");
      }
      if (job == null) {
        // this call to jobDone() may be due to an old crash, in which case skipjob is null,
        // and so is job. Hence, complete the returnedJob.
        completer.completeJob(returnedJob);
      } else {
        completer.completeJob(job);
      }
      return true;
    }

    if (returnedJob.getJobId() != job.getJobId()) {
      throw new ServerJobException("Client tried to finish a job that did not match"
          + "the currently queued job.");
    }
    completer.completeJob(returnedJob);
    return true;
  }
}
