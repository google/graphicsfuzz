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

package com.graphicsfuzz.common.util;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.graphicsfuzz.alphanumcomparator.AlphanumComparator;
import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.glslversion.ShadingLanguageVersion;
import com.graphicsfuzz.common.tool.PrettyPrinterVisitor;
import com.graphicsfuzz.common.transformreduce.GlslShaderJob;
import com.graphicsfuzz.common.transformreduce.ShaderJob;
import com.graphicsfuzz.gifsequencewriter.GifSequenceWriter;
import com.graphicsfuzz.server.thrift.FuzzerServiceConstants;
import com.graphicsfuzz.server.thrift.ImageComparisonMetric;
import com.graphicsfuzz.server.thrift.ImageJob;
import com.graphicsfuzz.server.thrift.ImageJobResult;
import com.graphicsfuzz.server.thrift.JobStatus;
import com.graphicsfuzz.server.thrift.ResultConstant;
import com.graphicsfuzz.shadersets.ShaderDispatchException;
import com.graphicsfuzz.util.ExecHelper;
import com.graphicsfuzz.util.ExecResult;
import com.graphicsfuzz.util.ToolHelper;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import javax.imageio.stream.FileImageOutputStream;
import javax.imageio.stream.ImageOutputStream;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.bytedeco.javacpp.opencv_core;
import org.bytedeco.javacpp.opencv_imgcodecs;
import org.bytedeco.javacpp.opencv_imgproc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ShaderJobFileOperations {

  private static final Logger LOGGER = LoggerFactory.getLogger(ShaderJobFileOperations.class);
  public static final String FUZZY_DIFF_KEY = "fuzzydiff";

  public boolean areImagesOfShaderResultsIdentical(
      File referenceShaderResultFile,
      File variantShaderResultFile) throws IOException {

    //noinspection deprecation: OK in this class.
    File reference = getUnderlyingImageFileFromShaderJobResultFile(referenceShaderResultFile);
    //noinspection deprecation: OK in this class.
    File variant = getUnderlyingImageFileFromShaderJobResultFile(variantShaderResultFile);

    LOGGER.info("Comparing: {} and {}.", reference, variant);
    boolean identical = FileUtils.contentEquals(reference, variant);
    LOGGER.info("Identical? {}", identical);

    return identical;
  }

  public boolean areImagesOfShaderResultsInteresting(
      File referenceShaderResultFile,
      File variantShaderResultFile,
      ImageComparisonMetric metric,
      double threshold,
      boolean aboveThresholdIsInteresting) throws IOException {


    //noinspection deprecation: OK in this class.
    File reference = getUnderlyingImageFileFromShaderJobResultFile(referenceShaderResultFile);
    //noinspection deprecation: OK in this class.
    File variant = getUnderlyingImageFileFromShaderJobResultFile(variantShaderResultFile);

    LOGGER.info("Comparing: {} and {}.", reference, variant);

    boolean result;
    String comparisonValue;

    switch (metric) {

      case HISTOGRAM_CHISQR: {
        final double diff = ImageUtil.compareHistograms(
            ImageUtil.getHistogram(reference.toString()),
            ImageUtil.getHistogram(variant.toString()));
        result = (aboveThresholdIsInteresting ? diff > threshold : diff <= threshold);
        comparisonValue = String.valueOf(diff);
        break;
      }
      case PSNR: {
        final double diff = ImageUtil.comparePSNR(reference, variant);
        result = (aboveThresholdIsInteresting ? diff > threshold : diff <= threshold);
        comparisonValue = String.valueOf(diff);
        break;
      }
      case FUZZY_DIFF: {
        try {
          FuzzyImageComparison.MainResult mainResult =
              FuzzyImageComparison.mainHelper(
                  new String[] {reference.toString(), variant.toString()});
          // Fuzzy diff has its own thresholds; images are different if a threshold is exceeded.
          // We negate this if needed:
          result = (aboveThresholdIsInteresting == mainResult.areImagesDifferent);
          comparisonValue = mainResult.outputsString();
          break;
        } catch (ArgumentParserException exception) {
          throw new RuntimeException(exception);
        }
      }
      default:
        throw new RuntimeException("Unrecognised image comparison metric: " + metric.toString());
    }

    if (result) {
      LOGGER.info("Interesting");
    } else {
      LOGGER.info("Not interesting");
    }
    LOGGER.info(": comparison value is " + comparisonValue);
    return result;

  }

  public boolean areShadersValid(
      File shaderJobFile,
      boolean throwExceptionOnInvalid)
      throws IOException, InterruptedException {
    for (ShaderKind shaderKind : ShaderKind.values()) {
      //noinspection deprecation: OK from within this class.
      final File shaderFile = getUnderlyingShaderFile(shaderJobFile, shaderKind);
      if (shaderFile.isFile() && !shaderIsValid(shaderFile, throwExceptionOnInvalid)) {
        return false;
      }
    }
    return true;
  }

  public boolean areShadersValidShaderTranslator(
      File shaderJobFile,
      boolean throwExceptionOnInvalid)
      throws IOException, InterruptedException {
    for (ShaderKind shaderKind : ShaderKind.values()) {
      //noinspection deprecation: OK from within this class.
      final File shaderFile = getUnderlyingShaderFile(shaderJobFile, shaderKind);
      if (shaderFile.isFile()) {
        final ShadingLanguageVersion shadingLanguageVersion = ShadingLanguageVersion
            .getGlslVersionFromFirstTwoLines(getFirstTwoLinesOfShader(shaderJobFile, shaderKind));
        if (!shaderIsValidShaderTranslator(shaderFile, shadingLanguageVersion,
            throwExceptionOnInvalid)) {
          return false;
        }
      }
    }
    return true;
  }

  public void assertExists(File file) throws FileNotFoundException {
    if (!Files.exists(file.toPath())) {
      throw new FileNotFoundException("Could not find " + file);
    }
  }

  // TODO: We should really have: ShaderJobFile, ShaderJobAST, ShaderJobForWorker.
  // For now, ShaderJobForWorker is ImageJob.

  public void assertShaderJobRequiredFilesExist(File shaderJobFile) throws FileNotFoundException {
    assertIsShaderJobFile(shaderJobFile);
    String shaderJobFileNoExtension = FilenameUtils.removeExtension(shaderJobFile.toString());

    assertExists(shaderJobFile);

    boolean shaderExists = false;
    //noinspection ConstantConditions
    shaderExists |= new File(shaderJobFileNoExtension + ".frag").isFile();
    shaderExists |= new File(shaderJobFileNoExtension + ".vert").isFile();
    shaderExists |= new File(shaderJobFileNoExtension + ".comp").isFile();

    if (!shaderExists) {
      throw new FileNotFoundException(
          "Cannot find vertex, fragment or compute shader at "
              + shaderJobFileNoExtension
              + ".[vert/frag/comp]");
    }

  }

  public void copyFile(File srcFile, File destFile, boolean replaceExisting) throws IOException {
    if (replaceExisting) {
      Files.copy(srcFile.toPath(), destFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
    } else {
      Files.copy(srcFile.toPath(), destFile.toPath());
    }
  }

  public void copyShaderJobFileTo(
      File shaderJobFileSource,
      File shaderJobFileDest,
      boolean replaceExisting) throws IOException {
    // TODO: Should we only move/copy particular files?
    copyOrMoveShaderJobFileTo(shaderJobFileSource, shaderJobFileDest, replaceExisting, true);
  }

  public void copyShaderJobResultFileTo(
      File shaderResultFileSource,
      File shaderResultFileDest,
      boolean replaceExisting) throws IOException {
    // TODO: Should we only move/copy particular files?
    copyOrMoveShaderJobResultFileTo(
        shaderResultFileSource,
        shaderResultFileDest,
        replaceExisting,
        true);
  }

  public void createFile(File file) throws IOException {
    Files.createFile(file.toPath());
  }

  public void deleteDirectory(File directory) throws IOException {
    FileUtils.deleteDirectory(directory);
  }

  public boolean deleteQuietly(File file) {
    return FileUtils.deleteQuietly(file);
  }

  public void deleteFile(File file) throws IOException {
    Files.delete(file.toPath());
  }

  public void deleteShaderJobFile(File shaderJobFile) throws IOException {
    assertIsShaderJobFile(shaderJobFile);

    // We conservatively delete specific files, instead of deleting all related files
    // returned from getShaderResultFileRelatedFiles().

    String shaderJobFileWithoutExtension = FilenameUtils.removeExtension(shaderJobFile.toString());

    deleteFile(new File(shaderJobFileWithoutExtension + ".json"));
    tryDeleteFile(new File(shaderJobFileWithoutExtension + ".vert"));
    tryDeleteFile(new File(shaderJobFileWithoutExtension + ".frag"));
    tryDeleteFile(new File(shaderJobFileWithoutExtension + ".primitives"));
    tryDeleteFile(new File(shaderJobFileWithoutExtension + ".license"));
    tryDeleteFile(new File(shaderJobFileWithoutExtension + ".prob"));

  }

  public void deleteShaderJobResultFile(File shaderJobResultFile) throws IOException {
    assertIsShaderJobResultFile(shaderJobResultFile);

    // We conservatively delete specific files, instead of deleting all related files
    // returned from getShaderResultFileRelatedFiles().

    String shaderJobResultFileWithoutExtension =
        FileHelper.removeEnd(shaderJobResultFile.toString(), ".info.json");

    deleteFile(new File(shaderJobResultFileWithoutExtension + ".info.json"));
    tryDeleteFile(new File(shaderJobResultFileWithoutExtension + ".png"));
    tryDeleteFile(new File(shaderJobResultFileWithoutExtension + ".txt"));

  }

  public boolean doesShaderExist(File shaderJobFile, ShaderKind shaderKind) {
    //noinspection deprecation: OK from within this class.
    File shaderFile = getUnderlyingShaderFile(shaderJobFile, shaderKind);
    return shaderFile.isFile();
  }

  public boolean doesShaderJobExist(File shaderJobFile) {
    assertIsShaderJobFile(shaderJobFile);
    return shaderJobFile.isFile();
  }

  public boolean doesShaderJobResultFileExist(File shaderJobResultFile) {
    assertIsShaderJobResultFile(shaderJobResultFile);
    return shaderJobResultFile.isFile();
  }

  /**
   * Determines whether the underlying shader files for the shader jobs use GraphicsFuzz defines.
   * Assumes that if one shader does, they all do.
   */
  public boolean doesShaderJobUseGraphicsFuzzDefines(File shaderJobFile) throws IOException {
    for (ShaderKind shaderKind : ShaderKind.values()) {
      //noinspection deprecation: fine inside this class.
      final File shaderFile = getUnderlyingShaderFile(shaderJobFile, shaderKind);
      if (!shaderFile.isFile()) {
        continue;
      }
      try (BufferedReader br = new BufferedReader(new FileReader(shaderFile))) {
        String line;
        while ((line = br.readLine()) != null) {
          if (line.trim().startsWith(ParseHelper.END_OF_GRAPHICSFUZZ_DEFINES)) {
            return true;
          }
        }
      }
    }
    return false;
  }

  /**
   * Does this shaderJobResultFile have an associated image result?
   *
   * <p>Perhaps we should be able to check this by reading the result file,
   * not by checking for the presence of a file. But this might be fine actually.
   */
  public boolean doesShaderJobResultFileHaveImage(File shaderJobResultFile) {
    File imageFile = getUnderlyingImageFileFromShaderJobResultFile(shaderJobResultFile);
    return imageFile.isFile();
  }

  /**
   * This the given shaderJob a compute shader job?
   * @param shaderJobFile A shader job to check.
   * @return true if and only if this is a compute shader job.
   */
  public boolean isComputeShaderJob(File shaderJobFile) {
    return new File(FilenameUtils.removeExtension(shaderJobFile.toString()) + ".comp").isFile();
  }

  public long getFileLength(File file) {
    return file.length();
  }

  public String[] getFirstTwoLinesOfShader(File shaderJobFile, ShaderKind shaderKind)
      throws IOException {

    //noinspection deprecation: OK from within this class.
    File shaderFile = getUnderlyingShaderFile(shaderJobFile, shaderKind);

    try (BufferedReader br = new BufferedReader(new FileReader(shaderFile))) {
      final String firstLine = br.readLine();
      final String secondLine = br.readLine();
      return new String[] {firstLine, secondLine};
    }
  }

  public String getShaderContents(
      File shaderJobFile,
      ShaderKind shaderKind) throws IOException {

    //noinspection deprecation: OK from within this class.
    File shaderFile = getUnderlyingShaderFile(shaderJobFile, shaderKind);

    return readFileToString(shaderFile);
  }

  public String getShaderJobFileHash(File shaderJobFile) throws IOException {
    return getMD5(shaderJobFile);
  }

  public long getShaderLength(File shaderJobFile, ShaderKind shaderKind) {
    assertIsShaderJobFile(shaderJobFile);
    //noinspection deprecation: Fine in this class.
    return getUnderlyingShaderFile(shaderJobFile, shaderKind).length();
  }

  public PrintStream getStdOut() {
    return System.out;
  }

  /**
   * Get a shader file associated with a shader job.
   * This method should be avoided because the representation of a shader job may change
   * and any operations performed on the returned File cannot be mocked.
   * @deprecated
   *
   */
  public File getUnderlyingShaderFile(
      File shaderJobFile,
      ShaderKind shaderKind) {
    assertIsShaderJobFile(shaderJobFile);
    String shaderJobFileNoExtension = FilenameUtils.removeExtension(shaderJobFile.toString());
    return new File(shaderJobFileNoExtension + "." + shaderKind.getFileExtension());
  }

  public boolean isDirectory(File file) {
    return file.isDirectory();
  }

  public boolean isFile(File file) {
    return file.isFile();
  }

  public File[] listFiles(File directory, FilenameFilter filter) throws IOException {
    if (!isDirectory(directory)) {
      throw new IOException("Not a directory: " + directory);
    }
    File[] res = directory.listFiles(filter);

    if (res == null) {
      throw new IOException("Failed to enumerate files in " + directory);
    }
    return res;
  }

  /**
   * Files are sorted.
   */
  public File[] listShaderJobFiles(File directory, FilenameFilter filter) throws IOException {
    File[] files =
        listFiles(directory,
            (dir, name) -> name.endsWith(".json") && (filter == null || filter.accept(dir, name)));
    AlphanumComparator comparator = new AlphanumComparator();
    Arrays.sort(files, (o1, o2) -> comparator.compare(o1.toString(), o2.toString()));
    return files;
  }

  /**
   * Files are sorted.
   */
  public File[] listShaderJobFiles(File directory) throws IOException {
    return listShaderJobFiles(directory, null);
  }

  public void mkdir(File directory) throws IOException {
    Files.createDirectories(directory.toPath());
  }

  public void forceMkdir(File outputDir) throws IOException {
    FileUtils.forceMkdir(outputDir);
  }

  public void moveFile(File srcFile, File destFile, boolean replaceExisting) throws IOException {
    if (replaceExisting) {
      Files.move(srcFile.toPath(), destFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
    } else {
      Files.move(srcFile.toPath(), destFile.toPath());
    }
  }

  public void moveShaderJobFileTo(
      File shaderJobFileSource,
      File shaderJobFileDest,
      boolean replaceExisting) throws IOException {
    // TODO: Should we only move/copy particular files?
    copyOrMoveShaderJobFileTo(shaderJobFileSource, shaderJobFileDest, replaceExisting, false);
  }

  public void moveShaderJobResultFileTo(
      File shaderResultFileSource,
      File shaderResultFileDest,
      boolean replaceExisting) throws IOException {
    // TODO: Should we only move/copy particular files?
    copyOrMoveShaderJobResultFileTo(
        shaderResultFileSource,
        shaderResultFileDest,
        replaceExisting,
        false);
  }

  public byte[] readFileToByteArray(File file) throws IOException {
    return FileUtils.readFileToByteArray(file);
  }

  public String readFileToString(File file) throws IOException {
    return FileUtils.readFileToString(file, Charset.defaultCharset());
  }

  public List<String> readLines(File file) throws IOException {
    return FileUtils.readLines(file, Charset.defaultCharset());
  }

  public ShaderJob readShaderJobFile(File shaderJobFile)
      throws IOException, ParseTimeoutException, InterruptedException, GlslParserException {

    assertIsShaderJobFile(shaderJobFile);

    final String shaderJobFilePrefix = FilenameUtils.removeExtension(shaderJobFile.toString());

    final List<TranslationUnit> translationUnits = new ArrayList<>();

    for (String extension : Arrays.asList("vert", "frag", "comp")) {
      final Optional<TranslationUnit> maybeTu =
          ParseHelper.maybeParseShader(new File(shaderJobFilePrefix + "." + extension));
      maybeTu.ifPresent(translationUnits::add);
    }

    final File licenseFile = new File(shaderJobFilePrefix + ".license");

    return new GlslShaderJob(
        licenseFile.exists()
            ? Optional.of(FileUtils.readFileToString(licenseFile, Charset.defaultCharset()))
            : Optional.empty(),
        new PipelineInfo(shaderJobFile),
        translationUnits);
  }

  public void readShaderJobFileToImageJob(File shaderJobFile, ImageJob imageJob)
      throws IOException {
    assertIsShaderJobFile(shaderJobFile);

    imageJob.setName(FilenameUtils.removeExtension(shaderJobFile.getName()));

    String shaderFileNoExtension = FilenameUtils.removeExtension(shaderJobFile.toString());

    final File infoFile = new File(shaderFileNoExtension + ".json");
    //noinspection deprecation: fine inside this class.
    final File fragmentFile = getUnderlyingShaderFile(shaderJobFile, ShaderKind.FRAGMENT);
    //noinspection deprecation: fine inside this class.
    final File vertexFile = getUnderlyingShaderFile(shaderJobFile, ShaderKind.VERTEX);
    final File primitivesFile = new File(shaderFileNoExtension + ".primitives");
    final File computeFile = new File(shaderFileNoExtension + ".comp");

    // Special case: compute shader job.
    if (isFile(computeFile)) {
      imageJob.setComputeSource(readFileToString(computeFile));
      imageJob.setComputeInfo(readFileToString(infoFile));
      return;
    }

    imageJob.setUniformsInfo(readFileToString(infoFile));

    if (isFile(fragmentFile)) {
      imageJob.setFragmentSource(readFileToString(fragmentFile));
    }

    if (isFile(vertexFile)) {
      imageJob.setVertexSource(readFileToString(vertexFile));
    }

    if (primitivesFile.isFile()) {
      setPrimitives(imageJob, primitivesFile);
    }
  }

  /**
   * Run get image LOCALLY; mainly for testing.
   */
  public ImageJobResult runGetImageOnImageJob(
      ImageJob imageJob,
      File shaderJobFileTemp,
      boolean useSwiftShader
  ) throws IOException, ShaderDispatchException, InterruptedException {

    // It is unclear where best to abstract this.
    // Running get image on an ImageJob should be all that is needed,
    // but running on a shaderJobFile makes sense for testing and we need to do this
    // underneath anyway, so we expose both ways.

    // We don't normally create output directories at this low-level, but in this case, the temp
    // directory will only be created when running shaders locally, which is rare for the server,
    // so we don't want to require them to exist.

    // Ensure temp output directory exists:

    File tempDir = getParent(shaderJobFileTemp);
    if (tempDir.toString().length() > 0) {
      mkdir(tempDir);
    }

    // Write shader job file:
    writeShaderJobFileFromImageJob(imageJob, shaderJobFileTemp);

    if (!imageJob.isSetSkipRender()) {
      throw new RuntimeException("Internal error: skipRender was null, but it should be set.");
    }

    // Run shader job file:
    return runGetImageOnShaderJobFile(
        shaderJobFileTemp,
        useSwiftShader,
        imageJob.isSkipRender());
  }

  /**
   * Run get image LOCALLY; mainly for testing.
   */
  public ImageJobResult runGetImageOnShaderJobFile(
      File shaderJobFile,
      boolean usingSwiftshader,
      boolean skipRender
  ) throws InterruptedException, IOException, ShaderDispatchException {

    String shaderJobFileNoExtension = FilenameUtils.removeExtension(shaderJobFile.toString());

    if (isFile(new File(shaderJobFileNoExtension + ".vert"))
        || isFile(new File(shaderJobFileNoExtension + ".comp"))) {

      throw new
          UnsupportedOperationException(
              "Running vertex and compute shaders locally is not supported yet.");
    }

    File fragShader = new File(shaderJobFileNoExtension + ".frag");
    File imageFile = new File(shaderJobFileNoExtension + ".png");

    // Delete the image file just in case it already exists.
    try {
      deleteFile(imageFile);
    } catch (NoSuchFileException exception) {
      // ignore
    }

    ExecResult res = usingSwiftshader
        ? ToolHelper.runSwiftshaderOnShader(
            ExecHelper.RedirectType.TO_BUFFER,
            fragShader,
            imageFile,
            skipRender)
        : ToolHelper.runGenerateImageOnShader(
            ExecHelper.RedirectType.TO_BUFFER,
            fragShader,
            imageFile,
            skipRender)
        ;

    ImageJobResult imageJobResult = new ImageJobResult();

    res.stdout.append(res.stderr);

    if (res.res == 0) {
      imageJobResult
          .setStatus(JobStatus.SUCCESS)
          .setLog("\n" + res.stdout.toString());
    } else {
      ResultConstant resultConstant = ResultConstant.ERROR;
      JobStatus status = JobStatus.UNEXPECTED_ERROR;

      if (res.res == FuzzerServiceConstants.COMPILE_ERROR_EXIT_CODE) {
        resultConstant = ResultConstant.COMPILE_ERROR;
        status = JobStatus.COMPILE_ERROR;
      } else if (res.res == FuzzerServiceConstants.LINK_ERROR_EXIT_CODE) {
        resultConstant = ResultConstant.LINK_ERROR;
        status = JobStatus.LINK_ERROR;
      }
      imageJobResult
          .setStatus(status)
          .setLog(resultConstant + "\n" + res.stdout.toString());
    }

    if (isFile(imageFile)) {
      byte[] png = readFileToByteArray(imageFile);
      imageJobResult.setPNG(png);
    }

    imageJobResult.setPassSanityCheck(true);
    return imageJobResult;

  }

  public void tryDeleteFile(File file) {
    FileUtils.deleteQuietly(file);
  }

  /**
   * Output a file alongside a shader job file.
   * This method should be avoided, but is useful for now.
   *
   * @param outputFileExtension E.g. ".prob"
   * @deprecated Should probably be avoided, but useful for now.
   */
  public void writeAdditionalInfo(
      File shaderJobFile,
      String outputFileExtension,
      String outputContents) throws FileNotFoundException {
    assertIsShaderJobFile(shaderJobFile);

    String outputFileNoExtension = FilenameUtils.removeExtension(shaderJobFile.toString());
    try (PrintStream stream = ps(new File(outputFileNoExtension + outputFileExtension))) {
      stream.println(outputContents);
    }
  }

  public void writeByteArrayToFile(File file, byte[] contents) throws IOException {
    FileUtils.writeByteArrayToFile(file, contents);
  }

  public void writeShaderJobFile(
      final ShaderJob shaderJob,
      final File outputShaderJobFile,
      final boolean emitGraphicsFuzzDefines) throws FileNotFoundException {

    assertIsShaderJobFile(outputShaderJobFile);

    String outputFileNoExtension = FilenameUtils.removeExtension(outputShaderJobFile.toString());

    for (TranslationUnit tu : shaderJob.getShaders()) {
      writeShader(
          tu,
          shaderJob.getLicense(),
          new File(outputFileNoExtension + "." + tu.getShaderKind().getFileExtension()),
          emitGraphicsFuzzDefines
      );
    }

    //noinspection deprecation: OK for use inside this class.
    writeAdditionalInfo(
        outputShaderJobFile,
        ".json",
        shaderJob.getPipelineInfo().toString());
  }

  public void writeShaderJobFile(
      final ShaderJob shaderJob,
      final File outputShaderJobFile) throws FileNotFoundException {
    writeShaderJobFile(shaderJob, outputShaderJobFile, true);
  }

  public void writeShaderJobFileFromImageJob(
      final ImageJob imageJob,
      final File outputShaderJobFile) throws IOException {

    assertIsShaderJobFile(outputShaderJobFile);

    String outputShaderJobFileNoExtension =
        FilenameUtils.removeExtension(outputShaderJobFile.toString());

    // Special case: compute shader job.
    if (imageJob.isSetComputeSource()) {
      writeStringToFile(
          new File(outputShaderJobFileNoExtension + ".json"),
          imageJob.getComputeInfo());

      writeStringToFile(
          new File(outputShaderJobFileNoExtension + ".comp"),
          imageJob.getComputeSource());

      return;
    }

    writeStringToFile(
        new File(outputShaderJobFileNoExtension + ".json"),
        imageJob.getUniformsInfo());

    if (imageJob.getVertexSource() != null) {
      writeStringToFile(
          new File(outputShaderJobFileNoExtension + ".vert"),
          imageJob.getVertexSource());
    }

    if (imageJob.getFragmentSource() != null) {
      writeStringToFile(
          new File(outputShaderJobFileNoExtension + ".frag"),
          imageJob.getFragmentSource());
    }

    if (imageJob.getComputeSource() != null) {
      writeStringToFile(
          new File(outputShaderJobFileNoExtension + ".comp"),
          imageJob.getComputeSource());
    }
  }

  /**
   * @param shaderResult Input imageJobResult.
   * @param shaderResultFile E.g. "variant_blah.info.json"
   * @param referenceShaderResultFile If present, will allow the difference metrics to be output
   *                                  to the .info.json shaderResultFile.
   */
  public void writeShaderResultToFile(
      ImageJobResult shaderResult,
      File shaderResultFile,
      Optional<File> referenceShaderResultFile) throws IOException {

    writeShaderResultToFileHelper(
        shaderResult,
        shaderResultFile,
        this,
        referenceShaderResultFile);
  }

  public void writeStringToFile(File file, String contents) throws IOException {
    FileUtils.writeStringToFile(file, contents, Charset.defaultCharset());
  }

  /**
   * Provides an in-memory representation of the image associated with a shader job result.
   * Assumes that an image file is present as part of the shader job result.
   * @param shaderJobResultFile The shader job for which a result image is to be processed.
   * @return In-memory representation of image.
   * @throws IOException on absence of an image file or failing to read the file.
   */
  public BufferedImage getBufferedImageFromShaderJobResultFile(File shaderJobResultFile)
      throws IOException {
    return ImageIO.read(getUnderlyingImageFileFromShaderJobResultFile(shaderJobResultFile));
  }

  private static void assertIsShaderJobFile(File shaderJobFile) {
    if (!shaderJobFile.getName().endsWith(".json")
        || shaderJobFile.getName().endsWith(".info.json")) {
      throw new IllegalArgumentException(
          "shaderJobFile: must be a .json file (and not .info.json):" + shaderJobFile);
    }
  }

  private static void assertIsShaderJobResultFile(File shaderJobResultFile) {
    if (!shaderJobResultFile.getName().endsWith(".info.json")) {
      throw new IllegalArgumentException(
          "shaderJobResultFile: must be a .info.json file" + shaderJobResultFile);
    }
  }

  private static File getParent(File file) {
    File resultDir = file.getParentFile();
    if (resultDir == null) {
      resultDir = new File(".");
    }
    return resultDir;
  }

  private static JsonObject makeInfoJson(
      ImageJobResult res,
      File outputImage,
      Optional<ImageData> referenceImage) throws IOException {
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
      // Add image data, e.g. histogram distance
      final JsonObject metrics = new JsonObject();
      referenceImage.get().getImageDiffStats(new ImageData(outputImage.getAbsolutePath()), metrics);
      boolean isIdentical =
          ImageUtil.identicalImages(outputImage, referenceImage.get().imageFile);
      metrics.addProperty("identical", isIdentical);
      infoJson.add("metrics", metrics);
    }
    if (res.isSetStage()) {
      infoJson.addProperty("stage", res.stage.toString());
    }
    if (res.isSetStatus()) {
      infoJson.addProperty("status", res.getStatus().toString());
    }
    infoJson.addProperty("passSanityCheck", "" + res.passSanityCheck);
    return infoJson;
  }

  private static PrintStream ps(File file) throws FileNotFoundException {
    return new PrintStream(new FileOutputStream(file));
  }

  private static void writeShader(
      TranslationUnit tu,
      Optional<String> license,
      File outputFile,
      boolean emitGraphicsFuzzDefines
  ) throws FileNotFoundException {
    try (PrintStream stream = ps(outputFile)) {
      PrettyPrinterVisitor.emitShader(
          tu,
          license,
          stream,
          PrettyPrinterVisitor.DEFAULT_INDENTATION_WIDTH,
          PrettyPrinterVisitor.DEFAULT_NEWLINE_SUPPLIER,
          emitGraphicsFuzzDefines
      );
    }
  }

  private void assertImagesExist(File shaderJobResultFile) throws FileNotFoundException {
    assertIsShaderJobFile(shaderJobResultFile);
    String fileNoExtension = FileHelper.removeEnd(shaderJobResultFile.toString(), ".info.json");
    File imageFile = new File(fileNoExtension + ".png");
    if (!isFile(imageFile)) {
      throw new FileNotFoundException(
          "Could not find image file "
              + imageFile
              + " for shader result file "
              + shaderJobResultFile);
    }
  }

  private void copyOrMoveShaderJobFileTo(
      File shaderJobFileSource,
      File shaderJobFileDest,
      boolean replaceExisting,
      boolean copy) throws IOException {
    // TODO: Should we only move/copy particular files?
    assertIsShaderJobFile(shaderJobFileSource);
    assertIsShaderJobFile(shaderJobFileDest);

    // Copy:
    //
    // x.json
    // x.frag
    // x.vert
    //
    // to:
    //
    // y.json
    // y.frag
    // y.vert

    // Calculate 'y'.
    String sourceNameNoExtension =
        FileHelper.removeEnd(shaderJobFileSource.getName(), ".json");
    String destNameNoExtension =
        FileHelper.removeEnd(shaderJobFileDest.getName(), ".json");

    File destDir = getParent(shaderJobFileDest);

    for (File file : getShaderJobFileRelatedFiles(shaderJobFileSource)) {
      // New name is "y" + ("x.blah" / "x")
      final String newName =
          destNameNoExtension
              + FileHelper.removeStart(file.getName(), sourceNameNoExtension);
      final File destFile = new File(destDir, newName);
      if (copy) {
        copyFile(file, destFile, replaceExisting);
      } else {
        moveFile(file, destFile, replaceExisting);
      }
    }
  }

  private void copyOrMoveShaderJobResultFileTo(
      File shaderResultFileSource,
      File shaderResultFileDest,
      boolean replaceExisting,
      boolean copy) throws IOException {
    // TODO: Should we only move/copy particular files?
    assertIsShaderJobResultFile(shaderResultFileSource);
    assertIsShaderJobResultFile(shaderResultFileDest);

    // Copy:
    //
    // x.info.json
    // x.png
    // x.txt
    //
    // to:
    //
    // y.info.json
    // y.png
    // y.txt

    // Calculate 'y'.
    String sourceNameNoExtension =
        FileHelper.removeEnd(shaderResultFileSource.getName(), ".info.json");
    String destNameNoExtension =
        FileHelper.removeEnd(shaderResultFileDest.getName(), ".info.json");

    File destDir = getParent(shaderResultFileDest);

    // For each x.*
    for (File file : getShaderResultFileRelatedFiles(shaderResultFileSource)) {

      // New name is "y" + ("x.blah" / "x")
      final String newName =
          destNameNoExtension
              + FileHelper.removeStart(file.getName(), sourceNameNoExtension);
      final File destFile = new File(destDir, newName);
      if (copy) {
        copyFile(file, destFile, replaceExisting);
      } else {
        moveFile(file, destFile, replaceExisting);
      }
    }
  }

  private String getMD5(File shaderJobFile) throws IOException {
    assertIsShaderJobFile(shaderJobFile);

    String fileNoExtension = FilenameUtils.removeExtension(shaderJobFile.toString());
    final File vertexShaderFile = new File(fileNoExtension + ".vert");
    final File fragmentShaderFile = new File(fileNoExtension + ".frag");
    final File computeShaderFile = new File(fileNoExtension + ".comp");

    if (!isFile(vertexShaderFile) && !isFile(fragmentShaderFile) && !isFile(computeShaderFile)) {
      throw new IllegalStateException("No frag, vert or comp shader found for " + shaderJobFile);
    }

    byte[] vertexData = isFile(vertexShaderFile)
        ? readFileToByteArray(vertexShaderFile)
        : new byte[0];
    byte[] fragmentData = isFile(fragmentShaderFile)
        ? readFileToByteArray(fragmentShaderFile)
        : new byte[0];
    byte[] computeData = isFile(computeShaderFile)
        ? readFileToByteArray(computeShaderFile)
        : new byte[0];
    byte[] combinedData = new byte[vertexData.length + fragmentData.length + computeData.length];
    System.arraycopy(
        vertexData,
        0,
        combinedData,
        0,
        vertexData.length);
    System.arraycopy(
        fragmentData,
        0,
        combinedData,
        vertexData.length,
        fragmentData.length);
    System.arraycopy(
        computeData,
        0,
        combinedData,
        vertexData.length + fragmentData.length,
        computeData.length);
    return DigestUtils.md5Hex(combinedData);
  }

  private List<Double> getPointsFromJson(JsonObject json, String key) {
    final List<Double> result = new ArrayList<>();
    final JsonArray points = json.get(key).getAsJsonArray();
    for (int i = 0; i < points.size(); i++) {
      result.add(points.get(i).getAsDouble());
    }
    return result;
  }

  private File[] getShaderJobFileRelatedFiles(File shaderJobFile) throws IOException {
    assertIsShaderJobFile(shaderJobFile);
    assertExists(shaderJobFile);

    String fileNoExtension =
        FilenameUtils.removeExtension(shaderJobFile.toString());

    File[] relatedFiles =
        Stream.of(".json", ".vert", ".frag", ".comp", ".primitives", ".prob", ".license")
            .map(ext -> new File(fileNoExtension + ext))
            .filter(this::isFile)
            .toArray(File[]::new);

    return relatedFiles;
  }

  private File[] getShaderResultFileRelatedFiles(File shaderResultFile) throws IOException {
    assertIsShaderJobResultFile(shaderResultFile);
    assertExists(shaderResultFile);

    String fileNoExtension =
        FileHelper.removeEnd(shaderResultFile.toString(), ".info.json");

    File[] relatedFiles =
        Stream.of(".info.json", ".txt", ".png")
            .map(ext -> new File(fileNoExtension + ext))
            .filter(this::isFile)
            .toArray(File[]::new);

    return relatedFiles;
  }

  private File getUnderlyingImageFileFromShaderJobResultFile(File shaderJobResultFile) {
    assertIsShaderJobResultFile(shaderJobResultFile);
    String shaderJobFileWithoutExtension =
        FileHelper.removeEnd(shaderJobResultFile.toString(), ".info.json");
    return new File(shaderJobFileWithoutExtension + ".png");
  }

  private void setPrimitives(ImageJob imageJob, File primitivesFile) throws IOException {

    JsonObject json = new Gson().fromJson(new FileReader(primitivesFile),
        JsonObject.class);
    imageJob.setPoints(getPointsFromJson(json, "points"));
    if (json.has("texPoints")) {
      imageJob.setTexturePoints(getPointsFromJson(json, "texPoints"));
      if (!json.has("texture")) {
        throw new RuntimeException("If texture points are provided, a texture must be provided");
      }
      final File textureFile = new File(primitivesFile.getParentFile(),
          json.get("texture").getAsString());
      if (!textureFile.isFile()) {
        throw new RuntimeException("Could not find texture file " + textureFile.getAbsolutePath());
      }
      imageJob.setTextureBinary(FileUtils.readFileToByteArray(textureFile));
    }
  }

  private boolean shaderIsValid(
      File shaderFile,
      boolean throwExceptionOnValidationError)
      throws IOException, InterruptedException {
    return checkValidationResult(ToolHelper.runValidatorOnShader(ExecHelper.RedirectType.TO_BUFFER,
        shaderFile), shaderFile.getName(), throwExceptionOnValidationError);
  }

  private boolean shaderIsValidShaderTranslator(
      File shaderFile,
      ShadingLanguageVersion shadingLanguageVersion,
      boolean throwExceptionOnValidationError)
      throws IOException, InterruptedException {
    if (!ShaderTranslatorShadingLanguageVersionSupport.isVersionSupported(shadingLanguageVersion)) {
      // Shader translator does not support this shading language version, so just say that the
      // shader is valid.
      return true;
    }
    final ExecResult shaderTranslatorResult = ToolHelper.runShaderTranslatorOnShader(
        ExecHelper.RedirectType.TO_BUFFER,
        shaderFile,
        ShaderTranslatorShadingLanguageVersionSupport
            .getShaderTranslatorArgument(shadingLanguageVersion));
    if (isMemoryExhaustedError(shaderTranslatorResult)) {
      return true;
    }
    return checkValidationResult(
        shaderTranslatorResult,
        shaderFile.getName(),
        throwExceptionOnValidationError);
  }

  // TODO(171): This is a workaround for an issue where shader_translator reports memory exhaustion.
  // If the issue in shader_translator can be fixed, we should get rid of this check.
  private boolean isMemoryExhaustedError(ExecResult shaderTranslatorResult) {
    return shaderTranslatorResult.res != 0
        && shaderTranslatorResult.stdout.toString().contains("memory exhausted");
  }

  private boolean checkValidationResult(ExecResult res,
                                        String filename, boolean throwExceptionOnValidationError) {
    if (res.res != 0) {
      LOGGER.warn("Shader {} failed to validate.  Validator stdout: " + res.stdout + ".  "
              + "Validator stderr: " + res.stderr,
          filename);
      if (throwExceptionOnValidationError) {
        throw new RuntimeException("Validation failed.");
      }
      return false;
    }
    return true;
  }


  /**
   * This method is quite complicated compared to others in this class,
   * so it is static in case we want to test it via a mocked ShaderJobFileOperations fileOps.
   * However, gif creation and image data creation is not mockable yet.
   */
  protected static void writeShaderResultToFileHelper(
      ImageJobResult shaderResult,
      File shaderJobResultFile,
      ShaderJobFileOperations fileOps,
      Optional<File> referenceShaderResultFile) throws IOException {

    assertIsShaderJobResultFile(shaderJobResultFile);

    String shaderJobResultNoExtension =
        FileHelper.removeEnd(shaderJobResultFile.toString(), ".info.json");

    // Special case: compute shader job.

    if (shaderResult.isSetComputeOutputs()) {

      JsonObject infoJson = new JsonObject();
      if (shaderResult.isSetStatus()) {
        infoJson.addProperty("status", shaderResult.getStatus().toString());
      }
      if (shaderResult.isSetLog()) {
        infoJson.addProperty("log", shaderResult.getLog());
      }
      if (shaderResult.isSetComputeOutputs()) {
        infoJson.add(
            "outputs", new Gson().fromJson(shaderResult.getComputeOutputs(), JsonObject.class));
      }

      fileOps.writeStringToFile(
          new File(shaderJobResultNoExtension + ".info.json"),
          infoJson.toString());

      return;
    }

    final File outputImage = new File(shaderJobResultNoExtension + ".png");

    if (shaderResult.isSetLog()) {
      fileOps.writeStringToFile(
          new File(shaderJobResultNoExtension + ".txt"),
          shaderResult.getLog());
    }

    if (shaderResult.isSetPNG()) {
      fileOps.writeByteArrayToFile(outputImage, shaderResult.getPNG());
    }

    // TODO: Not mockable yet; directly accesses files.

    // Create gif when there is two image files set. This may happen not only for NONDET state,
    // but also in case of Sanity error after a nondet.
    if (shaderResult.isSetPNG() && shaderResult.isSetPNG2()) {
      // we can dump both images
      File outputNondet1 = new File(shaderJobResultNoExtension + "_nondet1.png");
      File outputNondet2 = new File(shaderJobResultNoExtension +  "_nondet2.png");
      fileOps.writeByteArrayToFile(outputNondet1, shaderResult.getPNG());
      fileOps.writeByteArrayToFile(outputNondet2, shaderResult.getPNG2());

      // Create gif
      try {
        BufferedImage nondetImg = ImageIO.read(
            Thread.currentThread().getContextClassLoader().getResourceAsStream("nondet.png"));
        BufferedImage img1 = ImageIO.read(
            new ByteArrayInputStream(shaderResult.getPNG()));
        BufferedImage img2 = ImageIO.read(
            new ByteArrayInputStream(shaderResult.getPNG2()));
        File gifFile = new File(shaderJobResultNoExtension + ".gif");
        ImageOutputStream gifOutput = new FileImageOutputStream(gifFile);
        GifSequenceWriter gifWriter =
            new GifSequenceWriter(
                gifOutput,
                img1.getType(),
                500,
                true);

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


    Optional<ImageData> referenceImageData = Optional.empty();
    // TODO: Not mockable yet; directly accesses files.
    if (referenceShaderResultFile.isPresent()
        && fileOps.doesShaderJobResultFileHaveImage(referenceShaderResultFile.get())) {
      referenceImageData =
          Optional.of(
              new ImageData(
                  fileOps.getUnderlyingImageFileFromShaderJobResultFile(
                      referenceShaderResultFile.get())));
    }

    // TODO: Not mockable yet; directly accesses files.
    JsonObject infoObject = makeInfoJson(shaderResult, outputImage, referenceImageData);

    // Dump job info in JSON
    fileOps.writeStringToFile(
        shaderJobResultFile,
        JsonHelper.jsonToString(infoObject));
  }

  /**
   * Stores data about an image; right now its file and histogram.
   * Could be extended in due course with e.g. PSNR
   */
  private static final class ImageData {

    public final File imageFile;
    private final opencv_core.Mat imageMat;
    private final opencv_core.Mat histogram;

    public ImageData(File imageFile) throws FileNotFoundException {
      this.imageFile = imageFile;
      this.imageMat = opencv_imgcodecs.imread(imageFile.getAbsolutePath());
      opencv_imgproc.cvtColor(this.imageMat, this.imageMat, opencv_imgproc.COLOR_BGR2HSV);
      this.histogram = ImageUtil.getHistogram(imageFile.getAbsolutePath());
    }

    public ImageData(String imageFileName) throws FileNotFoundException {
      this(new File(imageFileName));
    }

    public void getImageDiffStats(
        ImageData other,
        JsonObject metrics) throws IOException {

      metrics.addProperty(
          "histogramDistance",
          ImageUtil.compareHistograms(this.histogram, other.histogram));

      metrics.addProperty(
          "psnr",
          opencv_core.PSNR(this.imageMat, other.imageMat));

      FuzzyImageComparison.MainResult fuzzyDiffResult;
      try {
        fuzzyDiffResult = FuzzyImageComparison.mainHelper(
            new String[] {imageFile.toString(), other.imageFile.toString()});
      } catch (ArgumentParserException exception) {
        throw new RuntimeException(exception);
      }

      JsonElement fuzzDiffResultJson = new Gson().toJsonTree(fuzzyDiffResult);
      metrics.add(FUZZY_DIFF_KEY, fuzzDiffResultJson);
    }

  }

}
