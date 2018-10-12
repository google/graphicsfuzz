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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonWriter;
import com.graphicsfuzz.server.SessionMap.Session;
import com.graphicsfuzz.server.thrift.FuzzerService;
import com.graphicsfuzz.server.thrift.GetTokenResult;
import com.graphicsfuzz.server.thrift.Job;
import com.graphicsfuzz.server.thrift.NoJob;
import com.graphicsfuzz.server.thrift.TokenError;
import com.graphicsfuzz.server.thrift.TokenNotFoundException;
import java.io.CharArrayWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.util.concurrent.ExecutorService;
import java.util.regex.Pattern;
import org.apache.commons.io.FileUtils;
import org.apache.thrift.TException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

public class FuzzerServiceImpl implements FuzzerService.Iface {

  private static final Logger LOGGER = LoggerFactory.getLogger(FuzzerServiceImpl.class);
  private final SessionMap sessions = new SessionMap();

  private final String processingDir;

  private final ExecutorService executorService;

  private final WorkQueue reductionWorkQueue;

  private final Pattern validTokenPattern = Pattern.compile("[a-zA-Z_0-9-]+");

  public FuzzerServiceImpl(
      String processingDir,
      ExecutorService executorService) {

    this.processingDir = processingDir;
    this.executorService = executorService;

    reductionWorkQueue = new WorkQueue(this.executorService, "Reduction Work Queue");

  }

  public WorkQueue getReductionWorkQueue() {
    return reductionWorkQueue;
  }

  public SessionMap getSessionMap() {
    return sessions;
  }

  public WorkQueue getClientWorkQueue(String client) {
    return sessions.getWorkQueue(client);
  }

  @Override
  public GetTokenResult getToken(String platformInfo, String oldToken) throws TException {
    String token = null;

    final String clientJsonFilename = "client.json";
    LOGGER.info("Called getToken with oldToken: {}.", oldToken);

    // Trim token.
    if (oldToken != null) {
      oldToken = oldToken.trim();
    }
    if (oldToken.isEmpty()) {
      oldToken = null;
    }

    // Check and store provided platform info JSON object.
    JsonObject info = new JsonObject();
    JsonElement pie = new JsonParser().parse(platformInfo);
    if (!pie.isJsonObject()) {
      LOGGER.error("Platform info is not valid JSON");
      return new GetTokenResult().setError(TokenError.INVALID_PLATFORM_INFO);
    }
    info.add("platform_info", pie.getAsJsonObject());

    // Write JSON platform info to a String.
    String clientInfoString = "";
    {
      CharArrayWriter fw = new CharArrayWriter();

      Gson gson = new GsonBuilder().setPrettyPrinting().setLenient().create();
      try (JsonWriter jw = gson.newJsonWriter(fw)) {
        gson.toJson(info, jw);
        clientInfoString = fw.toString();
      } catch (IOException exception) {
        throw new TException(exception);
      }
    }

    if (clientInfoString.isEmpty()) {
      LOGGER.error("Empty platform info.");
      return new GetTokenResult().setError(TokenError.INVALID_PLATFORM_INFO);
    }

    // Read old client info String
    String oldClientInfoString = "";

    if (oldToken != null) {

      if (oldToken.isEmpty()
          || !validTokenPattern.matcher(oldToken).matches()) {
        LOGGER.error("Invalid token provided: {}", oldToken);
        return new GetTokenResult().setError(TokenError.INVALID_PROVIDED_TOKEN);
      }
      try {
        File platformInfoFile = Paths.get(processingDir, oldToken, clientJsonFilename)
            .toFile();
        LOGGER.info("Reading platform info at: {}", platformInfoFile);
        oldClientInfoString =
            FileUtils.readFileToString(platformInfoFile, Charset.defaultCharset());

        if (!clientInfoString.equals(oldClientInfoString)) {
          LOGGER.info(
              "Platform info did not match. Old then new platform info: "
                  + "\n{}\n{}",
              oldClientInfoString,
              clientInfoString);
          return new GetTokenResult().setError(TokenError.PLATFORM_INFO_CHANGED);
        }

      } catch (IOException exception) {
        LOGGER.info("Failed to read existing platform info.", exception);
      }
    }

    if (oldToken != null && (oldClientInfoString.isEmpty() || clientInfoString
        .equals(oldClientInfoString))) {
      LOGGER.info("Using provided token.");
      sessions.putIfAbsent(oldToken, new Session(oldToken, platformInfo, executorService));
      token = oldToken;
    } else {
      LOGGER.info("Generating new token. Old then new platform info: \n{}\n{}", oldClientInfoString,
          clientInfoString);
      Session dummy = new Session();
      while (true) {
        String tokenStr;
        // For platforms with meaningful platform info, create a meaningful token, yet still
        // randomized
        if (info.getAsJsonObject("platform_info").has("manufacturer")
            && info.getAsJsonObject("platform_info").has("model")) {
          tokenStr = info.getAsJsonObject("platform_info").get("manufacturer").getAsString();
          tokenStr += "_";
          tokenStr += info.getAsJsonObject("platform_info").get("model").getAsString();
          tokenStr += String.valueOf(Math.abs(new SecureRandom().nextInt())).substring(0, 4);
        } else {
          tokenStr = String.valueOf(Math.abs(new SecureRandom().nextLong()));
        }
        tokenStr = tokenStr.replace(' ', '_');
        token = new String(tokenStr);
        if (sessions.putIfAbsent(token, dummy)) {
          Session newSession = new Session(token, platformInfo, executorService);
          sessions.replace(token, dummy, newSession);
          break;
        }
      }
    }

    File tokenDir = Paths.get(processingDir, token).toFile();
    try {
      FileUtils.forceMkdir(tokenDir);
    } catch (IOException exception) {
      sessions.remove(token);
      return new GetTokenResult().setError(TokenError.SERVER_ERROR);
    }

    LOGGER.info("Giving out token: " + token);

    // Write client info
    try {
      FileUtils.writeStringToFile(
          Paths.get(
              processingDir,
              token,
              clientJsonFilename).toFile(),
          clientInfoString,
          StandardCharsets.UTF_8);
    } catch (IOException exception) {
      sessions.remove(token);
      return new GetTokenResult().setError(TokenError.SERVER_ERROR);
    }

    return new GetTokenResult().setToken(token);
  }

