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

package com.graphicsfuzz.shadersets;

import com.graphicsfuzz.server.thrift.FuzzerServiceManager;
import com.graphicsfuzz.server.thrift.FuzzerServiceManager.Iface;
import com.graphicsfuzz.server.thrift.ImageJob;
import com.graphicsfuzz.server.thrift.ImageJobResult;
import com.graphicsfuzz.server.thrift.Job;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.thrift.TException;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.transport.THttpClient;
import org.apache.thrift.transport.TTransport;
import org.apache.thrift.transport.TTransportException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RemoteShaderDispatcher implements IShaderDispatcher {

  private static final Logger LOGGER = LoggerFactory.getLogger(RemoteShaderDispatcher.class);

  private final String url;
  private final String worker;
  private final FuzzerServiceManager.Iface fuzzerServiceManager;

  private final AtomicLong jobCounter;
  private final int retryLimit;

  private static final int DEFAULT_RETRY_LIMIT = 2;

  public RemoteShaderDispatcher(
      String url,
      String worker,
      Iface fuzzerServiceManager,
      AtomicLong jobCounter) {
    this(url, worker, fuzzerServiceManager, jobCounter, DEFAULT_RETRY_LIMIT);
  }

  public RemoteShaderDispatcher(
      String url,
      String worker,
      Iface fuzzerServiceManager,
      AtomicLong jobCounter,
      int retryLimit) {
    this.url = url;
    this.worker = worker;
    this.fuzzerServiceManager = fuzzerServiceManager;
    this.jobCounter = jobCounter;
    this.retryLimit = retryLimit;
  }

  public RemoteShaderDispatcher(String url, String worker) {
    this(url, worker, null, new AtomicLong(), DEFAULT_RETRY_LIMIT);
  }

  @Override
  public ImageJobResult getImage(ImageJob imageJob) throws ShaderDispatchException {

    LOGGER.info("Get image (via server) job: {}", imageJob.getName());

    // Due to strange Thrift behaviour, we set this default value explicitly
    // otherwise "isSetSkipRender()" is false.
    if (!imageJob.isSetSkipRender()) {
      imageJob.setSkipRender(false);
    }

    // Optimisation: no need to actually use HTTP if we are on the server.
    if (fuzzerServiceManager != null) {
      try {
        return getImageHelper(imageJob, fuzzerServiceManager);
      } catch (TException exception) {
        throw new ShaderDispatchException(exception);
      }
    } else {
      try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
        return getImageHelper(imageJob, getFuzzerServiceManagerProxy(httpClient));
      } catch (IOException | TException exception) {
        throw new ShaderDispatchException(exception);
      }
    }
  }

  private ImageJobResult getImageHelper(
      ImageJob imageJob,
      FuzzerServiceManager.Iface fuzzerServiceManagerProxy) throws TException {

    final Job job = new Job()
        .setJobId(jobCounter.incrementAndGet())
        .setImageJob(imageJob);

    return fuzzerServiceManagerProxy.submitJob(job, worker, retryLimit)
        .getImageJob()
        .getResult();
  }

  private Iface getFuzzerServiceManagerProxy(CloseableHttpClient httpClient)
      throws TTransportException {
    TTransport transport = new THttpClient(url, httpClient);
    transport.open();
    TProtocol protocol = new TBinaryProtocol(transport);
    return new FuzzerServiceManager.Client(
        protocol);
  }

}
