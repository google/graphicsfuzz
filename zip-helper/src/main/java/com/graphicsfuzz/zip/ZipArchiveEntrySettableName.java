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

package com.graphicsfuzz.zip;

import java.io.File;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;

/**
 * ZipArchiveEntry from commons compress has a protected setName method; we expose it as a public
 * method.
 */
public class ZipArchiveEntrySettableName extends ZipArchiveEntry {
  public ZipArchiveEntrySettableName(String name) {
    super(name);
  }

  public ZipArchiveEntrySettableName(ZipEntry entry) throws ZipException {
    super(entry);
  }

  public ZipArchiveEntrySettableName(ZipArchiveEntry entry) throws ZipException {
    super(entry);
  }

  public ZipArchiveEntrySettableName() {
  }

  public ZipArchiveEntrySettableName(File inputFile, String entryName) {
    super(inputFile, entryName);
  }

  public void setName(String name) {
    super.setName(name);
  }
}
