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

package com.graphicsfuzz.libgdxclient;

import java.util.Scanner;
import com.graphicsfuzz.server.thrift.JobStage;

public class PersistentData {

  private IPersistentFile persistentFile;

  public PersistentData(IPersistentFile persistentFile) {
    this.persistentFile = persistentFile;
  }

  public PersistentData() {
    this.persistentFile = new PersistentFile();
  }

  private void writeKeyFileHelper(String key, String value, boolean append) {
    persistentFile.store(key, value.getBytes(), append);
  }

  private void writeKeyFile(String key, String value) {
    writeKeyFileHelper(key, value, false);
  }

  private void appendKeyFile(String key, String value) {
    writeKeyFileHelper(key, value, true);
  }

  private String readKeyFile (String key) {
    byte[] data = persistentFile.load(key);
    if (data == null) {
      return "";
    } else {
      return new String(data);
    }
  }

  public void reset() {
    persistentFile.reset(Constants.PERSISTENT_KEY_ERROR_MSG);
    persistentFile.reset(Constants.PERSISTENT_KEY_STAGE);
    persistentFile.reset(Constants.PERSISTENT_KEY_JOB_ID);
    persistentFile.reset(Constants.PERSISTENT_KEY_TIMEOUT);
    persistentFile.reset(Constants.PERSISTENT_KEY_TIME_COMPILE);
    persistentFile.reset(Constants.PERSISTENT_KEY_TIME_FIRST_RENDER);
    persistentFile.reset(Constants.PERSISTENT_KEY_TIME_OTHER_RENDER);
    persistentFile.reset(Constants.PERSISTENT_KEY_TIME_CAPTURE);
    persistentFile.reset(Constants.PERSISTENT_KEY_SANITY);
    persistentFile.reset(Constants.PERSISTENT_KEY_IMG1);
    persistentFile.reset(Constants.PERSISTENT_KEY_IMG2);
    return;
  }

  public void setJobId(long jobId) {
    String str = "" + jobId;
    writeKeyFile(Constants.PERSISTENT_KEY_JOB_ID, str);
  }

  public long getJobId() {
    String str = readKeyFile(Constants.PERSISTENT_KEY_JOB_ID);
    Scanner scan = new Scanner(str);
    if (scan.hasNextLong()) {
      return scan.nextLong();
    } else {
      return -1L;
    }
  }

  public void setStage(JobStage stage) {
    writeKeyFile(Constants.PERSISTENT_KEY_STAGE, String.valueOf(stage.getValue()));
  }

  public JobStage getStage() {
   Scanner scanner = new Scanner(readKeyFile(Constants.PERSISTENT_KEY_STAGE));
    if (scanner.hasNextInt()) {
      return JobStage.findByValue(scanner.nextInt());
    }
    return null;
  }

  public void appendErrMsg(String content) {
    appendKeyFile(Constants.PERSISTENT_KEY_ERROR_MSG, content + "\n");
  }

  public void setString(String key, String s) {
    writeKeyFile(key, s);
  }

  public String getString(String key) {
    return readKeyFile(key);
  }

  public void setInt(String key, int i) {
    writeKeyFile(key, "" + i);
  }

  public int getInt(String key, int def) {
    String str = readKeyFile(key);
    Scanner scan = new Scanner(str);
    if (scan.hasNextInt()) {
      return scan.nextInt();
    } else {
      return def;
    }
  }

  public void setBool(String key, boolean b) {
    writeKeyFile(key, "" + b);
  }

  public boolean getBool(String key, boolean def) {
    String str = readKeyFile(key);
    Scanner scan = new Scanner(str);
    if (scan.hasNextBoolean()) {
      return scan.nextBoolean();
    } else {
      return def;
    }
  }

  public void setBinaryBlob(String key, byte[] blob) {
    persistentFile.store(key, blob, false);
  }

  public byte[] getBinaryBlob(String key) {
    return persistentFile.load(key);
  }
}
