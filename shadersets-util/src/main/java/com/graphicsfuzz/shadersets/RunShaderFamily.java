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

import com.google.gson.JsonObject;
import com.graphicsfuzz.common.util.IShaderSet;
import com.graphicsfuzz.common.util.IShaderSetExperiment;
import com.graphicsfuzz.common.util.JsonHelper;
import com.graphicsfuzz.common.util.LocalShaderSet;
import com.graphicsfuzz.common.util.LocalShaderSetExperiement;
import com.graphicsfuzz.gifsequencewriter.GifSequenceWriter;
import com.graphicsfuzz.server.thrift.FuzzerServiceManager;
import com.graphicsfuzz.server.thrift.ImageJobResult;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import javax.imageio.ImageIO;
import javax.imageio.stream.FileImageOutputStream;
import javax.imageio.stream.ImageOutputStream;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RunShaderFamily {

  private static final Logger LOGGER = LoggerFactory.getLogger(RunShaderFamily.class);

  public static void main(String[] args) {
    try {
      mainHelper(args, null);
    } catch (ArgumentParserException exception) {
      exception.getParser().handleError(exception);
      System.exit(1);
    } catch (Exception ex) {
      ex.printStackTrace();
      System.exit(1);
    }
  }

  public static void mainHelper(
      String[] args,
      FuzzerServiceManager.Iface managerOverride)
      throws ShaderDispatchException, InterruptedException, IOException, ArgumentParserException {

    ArgumentParser parser = ArgumentParsers.newArgumentParser("RunShaderFamily")
        .defaultHelp(true)
        .description("Get images for all shaders in a shader set.");

    parser.addArgument("--verbose")
        .action(Arguments.storeTrue())
        .help("Verbose output.");

    parser.addArgument("--server")
        .help(
            "URL of server to use for sending get image requests.")
        .type(String.class);

    parser.addArgument("--token")
        .help("The token of the client used for get image requests. Used with --server.")
        .type(String.class);

    parser.addArgument("--output")
        .help("Output directory.")
        .setDefault(new File("."))
        .type(File.class);

    parser.addArgument("shader_family")
        .help("Shader family directory, or prefix of single shader job")
        .type(String.class);


    Namespace ns = parser.parseArgs(args);

    final boolean verbose = ns.get("verbose");
    final String shaderFamily = ns.get("shader_family");
    final String server = ns.get("server");
    final String token = ns.get("token");
    final File outputDir = ns.get("output");

    if (managerOverride != null && (server == null || token == null)) {
      throw new ArgumentParserException(
          "Must supply server (dummy string) and token when executing in server process.",
          parser);
    }

    if (server != null) {
      if (token == null) {
        throw new ArgumentParserException("Must supply token with server.", parser);
      }
    }

    IShaderDispatcher imageGenerator =
        server == null
            ? new LocalShaderDispatcher(false)
            : new RemoteShaderDispatcher(
                server + "/manageAPI",
                token,
                managerOverride,
                new AtomicLong());

    FileUtils.forceMkdir(outputDir);

    if (!new File(shaderFamily).isDirectory()) {
      if (!new File(shaderFamily + ".json").exists()) {
        throw new ArgumentParserException(
            "Shader family must be a directory or the prefix of a single shader job.", parser);
      }
      // Special case: run get image on a single shader.
      runShader(outputDir, shaderFamily, imageGenerator,
          Optional.empty());
      return;
    }

    IShaderSet shaderSet = new LocalShaderSet(new File(shaderFamily));

    runShaderFamily(shaderSet, outputDir, imageGenerator);
  }

  public static int runShaderFamily(IShaderSet shaderSet, File experimentOutDir,
                                    IShaderDispatcher imageGenerator)
      throws ShaderDispatchException, InterruptedException, IOException {

    int numShadersRun = 0;

    IShaderSetExperiment experiment =
        new LocalShaderSetExperiement(
            experimentOutDir.toString(),
            shaderSet);

    if (experiment.getReferenceImage() == null && experiment.getReferenceTextFile() == null) {
      runShader(
          experimentOutDir,
          FilenameUtils.removeExtension(shaderSet.getReference().toString()),
          imageGenerator,
          Optional.empty());
      ++numShadersRun;
    }

    if (experiment.getReferenceImage() == null) {
      LOGGER.info("Recipient failed to render, so skipping variants.");
      return numShadersRun;
    }

    for (File variant : shaderSet.getVariants()) {

      boolean foundImageOrTextFile =
          experiment
              .getVariantImages()
              .anyMatch(f ->
                  FilenameUtils.removeExtension(f.getName())
                      .equals(FilenameUtils.removeExtension(variant.getName())))
              ||
              experiment
                  .getVariantTextFiles()
                  .anyMatch(f ->
                      FilenameUtils.removeExtension(f.getName())
                          .equals(FilenameUtils.removeExtension(variant.getName())));

      if (foundImageOrTextFile) {
        LOGGER.info("Skipping {} because we already have a result.", variant);
      } else {
        try {
          runShader(
              experimentOutDir,
              FilenameUtils.removeExtension(variant.toString()),
              imageGenerator,
              Optional.of(new ImageData(experiment.getReferenceImage())));

        } catch (Exception err) {
          LOGGER.error("runShader() raise exception on {}", variant);
          err.printStackTrace();
        }
        ++numShadersRun;
      }
    }
    return numShadersRun;
  }

  public static ImageJobResult runShader(
      File outputDir,
      String shaderJobPrefix,
      IShaderDispatcher imageGenerator,
      Optional<ImageData> referenceImage)
      throws ShaderDispatchException, InterruptedException, IOException {

    final String shaderName = new File(shaderJobPrefix).getName();
    final File outputImage = new File(outputDir, shaderName + ".png");
    final File outputText = new File(outputDir, shaderName + ".txt");

    LOGGER.info("Shader set experiment: {} ", shaderJobPrefix);
    ImageJobResult res = imageGenerator.getImage(shaderJobPrefix, outputImage, false);

    if (res.isSetLog()) {
      FileUtils.writeStringToFile(outputText, res.getLog(), Charset.defaultCharset());
    }
    if (res.isSetPNG()) {
      FileUtils.writeByteArrayToFile(outputImage, res.getPNG());
    }

    // Create gif when there is two image files set. This may happen not only for NONDET state,
    // but also in case of Sanity error after a nondet.
    if (res.isSetPNG() && res.isSetPNG2()) {
      // we can dump both images
      File outputNondet1 = new File(outputDir, shaderName + "_nondet1.png");
      File outputNondet2 = new File(outputDir, shaderName + "_nondet2.png");
      FileUtils.writeByteArrayToFile(outputNondet1, res.getPNG());
      FileUtils.writeByteArrayToFile(outputNondet2, res.getPNG2());

      // Create gif
      try {
        BufferedImage nondetImg = ImageIO.read(
            Thread.currentThread().getContextClassLoader().getResourceAsStream("nondet.png"));
        BufferedImage img1 = ImageIO.read(
            new ByteArrayInputStream(res.getPNG()));
        BufferedImage img2 = ImageIO.read(
            new ByteArrayInputStream(res.getPNG2()));
        File gifFile = new File(outputDir,shaderName + ".gif");
        ImageOutputStream gifOutput = new FileImageOutputStream(gifFile);
        GifSequenceWriter gifWriter = new GifSequenceWriter(gifOutput, img1.getType(), 500, true);
        gifWriter.writeToSequence(nondetImg);
        gifWriter.writeToSequence(img1);
        gifWriter.writeToSequence(img2);
        gifWriter.close();
        gifOutput.close();
      } catch (Exception err) {
        LOGGER.error("Error while creating GIF for nondet");
        err.printStackTrace();
      }
    }

    // Dump job info in JSON
    File outputJson = new File(outputDir,shaderName + ".info.json");
    JsonObject infoJson = makeInfoJson(res, outputImage, referenceImage);
    FileUtils.writeStringToFile(outputJson,
        JsonHelper.jsonToString(infoJson), Charset.defaultCharset());

    return res;
  }

  private static JsonObject makeInfoJson(ImageJobResult res, File outputImage,
      Optional<ImageData> referenceImage) {
    JsonObject infoJson = new JsonObject();
    if (res.isSetTimingInfo()) {
      JsonObject timingInfoJson = new JsonObject();
      timingInfoJson.addProperty("compilationTime", res.timingInfo.compilationTime);
      timingInfoJson.addProperty("linkingTime", res.timingInfo.linkingTime);
      timingInfoJson.addProperty("firstRenderTime", res.timingInfo.firstRenderTime);
      timingInfoJson.addProperty("otherRendersTime", res.timingInfo.otherRendersTime);
      timingInfoJson.addProperty("captureTime", res.timingInfo.captureTime);
      infoJson.add("timingInfo", timingInfoJson);
    }
    if (res.isSetPNG() && referenceImage.isPresent()) {
      try {
        // Add image data, e.g. histogram distance
        final Map<String, Double> imageStats = referenceImage.get().getImageDiffStats(
            new ImageData(outputImage.getAbsolutePath()));
        final JsonObject metrics = new JsonObject();
        for (String key : imageStats.keySet()) {
          metrics.addProperty(key, imageStats.get(key));
        }
        boolean isIdentical =
              ImageUtil.identicalImages(outputImage, referenceImage.get().imageFile);
        metrics.addProperty("identical", isIdentical);
        infoJson.add("metrics", metrics);
      } catch (FileNotFoundException err) {
        err.printStackTrace();
      }
    }
    if (res.isSetStage()) {
      infoJson.addProperty("stage", res.stage.toString());
    }
    if (res.isSetStatus()) {
      infoJson.addProperty("Status", res.getStatus().toString());
    }
    infoJson.addProperty("passSanityCheck", "" + res.passSanityCheck);
    return infoJson;
  }

}
