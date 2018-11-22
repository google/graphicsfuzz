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

import com.graphicsfuzz.common.util.ReductionProgressHelper;
import com.graphicsfuzz.common.util.ShaderJobFileOperations;
import java.io.File;
import java.io.IOException;
import java.util.Optional;

public class ReductionFilesHelper {

  static File getReductionDir(String token, String shaderSet, String variant) {
    return new File(WebUiConstants.WORKER_DIR + "/" + token + "/" + shaderSet
        + "/reductions/" + variant);
  }

  static Optional<File> getLatestReductionImage(
      String token,
      String shaderset,
      String shader,
      ShaderJobFileOperations fileOps) throws IOException {

    final File reductionDir = getReductionDir(token, shaderset, shader);
    final Optional<Integer> latestSuccessfulReductionStep =
          ReductionProgressHelper.getLatestReductionStepSuccess(
              reductionDir,
              "variant",
              fileOps);

    if (!latestSuccessfulReductionStep.isPresent()) {
      return Optional.empty();
    }
    final File simplifiedImage = new File(reductionDir, shader + "_reduced_"
          + latestSuccessfulReductionStep.get() + "_simplified.png");
    if (simplifiedImage.exists()) {
      return Optional.of(simplifiedImage);
    }
    final File latestImage = new File(reductionDir, shader + "_reduced_"
          + latestSuccessfulReductionStep.get() + "_success.png");
    if (!latestImage.exists()) {
      return Optional.empty();
    }
    return Optional.of(latestImage);
  }

}
