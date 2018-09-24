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

import static com.graphicsfuzz.server.thrift.FuzzerServiceConstants.DOWNLOAD_FIELD_NAME_TOKEN;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FileDownloadServlet extends HttpServlet {

  private static final Logger LOGGER = LoggerFactory.getLogger(FileDownloadServlet.class);

  @FunctionalInterface
  public interface DownloadProcessor {

    /**
     * @return The file to write to.
     */
    File processDownload(String pathInfo, String token) throws DownloadException;
  }

  private final DownloadProcessor downloadProcessor;

  private final Path directoryRoot;

  /**
   * @param directoryRoot A safety precaution: only files inside this directory can be accessed.
   *                      Downloading files above this directory is disallowed.
   */
  public FileDownloadServlet(DownloadProcessor downloadProcessor, String directoryRoot) {
    this.downloadProcessor = downloadProcessor;
    this.directoryRoot = Paths.get(directoryRoot).toAbsolutePath().normalize();
  }

  @Override
  protected void doGet(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {

    String pathInfo = request.getPathInfo();
    if (pathInfo == null) {
      throw new ServletException("Missing path!");
    }
    pathInfo = pathInfo.substring(1);
    File file = null;
    String token = request.getParameter(DOWNLOAD_FIELD_NAME_TOKEN);
    try {
      file = downloadProcessor.processDownload(pathInfo, token);
    } catch (DownloadException exception) {
      throw new ServletException(exception);
    }
    Path pathOfFile = file.toPath().toAbsolutePath();
    if (!pathOfFile.startsWith(directoryRoot)) {
      throw new IOException("Invalid path!");
    }

    try (FileInputStream fis = new FileInputStream(file)) {
      response.setStatus(HttpServletResponse.SC_OK);
      String contentType = "text/plain";
      try {
        contentType = Files.probeContentType(pathOfFile);
      } catch (IOException exception) {
        LOGGER.info("Failed to probe content type of file path: {}", pathOfFile, exception);
      }
      response.setContentType(contentType);
      try (OutputStream out = response.getOutputStream()) {
        IOUtils.copy(fis, out);
      }
    }

  }
}