  @Override
  public Job getJob(String token) throws TException {

    if (!sessions.containsToken(token)) {
      throw new TokenNotFoundException().setToken(token);
    }

    return sessions.lockSessionAndExecute(token, session -> {
      try {
        MDC.put("token", token);
        LOGGER.info("getJob");
        session.touch();

        while (true) {
          if (session.jobQueue.size() == 0) {
            LOGGER.info("no job");
            return new Job().setJobId(0).setNoJob(new NoJob());
          }
          Job res = session.jobQueue.peek().getJob();
          if (res == null) {
            LOGGER.info("There was a server job but it returned null, so it will be removed.");
            session.jobQueue.remove();
            continue;
          }
          StringBuilder logmsg = new StringBuilder();
          logmsg.append("getJob(): worker '" + token
              + "' gets job " + res.getJobId());
          if (res.isSetSkipJob()) {
            logmsg.append(" (skip)");
          } else if (res.isSetImageJob()) {
            logmsg.append("(name: ");
            logmsg.append(res.getImageJob().getName());
            logmsg.append(")");
          } else {
            logmsg.append("(job neither skip nor image? should not happen!)");
          }
          LOGGER.info(logmsg.toString());
          return res;
        }
      } catch (ServerJobException exception) {
        throw new TException(exception);
      } finally {
        MDC.remove("token");
      }
    });

  }

  @Override
  public void jobDone(String token, Job job) throws TException {

    if (!sessions.containsToken(token)) {
      throw new TokenNotFoundException().setToken(token);
    }

    sessions.lockSessionAndExecute(token, session -> {
      try {
        MDC.put("token", token);
        StringBuilder logmsg = new StringBuilder();
        logmsg.append("jobDone(): JobId#" + job.getJobId()
            + " Queue has size: " + session.jobQueue.size());
        if (job.isSetImageJob() && job.getImageJob().isSetResult()) {
          logmsg.append(" job status: " + job.getImageJob().getResult().getStatus());
        }
        LOGGER.info(logmsg.toString());
        final IServerJob serverJob = session.jobQueue.peek();

        // Execute finish job on the server job and see if it should be removed.
        boolean remove = serverJob.finishJob(job);

        LOGGER.info("Returned from finishJob. Removing job? {}", remove);
        if (remove) {
          session.jobQueue.remove();
        }
        return null;
      } catch (ServerJobException exception) {
        throw new TException(exception);
      } finally {
        MDC.remove("token");
      }
    });
  }
}
