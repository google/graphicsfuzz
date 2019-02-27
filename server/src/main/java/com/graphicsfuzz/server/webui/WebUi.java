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

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.graphicsfuzz.alphanumcomparator.AlphanumComparator;
import com.graphicsfuzz.common.util.FileHelper;
import com.graphicsfuzz.common.util.ReductionProgressHelper;
import com.graphicsfuzz.common.util.ShaderJobFileOperations;
import com.graphicsfuzz.reducer.ReductionKind;
import com.graphicsfuzz.server.thrift.CommandInfo;
import com.graphicsfuzz.server.thrift.CommandResult;
import com.graphicsfuzz.server.thrift.FuzzerServiceManager;
import com.graphicsfuzz.server.thrift.WorkerInfo;
import com.graphicsfuzz.util.Constants;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.thrift.TException;

/**
 * Simple Web UI.
 *
 * <p>The two entry points are doGet() and doPost(). They dispatche response handling based on the
 * HTTP request path, which we call 'route'. Dedicated functions build the relevant web response.
 * The StringBuilder field 'html' is used to progressively construct the HTML, and is eventually
 * send back as a response.
 *
 * <p>Routes from http://example.org/webui :
 * /file/ : serve a file content from filesystem, rooted at the working dir of the server process
 * /other/thing/not/matched/by/dispatch : serve a resource file from resources/public/
 * / : homepage: list workers, and options to start experiments.
 * Should also display the server queue state.
 * worker/"workername" : worker page, with overview of results
 * worker/"workername"/"shadersetname" : result of this worker on this shaderset.
 * Can start reduction on a single variant
 * startExperiment/
 *
 */
public class WebUi extends HttpServlet {

  private final StringBuilder html;
  private long startTime;
  private final AccessFileInfo accessFileInfo;

  private final FilenameFilter variantFragFilter =
      (dir, name) -> name.startsWith("variant_") && name.endsWith(".frag");

  private final ShaderJobFileOperations fileOps;
  private final FuzzerServiceManager.Iface fuzzerServiceManagerProxy;

  public WebUi(FuzzerServiceManager.Iface fuzzerServiceManager, ShaderJobFileOperations fileOps) {
    this.html = new StringBuilder();
    this.accessFileInfo = new AccessFileInfo();
    this.fileOps = fileOps;
    this.fuzzerServiceManagerProxy = fuzzerServiceManager;
  }

  private static final class Shaderset {
    final String name;
    final File dir;
    final File preview;
    final int nbVariants;

    public Shaderset(String name) {
      this.name = name;
      this.dir = new File(WebUiConstants.SHADERSET_DIR + "/" + name);
      this.preview = new File(dir + "/thumb.png");
      this.nbVariants = getNbVariants();
    }

    private int getNbVariants() {
      final File[] variants = dir.listFiles(item -> item.getName().startsWith("variant")
          && item.getName().endsWith(".frag"));
      return variants == null ? 0 : variants.length;
    }
  }

  private static final class ShadersetExp {
    final Shaderset shaderset;
    final String name;
    final String worker;
    final File dir;
    int nbVariants;
    int nbVariantDone;
    int nbErrors;
    int nbSameImage;
    int nbSlightlyDifferentImage;
    int nbWrongImage;

    public ShadersetExp(String name, String worker, AccessFileInfo accessFileInfo)
        throws FileNotFoundException {
      this.name = name;
      this.worker = worker;
      this.dir = new File(WebUiConstants.WORKER_DIR + "/" + worker + "/" + name);
      this.shaderset = new Shaderset(name);
      this.nbVariants = shaderset.nbVariants;

      // Set variant counters
      for (File file : dir.listFiles()) {
        if (file.getName().startsWith("variant") && file.getName().endsWith(".info.json")) {
          nbVariantDone++;
          JsonObject info = accessFileInfo.getResultInfo(file);
          String status = info.get("status").getAsString();
          if (status.contentEquals("SUCCESS")) {
            if (imageIsIdentical(info)) {
              nbSameImage++;
            } else if (imageIsAcceptable(info)) {
              nbSlightlyDifferentImage++;
            } else {
              nbWrongImage++;
            }
          } else {
            nbErrors++;
          }
        }
      }
    }
  }

  private static boolean imageIsIdentical(JsonObject info) {
    // We're looking for metrics/identical, conservatively return false if it is not found.
    if (info == null) {
      return false;
    }
    if (!info.has("metrics")) {
      return false;
    }
    final JsonObject metricsJson = info.get("metrics").getAsJsonObject();
    if (!metricsJson.has("identical")) {
      return false;
    }
    return metricsJson.get("identical").getAsBoolean();
  }

  private static boolean imageIsAcceptable(JsonObject info) {

    // This currently uses histogram data, if available, but can easily be adapted to
    // use other data that is available, e.g. PSNR.

    final String metricsKey = "metrics";
    final String histogramDistanceKey = "histogramDistance";
    final double histogramThreshold = 100.0;

    if (info == null) {
      // If we do not have a stats file, conservatively say that the image is unacceptable.
      return false;
    }

    if (!info.has(metricsKey)) {
      return false;
    }
    final JsonObject metricsJson = info.get(metricsKey).getAsJsonObject();
    if (!(metricsJson.has(histogramDistanceKey))) {
      return false;
    }
    return metricsJson.get(histogramDistanceKey)
          .getAsJsonPrimitive().getAsNumber().doubleValue() < histogramThreshold;
  }

  private enum ReductionStatus {
    NOREDUCTION, ONGOING, FINISHED, NOTINTERESTING, EXCEPTION, INCOMPLETE
  }

  private String getResourceContent(String resourceName) throws IOException {
    InputStream is = this.getClass().getResourceAsStream("/private/" + resourceName);
    java.util.Scanner scanner = new java.util.Scanner(is, "UTF-8").useDelimiter("\\A");
    return scanner.hasNext() ? scanner.next() : "";
  }

  private static void copyStream(InputStream input, ServletOutputStream output) throws IOException {
    byte[] buffer = new byte[1024];
    int bytesRead;
    while ((bytesRead = input.read(buffer)) != -1) {
      output.write(buffer, 0, bytesRead);
    }
  }

  private static String getContentType(String filename) {
    try {
      return (Files.probeContentType(Paths.get(filename)));
    } catch (IOException exception) {
      return "text/plain";
    }
  }

  //Returns contents of given file (for displaying files, e.g. shaders)
  private String getFileContents(File file) throws IOException {
    if (!file.isFile()) {
      return "ERROR FILE " + file.getPath() + " NOT FOUND";
    }
    return FileUtils.readFileToString(file, Charset.defaultCharset());
  }

  //Get list of all worker directories
  private List<File> getAllWorkers(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
    File workerDir = new File(WebUiConstants.WORKER_DIR);
    if (!workerDir.isDirectory()) {
      err404(request, response);
      return null;
    }
    File[] workerCandidates = workerDir.listFiles();
    List<File> workers = new ArrayList<>();
    if (workerCandidates != null) {
      for (File wc : workerCandidates) {
        // a directory is a worker if it does contains client.json file
        File info = new File(wc.getPath() + "/client.json");
        if (info.exists()) {
          workers.add(wc);
        }
      }
    }
    workers.sort(Comparator.naturalOrder());
    return workers;
  }

  private List<WorkerInfo> getLiveWorkers(boolean includeInactive) throws TException {
    List<WorkerInfo> workers = fuzzerServiceManagerProxy.getServerState().getWorkers();
    workers.sort((workerInfo, t1) -> {
      Comparator<String> comparator = Comparator.naturalOrder();
      return comparator.compare(workerInfo.getWorkerName(), t1.getWorkerName());
    });
    if (!includeInactive) {
      workers.removeIf(w -> !(w.live));
    }
    return workers;
  }

  //Get list of all shaderset directories
  private List<File> getAllShadersets(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
    List<File> shadersets = new ArrayList<>();
    File shadersetDir = new File(WebUiConstants.SHADERSET_DIR);
    if (!shadersetDir.isDirectory()) {
      err404(request, response);
      return shadersets;
    }
    File[] shadersetFiles = shadersetDir.listFiles();
    for (File shaderset : shadersetFiles) {
      if (shaderset.isDirectory()) {
        shadersets.add(shaderset);
      }
    }
    shadersets.sort(Comparator.naturalOrder());
    return shadersets;
  }

