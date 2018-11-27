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

package com.graphicsfuzz.generator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class GenerateAndRunShadersTest {

  @Rule
  public TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Test
  public void testNoReferences() throws Exception {
    final File donors = temporaryFolder.newFolder();
    final File references = temporaryFolder.newFolder();
    final String outputDir = new File(temporaryFolder.getRoot(), "output").getAbsolutePath();

    assertTrue(references.delete());

    try {
      GenerateAndRunShaders.mainHelper(
            new String[]{
                references.getAbsolutePath(),
                donors.getAbsolutePath(),
                outputDir,
                "dummy_server",
                "dummy_worker",
                "100"
            }
      );
      throw new RuntimeException("Exception expected.");
    } catch (IllegalArgumentException exception) {
      assertEquals("References directory does not exist.", exception.getMessage());
    }

  }

  @Test
  public void testNoDonors() throws Exception {
    final File donors = temporaryFolder.newFolder();
    final File references = temporaryFolder.newFolder();
    final String outputDir = new File(temporaryFolder.getRoot(), "output").getAbsolutePath();

    assertTrue(donors.delete());

    try {
      GenerateAndRunShaders.mainHelper(
            new String[] {
                references.getAbsolutePath(),
                donors.getAbsolutePath(),
                outputDir,
                "dummy_server",
                "dummy_worker",
                "100"
            }
      );
      throw new RuntimeException("Exception expected.");
    } catch (IllegalArgumentException exception) {
      assertEquals("Donors directory does not exist.", exception.getMessage());
    }
  }

  @Test
  public void testMissingJson() throws Exception {
    final File donors = temporaryFolder.newFolder();
    final File references = temporaryFolder.newFolder();
    final String outputDir = new File(temporaryFolder.getRoot(), "output").getAbsolutePath();

    new File(references, "a.frag").createNewFile();

    try {
      GenerateAndRunShaders.mainHelper(
            new String[]{
                references.getAbsolutePath(),
                donors.getAbsolutePath(),
                outputDir,
                "dummy_server",
                "dummy_worker",
                "100"
            }
      );
      throw new RuntimeException("Exception expected.");
    } catch (IllegalArgumentException exception) {
      assertEquals("No shader jobs found.", exception.getMessage());
    }
  }

}
