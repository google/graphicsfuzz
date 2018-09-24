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

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.graphicsfuzz.server.thrift.ComputeJob;
import com.graphicsfuzz.server.thrift.ComputeJobResult;
import com.graphicsfuzz.server.thrift.FuzzerServiceManager;
import com.graphicsfuzz.server.thrift.FuzzerServiceManager.Iface;
import com.graphicsfuzz.server.thrift.ImageJob;
import com.graphicsfuzz.server.thrift.ImageJobResult;
import com.graphicsfuzz.server.thrift.Job;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
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

  private String url;
  private String token;
  private FuzzerServiceManager.Iface fuzzerServiceManager;

  private final AtomicLong jobCounter;
  private final int retryLimit;

  private static final int DEFAULT_RETRY_LIMIT = 2;

  public RemoteShaderDispatcher(String url, String token,
      FuzzerServiceManager.Iface fuzzerServiceManager, AtomicLong jobCounter) {
    this(url, token, fuzzerServiceManager, jobCounter, DEFAULT_RETRY_LIMIT);
  }

  public RemoteShaderDispatcher(String url, String token,
      FuzzerServiceManager.Iface fuzzerServiceManager, AtomicLong jobCounter, int retryLimit) {
    this.url = url;
    this.token = token;
    this.fuzzerServiceManager = fuzzerServiceManager;
    this.jobCounter = jobCounter;
    this.retryLimit = retryLimit;
  }

  public RemoteShaderDispatcher(String url, String token) {
    this(url, token, null, new AtomicLong(), DEFAULT_RETRY_LIMIT);
  }

  @Override
  public ImageJobResult getImage(
      String shaderFilesPrefix,
      File outputImageFile,
      boolean skipRender) throws ShaderDispatchException {

    LOGGER.info("Get image (via server) {}", shaderFilesPrefix);

    // Optimisation: no need to actually use HTTP if we are on the server.
    if (fuzzerServiceManager != null) {
      try {
        return getImageHelper(shaderFilesPrefix, fuzzerServiceManager, skipRender);
      } catch (IOException | TException exception) {
        throw new ShaderDispatchException(exception);
      }
    } else {
      try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
        return getImageHelper(shaderFilesPrefix, getFuzzerServiceManagerProxy(httpClient),
            skipRender);
      } catch (IOException | TException exception) {
        throw new ShaderDispatchException(exception);
      }
    }
  }

  private ImageJobResult getImageHelper(String shaderFilesPrefix,
      FuzzerServiceManager.Iface fuzzerServiceManagerProxy,
      boolean skipRender)
      throws IOException, TException {

    ImageJob imageJob = new ImageJob()
        .setSkipRender(skipRender)
        .setName(FilenameUtils.getBaseName(shaderFilesPrefix));

    final File uniformsFile = new File(shaderFilesPrefix + ".json");
    final File fragmentFile = new File(shaderFilesPrefix + ".frag");
    final File vertexFile = new File(shaderFilesPrefix + ".vert");
    final File primitivesFile = new File(shaderFilesPrefix + ".primitives");

    imageJob.setUniformsInfo(FileUtils.readFileToString(uniformsFile, Charset.defaultCharset()));

    if (fragmentFile.isFile()) {
      imageJob.setFragmentSource(FileUtils.readFileToString(
          fragmentFile,
          Charset.defaultCharset()));
    }

    if (vertexFile.isFile()) {
      imageJob.setVertexSource(FileUtils.readFileToString(vertexFile, Charset.defaultCharset()));
    }

    if (primitivesFile.isFile()) {
      setPrimitives(imageJob, primitivesFile);
    }

    final Job job = new Job()
        .setJobId(jobCounter.incrementAndGet())
        .setImageJob(imageJob);

    return fuzzerServiceManagerProxy.submitJob(job, token, retryLimit)
        .getImageJob()
        .getResult();
  }

  private void setPrimitives(ImageJob imageJob, File primitivesFile) throws IOException {
    JsonObject json = new Gson().fromJson(new FileReader(primitivesFile),
        JsonObject.class);
    imageJob.setPoints(getPointsFromJson(json, "points"));
    if (json.has("texPoints")) {
      imageJob.setTexturePoints(getPointsFromJson(json, "texPoints"));
      if (!json.has("texture")) {
        throw new RuntimeException("If texture points are provided, a texture must be provided");
      }
      final File textureFile = new File(primitivesFile.getParentFile(),
          json.get("texture").getAsString());
      if (!textureFile.isFile()) {
        throw new RuntimeException("Could not find texture file " + textureFile.getAbsolutePath());
      }
      imageJob.setTextureBinary(FileUtils.readFileToByteArray(textureFile));
    }

  }

  private List<Double> getPointsFromJson(JsonObject json, String key) {
    final List<Double> result = new ArrayList<>();
    final JsonArray points = json.get(key).getAsJsonArray();
    for (int i = 0; i < points.size(); i++) {
      result.add(points.get(i).getAsDouble());
    }
    return result;
  }

  @Override
  public ComputeJobResult dispatchCompute(File computeShaderFile, boolean skipExecution)
      throws ShaderDispatchException {
    LOGGER.info("Dispatch compute (via server) {}", computeShaderFile);

    // Optimisation: no need to actually use HTTP if we are on the server.
    if (fuzzerServiceManager != null) {
      try {
        return dispatchComputeHelper(computeShaderFile, fuzzerServiceManager, skipExecution);
      } catch (IOException | TException exception) {
        throw new ShaderDispatchException(exception);
      }
    } else {
      try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
        return dispatchComputeHelper(computeShaderFile, getFuzzerServiceManagerProxy(httpClient),
            skipExecution);
      } catch (IOException | TException exception) {
        throw new ShaderDispatchException(exception);
      }
    }
  }

  private ComputeJobResult dispatchComputeHelper(File computeShaderFile,
      FuzzerServiceManager.Iface fuzzerServiceManagerProxy,
      boolean skipExecution) throws TException, IOException {

    final String computeShaderFilePrefix = FilenameUtils.removeExtension(computeShaderFile
        .getAbsolutePath());

    ComputeJob computeJob = new ComputeJob()
        .setName(FilenameUtils.getBaseName(computeShaderFilePrefix));

    final File environmentFile = new File(computeShaderFilePrefix + ".json");

    computeJob.setEnvironment(
        FileUtils.readFileToString(environmentFile, Charset.defaultCharset()));
    computeJob.setComputeSource(
        FileUtils.readFileToString(computeShaderFile, Charset.defaultCharset()));

    final Job job = new Job()
        .setJobId(jobCounter.incrementAndGet())
        .setComputeJob(computeJob);

    return fuzzerServiceManagerProxy.submitJob(job, token, retryLimit)
        .getComputeJob()
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
