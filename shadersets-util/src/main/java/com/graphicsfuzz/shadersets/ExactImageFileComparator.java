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

import java.io.File;
import java.io.IOException;
import org.apache.commons.io.FileUtils;

public class ExactImageFileComparator implements IImageFileComparator {

  private final boolean identicalIsInteresting;

  public ExactImageFileComparator(boolean identicalIsInteresting) {
    this.identicalIsInteresting = identicalIsInteresting;
  }


  @Override
  public boolean areFilesInteresting(File reference, File variant) {
    try {
      assert reference.exists();
      assert variant.exists();
      boolean equalContent = FileUtils.contentEquals(reference, variant);
      if (!equalContent && identicalIsInteresting) {
        System.err.println("Not interesting: images do not match");
        return false;
      }
      if (equalContent && !identicalIsInteresting) {
        System.err.println("Not interesting: images match");
        return false;
      }
      return true;
    } catch (IOException exception) {
      System.err.println("Not interesting: exception while comparing files - "
          + exception.getMessage());
      return false;
    }
  }
}
