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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Paths;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.io.IOUtils;


public class ClasspathFileServlet extends HttpServlet {

  private String directory = "ogltestingstatic";

  @Override
  protected void doGet(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {

    String pathInfo = request.getPathInfo();
    if (pathInfo == null) {
      throw new ServletException("Missing path!");
    }

    // Here, we try hard to make sure users cannot do ".." to access the class files
    // of our server. I think Jetty might guard against this anyway.

    // pathInfo example: "/index.html"

    if (pathInfo.contains("\\") || pathInfo.contains("%") || pathInfo.contains("/../")) {
      throw new RuntimeException("Invalid URL");
    }
    pathInfo = "/" + pathInfo.substring(1);
    pathInfo = Paths.get(pathInfo).normalize().toString();
    pathInfo = pathInfo.replaceAll("\\\\", "/");

    if (!pathInfo.startsWith("/") || pathInfo.contains("/../")) {
      throw new RuntimeException("Invalid URL");
    }

    pathInfo = directory + pathInfo;

    ClassLoader cl = Thread.currentThread().getContextClassLoader();

    try (InputStream is = cl.getResourceAsStream(pathInfo)) {
      if (is == null) {
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        return;
      }
      response.setStatus(HttpServletResponse.SC_OK);
      try (OutputStream out = response.getOutputStream()) {
        IOUtils.copy(is, out);
      }
    }
  }

}
