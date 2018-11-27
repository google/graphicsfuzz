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
import com.graphicsfuzz.server.thrift.CommandResult;
import com.graphicsfuzz.server.thrift.FuzzerServiceManager;
import com.graphicsfuzz.server.thrift.ImageJob;
import com.graphicsfuzz.server.thrift.Job;
import com.graphicsfuzz.server.thrift.ServerInfo;
import com.graphicsfuzz.server.thrift.WorkerInfo;
import com.graphicsfuzz.server.thrift.WorkerNameNotFoundException;
import com.graphicsfuzz.util.ExecHelper;
import com.graphicsfuzz.util.ExecHelper.RedirectType;
import com.graphicsfuzz.util.ExecResult;
import com.graphicsfuzz.util.ToolPaths;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.thrift.TException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FuzzerServiceManagerImpl implements FuzzerServiceManager.Iface {

  private static final Logger LOGGER = LoggerFactory.getLogger(FuzzerServiceManagerImpl.class);

  private FuzzerServiceImpl service;

  private final AtomicLong jobIdCounter;

  private final ICommandDispatcher commandDispatcher;

  public FuzzerServiceManagerImpl(FuzzerServiceImpl service,
        ICommandDispatcher commandDispatcher) {
    this.service = service;
    this.jobIdCounter = new AtomicLong();
    this.commandDispatcher = commandDispatcher;
  }

  @Override
  public void clearClientJobQueue(String forClient) throws TException {
    try {
      service.getClientWorkQueue(forClient).clearQueue();
    } catch (InterruptedException exception) {
      throw new TException(exception);
    }
  }

  @Override
  public Job submitJob(Job job, String worker, int retryLimit) throws TException {
    LOGGER.info("submitJob {}", worker);
    Job[] result = new Job[1];

    if (!service.getSessionMap().containsWorker(worker)) {
      throw new WorkerNameNotFoundException().setWorkerName(worker);
    }

    service.getSessionMap().lockSessionAndExecute(worker, session -> {
      session.jobQueue.add(new SingleJob(job, job1 -> {
        synchronized (result) {
          result[0] = job1;
          result.notifyAll();
        }
      }, jobIdCounter, retryLimit));
      return null;
    });

    synchronized (result) {
      while (result[0] == null) {
        try {
          result.wait();
        } catch (InterruptedException exception) {
          throw new TException(exception);
        }
      }
    }

    return result[0];
  }

  @Override
  public void queueCommand(
        String name,
        List<String> command,
        String queueName,
        String logFile)
        throws TException {

    if (name == null) {
      throw new TException("name must be set.");
    }

    if (command == null || command.isEmpty()) {
      throw new TException("Command must be a non-empty list.");
    }

    if (queueName == null) {
      throw new TException("queueName must be set.");
    }

    final String worker = new String(queueName);

    if (!service.getSessionMap().containsWorker(worker)) {
      throw new WorkerNameNotFoundException().setWorkerName(worker);
    }

    try {
      Path workDir = Paths.get(".").toAbsolutePath().normalize();

      if (logFile != null) {
        Path child = Paths.get(logFile).toAbsolutePath().normalize();
        if (!child.startsWith(workDir)) {
          throw new TException("Invalid log file location.");
        }
      }

      service.getSessionMap().lockSessionAndExecute(
          worker, session -> {
            session.workQueue.add(new CommandRunnable(
                name,
                command,
                queueName,
                logFile,
                this,
                commandDispatcher));
            return null;
          });
    } catch (Exception ex) {
      LOGGER.error("", ex);
      throw new TException(ex);
    }
  }

  @Override
  public CommandResult executeCommand(String name, List<String> command) throws TException {
    try {
      ExecResult res =
            new ExecHelper(ToolPaths.getPythonDriversDir()).exec(
                  RedirectType.TO_BUFFER,
                  null,
                  true,
                  command.toArray(new String[0])
            );

      return new CommandResult().setOutput(res.stdout.toString())
            .setError(res.stderr.toString())
            .setExitCode(res.res);
    } catch (Exception ex) {
      throw new TException(ex);
    }
  }

  @Override
  public ServerInfo getServerState() throws TException {

    // Get reduction queue.
    List<CommandInfo> reductionQueue = new ArrayList<>();

    {
      List<String> reductionQueueStr = service.getReductionWorkQueue().queueToStringList();
      for (String reductionCommand : reductionQueueStr) {
        reductionQueue.add(new CommandInfo().setWorkerName(reductionCommand));
      }
    }

    // Get workers
    List<WorkerInfo> workers = new ArrayList<>();

    {
      Set<String> workerSet = service.getSessionMap().getWorkerSet();
      for (String worker : workerSet) {
        service.getSessionMap().lockSessionAndExecute(worker, session -> {

          workers.add(
                new WorkerInfo()
                      .setWorkerName(worker)
                      .setCommandQueue(session.workQueue.getQueueAsCommandInfoList())
                      .setJobQueue(getJobQueueAsJobInfoList(session.jobQueue))
                      .setLive(session.isLive())
          );

          return null;
        });
      }
    }

    return
          new ServerInfo()
                .setReductionQueue(reductionQueue)
                .setWorkers(workers);
  }

  private List<String> getJobQueueAsJobInfoList(Queue<IServerJob> jobQueue) {
    List<String> res = new ArrayList<>();
    for (IServerJob job : jobQueue) {
      if (job instanceof SingleJob) {
        SingleJob sj = (SingleJob) job;
        if (sj.job.isSetImageJob()) {
          ImageJob ij = sj.job.getImageJob();

          StringBuilder infoString = new StringBuilder();
          if (ij.isSetName()) {
            infoString.append(ij.getName());
          }
          if (ij.isSetName()) {
            infoString.append(ij.getName());
            infoString.append("; ");
          }
          res.add(infoString.toString());
          continue;
        }
      }
      // otherwise:
      res.add(job.toString());
    }
    return res;
  }

}
