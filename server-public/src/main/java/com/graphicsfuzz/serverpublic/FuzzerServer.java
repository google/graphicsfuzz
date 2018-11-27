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

package com.graphicsfuzz.serverpublic;

import com.graphicsfuzz.server.FileDownloadServlet;
import com.graphicsfuzz.server.FuzzerServiceImpl;
import com.graphicsfuzz.server.FuzzerServiceManagerImpl;
import com.graphicsfuzz.server.thrift.FuzzerService;
import com.graphicsfuzz.server.thrift.FuzzerServiceManager;
import com.graphicsfuzz.util.ToolPaths;
import java.nio.file.Paths;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TJSONProtocol;
import org.apache.thrift.server.TServlet;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.server.handler.gzip.GzipHandler;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class FuzzerServer {

  private static final Logger LOGGER = LoggerFactory.getLogger(FuzzerServer.class);
  private final String workingDir = "";
  private final String shaderSetsDir = "shaderfamilies";
  private final String processingDir = "processing";

  private final ExecutorService executorService = Executors.newCachedThreadPool();

  private final int port;

  public FuzzerServer(int port) {
    this.port = port;
  }

  public void start() throws Exception {

    FuzzerServiceImpl fuzzerService = new FuzzerServiceImpl(
        Paths.get(workingDir, processingDir).toString(),
        executorService);

    FuzzerService.Processor processor =
        new FuzzerService.Processor<FuzzerService.Iface>(fuzzerService);

    FuzzerServiceManagerImpl fuzzerServiceManager = new FuzzerServiceManagerImpl(fuzzerService,
          new PublicServerCommandDispatcher());
    FuzzerServiceManager.Processor managerProcessor =
        new FuzzerServiceManager.Processor<FuzzerServiceManager.Iface>(fuzzerServiceManager);

    ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
    context.setContextPath("/");

    {
      ServletHolder sh = new ServletHolder();
      sh.setServlet(new TServlet(processor, new TBinaryProtocol.Factory()));
      context.addServlet(sh, "/request");
    }
    {
      ServletHolder serveltHolderJson = new ServletHolder();
      serveltHolderJson.setServlet(new TServlet(processor, new TJSONProtocol.Factory()));
      context.addServlet(serveltHolderJson, "/requestJSON");
    }

    {
      ServletHolder shManager = new ServletHolder();
      shManager.setServlet(new TServlet(managerProcessor, new TBinaryProtocol.Factory()));
      context.addServlet(shManager, "/manageAPI");
    }

    final String staticDir = ToolPaths.getStaticDir();
    context.addServlet(
          new ServletHolder(
                new FileDownloadServlet(
                    (pathInfo, worker) -> Paths.get(staticDir, pathInfo).toFile(), staticDir)),
          "/static/*");

    HandlerList handlerList = new HandlerList();
    handlerList.addHandler(context);

    GzipHandler gzipHandler = new GzipHandler();
    gzipHandler.setHandler(handlerList);

    Server server = new Server(port);

    server.setHandler(gzipHandler);
    server.start();
    server.join();
  }
}
