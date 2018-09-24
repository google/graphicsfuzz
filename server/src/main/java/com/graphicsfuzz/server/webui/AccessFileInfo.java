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

package com.graphicsfuzz.server.webui;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.HashMap;

/**
 * Wrapper to access JSON info files. Caches results to speed up the UI. Update results when file
 * is modified.
 */

public class AccessFileInfo {

  Gson gson;

  HashMap<String, JsonObject> workerInfoMap;
  HashMap<String, Long> workerInfoLastModified;

  HashMap<String, JsonObject> resultInfoMap;
  HashMap<String, Long> resultInfoLastModified;

  public AccessFileInfo() {
    gson = new Gson();
    workerInfoMap = new HashMap<String, JsonObject>();
    workerInfoLastModified = new HashMap<String, Long>();
    resultInfoMap = new HashMap<String, JsonObject>();
    resultInfoLastModified = new HashMap<String, Long>();
  }

  // Worker info ==============================================================

  public JsonObject getWorkerInfo(String workerName) throws  FileNotFoundException {
    File workerInfoFile = new File(WebUiConstants.WORKER_DIR
        + "/" + workerName
        + "/" + WebUiConstants.WORKER_INFO_FILE);
    if (workerInfoMap.containsKey(workerName)
        && workerInfoFile.lastModified() == workerInfoLastModified.get(workerName).longValue()) {
      return workerInfoMap.get(workerName);
    } else {
      JsonObject workerInfo = readWorkerInfoFromFile(workerInfoFile);
      workerInfoMap.put(workerName, workerInfo);
      workerInfoLastModified.put(workerName, new Long(workerInfoFile.lastModified()));
      return workerInfo;
    }
  }

  private JsonObject readWorkerInfoFromFile(File workerInfoFile) throws FileNotFoundException {
    JsonObject json = gson
        .fromJson(new FileReader(workerInfoFile), JsonObject.class)
        .getAsJsonObject("platform_info");
    return json;
  }

  // Result info ==============================================================

  public JsonObject getResultInfo(File resultInfoFile) throws FileNotFoundException {
    String resultPath = resultInfoFile.getPath();
    if (resultInfoMap.containsKey(resultPath)
        && resultInfoFile.lastModified() == resultInfoLastModified.get(resultPath).longValue()) {
      return resultInfoMap.get(resultPath);
    } else {
      JsonObject resultInfo = readResultInfoFromFile(resultInfoFile);
      resultInfoMap.put(resultPath, resultInfo);
      resultInfoLastModified.put(resultPath, new Long(resultInfoFile.lastModified()));
      return resultInfo;
    }
  }

  private JsonObject readResultInfoFromFile(File resultInfoFile) throws FileNotFoundException {
    return gson.fromJson(new FileReader(resultInfoFile), JsonObject.class);
  }

}
