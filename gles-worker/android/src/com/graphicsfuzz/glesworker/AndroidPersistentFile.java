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

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.io.RandomAccessFile;

public class AndroidPersistentFile implements IPersistentFile {

  private Context context;

  AndroidPersistentFile(Context context) {
    this.context = context;
  }

  private String makeFilename(String key) {
    return Constants.PERSISTENT_DATA_FILENAME + "_" + key;
  }

  @Override
  public void store(String key, byte[] data, boolean append) {
    try {
      FileOutputStream outStrm = new FileOutputStream(context.getFilesDir() + "/" + makeFilename(key), append);
      outStrm.write(data);
      outStrm.flush();
      outStrm.getFD().sync();
      outStrm.close();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  @Override
  public byte[] load(String key) {
    String filename = makeFilename(key);

    File file = new File(context.getFilesDir(), filename);
    if (!file.exists()) {
      return null;
    }

    byte[] data = null;

    try {
      RandomAccessFile f = new RandomAccessFile(file, "r");
      data = new byte[(int) file.length()];
      f.readFully(data);
      f.close();
    } catch (Exception e) {
      e.printStackTrace();
    }

    return data;
  }

  @Override
  public void reset(String key) {
    File file = new File(context.getFilesDir(), makeFilename(key));
    if (file.exists()) {
      file.delete();
    }
  }
}
