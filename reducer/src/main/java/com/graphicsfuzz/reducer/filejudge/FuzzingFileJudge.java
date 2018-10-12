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

package com.graphicsfuzz.reducer.filejudge;

import com.graphicsfuzz.reducer.FileJudgeException;
import com.graphicsfuzz.reducer.IFileJudge;
import com.graphicsfuzz.shadersets.IShaderDispatcher;
import java.io.File;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.io.FileUtils;

public class FuzzingFileJudge implements IFileJudge {

  private final File crashes;
  private final IShaderDispatcher imageGenerator;

  private int canonicalHash = 256;

  public FuzzingFileJudge(File corpus, IShaderDispatcher imageGenerator) {
    this.crashes = new File(corpus, "crashes");
    this.imageGenerator = imageGenerator;
  }

  @Override
  public boolean isInteresting(
      File shaderJobFile,
      File shaderResultFileOutput
  ) {
    throw new RuntimeException();
    /*
    try {
      ExecResult res = ToolHelper.runValidatorOnShader(ExecHelper.RedirectType.TO_LOG, file);
      if (res.res != 0) {
        return false;
      }

      File outputImage = new File(workingDir,
            FilenameUtils.removeExtension(file.getName()) + ".png");

      ImageJobResult imageRes = imageGenerator.getImage(
            file, outputImage, false);
      if (outputImage.isFile()) {
        outputImage.delete();
      }

      switch (imageRes.getStatus()) {
        case SUCCESS:
          break;
        case UNEXPECTED_ERROR:
          this.recordCrash(file);
          return false;
        default:
          throw new AssertionError(
                imageRes.getStatus() + " should not be a possible response here.");
      }

      int classification = this.classifyContents(imageRes.getPNG());

      if (this.canonicalHash == 256) {
        this.canonicalHash = classification;
        return true;
      }

      return classification == this.canonicalHash;

    } catch (InterruptedException | IOException | ShaderDispatchException ex) {
      throw new FileJudgeException(ex);
    }
    */
  }

  private int classifyContents(byte[] imageContents) {
    return this.sha1(imageContents)[0] & 0xff;
  }

  private byte[] sha1(byte[] imageContents) {
    MessageDigest crypt;
    try {
      crypt = MessageDigest.getInstance("SHA-1");
    } catch (NoSuchAlgorithmException ex) {
      throw new AssertionError(ex);
    }
    crypt.reset();
    crypt.update(imageContents);
    return crypt.digest();
  }

  private void recordCrash(File file) throws IOException {
    if (!this.crashes.isDirectory()) {
      this.crashes.mkdirs();
    }
    String hash = Hex.encodeHexString(this.sha1(FileUtils.readFileToByteArray(file)));
    FileUtils.copyFile(
          file,
          new File(crashes, file.getName() + "." + hash.substring(0, 8))
    );
  }
}