  // Homepage: /webui
  private void homepage(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException, TException {

    response.setContentType("text/html");
    htmlHeader("Homepage");

    // General actions
    htmlAppendLn(
        "<div class='ui segment'>\n",
        "<h3>General actions</h3>\n",
        "<p>\n",
        "<a class='ui button' href='/webui/experiment'>",
        "Run shader families on workers</a>\n",
        "<a class='ui button' href='/webui/compare'>",
        "Compare results for workers</a>\n",
        "<a class='ui button' href='/static/webgl_viewer.html'>",
        "Run WebGL viewer</a>\n",
        "<a class='ui button' href='/static/webgl_viewer.html?context=webgl2'>",
        "Run WebGL2 viewer</a>\n",
        "<div class='ui button' onclick=\"promptForInfo('Enter a worker name",
        "(or leave blank for a generated name): ',",
        "'/static/runner.html?worker=', '', '/static/runner.html', true);\">",
        "Launch WebGL worker</div>\n",
        "<div class='ui button' onclick=\"promptForInfo('Enter a worker name (or leave blank for a",
        "generated name): ', '/static/runner.html?context=webgl2&worker=', '',",
        "'/static/runner.htmlcontext=webgl2', true);\">",
        "Launch WebGL2 worker</div>\n",
        "</p>\n",
        "</div>\n");

    // Connected workers
    htmlAppendLn(
        "<div class='ui segment'>\n",
        "<h3>Connected workers</h3>\n",
        "<div class='ui selection animated celled list'>\n");
    List<String> workers = new ArrayList<>();
    for (WorkerInfo worker : getLiveWorkers(false)) {
      htmlAppendLn("<a class='item' href='/webui/worker/", worker.getWorkerName(), "'>",
          "<i class='large middle aligned mobile icon'></i><div class='content'>",
          "<div class='header'>", worker.getWorkerName(), "</div>",
          "#queued jobs: ",
          Integer.toString(worker.getCommandQueueSize()), "</div></a>");
      workers.add(worker.getWorkerName());
    }
    htmlAppendLn("</div></div>");

    // Disconnected workers
    htmlAppendLn(
        "<div class='ui segment'>\n",
        "<h3>Disconnected workers</h3>\n",
        "<button class='ui black basic button' onclick='toggleDiv(this)'",
        " data-hide='disconnected-workers'>Show/Hide</button>\n",
        "<div class='disconnected-workers invisible ui selection animated celled list'>");
    List<File> workerFiles = getAllWorkers(request, response);
    if (workers != null) {
      for (File worker : workerFiles) {
        if (!workers.contains(worker.getName())) {
          htmlAppendLn("<a class='item' href='/webui/worker/", worker.getName(), "'>",
              worker.getName(), "</a>");
        }
      }
    }
    htmlAppendLn("</div></div>");

    //List of shadersets
    htmlAppendLn(
        "<div class='ui segment'>\n",
        "<h3>Shader Families</h3>\n",
        "<div class='ui middle aligned selection animated celled list'>\n");
    //StringBuilder shadersetListHTML = new StringBuilder();
    List<File> shadersets = getAllShadersets(request, response);
    if (shadersets.size() > 0) {
      for (File file : shadersets) {
        Shaderset shaderset = new Shaderset(file.getName());
        htmlAppendLn("<a class='item' href='/webui/shaderset/", shaderset.name, "'>",
            "<img class='ui mini image' src='/webui/file/", shaderset.preview.getPath(), "'>",
            "<div class='content'><div class='header'>", shaderset.name,
            "</div>#variants: ", Integer.toString(shaderset.nbVariants),
            "</div></a>");
      }
    }
    htmlAppendLn("</div></div>");

    // Server log
    String serverLog = getFileContents(new File(WebUiConstants.WORKER_DIR + "/server.log"));
    // Some logs may be in the megabytes, which makes the page load time very long.
    // To avoid this, truncate long logs to show only the last 10k characters.
    final int maxLogCharacters = 10000;
    if (serverLog.length() > maxLogCharacters) {
      serverLog = serverLog.substring(serverLog.length() - maxLogCharacters);
    }
    htmlAppendLn(
        "<div class='ui segment'>\n",
        "<h3>Server Log</h3>\n",
        "<textarea id='ServerLog' readonly rows='25' cols='160'>",
        serverLog,
        "</textarea></div>");

    htmlFooter();

    response.getWriter().println(html);
  }

  // ==========================================================================
  // Worker page: /webui/worker/<worker-name>
  private void worker(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException, TException {
    response.setContentType("text/html");

    // Get worker name
    String[] path = request.getPathInfo().split("/");
    if (path.length < 3) {
      err404(request, response, "Worker name is missing in URL");
      return;
    }
    String workerName = path[2];
    File workerDir = new File(WebUiConstants.WORKER_DIR + "/" + workerName);
    if (!workerDir.isDirectory()) {
      err404(request, response, "No worker directory for: " + workerName);
      return;
    }

    htmlHeader(workerName);

    // Main actions
    htmlAppendLn("<div class='ui segment'>",
        "<h3>Worker: ", workerName, "</h3>\n",
        "<p><a class='ui button' href='/webui/experiment'>",
        "Run shader families</a></p>\n",
        "<p><form method='post' id='deleteForm'>\n",
        "<input type='hidden' name='path' value='processing/", workerName, "'/>\n",
        "<input type='hidden' name='type' value='delete'/>\n",
        "<input type='hidden' name='num_back' value='2'/>\n",
        "<div class='ui button'",
        " onclick=\"checkAndSubmit('Confirm deletion of ", workerName, "', deleteForm)\">",
        "Remove worker ", workerName, "</div>\n",
        "</form></p>\n",
        "<p><form method='post' id='clearForm'>\n",
        "<input type='hidden' id='' name='queueType' value='worker'/>\n",
        "<input type='hidden' id='' name='type' value='clear'/>\n",
        "<input type='hidden' id='' name='worker' value='", workerName, "'/>\n",
        "<div class='ui button' onclick=\"clearForm.submit();\">Clear Queue</div>\n",
        "</form></p>\n",
        "</div>");

    // Platform info
    String infoPath = WebUiConstants.WORKER_DIR + "/" + workerName
        + "/" + WebUiConstants.WORKER_INFO_FILE;

    htmlAppendLn("<div class='ui segment'>",
        "<h3>Worker info</h3>\n",
        "<button class='ui black basic button' onclick='toggleDiv(this)'",
        " data-hide='worker-info'>Show/Hide</button>\n",
        "<a class='ui button' href='/webui/file/", infoPath, "'>",
        "Get worker info as JSON</a>");

    JsonObject info = accessFileInfo.getWorkerInfo(workerName);

    htmlAppendLn("<table class='worker-info invisible ui celled compact table'>",
        "<thead><tr><th>Attribute</th><th>Value</th></tr></thead>",
        "<tbody>");
    for (Map.Entry<String,JsonElement> entry: info.entrySet()) {
      htmlAppendLn("<tr><td>", entry.getKey(), "</td><td>");
      JsonElement value = entry.getValue();
      // we consider values are either array of primitives, or just a primitive
      if (value.isJsonArray()) {
        for (JsonElement e : value.getAsJsonArray()) {
          html.append(e.getAsString());
          html.append(" ");
        }
      } else {
        html.append(value.getAsString());
      }
      htmlAppendLn("</td>");
    }
    htmlAppendLn("</tbody></table></div>");

    // Worker job queue
    htmlAppendLn("<div class='ui segment'><h3>Worker job queue</h3>");
    //String jobQueue = "No Jobs";
    List<WorkerInfo> workers;
    boolean atLeastOne = false;

    for (WorkerInfo worker : getLiveWorkers(true)) {
      if (worker.getWorkerName().equals(workerName)) {
        List<CommandInfo> commands = worker.getCommandQueue();
        if (commands.size() > 0) {
          atLeastOne = true;
          htmlAppendLn("<button class='ui black basic button'onclick='toggleDiv(this)'",
              " data-hide='job-queue'>Show/Hide</button>\n",
              "<div class='job-queue ui celled list'>");
          for (CommandInfo ci : commands) {
            htmlAppendLn("<div class='item'><div class='header'>", ci.workerName, "</div></div>");
          }
          htmlAppendLn("</div>");
          break;
        }
      }
    }

    if (!atLeastOne) {
      htmlAppendLn("<p>No job queued</p>");
    }
    htmlAppendLn("</div>");

    // Links to all experiment results for the worker
    htmlAppendLn("<div class='ui segment'>\n",
        "<h3>Results</h3>\n",
        "<a href='/webui/worker/", workerName, "/all' class='ui button'>View all results</a>");

    htmlAppendLn("<div class='ui middle aligned selection animated celled list'>");

    File[] shaderFamilies = workerDir.listFiles(File::isDirectory);
    Arrays.sort(shaderFamilies,
        (f1, f2) -> new AlphanumComparator().compare(f1.getName(), f2.getName()));
    for (File shaderFamilyFile : shaderFamilies) {
      final String shaderFamily = shaderFamilyFile.getName();
      ShadersetExp shadersetExp = new ShadersetExp(shaderFamily, workerName, accessFileInfo);

      htmlAppendLn(
          "<a class='item' href='/webui/worker/", workerName, "/", shaderFamily, "'>",
          "<img class='ui mini image' src='/webui/file/", WebUiConstants.WORKER_DIR, "/",
          workerName, "/", shaderFamily, "/reference.png'>",
          "<div class='content'><div class='header'>", shaderFamily, "</div>",
          "Variant done: ", Integer.toString(shadersetExp.nbVariantDone),
          " / ", Integer.toString(shadersetExp.nbVariants),
          " | Wrong images: ", Integer.toString(shadersetExp.nbWrongImage),
          " | Slightly different images: ", Integer.toString(shadersetExp.nbSlightlyDifferentImage),
          " | Errors: ", Integer.toString(shadersetExp.nbErrors),
          "</div></a>");
    }
    htmlAppendLn("</div></div>");

    htmlFooter();
    response.getWriter().println(html);
  }

  //Page to view results of an experiment run by a worker: /webui/worker/<worker-name>/<exp-name>
  private void workerExperiment(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
    response.setContentType("text/html");

    //Get worker name and experiment name
    String[] path = request.getPathInfo().split("/");
    assert (path.length >= 4);
    // path == ["", "worker", "<workername>", "<experimentName>"]
    String workerName = path[2];
    String shaderFamily = path[3];
    String workerSlashExp = workerName + "/" + shaderFamily;

    htmlHeaderResultTable(workerSlashExp);

    htmlAppendLn("<div class='ui segment'><h3>Results for: ", workerSlashExp, "</h3>\n",
        "<form method='post' id='deleteForm'>\n",
        "<input type='hidden' name='path' value='processing/", workerSlashExp, "'/>\n",
        "<input type='hidden' name='type' value='delete'/>\n",
        "<input type='hidden' name='num_back' value='2'/>\n",
        "<div class='ui button'",
        " onclick=\"checkAndSubmit('Confirm deletion of ", workerSlashExp, "', deleteForm)\">",
        "Delete these results</div>\n",
        "</form>");

    htmlAppendLn("</div>");

    // Shader family results table

    htmlAppendLn("<div class='ui segment'>\n",
        "<h3>Results table</h3>");
    String[] workers = new String[1];
    workers[0] = workerName;

    htmlComparativeTable(shaderFamily, workers);

    htmlAppendLn("</div>");

    htmlFooter();
    response.getWriter().println(html);
  }

  //Results page to view all experiment results by a worker: /webui/worker/<worker-name>/all
  private void workerAllExperiments(HttpServletRequest request, HttpServletResponse response)
      throws IOException, ServletException {
    response.setContentType("text/html");

    //Get worker name
    String[] path = request.getPathInfo().split("/");
    assert (path.length >= 4);
    String workerName = path[2];

    htmlHeaderResultTable(workerName + " all results");

    htmlAppendLn("<div class='ui segment'>",
        "<h3>All results for worker: ",  workerName, "</h3>",
        "</div>");

    //Get worker directory and all results within
    File workerDir = new File(WebUiConstants.WORKER_DIR + "/" + workerName);
    if (!workerDir.isDirectory()) {
      err404(request, response, "No worker directory for: " + workerName);
      return;
    }

    //Iterate through files in workerDir - get experiment results
    File[] shaderFamilies = workerDir.listFiles(File::isDirectory);
    Arrays.sort(shaderFamilies,
        (f1, f2) -> new AlphanumComparator().compare(f1.getName(), f2.getName()));
    String[] workers = new String[1];
    for (File shaderFamilyFile : shaderFamilies) {
      final String shaderFamily = shaderFamilyFile.getName();
      htmlAppendLn("<div class='ui segment'>\n", "<h3>", shaderFamily, "</h3>");
      workers[0] = workerName;
      htmlComparativeTable(shaderFamily, workers);
      htmlAppendLn("</div>");
    }

    htmlFooter();
    response.getWriter().println(html);
  }

  //Page to setup experiments using multiple workers/shadersets - /webui/experiment
  private void experimentSetup(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException, TException {

    response.setContentType("text/html");

    htmlHeader("Run shader families");

    htmlAppendLn("<div class='ui segment'>",
        "<h3>Select workers and shader families</h3>",
        "<form class='ui form' method='post'>");

    List<WorkerInfo> workers = getLiveWorkers(false);

    htmlAppendLn("<h4 class='ui dividing header'>Workers</h4>");
    if (workers.size() == 0) {
      htmlAppendLn("<p>No connected worker</p>");
    } else {

      htmlAppendLn("<button type='button' class='ui black basic button'",
          " onclick='applyAllCheckboxes(workercheck, true)'>",
          "Select all</button>",
          "<button type='button' class='ui black basic button'",
          " onclick='applyAllCheckboxes(workercheck, false)'>",
          "Deselect all</button>",
          "<div class='ui hidden divider'></div>");

      int dataNum = 0;
      for (WorkerInfo workerInfo: workers) {
        htmlAppendLn("<div class='field'>",
            "<div class='ui checkbox'>",
            "<input tabindex='0' class='hidden' type='checkbox' name='workercheck'",
            " data-num='", Integer.toString(dataNum), "' onclick='applyCheckbox(event);'",
            " value='", workerInfo.getWorkerName(), "'>",
            "<label data-num='", Integer.toString(dataNum), "' onclick='applyCheckbox(event);'>",
            workerInfo.getWorkerName(), "</label>",
            "</div></div>");
        dataNum += 1;
      }
    }

    List<File> shadersets = getAllShadersets(request, response);

    htmlAppendLn("<h4 class='ui dividing header'>Shader families</h4>");
    if (shadersets.size() == 0) {
      htmlAppendLn("<p>No shader families detected</p>");
    } else {
      htmlAppendLn("<button type='button' class='ui black basic button'",
          " onclick='applyAllCheckboxes(shadersetcheck, true)'>",
          "Select all</button>",
          "<button type='button' class='ui black basic button'",
          " onclick='applyAllCheckboxes(shadersetcheck, false)'>",
          "Deselect all</button>",
          "<div class='ui hidden divider'></div>");

      int dataNum = 0;
      for (File f : shadersets) {
        htmlAppendLn("<div class='field'>",
            "<div class='ui checkbox'>",
            "<input tabindex='0' class='hidden' type='checkbox' name='shadersetcheck'",
            " data-num='", Integer.toString(dataNum), "' onclick='applyCheckbox(event);'",
            " value='", f.getName(), "'>",
            "<label data-num='", Integer.toString(dataNum), "' onclick='applyCheckbox(event);'>",
            f.getName(), "</label>",
            "</div></div>");
        dataNum += 1;
      }
    }

    htmlAppendLn("<button class='ui button' type='submit'>Run jobs</button>",
        "<input type='hidden' name='type' value='experiment'/>",
        "</form></div>");

    htmlFooter();
    response.getWriter().println(html);
  }

  //Results page for a shaderset showing results by all workers - /webui/shaderset/<shaderset-name>
  private void shadersetResults(HttpServletRequest request, HttpServletResponse response)
      throws IOException, ServletException {
    response.setContentType("text/html");

    String shaderFamily = request.getPathInfo().split("/")[2];

    htmlHeaderResultTable(shaderFamily + " all results");

    htmlAppendLn("<div class='ui segment'>\n",
        "<h3>All results for shader family: ", shaderFamily, "</h3>");

    // FIXME: how to cleanly get an array of worker names? (the list-to-array below is ugly)
    File[] workerDirs = new File(WebUiConstants.WORKER_DIR).listFiles();
    List<String> workerList = new ArrayList<String>();
    for (File workerDir : workerDirs) {
      File expResult = new File(workerDir, shaderFamily);
      if (expResult.isDirectory()) {
        workerList.add(workerDir.getName());
      }
    }
    String[] workers = new String[workerList.size()];
    for (int i = 0; i < workerList.size(); i++) {
      workers[i] = workerList.get(i);
    }

    htmlComparativeTable(shaderFamily, workers);

    htmlAppendLn("</div>");
    htmlFooter();
    response.getWriter().println(html);
  }

  // ==========================================================================
  // Page for viewing a single shader - /webui/shader/<shader-filepath>
  private void viewShader(HttpServletRequest request, HttpServletResponse response)
      throws IOException, TException {
    response.setContentType("text/html");

    //Get shader path
    String[] path = request.getPathInfo().split("/");
    StringBuilder shaderPath = new StringBuilder();
    shaderPath.append(path[2]);
    for (int i = 3; i < path.length; i++) {
      shaderPath.append("/").append(path[i]);
    }
    File shader = new File(shaderPath.toString());
    String shaderset = shader.getParentFile().getName();
    String shaderName = FilenameUtils.removeExtension(shader.getName());

    htmlHeader(shaderName);

    htmlAppendLn("<div class='ui segment'><h3>Shader: ", shaderName, "</h3>");

    htmlAppendLn("<a class='ui button' href='/webui/run/", shaderPath.toString(),
        "'>Run shader</a>");

    htmlAppendLn("<a class='ui button' href='/webui/file/", shaderPath.toString(),
        "'>Get shader source code</a>");

    String jsonPath = FilenameUtils.removeExtension(shaderPath.toString()) + ".json";

    htmlAppendLn("<a class='ui button' href='/webui/file/", jsonPath,
        "'>See uniform init values as JSON file</a>");

    //Show shader file contents in textarea
    String shaderContents = getFileContents(new File(shaderPath.toString()));

    htmlAppendLn("</div><div class='ui segment'><h3>Shader source code</h3>\n",
        "<textarea readonly rows='25' cols='160'>");
    htmlAppendLn(shaderContents);
    htmlAppendLn("</textarea>");

    String jsonContents = getFileContents(new File(jsonPath));

    htmlAppendLn("<div class='ui divider'></div>",
        "<p>Uniform values:</p>",
        "<textarea readonly rows='25' cols='160'>");
    htmlAppendLn(jsonContents);
    htmlAppendLn("</textarea>");

    htmlAppendLn("</div>");

    htmlFooter();
    response.getWriter().println(html);
  }

  // Page to view the result from a single shader by a worker - /webui/result/<result-filepath>
  private void viewResult(HttpServletRequest request, HttpServletResponse response)
      throws IOException, ServletException, TException {
    response.setContentType("text/html");

    // Get result path
    final String[] path = request.getPathInfo().split("/");
    if (path.length != 6) {
      err404(request, response, "Path to result " + request.getPathInfo() + " invalid!");
      return;
    }
    final String worker = path[3];
    final String shaderFamily = path[4];
    final String variant = path[5];
    final String variantDir =
        WebUiConstants.WORKER_DIR + "/" + worker + "/" + shaderFamily + "/";
    final String variantFullPathNoExtension = variantDir + variant;

    File infoFile = new File(variantDir, variant + ".info.json");
    if (!infoFile.isFile()) {
      err404(request, response, "Invalid result path: cannot find corresponding info file");
      return;
    }

    JsonObject info = accessFileInfo.getResultInfo(infoFile);
    final String status = info.get("status").getAsString();
    String shaderPath = "shaderfamilies/" + shaderFamily + "/" + variant + ".frag";

    htmlHeader("Single result");
    htmlAppendLn("<div class='ui segment'><h3>Single result</h3>",
        "<p>Shader <b><a href='/webui/shader/", shaderPath, "'>",
        variant, "</a></b> of <b>", shaderFamily, "</b>",
        " run on <b>", worker, "</b><br>",
        "status: <b>", status, "</b></p>");

    htmlAppendLn("<form method='post' id='deleteForm'>\n",
        "<input type='hidden' name='path' value='", variantFullPathNoExtension + ".info.json",
        "'/>\n",
        "<input type='hidden' name='type' value='delete'/>\n",
        "<input type='hidden' name='num_back' value='2'/>\n",
        "<div class='ui button'",
        " onclick=\"checkAndSubmit('Confirm deletion of ",
        variantFullPathNoExtension,
        "', deleteForm)\">",
        "Delete this result</div>\n",
        "</form>",
        "<div class='ui divider'></div>");

    String referencePngPath =
        variantFullPathNoExtension.replace(variant, "reference.png");

    htmlAppendLn("<p>Reference image:</p>",
        "<img src='/webui/file/", referencePngPath, "'>");

    String pngPath = variantDir + variant + ".png";
    File pngFile = new File(pngPath);
    if (pngFile.exists()) {
      htmlAppendLn("<p>Result image:</p>",
          "<img src='/webui/file/", pngPath, "'>");
    }

    String gifPath = variantDir + variant + ".gif";
    File gifFile = new File(gifPath);
    if (gifFile.exists()) {
      htmlAppendLn("<p>Results non-deterministic animation:</p>",
          "<img src='/webui/file/", gifPath, "'>",
          "<p>Here are the second-to-last and last renderings:</p>\n",
          "<img src='/webui/file/",
          gifPath.replace(".gif", "_nondet2.png"),
          "'> ",
          "<img src='/webui/file/",
          gifPath.replace(".gif", "_nondet1.png"),
          "'> ");
    }

    if (!(pngFile.exists()) && !(gifFile.exists())) {
      htmlAppendLn("<p>No image to display for this result status</p>");
    }

    htmlAppendLn("</div>\n",
        "<div class='ui segment'>\n",
        "<h3>Run log</h3>\n",
        "<textarea readonly rows='25' cols='160'>");
    htmlAppendLn(getFileContents(new File(variantDir + variant + ".txt")));
    htmlAppendLn("</textarea>\n",
        "</div>");

    // Get result file
    File result = new File(variantFullPathNoExtension);

    // Information/links for result
    File referenceRes = new File(result.getParentFile(), "reference.info.json");

    String workerName = result.getParentFile().getParentFile().getName();
    File reductionDir = new File(result.getParentFile() + "/reductions", variant);

    // Get results from reductions

    htmlAppendLn("<div class='ui segment'>\n",
        "<h3>Reduction results</h3>");

    final ReductionStatus referenceReductionStatus = getReductionStatus(worker, shaderFamily,
        "reference");
    File referenceShader = new File(WebUiConstants.SHADERSET_DIR + "/"
        + shaderFamily, "reference.frag");
    if (referenceReductionStatus == ReductionStatus.FINISHED) {
      referenceShader = new File(
          ReductionFilesHelper.getReductionDir(worker, shaderFamily, "reference"),
          "reference_reduced_final.frag"
      );
    }


    String reductionHtml = "";
    final ReductionStatus reductionStatus = getReductionStatus(worker, shaderFamily, variant);

    htmlAppendLn("<p>Reduction status: <b>", reductionStatus.toString(), "</b></p>");

    if (reductionStatus == ReductionStatus.NOREDUCTION) {
      htmlAppendLn("<button class='ui button' onclick='toggleDiv(this)'",
          " data-hide='reduce-menu'>Reduce result</button>",
          "<div class='reduce-menu invisible'>");
      htmlReductionForm(
          "shaderfamilies/" + shaderFamily + "/" + variant + ".json",
          reductionDir.getPath(),
          workerName,
          referenceRes.getPath(),
          result.getPath(),
          status);
      htmlAppendLn("</div>");
    } else {
      htmlAppendLn("<p><form method='post' id='deleteReductionForm'>\n",
          "<input type='hidden' name='path' value='", reductionDir.getPath(), "'/>\n",
          "<input type='hidden' name='type' value='delete'/>\n",
          "<input type='hidden' name='num_back' value='2'/>\n",
          "<div class='ui button'",
          " onclick=\"checkAndSubmit('Confirm deletion of reduction results for ",
          shaderFamily + ": " + variant, "', deleteReductionForm)\">",
          "Delete reduction results</div>\n",
          "</form></p>");
    }

    switch (reductionStatus) {

      case NOREDUCTION:
        htmlAppendLn("<p>Reduction does not exist for this result.</p>");
        break;

      case NOTINTERESTING:
        htmlAppendLn("<p>Reduction failed: initial reduction step was not interesting.</p>");
        break;

      case EXCEPTION:
        htmlAppendLn("<p>Reduction failed with an exception:</p>",
            "<textarea readonly rows='25' cols='160'>\n",
            getFileContents(ReductionProgressHelper.getReductionExceptionFile(
                ReductionFilesHelper.getReductionDir(worker, shaderFamily, variant), variant)),
            "</textarea>");
        break;

      case ONGOING:
        final Optional<Integer> reductionStep = ReductionProgressHelper
              .getLatestReductionStepAny(ReductionFilesHelper
                    .getReductionDir(worker, shaderFamily, variant),
                  "variant", fileOps);
        htmlAppendLn(
            "<p>Reduction not finished for this result: ",
            (reductionStep
                .map(integer -> "made " + integer + " step(s)")
                .orElse("no steps made yet")
            ),
            ".</p>"
        );
        break;

      case FINISHED:
        produceDiff(variant, reductionDir, referenceShader);
        break;

      case INCOMPLETE:
        File reductionIncompleteResult = new File(reductionDir,
            variant + "_incomplete_reduced_final.frag");
        htmlAppendLn("<p>Reduction hit the step limit.</p>");
        produceDiff(variant, reductionDir, referenceShader);
        break;

      default:
        err404(request, response, "Invalid Reduction status");
    }

    // Show reduction log, if it exists, regardless of current reduction status
    final File logFile =
        new File(ReductionFilesHelper.getReductionDir(worker, shaderFamily, variant),
          "command.log");
    if (logFile.exists()) {
      htmlAppendLn("<p>Contents of reduction log file:</p>",
          "<textarea readonly rows='25' cols='160'>\n",
          getFileContents(logFile),
          "</textarea>");
    }

    htmlAppendLn("</div>");

    htmlFooter();
    response.getWriter().println(html);
  }

  private void produceDiff(String shader, File reductionDir, File referenceShader)
        throws TException {
    File reductionResult = new File(reductionDir, shader + "_reduced_final.frag");
    List<String> args = new ArrayList<>();
    args.add("diff");
    args.add("-u");
    args.add(referenceShader.getPath());
    args.add(reductionResult.getPath());
    // TODO: make the reducer do the diff at the end of reduction, or at least do the diff
    // here locally without resorting to the server!
    CommandResult commandResult;
    commandResult = fuzzerServiceManagerProxy.executeCommand("diff", args);

    htmlAppendLn("<a class='ui button' href='/webui/shader/", referenceShader.getPath(),
        "'>View reference shader</a>");
    htmlAppendLn("<a class='ui button' href='/webui/shader/", reductionResult.getPath(),
        "'>View reduced shader</a>");

    // Watch out, diff exits with 1 if there is a difference.
    switch (commandResult.getExitCode()) {
      case 0:
        // files are the same! That's suspicious
        htmlAppendLn("<p>The reduced variant is the same as the reduced reference! ",
            "(diff returns 0)</p>");
        break;
      case 1:
        // files differ
        htmlAppendLn("<p>Differences in reduced shader:</p>",
            "<textarea readonly rows='25' cols='160'>\n",
            commandResult.getOutput(),
            "</textarea>");
        break;
      default:
        // probably a diff error
        htmlAppendLn("<p>Attempt to diff shaders failed with exit code ",
            Integer.toString(commandResult.getExitCode()), "</p>",
            "<textarea readonly rows='25' cols='160'>\n",
            commandResult.getError(),
            "</textarea>");
    }
  }

  private ReductionStatus getReductionStatus(String worker, String shaderSet, String shader) {
    final File reductionDir = ReductionFilesHelper.getReductionDir(worker, shaderSet, shader);
    if (! reductionDir.exists()) {
      return ReductionStatus.NOREDUCTION;
    }

    if (new File(reductionDir, Constants.REDUCTION_INCOMPLETE).exists()) {
      return ReductionStatus.INCOMPLETE;
    }

    if (new File(reductionDir, shader + "_reduced_final.frag").exists()) {
      return ReductionStatus.FINISHED;
    }

    if (new File(reductionDir, "NOT_INTERESTING").exists()) {
      return ReductionStatus.NOTINTERESTING;
    }

    if (ReductionProgressHelper.getReductionExceptionFile(reductionDir, shader).exists()) {
      return ReductionStatus.EXCEPTION;
    }

    return ReductionStatus.ONGOING;
  }

  // Page to setup running a shader on workers
  private void startRunShader(HttpServletRequest request, HttpServletResponse response)
      throws IOException, TException {
    response.setContentType("text/html");

    htmlHeader("Run shader");

    String[] path = request.getPathInfo().split("/");
    StringBuilder shaderPath = new StringBuilder();
    shaderPath.append(path[2]);
    for (int i = 3; i < path.length; i++) {
      shaderPath.append("/").append(path[i]);
    }


    // TODO: make it so that it compares with the reference, and get rid of this message
    htmlAppendLn("<script>",
        "window.onload = alert('Warning - results of running a single shader manually",
        " are always flagged as issue results -- never SAME_AS_REFERENCE')",
        "</script>");

    htmlAppendLn("<div class='ui segment'>",
        "<h3>Run shader:", shaderPath.toString(), "</h3>\n",
        "<a class='ui button' href='/webui/shader/", shaderPath.toString(), "'>",
        "Go back to shader page</a>\n",
        "<div class='ui divider'></div>\n",
        "<form class='ui form' method='post'>\n",
        "<h4>Select workers</h4>\n",
        "<input type='hidden' name='type' value='run_shader'>",
        "<input type='hidden' name='shaderpath' value='",
        FilenameUtils.removeExtension(shaderPath.toString()) + ".json", "'>\n",
        "<button class='ui basic black button' type='button'",
        " onclick=\"applyAllCheckboxes('workercheck', true)\">Select all workers</button>\n",
        "<button class='ui basic black button' type='button'",
        " onclick=\"applyAllCheckboxes('workercheck', false)\">Deselect all workers</button>",
        "<div class='ui divider'></div>");

    int dataNum = 0;
    for (WorkerInfo workerInfo: getLiveWorkers(false)) {
      htmlAppendLn("<div class='ui field'>",
          "<div class='ui checkbox'>",
          "<input tabindex='0' class='hidden' type='checkbox' name='workercheck'",
          " data-num='", Integer.toString(dataNum), "' onclick='applyCheckbox(event);'",
          " value='", workerInfo.getWorkerName(), "'>",
          "<label data-num='", Integer.toString(dataNum), "' onclick='applyCheckbox(event);'>",
          workerInfo.getWorkerName(), "</label>",
          "</div></div>");
      dataNum += 1;
    }
    if (dataNum == 0) {
      htmlAppendLn("<p><b>No worker connected</b></p>");
    }

    htmlAppendLn("<div class='ui divider'></div>\n",
        "<button class='ui button' type='submit'>Run shader</button>\n",
        "</form></div>");

    htmlFooter();
    response.getWriter().println(html);
  }

  //POST - Attempts to run experiments, returns result of attempts (String message for user)
  // HUGUES: this is crap. we should have a POST that adds a job, and reply some JSON to tell
  //         whether the job has been scheduled or not. Then call this POST with AJAX.
  private void startExperiment(HttpServletRequest request, HttpServletResponse response)
      throws IOException, TException {
    StringBuilder msg = new StringBuilder();

    String[] workers = request.getParameterValues("workercheck");
    String[] shadersets = request.getParameterValues("shadersetcheck");

    if (workers == null || workers.length == 0) {
      msg.append("Select at least one worker");
    } else if (shadersets == null || shadersets.length == 0) {
      msg.append("Select at least one shaderset");
    } else {
      //Start experiments for each worker/shader combination, display result in alert
      for (String worker : workers) {
        for (String shaderset : shadersets) {
          msg.append("Experiment ").append(shaderset).append(" on worker ").append(worker);
          List<String> commands = new ArrayList<>();
          commands.add("run_shader_family");
          commands.add("--server");
          commands.add("http://localhost:8080");
          commands.add("--worker");
          commands.add(worker);
          commands.add("--output");
          commands.add("processing/" + worker + "/" + shaderset);
          commands.add(WebUiConstants.SHADERSET_DIR + "/" + shaderset);
          fuzzerServiceManagerProxy.queueCommand("run_shader_family: " + shaderset, commands,
              worker,"processing/" + worker + "/" + shaderset + "/command.log");
          msg.append(" started successfully!\\n");
        }
      }
    }

    html.setLength(0);
    htmlAppendLn("<script>\n",
        getResourceContent("goBack.js"), "\n",
        "window.onload = goBack('", msg.toString(), "', 1);\n",
        "</script>");

    response.getWriter().println(html);
  }

  // Page for selecting workers/shadersets to compare results - /webui/compareResults
  private void compareResults(HttpServletRequest request, HttpServletResponse response)
      throws IOException, ServletException {

    response.setContentType("text/html");

    htmlHeaderResultTable("Compare Results");

    htmlAppendLn("<div class='ui segment'>\n",
        "<h3>Comparative results</h3>\n");

    String[] shaderFamilies = request.getParameterValues("shadersetcheck");
    String[] workers = request.getParameterValues("workercheck");

    for (String shaderFamily: shaderFamilies) {
      htmlAppendLn("<h4 class='ui dividing header'>", shaderFamily, "</h4>");
      htmlComparativeTable(shaderFamily, workers);
    }
    htmlAppendLn("</div>");
    htmlFooter();
    response.getWriter().println(html);
  }

  // Page for selecting workers/shadersets to compare results - /webui/compare
  private void compareWorkers(HttpServletRequest request, HttpServletResponse response)
      throws IOException, ServletException {

    response.setContentType("text/html");

    htmlHeader("Compare workers");

    htmlAppendLn("<div class='ui segment'>\n",
        "<h3>Compare results of workers</h3>\n",
        "<h4>Select workers</h4>\n",
        //"<button class='ui black basic button' onclick='toggleDiv(this)'",
        //" data-hide='worker-list'>Show/Hide</button>\n",
        //"<div class='ui hidden divider'></div>\n",
        "<form class='ui form' method='post' action='/webui/compareResults'>\n",
        "<input type='hidden' name='type' value='compareResults'/>\n",
        //"<div class='worker-list'>",
        "<button class='ui basic black button' type='button'",
        " onclick=\"applyAllCheckboxes(workercheck, true)\">Select all workers</button>\n",
        "<button class='ui basic black button' type='button'",
        " onclick=\"applyAllCheckboxes(workercheck, false)\">Deselect all workers</button>\n",
        "<div class='ui hidden divider'></div>");

    int dataNum = 0;
    for (File workerFile: getAllWorkers(request, response)) {
      htmlAppendLn("<div class='ui field'>",
          "<div class='ui checkbox'>",
          "<input tabindex='0' class='hidden' type='checkbox' name='workercheck'",
          " data-num='", Integer.toString(dataNum), "' onclick='applyCheckbox(event);'",
          " value='", workerFile.getName(), "'>",
          "<label data-num='", Integer.toString(dataNum), "' onclick='applyCheckbox(event);'>",
          workerFile.getName(), "</label>",
          "</div></div>");
      dataNum += 1;
    }
    if (dataNum == 0) {
      htmlAppendLn("<p><b>No worker found.</b></p>");
    }

    htmlAppendLn(//"</div>\n", // Hugues: end matching div to show/hide workers
        "<div class='ui divider'></div>\n",
        "<h4>Select shader families</h4>\n",
        // Hugues: this refuses to work, I'm not sure why.
        //"<button class='ui black basic button' onclick='toggleDiv(this)'",
        //" data-hide='shader-family-list'>Show/Hide</button>\n",
        //"<div class='ui hidden divider'></div>\n",
        //"<div class='shader-family-list'>",
        "<button class='ui basic black button' type='button'",
        " onclick=\"applyAllCheckboxes(shadersetcheck, true)\">",
        "Select all shader families</button>\n",
        "<button class='ui basic black button' type='button'",
        " onclick=\"applyAllCheckboxes(shadersetcheck, false)\">",
        "Deselect all shader families</button>\n",
        "<div class='ui hidden divider'></div>");

    dataNum = 0;
    for (File shaderFamily: getAllShadersets(request, response)) {
      htmlAppendLn("<div class='ui field'>",
          "<div class='ui checkbox'>",
          "<input tabindex='0' class='hidden' type='checkbox' name='shadersetcheck'",
          " data-num='", Integer.toString(dataNum), "' onclick='applyCheckbox(event);'",
          " value='", shaderFamily.getName(), "'>",
          "<label data-num='", Integer.toString(dataNum), "' onclick='applyCheckbox(event);'>",
          shaderFamily.getName(), "</label>",
          "</div></div>");
      dataNum += 1;
    }
    if (dataNum == 0) {
      htmlAppendLn("<p><b>No shader family found.</b></p>");
    }

    htmlAppendLn(//"</div>\n", // Hugues: matching end of div for show/hide
        "<div class='ui divider'></div>\n",
        "<button class='ui button' type='submit'>Compare</button>\n",
        "</form></div>");

    htmlFooter();
    response.getWriter().println(html);
  }

  //POST - Deletes a given file - /webui/delete/<result-filepath>
  private void delete(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
    response.setContentType("text/html");

    final File file = new File(request.getParameter("path"));
    final String numBack = request.getParameter("num_back");

    if (!file.exists()) {
      err404(request, response, "Path to results " + file.getPath() + " not allowed/valid!");
      return;
    }

    if (file.toString().endsWith(".info.json")) {
      fileOps.deleteShaderJobResultFile(file);
      // There might also be a reduction result. If so, we delete it.
      // e.g. variant_001
      final String variantName = FileHelper.removeEnd(file.getName(), ".info.json");
      // e.g. reductions/variant_001
      final File reductionDir = new File(file.getParentFile(), "reductions/" + variantName);
      fileOps.deleteQuietly(reductionDir);
    } else {
      FileUtils.forceDelete(file);
    }

    html.setLength(0);
    htmlAppendLn("<script>\n",
        getResourceContent("goBack.js"), "\n",
        "window.onload = goBack('", file.getPath(), " deleted!', ", numBack, ");\n",
        "</script>");

    response.getWriter().println(html);
  }

  //POST - Link to start reductions on a result
  private void reduce(HttpServletRequest request, HttpServletResponse response)
      throws IOException {
    response.setContentType("text/html");

    final List<String> args = new ArrayList<>();
    args.add("glsl-reduce");
    final String shaderJobFilePath = request.getParameter("shader-path");
    args.add(shaderJobFilePath);
    args.add("--reduction-kind");
    final String reductionType = request.getParameter("reduction-kind");
    args.add(reductionType);
    args.add("--metric");
    args.add(request.getParameter("metric"));
    args.add("--output");
    final String output = request.getParameter("output");
    args.add(output);
    if (!ReductionKind.NO_IMAGE.toString().equalsIgnoreCase(reductionType)) {
      args.add("--reference");
      args.add(request.getParameter("reference-image"));
    }
    args.add("--worker");
    final String worker = request.getParameter("worker");
    args.add(worker);
    args.add("--server");
    args.add("http://localhost:8080");
    final String threshold = request.getParameter("threshold");
    if (threshold != null) {
      args.add("--threshold");
      args.add(threshold);
    }
    final String errorString = request.getParameter("error-string");
    if (errorString != null) {
      args.add("--error-string");
      args.add(errorString);
    }
    final String preserveSemantics = request.getParameter("preserve-semantics");
    if (preserveSemantics != null) {
      if (preserveSemantics.equals("on")) {
        args.add("--preserve-semantics");
      }
    }
    final String skipRender = request.getParameter("skip-render");
    if (skipRender != null) {
      if (skipRender.equals("on")) {
        args.add("--skip-render");
      }
    }
    final String timeout = request.getParameter("timeout");
    if (timeout != null) {
      args.add("--timeout");
      args.add(timeout);
    }
    final String maxSteps = request.getParameter("max-steps");
    if (maxSteps != null) {
      args.add("--max-steps");
      args.add(maxSteps);
    }
    final String retryLimit = request.getParameter("retry-limit");
    if (retryLimit != null) {
      args.add("--retry-limit");
      args.add(retryLimit);
    }
    final String seed = request.getParameter("seed");
    if (seed != null) {
      args.add("--seed");
      args.add(seed);
    }

    String message;
    try {
      fuzzerServiceManagerProxy.queueCommand(
          args.get(0) + ":" + shaderJobFilePath, args, worker, output + "/command.log");
      message = "Reduction started successfully!";
    } catch (TException exception) {
      message = "Reduction failed (is worker live?):\\n" + exception.getMessage();
    }
    reduceReference(shaderJobFilePath, worker);

    html.setLength(0);
    htmlAppendLn("<script>\n",
        getResourceContent("goBack.js"), "\n",
        "window.onload = goBack('", message, "', 1);\n",
        "</script>");

    response.getWriter().println(html);
  }

  private void reduceReference(String shaderJobFilePath, String worker) {
    File shaderJobFile = new File(shaderJobFilePath);
    File referenceShaderJobFile;
    if (!shaderJobFile.getName().startsWith("reference")) {
      referenceShaderJobFile =
          new File(shaderJobFile.getParentFile(), "reference.json");
    } else {
      referenceShaderJobFile = shaderJobFile;
    }
    String shaderset = referenceShaderJobFile.getParentFile().getName();
    File reductionDir =
        new File(WebUiConstants.WORKER_DIR + "/" + worker + "/" + shaderset + "/reductions",
            "reference");
    if (reductionDir.isDirectory()) {
      return;
    }
    File referenceResult =
        new File(WebUiConstants.WORKER_DIR + "/" + worker + "/" + shaderset,
            "reference.info.json");
    List<String> args = new ArrayList<>();
    args.add("glsl-reduce");
    args.add(referenceShaderJobFile.getPath());
    args.add("--reduction-kind");
    args.add("IDENTICAL");
    args.add("--preserve-semantics");
    args.add("--output");
    args.add(reductionDir.getPath());
    args.add("--reference");
    args.add(referenceResult.getPath());
    args.add("--worker");
    args.add(worker);
    args.add("--server");
    args.add("http://localhost:8080");
    try {
      fuzzerServiceManagerProxy.queueCommand(
          "Reference Reduction: " + shaderset,
          args,
          worker,
          new File(reductionDir, "command.log").toString());
    } catch (TException exception) {
      throw new RuntimeException(exception);
    }
  }

  //POST - Link to clear a queue (worker or reduction)
  private void clear(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
    response.setContentType("text/html");

    String queueType = request.getParameter("queueType");
    String worker = request.getParameter("worker");
    String msg;

    if (queueType.equals("worker")) {
      //Attempt to clear worker job queue
      try {
        fuzzerServiceManagerProxy.clearClientJobQueue(worker);
        msg = "Queue for worker " + worker + " cleared!";
      } catch (TException exception) {
        msg = "Worker " + worker + " has no queued commands!";
      }
    } else {
      err404(request, response, "Unexpected queue type!");
      return;
    }

    html.setLength(0);
    htmlAppendLn("<script>\n",
        getResourceContent("goBack.js"), "\n",
        "window.onload = goBack('", msg, "', 1);\n",
        "</script>");

    response.getWriter().println(html);
  }

  //Renames a worker (worker) (renames dir in the filesystem) and redirects to new worker page
  private void renameWorker(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
    response.setContentType("text/html");

    //Get details of worker to rename
    String query = request.getQueryString();
    String worker = request.getParameter("worker");
    String name = request.getParameter("name");

    //Check validity of renaming
    File workerDir = new File(WebUiConstants.WORKER_DIR, worker);
    File newWorkerDir = new File(WebUiConstants.WORKER_DIR, name);

    String specialChars = "[^a-zA-Z0-9_/-]";
    Pattern pattern = Pattern.compile(specialChars);

    //Check if worker is live (cannot rename live workers without some new thrift functionality
    boolean workerLive = false;
    try {
      for (WorkerInfo workerInfo : getLiveWorkers(true)) {
        if (workerInfo.getWorkerName().equals(worker)) {
          workerLive = true;
          break;
        }
      }
    } catch (TException exception) {
      err404(request, response, exception.getMessage());
      return;
    }

    String msg;
    if (pattern.matcher(name).find()) {
      //Special chars found
      msg = "New name must conform to pattern: " + specialChars;
      name = worker;

    } else if (!workerDir.exists()) {
      //Worker doesn't exist
      msg = "Worker " + workerDir.getName() + " does not exist!";

    } else if (newWorkerDir.exists()) {
      //New name already in use
      msg = "Worker " + name + " already exists!";

    } else if (workerLive) {
      //Worker currently live
      msg = "Worker " + worker + " is currently connected and cannot be renamed.";
      name = worker;

    } else {
      //Attempt to rename
      workerDir.renameTo(newWorkerDir);
      if (!newWorkerDir.exists()) {
        msg = "Renaming failed!";
      } else {
        msg = "Renamed successfully";
      }
    }

    html.setLength(0);
    htmlAppendLn("<script>\n",
        getResourceContent("redirect.js"), "\n",
        "window.onload = redirect('", msg, "', '/webui/worker/",  name, "');\n",
        "</script>");

    response.getWriter().println(html);
  }

  private void runShader(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
    response.setContentType("text/html");
    // e.g. shaderfamilies/family1/variant2.json
    String shaderPath = request.getParameter("shaderpath");
    // e.g. shaderfamilies/family1/variant2.json
    File shader = new File(shaderPath);
    // e.g. family1
    String shaderset = shader.getParentFile().getName();
    String[] workers = request.getParameterValues("workercheck");
    String javascript = getResourceContent("goBack.js");

    if (workers != null && workers.length > 0) {
      List<String> commands = new ArrayList<>();
      commands.add("run_shader_family");
      commands.add(shaderPath);
      commands.add("--server");
      commands.add("http://localhost:8080");
      StringBuilder msg = new StringBuilder();
      for (String worker : workers) {
        commands.add("--worker");
        commands.add(worker);
        commands.add("--output");
        commands.add("processing/" + worker + "/" + shaderset + "/");
        try {
          fuzzerServiceManagerProxy
              .queueCommand("run_shader_family: " + shaderPath, commands, worker,
                  "processing/" + worker + "/" + shaderset + "/command.log");
        } catch (TException exception) {
          err404(request, response, exception.getMessage());
          return;
        }
        commands.remove(commands.size() - 1);
        msg.append("Shader running on ").append(worker).append("\\n");
      }
      javascript += "\nwindow.onload = goBack('" + msg.toString() + "', 2);";
    } else {
      javascript += "\nwindow.onload = goBack('Please select a worker', 1);";
    }



    html.setLength(0);
    htmlAppendLn("<script>\n",
        javascript, "\n",
        "</script>");

    response.getWriter().println(html);
  }

  private void err404(HttpServletRequest request, HttpServletResponse response, String msg)
      throws ServletException, IOException {
    response.setContentType("text/html");
    response.setStatus(404);
    String content = "<pre>\n404\nPath info: " + request.getPathInfo() + "\n";
    if (msg != null) {
      content += "\nMessage:\n" + msg + "\n";
    }
    content += "</pre>\n";
    PrintWriter out = response.getWriter();
    out.println(content);
  }

  private void err404(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
    err404(request, response, null);
  }

  private void resource(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
    // try to serve a public resource
    InputStream resourceAsStream = this.getClass()
        .getResourceAsStream("/public" + request.getPathInfo());
    if (resourceAsStream == null) {
      err404(request, response);
      return;
    }
    response.setContentType(getContentType(request.getPathInfo()));
    copyStream(resourceAsStream, response.getOutputStream());
  }

  private void file(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
    String prefix = "/" + WebUiConstants.FILE_ROUTE + "/";
    if (!request.getPathInfo().startsWith(prefix)) {
      err404(request, response);
      return;
    }
    String filename = request.getPathInfo().substring(prefix.length());
    File file = new File(filename);
    if ((!file.exists()) || file.isDirectory()) {
      err404(request, response);
      return;
    }
    response.setContentType(getContentType(filename));
    ServletOutputStream outputStream = response.getOutputStream();
    outputStream.write(Files.readAllBytes(Paths.get(filename)));
  }

  // ========================= "GET" requests dispatcher =======================================

  public void doGet(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {

    startTime = System.currentTimeMillis();

    // Dispatch based on path structure
    String path = request.getPathInfo();
    if (path == null) {
      path = "/";
    }
    String[] actions = path.split("/");

    try {
      if (actions.length == 0) {
        // 'webui/' : homepage
        homepage(request, response);
      } else if (actions[1].equals(WebUiConstants.FILE_ROUTE)) {
        // serve a 'raw file', proxy to the filesystem
        file(request, response);
      } else if (actions[1].equals("worker")) {
        if (actions.length >= 4 && actions[3].equals("all")) {
          workerAllExperiments(request, response);
        } else if (actions.length >= 4) {
          workerExperiment(request, response);
        } else {
          worker(request, response);
        }
      } else if (actions[1].equals("experiment")) {
        experimentSetup(request, response);
      } else if (actions[1].equals("shaderset")) {
        shadersetResults(request, response);
      } else if (actions[1].equals("shader")) {
        viewShader(request, response);
      } else if (actions[1].equals("result")) {
        viewResult(request, response);
      } else if (actions[1].equals("run")) {
        startRunShader(request, response);
      } else if (actions[1].equals("compare")) {
        compareWorkers(request, response);
      } else {
        // if no action match, serve from resources/public
        resource(request, response);
      }
    } catch (Exception exception) {
      exception.printStackTrace();
      throw new ServletException(
          "GET method failed, request was: " + request.toString(),
          exception);
    }
  }

  // ========================= "POST" requests dispatcher =======================================

  public void doPost(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
    String type = request.getParameter("type");

    startTime = System.currentTimeMillis();

    // Hugues: dispatching base on a "type" parameter is NOT ideal.
    // We should scan the resquest path instead.

    if (type == null) {
      err404(request, response, "Invalid POST request: no type!");
      return;
    }

    try {
      if (type.equals("delete")) {
        delete(request, response);
      } else if (type.equals("reduce")) {
        reduce(request, response);
      } else if (type.equals("clear")) {
        clear(request, response);
      } else if (type.equals("rename")) {
        renameWorker(request, response);
      } else if (type.equals("run_shader")) {
        runShader(request, response);
      } else if (type.equals("compareResults")) {
        compareResults(request, response);
      } else if (type.equals("experiment")) {
        startExperiment(request, response);
      }
    } catch (Exception exception) {
      exception.printStackTrace();
      throw new ServletException("POST method failed, request was: " + request.toString());
    }
  }

  // HTML functions ===========================================================

  private void htmlAppendLn(String... args) {
    for (String a: args) {
      html.append(a);
    }
    html.append("\n");
  }

  private void htmlHeader(String title) {
    htmlHeaderImplem(title, true);
  }

  private void htmlHeaderResultTable(String title) {
    htmlHeaderImplem(title, false);
  }

  private void htmlHeaderImplem(String title, boolean withContainer) {
    html.setLength(0);

    htmlAppendLn(
        "<!DOCTYPE html>\n",
        "<html>\n",
        "<head>\n",
        "<meta charset='utf-8' />\n",
        "<meta http-equiv='X-UA-Compatible' content='IE=edge,chrome=1' />\n",
        "<meta name='viewport' content='width=device-width,",
        " initial-scale=1.0, maximum-scale=1.0'>\n",
        "<title>",
        title,
        " - GraphicsFuzz</title>\n",
        "<link href='/static/semantic/semantic.min.css'",
        " rel='stylesheet' type='text/css' />\n",
        "<link href='/webui/graphicsfuzz.css' rel='stylesheet' type='text/css' />\n",
        "<script src='/static/jquery/jquery-3.1.1.min.js'></script>\n",
        "<script src='/static/semantic/semantic.min.js'></script>\n",
        "<script src='/webui/graphicsfuzz.js'></script>\n",
        "</head>\n",
        "<body>\n",
        withContainer ? "<div class='ui container'>\n" : "<div class='resultmain'>",
        "<div class='ui basic segment'>\n",
        "<a href='/webui'><img class='ui small image' src='/webui/GraphicsFuzz_logo.png'></a>\n",
        "</div>\n");
  }

  private void htmlFooter() {
    htmlAppendLn(
        "<pre>Page generated in: ",
        Long.toString(System.currentTimeMillis() - startTime),
        "ms</pre>\n",
        "<div class='ui center aligned basic segment'>",
        "<p>Powered by <a href='https://github.com/google/graphicsfuzz'>GraphicsFuzz</a></p>",
        "</div>\n",
        "</div>\n",
        "</body>\n</html>\n");
  }

  private String reductionLabelColor(ReductionStatus reductionStatus) {
    switch (reductionStatus) {
      case NOREDUCTION:
        return "grey";
      case FINISHED:
        return "green";
      case ONGOING:
        return "teal";
      case INCOMPLETE:
        return "orange";

      // All "bad" status of reduction in red
      case NOTINTERESTING:
        return "red";
      case EXCEPTION:
        return "red";
      default:
        return "red";
    }
  }

  private void htmlVariantResultTableCell(File variantInfoFile, String referencePngPath,
      ReductionStatus reductionStatus) throws FileNotFoundException {

    JsonObject info = accessFileInfo.getResultInfo(variantInfoFile);
    String status = info.get("status").getAsString();
    String cellHref = "/webui/result/" + variantInfoFile.getPath().replace(".info.json", "");

    if (status.contentEquals("SUCCESS")) {

      if (imageIsIdentical(info)) {
        htmlAppendLn("<td class='selectable center aligned'><a href='",
            cellHref,
            "'>",
            "<img class='ui centered tiny image' src='/webui/file/", referencePngPath, "'></a>");
      } else {
        htmlAppendLn("<td class='",
            imageIsAcceptable(info) ? "warnimg" : "wrongimg",
            " selectable center aligned'>",
            "<a href='",
            cellHref,
            "'>",
            "<img class='wrongimg ui centered tiny image' src='/webui/file/",
            variantInfoFile.getPath().replace(".info.json", ".png"),
            "'></a>\n",
            "<div class='ui tiny ", reductionLabelColor(reductionStatus), " label'>",
            reductionStatus.toString(),
            "</div>");
      }

    } else if (status.contentEquals("NONDET")) {

      htmlAppendLn("<td class='selectable nondet center aligned'><a href='",
          cellHref,
          "'>",
          "<img class='ui centered tiny image' src='/webui/file/",
          variantInfoFile.getPath().replace(".info.json", ".gif"),
          "'></a>\n",
          "<div class='ui tiny ", reductionLabelColor(reductionStatus), " label'>",
          reductionStatus.toString(),
          "</div>");

    } else {

      htmlAppendLn("<td class='gfz-error bound-cell-width selectable center aligned'>",
          "<a href='",
          cellHref,
          "'>", status.replace("_", " "), " ",
          info.get("stage").getAsString().replace("_", " "),
          "</a>\n",
          "<div class='ui tiny ", reductionLabelColor(reductionStatus), " label'>",
          reductionStatus.toString(),
          "</div>");

    }
    htmlAppendLn("</td>");
  }

  // Hugues: This is way too complex, do something *simpler* using semantic-ui
  private void htmlReductionForm(
      String shaderJobFilePath,
      String output,
      String worker,
      String referenceResultPath,
      String variantShaderJobResultFileNoExtension,
      String resultStatus) {
    final boolean success = resultStatus.equals("SUCCESS");

    htmlAppendLn(
        "<form class='ui form' method='post' id='reduceForm'>",
        "<fieldset>",
        "<legend>Reduction Options</legend>",
        "<input type='hidden' name='type' value='reduce'>",
        "<input type='hidden' name='shader-path' value='", shaderJobFilePath, "'>",
        "<input type='hidden' name='output' value='", output, "'>",
        "<input type='hidden' name='worker' value='", worker, "'>",
        "<table><tr>",
        "<td class='row_middle'><h3 class='no_margin'>Required Arguments</h3></td>",
        "<td class='row_middle'><h3 class='no_margin'>Optional Arguments</h3></td>",
        "</tr><tr>",
        "<td class='row_top'>",
        "<table class='reduce_table'>",
        "<tr>",
        "<td align='right'><p class='no_space'>Reduction Mode:</p></td>",
        "<td>",
        "<select name='reduction-kind' class='reduce_col' onchange='selectReduceKind(this);'>",
        "<option value='ABOVE_THRESHOLD'>Above Threshold</option>",
        (success
            ? "<option value='NO_IMAGE'>No Image</option>"
            : "<option value='NO_IMAGE' selected='selected'>No Image</option>"
        ),
        "<option value='NOT_IDENTICAL'>Not Identical</option>",
        "<option value='IDENTICAL'>Identical</option>",
        "<option value='BELOW_THRESHOLD'>Below Threshold</option>",
        "</select>",
        "</td>",
        "</tr>",
        (success
            ? "<tr id='metric_tr' class=''>"
            : "<tr id='metric_tr' class='invisible'>"
        ),
        "<td align='right'><p class='no_space'>Comparison metric:</p></td>",
        "<td>",
        "<select name='metric' class='reduce_col'>",
        "<option value='HISTOGRAM_CHISQR'>HISTOGRAM_CHISQR</option>",
        "<option value='PSNR'>PSNR</option>",
        "</select>",
        "</td>",
        "</tr>",
        (success
            ? "<tr id='threshold_tr' class=''>"
            : "<tr id='threshold_tr' class='invisible'>"
        ),
        "<td align='right'><p class='no_space'>Threshold:</p></td>",
        "<td><input class='reduce_col' name='threshold' value='100.0'/></td>",
        "</tr>",
        (success
            ? "<tr id='error_string_tr' class='invisible'>"
            : "<tr id='error_string_tr' class=''>"
        ),
        "<td align='right'><p class='no_space'>Error string to look for in log <br>",
        "(no need to escape spaces):</p></td>",
        "<td><input class='reduce_col' name='error-string' value=''/></td>",
        "</tr>",
        "<tr>",
        "<td class='row_top row_right' rowspan='2'><p class='no_space'>Reference Image:</p></td>",
        "<td><input type='radio' value='", referenceResultPath, "' checked='checked'",
        " name='reference-image'/><p class='no_space'>    Reference</p></td>",
        "</tr>",
        "<tr align='left'>",
        "<td><input type='radio' value='", variantShaderJobResultFileNoExtension,
        ".info.json' name='reference-image'/><p class='no_space'>    Variant</p></td>",
        "</tr>",
        "</table>",
        "</td>",
        "<td>",
        "<table class='reduce_table'>",
        "<tr>",
        "<td align='right' class=''>",
        "<p class='no_space'>Preserve Semantics:</p>",
        "</td>",
        "<td class='checkbox'>",
        (success
            ? "<input type='checkbox' name='preserve-semantics' checked='checked'/>"
            : "<input type='checkbox' name='preserve-semantics'/>"
        ),
        "</td>",
        "</tr>",
        "<tr>",
        "<td align='right'>",
        "<p class='no_space'>Skip Render:</p>",
        "</td>",
        "<td class='checkbox'>",
        "<input type='checkbox' name='skip-render'/>",
        "</td>",
        "</tr>",
        "<tr>",
        "<td align='right'>",
        "<p class='no_space'>Timeout:</p>",
        "</td>",
        "<td>",
        "<input size='15' name='timeout' value='30'/>",
        "</td>",
        "</tr>",
        "<tr>",
        "<td align='right'>",
        "<p class='no_space'>Max Steps:</p>",
        "</td>",
        "<td>",
        "<input size='15' name='max-steps' value='2000'/>",
        "</td>",
        "</tr>",
        "<tr>",
        "<td align='right'>",
        "<p class='no_space'>Retry Limit:</p>",
        "</td>",
        "<td>",
        "<input size='15' name='retry-limit' value='2'/>",
        "</td>",
        "</tr>",
        "<tr>",
        "<td align='right'>",
        "<p class='no_space'>Random Seed:</p>",
        "</td>",
        "<td>",
        "<input size='15' name='seed' value='-136936935'/>",
        "</td>",
        "</tr>",
        "<tr>",
        "<td align='right' class=''>",
        "<p class='no_space'>Initial phase:</p>",
        "</td>",
        "</tr>",
        "</table>",
        "</td>",
        "</tr>",
        "</table>",
        "<input type='submit' value='Start Reduction'/>",
        "</fieldset>",
        "</form>");
  }

  private void htmlComparativeTable(String shaderFamily, String[] workers)
      throws FileNotFoundException {

    htmlAppendLn("<table class='ui celled compact collapsing table'>\n",
        "<thead><tr>");
    File variantsDir = new File(WebUiConstants.SHADERSET_DIR + "/" + shaderFamily);
    File[] variantFragFiles = variantsDir.listFiles(variantFragFilter);
    Arrays.sort(variantFragFiles, (f1, f2) ->
        new AlphanumComparator().compare(f1.getName(), f2.getName()));

    boolean showWorkerNames = workers.length > 1;

    // First row: variant names
    if (showWorkerNames) {
      htmlAppendLn("<th class='center aligned'>Worker</th>");
    }
    htmlAppendLn("<th class='center aligned'>",
        "<a href='/webui/shader/",
        WebUiConstants.SHADERSET_DIR,
        "/",
        shaderFamily,
        "/reference.frag",
        "'>",
        "reference",
        "</a></th>");
    for (File f: variantFragFiles) {
      htmlAppendLn("<th class='selectable center aligned'>",
          "<a href='/webui/shader/", f.getPath(), "'>",
          f.getName().replace(".frag", ""), "</a></th>");
    }
    htmlAppendLn("</tr></thead>\n",
        "<tbody>");
    // Subsequent rows: results
    for (String worker: workers) {

      htmlAppendLn("<tr>");
      if (showWorkerNames) {
        htmlAppendLn("<td>", worker, "</td>");
      }

      // Reference result is separate as it doesn't contain a "identical" field, etc
      // FIXME: make sure reference result has same format as variants to be able to refactor
      String refHref = WebUiConstants.WORKER_DIR + "/" + worker + "/"
          + shaderFamily + "/reference";
      File refInfoFile = new File(refHref + ".info.json");
      String refPngPath = refHref + ".png";

      htmlAppendLn("<td ");
      if (refInfoFile.exists()) {
        JsonObject refInfo = accessFileInfo.getResultInfo(refInfoFile);
        String refStatus = refInfo.get("status").getAsString();
        if (refStatus.contentEquals("SUCCESS")) {
          htmlAppendLn("class='selectable center aligned'><a href='/webui/result/",
              refHref,
              "'>",
              "<img class='ui centered tiny image' src='/webui/file/", refPngPath, "'></a>");
        } else {
          htmlAppendLn("<td class='gfz-error bound-cell-width selectable center aligned'>",
              "<a href='/webui/result/",
              refHref,
              "'>", refStatus.replace("_", " "), " ",
              refInfo.get("stage").getAsString().replace("_", " "),
              "</a>\n");
        }
      } else {
        htmlAppendLn("class='bound-cell-width force-cell-height center aligned'>No result yet");
      }
      htmlAppendLn("</td>");

      for (File f : variantFragFiles) {
        File infoFile = new File(WebUiConstants.WORKER_DIR + "/" + worker
            + "/" + shaderFamily + "/" + f.getName().replace(".frag", ".info.json"));

        if (infoFile.isFile()) {
          ReductionStatus reductionStatus = getReductionStatus(worker, shaderFamily,
              infoFile.getName().replace(".info.json", ""));

          htmlVariantResultTableCell(infoFile, refPngPath, reductionStatus);
        } else {
          htmlAppendLn("<td class='bound-cell-width center aligned'>No result yet</td>");
        }
      }
      htmlAppendLn("</tr>");
    }
    htmlAppendLn("</tbody>\n</table>");
  }

}
