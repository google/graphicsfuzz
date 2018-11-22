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
import com.graphicsfuzz.repackaged.com.google.gson.JsonObject;
import com.graphicsfuzz.repackaged.com.google.gson.JsonParser;
import com.graphicsfuzz.repackaged.org.apache.thrift.TException;
import com.graphicsfuzz.server.thrift.ImageJobResult;
import com.graphicsfuzz.server.thrift.Job;
import com.graphicsfuzz.server.thrift.JobStage;
import com.graphicsfuzz.server.thrift.JobStatus;
import com.graphicsfuzz.server.thrift.ResultConstant;

public abstract class ComputeJobManager {

  public abstract void createAndCompileComputeShader(String computeShaderSource);

  public abstract boolean compilationSucceeded();

  public abstract void createAndLinkComputeProgram();

  public abstract boolean linkingSucceeded();

  public abstract String getCompilationLog();

  public abstract void prepareEnvironment(JsonObject environmentJson);

  public abstract JsonObject executeComputeShader();

  public abstract void reset();

  WorkerState handle(WorkerState state,
      PersistentData persistentData,
      JobGetter jobGetter,
      Job job) throws TException {

    switch (state) {
      case COMPUTE_PREPARE:
        try {
          handleComputePrepare(state, persistentData, job);
          return WorkerState.COMPUTE_EXECUTE;
        } catch (PrepareShaderException exception) {
          return WorkerState.COMPUTE_REPLY_JOB;
        }
      case COMPUTE_STANDALONE_PREPARE:
        try {
          handleComputePrepare(state, persistentData, job);
          return WorkerState.COMPUTE_STANDALONE_EXECUTE;
        } catch (PrepareShaderException exception) {
          throw new RuntimeException();
        }
      case COMPUTE_EXECUTE:
        handleExecute(persistentData, job);
        return WorkerState.COMPUTE_REPLY_JOB;
      case COMPUTE_STANDALONE_EXECUTE:
        handleExecute(persistentData, job);
        return WorkerState.COMPUTE_STANDALONE_IDLE;
      case COMPUTE_REPLY_JOB:
        persistentData.setStage(JobStage.COMPUTE_REPLY_JOB);
        Gdx.app.log("ComputeJobManager", "COMPUTE_REPLY_JOB");
        if (!job.getImageJob().isSetResult()) {
          job.getImageJob().setResult(
              new ImageJobResult().setStatus(JobStatus.SKIPPED));
        }
        job.getImageJob().getResult().setLog(persistentData.getString(Constants.PERSISTENT_KEY_ERROR_MSG));
        jobGetter.replyJob(job);
        persistentData.reset();
        return WorkerState.GET_JOB;
      default:
        throw new RuntimeException("Could not handle worker state " + state);
    }
  }

  private void handleExecute(PersistentData persistentData, Job job) {
    persistentData.setStage(JobStage.COMPUTE_EXECUTE);
    Gdx.app.log("ComputeJobManager", "COMPUTE_EXECUTE");
    JsonObject computeShaderResult = executeComputeShader();
    job.getImageJob().setResult(
        new ImageJobResult()
            .setStage(JobStage.COMPUTE_EXECUTE)
            .setStatus(JobStatus.SUCCESS)
            .setComputeOutputs(computeShaderResult.toString()));
  }

  private void handleComputePrepare(WorkerState state, PersistentData persistentData,
        Job job) throws PrepareShaderException {
    persistentData.setStage(JobStage.COMPUTE_PREPARE);
    Gdx.app.log("ComputeJobManager", "COMPUTE_PREPARE");
    reset();
    try {
      prepareComputeShader(
          job.imageJob.computeSource,
          job.imageJob.computeInfo);
    } catch (PrepareShaderException exception) {
      JobStatus status = JobStatus.UNEXPECTED_ERROR;
      if (exception.resultConstant == ResultConstant.COMPILE_ERROR) {
        status = JobStatus.COMPILE_ERROR;
      } else if (exception.resultConstant == ResultConstant.LINK_ERROR) {
        status = JobStatus.LINK_ERROR;
      }
      job.getImageJob().setResult(
          new ImageJobResult()
              .setStage(JobStage.COMPUTE_PREPARE)
              .setStatus(status));
      persistentData.appendErrMsg(status.toString() + " " + state.toString() + "\n"
          + "### START OF ERROR MESSAGE ###\n"
          + exception.getMessage() + "\n"
          + "### END OF ERROR MESSAGE ###");
      throw exception;
    }
  }

  private void prepareComputeShader(String computeShaderSource, String environmentJson)
      throws PrepareShaderException {
    createAndCompileComputeShader(computeShaderSource);
    if (!compilationSucceeded()) {
      throw new PrepareShaderException(
          ResultConstant.COMPILE_ERROR,
          getCompilationLog());
    }
    createAndLinkComputeProgram();
    if (!linkingSucceeded()) {
      throw new RuntimeException();
    }
    prepareEnvironment(new JsonParser().parse(environmentJson).getAsJsonObject());
  }

}