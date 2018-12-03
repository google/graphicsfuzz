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

import ar.com.hjg.pngj.ImageInfo;
import ar.com.hjg.pngj.ImageLineByte;
import ar.com.hjg.pngj.PngWriter;
import com.badlogic.gdx.Application.ApplicationType;
import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.ApplicationLogger;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input.TextInputListener;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.GL30;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import com.graphicsfuzz.repackaged.com.google.gson.JsonObject;
import com.graphicsfuzz.repackaged.org.apache.commons.io.output.ByteArrayOutputStream;
import com.graphicsfuzz.repackaged.org.apache.thrift.TException;
import com.graphicsfuzz.server.thrift.ImageJob;
import com.graphicsfuzz.server.thrift.ImageJobResult;
import com.graphicsfuzz.server.thrift.Job;
import com.graphicsfuzz.server.thrift.JobStage;
import com.graphicsfuzz.server.thrift.JobStatus;
import com.graphicsfuzz.server.thrift.ResultConstant;
import com.graphicsfuzz.server.thrift.TimingInfo;

@SuppressWarnings({"FieldCanBeLocal", "WeakerAccess"})
public class Main extends ApplicationAdapter {

  private final long RENDER_LOOP_WATCHDOG_TIME_SECONDS = 30;
  private final long COMPILE_SHADER_WATCHDOG_TIME_SECONDS = 30;
  private final long SEND_PNG_WATCHDOG_TIME_SECONDS = 30;

  private final long FRAME_WATCHDOG_TIME_SECONDS = 35;

  private final Object watchdogMutex = new Object();

  private final int WIDTH = 256;
  private final int HEIGHT = 256;

  private ByteBuffer pixelBuffers[] = new ByteBuffer[2];
  private int currentPixelBufferIndex;
  private static final int CHANNELS = 4;

  private com.graphicsfuzz.glesworker.JobGetter jobGetter;

  private MyShaderProgram program;
  private JsonObject uniformDict;


  private static final String defaultVertexShader300es = ""
      + "#version 300 es\n"
      + "in vec3 " + ShaderProgram.POSITION_ATTRIBUTE + ";\n"
      + "\n"
      + "void main(void) {\n"
      + "    gl_Position = vec4(" + ShaderProgram.POSITION_ATTRIBUTE + ", 1.0);\n"
      + "}";

  private static final String defaultVertexShader100 = ""
      + "#version 100\n"
      + "attribute vec3 " + ShaderProgram.POSITION_ATTRIBUTE + ";\n"
      + "\n"
      + "void main(void) {\n"
      + "    gl_Position = vec4(" + ShaderProgram.POSITION_ATTRIBUTE + ", 1.0);\n"
      + "}";

  private Job job;
  private TimingInfo timingInfo = new TimingInfo();

  // persistentData must be set by launcher
  public PersistentData persistentData = null;

  private byte[] otherPNG;

  private Mesh standardMesh;

  private int backoffWait = 1;
  private int backoffLimit = 6;

  // Initially wait so that window finishes animating.
  // Hugues think this counter is used for two different things:
  // 1. Time the delay before retrying to obtain a job from the server
  // 2. Store how much frames must be rendered before capturing a PNG
  private int waitCounter = 4;

  private final String defaultServerURL = "localhost:8080";
  private String url = null;

  private boolean showingTextInput = false;

  private JsonObject platformInfoJson = new JsonObject();

  public GL30 gl30;

  public ImageJob standaloneRenderJob;
  public String standaloneOutputFilename;

  public final int waitFramesDuringRender = 5;

  public IWatchdog watchdog = new NoOpWatchdog();

  public IWatchdog perFrameWatchdog = new NoOpWatchdog();

  public IProgramBinaryGetter programBinaryGetter = null;

  public static final int INITIAL_BYTES_FOR_PROGRAM_BINARY = 20 << 10 << 10; // 20MB

  private ByteBuffer programBinaryBytes = null;
  private boolean programBinaryWritten = false;

  public void setBackoffLimit(int backoffLimit) {
    this.backoffLimit = backoffLimit;
  }

  private WorkerState state = WorkerState.NO_CONNECTION;

  // Sanity check

  private static final String sanityFragmentSource100 = ""
          + "#version 100\n"
          + "#ifdef GL_ES\n"
          + "#ifdef GL_FRAGMENT_PRECISION_HIGH\n"
          + "precision highp float;\n"
          + "precision highp int;\n"
          + "#else\n"
          + "precision mediump float;\n"
          + "precision mediump int;\n"
          + "#endif\n"
          + "#endif\n"
          + "uniform vec2 resolution;\n"
          + "void main()\n"
          + "{\n"
          + "  gl_FragColor = vec4(gl_FragCoord.x/resolution.x, gl_FragCoord.y/resolution.y, 1.0, 1.0);\n"
          + "}\n";

  //private String sanityFragmentSource = sanityFragmentSource100;

  private final String sanityUniformsInfo = ""
      + "{\n"
      + "  \"resolution\": { \"func\":  \"glUniform2f\", \"args\": [ 256.0, 256.0 ] }\n"
      + "}\n";

  private final int numSanityRender = 3;
  private int sanityCounter = 0;
  private ByteBuffer sanityReferenceImage;
  private ByteBuffer sanityCheckImage;
  private ByteBuffer sanityCheckImageTmp;
  private boolean passSanityCheck;

  // For writing text
  private Stage GdxStage;
  private Label DisplayLabel;
  private final String DisplayAnimTxt = "GRAPHICSFUZZ";
  private int DisplayAnimCount = 0;
  private long timestamp = 0;
  private final int DISPLAY_TXT_MARGIN = 10; // safety margin to make sure we do not write on drawing
  private final DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
  private StringBuilder DisplayContent;
  private boolean OnlyRenderDisplayText = true;


  // For compute shaders
  public ComputeJobManager computeJobManager = null;

  public void setPlatformInfoJson(JsonObject platformInfoJson) {
    this.platformInfoJson = platformInfoJson;
  }

  public void setUrl(String url) {
    //Sanitise server URL - remove trailing forwardslash (as URL may be appended to later)
    if (url.endsWith("/")) {
      url = url.substring(0, url.length() - 1);
    }
    this.url = url;
  }

  private ApplicationLogger overrideLogger = null;

  public void setOverrideLogger(ApplicationLogger logger){
    this.overrideLogger = logger;
  }

  @Override
  public void create() {

    if(this.overrideLogger != null){
      Gdx.app.setApplicationLogger(overrideLogger);
    }

    // Only DesktopLauncher sets this.
    if(gl30 == null) {
      // Gdx.gl30 could also be null though.
      gl30 = Gdx.gl30;
    }

    Gdx.graphics.getGL20().glEnable(GL20.GL_TEXTURE_2D);
    Gdx.graphics.getGL20().glEnable(GL20.GL_DEPTH_TEST);
    Gdx.graphics.getGL20().glDepthFunc(GL20.GL_LESS);

    standardMesh = buildFullScreenQuadMesh();

    if(standaloneRenderJob != null) {
      if (standaloneRenderJob.isSetComputeSource()) {
        updateState(WorkerState.COMPUTE_STANDALONE_PREPARE);
      } else {
        updateState(WorkerState.IMAGE_STANDALONE_PREPARE);
      }
      return;
    }

    PlatformInfoUtil.getPlatformDetails(platformInfoJson);
    PlatformInfoUtil.getGlVersionInfo(platformInfoJson, gl30);

    sanityReferenceImage = createPixelBuffer();
    sanityCheckImage = createPixelBuffer();
    sanityCheckImageTmp = createPixelBuffer();

    GdxStage = new Stage(new ScreenViewport());
    Label.LabelStyle label1Style = new Label.LabelStyle();
    label1Style.font = new BitmapFont();
    label1Style.fontColor = Color.WHITE;

    DisplayContent = new StringBuilder();
    DisplayLabel = new Label(DisplayContent.toString(), label1Style);
    DisplayLabel.setPosition(WIDTH + DISPLAY_TXT_MARGIN, 0);
    DisplayLabel
        .setSize(Gdx.graphics.getWidth() - WIDTH - DISPLAY_TXT_MARGIN - 10, Gdx.graphics.getHeight() - 10);
    DisplayLabel.setAlignment(Align.topLeft);
    DisplayContent = new StringBuilder();
    GdxStage.addActor(DisplayLabel);
  }

  private boolean initClient() throws TException {

    // Recover server
    FileHandle serverFile = Gdx.files.local("server.txt");
    if (url == null) {
      if (serverFile.exists()) {
        url = serverFile.readString().trim();
      } else {
        if (Gdx.app.getType() == ApplicationType.Desktop) {
          Gdx.app.log("Main", "Please set a server URL by creating server.txt");
          setBackoffWaitTicks();
        } else if (!showingTextInput) {
          showingTextInput = true;
          Gdx.input.getTextInput(new TextInputListener() {
            @Override
            public void input(String text) {
              url = "http://" + text;
              showingTextInput = false;
            }

            @Override
            public void canceled() {
              showingTextInput = false;
            }
          }, "Absent or invalid server, provide new server URL (without leading 'http://'):",
              defaultServerURL, "");
        }
        return false;
      }
    }

    if (jobGetter == null) {
      try {
        jobGetter = new JobGetter(url);
        // Sets all fields to 0
        timingInfo.clear();
        resetBackoff();

      } catch (TException e) {
        waitCounter = backoffLimit;
        Gdx.app.log("Main", "Failed to connect. Waiting " + waitCounter + " ticks.");
        return false;
      }
    }

    // Here we've established connection with the server, so its URL is good, let's save it
    serverFile.writeString(url, false);

    // Recover worker name
    FileHandle workerNameFile = Gdx.files.local("worker-name.txt");

    if (jobGetter.worker == null) {
      if (workerNameFile.exists()) {
        jobGetter.worker = workerNameFile.readString();
      } else {
        if (Gdx.app.getType() == ApplicationType.Desktop) {
          Gdx.app.log("Main", "Please set a worker name by creating worker-name.txt with one line of text.");
          setBackoffWaitTicks();
        } else if (!showingTextInput) {
          String defaultWorker = "";
          if (platformInfoJson.has("clientplatform") &&
              platformInfoJson.get("clientplatform").getAsString().equalsIgnoreCase("android") &&
              platformInfoJson.has("model")) {
            defaultWorker = platformInfoJson.get("model").getAsString().trim().replace(" ", "-");
          }
          showingTextInput = true;
          Gdx.input.getTextInput(new TextInputListener() {
            @Override
            public void input(String text) {
              jobGetter.worker = text;
              showingTextInput = false;
            }

            @Override
            public void canceled() {
              showingTextInput = false;
            }
          }, "Absent worker name (or could not validate it with the server). " +
                  "Please provide new name or fix server connection issue.", defaultWorker, "");
        }

        // Return: the text listener will be invoked on the next frame.
        return false;
      }
    }

    String platformInfo = platformInfoJson.toString();

    boolean workerNameOK = false;
    try {
      workerNameOK = jobGetter.setWorkerName(jobGetter.worker, platformInfo);
    } catch (TException e) {
      Gdx.app.log("Main", "Exception when trying to get worker name: " + e.getMessage());
      workerNameOK = false;
    }

    if (workerNameOK) {
      Gdx.app.log("Main", "Set worker name: " + jobGetter.worker);
      workerNameFile.writeString(jobGetter.worker, false);
      return true;
    } else {
      Gdx.app.log("Main", "Worker name was refused, or server is unreachable.");
      // Reset worker name
      jobGetter.worker = null;
      return false;
    }
  }

  private int getWidth() {
    return WIDTH;
    // Use Gdx.graphics.getBackBufferWidth() to fit to screen
  }

  private int getHeight() {
    return HEIGHT;
    // Use Gdx.graphics.getBackBufferHeight() to fit to screen
  }

  private void setBackoffWaitTicks() {

    int ticks = backoffWait/10 + 1;
    if(ticks > backoffLimit) {
      ticks = backoffLimit;
    } else {
      backoffWait *= 2;
    }
    Gdx.app.log("Main", "Waiting " + ticks + " ticks.");
    waitCounter = ticks;
  }

  private void resetBackoff() {
    backoffWait = 1;
  }

  private boolean waitIfNeeded(boolean sleep) {
    if (waitCounter > 0) {
      --waitCounter;
      if(sleep) {
        try {
          Thread.sleep(300);
        } catch (InterruptedException e) {
          // nothing
        }
      }
      return true;
    }
    return false;
  }

  private void doGetJob() throws TException {

    JobStage stage = persistentData.getStage();
    boolean acceptableStage =
        (stage == null)                       // no persitent data at all
        || (stage == JobStage.GET_JOB);  // default state when not processing a job
    if (!acceptableStage) {
      // An error may occur when we try to reply, and this leads to infinite looping on
      // trying to reply. To avoid this, reset the stage once it has been detected as
      // non acceptable.
      persistentData.setStage(JobStage.GET_JOB);
      ImageJobResult res = new ImageJobResult();
      res.setStage(stage);
      if (persistentData.getBool(Constants.PERSISTENT_KEY_TIMEOUT, false)) {
        res.setStatus(JobStatus.TIMEOUT);
      } else if (persistentData.getBool(Constants.PERSISTENT_KEY_SANITY, true) == false) {
        res.setStatus(JobStatus.SANITY_ERROR);
      } else {
        res.setStatus(JobStatus.CRASH);
      }
      StringBuilder errMsg = new StringBuilder();
      errMsg.append("POST_MORTEM: " + res.getStatus().toString() + " " + stage.toString() + "\n");
      errMsg.append("### RECORDED DATA START ###\n");
      errMsg.append(persistentData.getString(Constants.PERSISTENT_KEY_ERROR_MSG));
      errMsg.append("\n### RECORDED DATA END ###\n");

      long jobId = persistentData.getJobId();
      if (jobId == -1L) {
        Gdx.app.log("Main", "doGetJob(): error: bad stage but null jobId?"
            + "A thrift error should follow.");
      }
      res.setTimingInfo(new TimingInfo()
          .setCompilationTime(persistentData.getInt(Constants.PERSISTENT_KEY_TIME_COMPILE, -1))
          .setFirstRenderTime(persistentData.getInt(Constants.PERSISTENT_KEY_TIME_FIRST_RENDER, -1))
          .setOtherRendersTime(persistentData.getInt(Constants.PERSISTENT_KEY_TIME_OTHER_RENDER, -1))
          .setCaptureTime(persistentData.getInt(Constants.PERSISTENT_KEY_TIME_CAPTURE, -1)));
      res.setLog(errMsg.toString())
          .setPassSanityCheck(persistentData.getBool(Constants.PERSISTENT_KEY_SANITY, true));

      byte[] imgData1 = persistentData.getBinaryBlob(Constants.PERSISTENT_KEY_IMG1);
      byte[] imgData2 = persistentData.getBinaryBlob(Constants.PERSISTENT_KEY_IMG2);

      if (imgData1 != null) {
        res.setPNG(imgData1);
      }
      if (imgData2 != null) {
        res.setPNG2(imgData2);
      }

      Job toreply = new Job()
          .setJobId(jobId)
          .setImageJob(new ImageJob().setResult(res));
      Gdx.app.log("Main", "Bad stage detected, will reply: " + toreply.toString());
      // we do not use jobgetter.replyJob() as if we are here due to a crash, there is no previous
      // job stored in latestJob, so replyJob() will think the answer has already been sent.
      // FIXME: we should simplify all this.
      jobGetter.fuzzerServiceProxy.jobDone(jobGetter.worker, toreply);

      persistentData.reset();
      return;
    }

    // WARNING: this "RESET_LAUNCHER_LOG" log is expected by the launcher to clear
    // crash log before starting a new job. Keep it!
    Gdx.app.log("Main", "RESET_LAUNCHER_LOG");

    timingInfo.clear();
    passSanityCheck = true;
    persistentData.reset();

    job = jobGetter.getJob();
    persistentData.setStage(JobStage.START_JOB);
    persistentData.setJobId(job.getJobId());
    persistentData.appendErrMsg(System.nanoTime() + " START JOB at " + dateFormat.format(new Date()));

    if (job.isSetNoJob()) {
      Gdx.app.log("Main","No job for me.");
      setBackoffWaitTicks();
      jobGetter.clearJob();
      persistentData.reset();
      return;
    }
    resetBackoff();

    if (job.isSetSkipJob()) {
      Gdx.app.log("Main", "Skip job.");
      persistentData.reset();
      jobGetter.replyJob(job);
      return;
    }

    if (job.isSetImageJob()) {
      otherPNG = null;
      if (job.getImageJob().isSetComputeSource()) {
        Gdx.app.log("Main", "Compute job.");
        updateState(WorkerState.COMPUTE_PREPARE);
      } else {
        updateState(WorkerState.IMAGE_PREPARE);
      }
      return;
    }


    throw new RuntimeException("Unknown job type");

  }

  public static void checkForGlError() throws com.graphicsfuzz.glesworker.GlErrorException {
    int error = Gdx.gl.glGetError();
    if(error != GL20.GL_NO_ERROR) {
      throw new com.graphicsfuzz.glesworker.GlErrorException(error);
    }
  }

  private String glErrorToString(int error) {
    switch (error) {
      case GL30.GL_INVALID_ENUM: return "GL_INVALID_ENUM";
      case GL30.GL_INVALID_VALUE: return "GL_INVALID_VALUE";
      case GL30.GL_INVALID_OPERATION: return "GL_INVALID_OPERATION";
      case GL30.GL_INVALID_FRAMEBUFFER_OPERATION: return "GL_INVALID_FRAMEBUFFER_OPERATION";
      case GL30.GL_OUT_OF_MEMORY: return "GL_OUT_OF_MEMORY";
      default: return "GL_UNKNOWN_ERROR";
    }
  }

  private void renderCurrentShader(String name, Mesh mesh) throws com.graphicsfuzz.glesworker.GlErrorException {
    Gdx.app.log("Main", "renderCurrentShader " + name);

    program.begin();
    checkForGlError();

    UniformSetter.setUniforms(program, uniformDict, gl30);

    long start = System.nanoTime();
    mesh.render(program, GL20.GL_TRIANGLES);
    long end = System.nanoTime();
    Gdx.app.log("Main", "mesh.render: " + (end - start) + " ns");
    checkForGlError();

    program.end();
    checkForGlError();

    Gdx.gl.glFlush();
    checkForGlError();

    Gdx.gl.glFinish();
    checkForGlError();
  }

  private void prepareProgram(String vertexSource, String fragmentSource, String uniformsInfo,
      boolean getProgramBinary) throws PrepareShaderException {

    try {
      watchdog.start(watchdogMutex,"CompileWatchdog", COMPILE_SHADER_WATCHDOG_TIME_SECONDS, persistentData);

      clearProgram();

      Gdx.app.log("Main","Compiling shader.");

      long compilation_start = System.nanoTime();

      program = new MyShaderProgram(vertexSource, fragmentSource);

      long compilation_time_microsec = (System.nanoTime() - compilation_start) / 1000;
      timingInfo.setCompilationTime(new Long(compilation_time_microsec).intValue());
      persistentData.setInt(Constants.PERSISTENT_KEY_TIME_COMPILE, timingInfo.getCompilationTime());
      Gdx.app.log("Main","Compilation Time, in microsec: "  + timingInfo.getCompilationTime());

      if(program.fragmentOrVertexFailedToCompile()) {
        throw new PrepareShaderException(
            ResultConstant.COMPILE_ERROR,
            program.getLog());
      }

      if(program.failedToLink()) {
        throw new PrepareShaderException(
            ResultConstant.LINK_ERROR,
            program.getLog());
      }

      if(!program.isCompiled()) {
        throw new PrepareShaderException(
            ResultConstant.UNEXPECTED_ERROR,
            "Did not expect to get here! Shader failed to compile?");
      }

      uniformDict = UniformSetter.getUniformDict(uniformsInfo);

      programBinaryWritten = false;

      if (programBinaryGetter != null) {
        if (getProgramBinary) {
          int[] bytesWritten = new int[1];
          int[] binaryFormatWritten = new int[1];

          if (programBinaryBytes == null) {
            programBinaryBytes = ByteBuffer.allocateDirect(INITIAL_BYTES_FOR_PROGRAM_BINARY);
          }

          programBinaryBytes.position(0);
          programBinaryBytes.limit(programBinaryBytes.capacity());

          try {
            programBinaryGetter.glGetProgramBinary(
                program.getProgramHandle(),
                bytesWritten,
                binaryFormatWritten,
                programBinaryBytes);

            if (Gdx.gl.glGetError() == GL20.GL_NO_ERROR) {
              programBinaryBytes.position(0);
              // Set the limit (size) to the number of bytes written:
              programBinaryBytes.limit(bytesWritten[0]);
              programBinaryWritten = true;

              Gdx.app.log("Main", "Successfully read program binary.");

            } else {
              // There was an error getting the binary: ignore it.
              // TODO: The binary might be too big for our buffer; if so, retry.

              Gdx.app.log("Main", "GL error getting program binary.");
            }
          } catch (Exception ex) {
            Gdx.app.log("Main", "Exception getting program binary.", ex);
          }
        }
        else {
          Gdx.app.log("Main", "Skipped getting program binary.");
        }
      } else {
        Gdx.app.log("Main", "Getting program binary is not supported by app version.");
      }
    }
    finally {
      watchdog.stop();
    }
  }

  private void updateState(WorkerState newState) {
    state = newState;
    OnlyRenderDisplayText = true;
  }

  @Override
  public void render() {

    try {
      perFrameWatchdog.stop();

      // reset error
      Gdx.gl.glGetError();

      // clear screen
      Gdx.gl.glClearColor(0, 0, 0, 0);
      checkForGlError();

      Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
      checkForGlError();

      // set viewport to full screen
      Gdx.gl.glViewport(0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
      checkForGlError();

      if (!isStandalone(state)) {

        // Fill in display text
        DisplayContent.setLength(0);
        DisplayContent.append("\n");
        DisplayContent.append(DisplayAnimTxt.substring(0, DisplayAnimCount) + "\n");
        DisplayAnimCount++;
        if (DisplayAnimCount > DisplayAnimTxt.length()) {
          DisplayAnimCount = 0;
        }
        DisplayContent.append("datetime: "
              + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date())
              + "\n\n");
        DisplayContent.append("worker: " + (jobGetter != null ? jobGetter.worker : "") + "\n");

        DisplayContent.append("server: " + url + "\n");
        DisplayContent.append("state: " + state.toString() + "\n");

        long elapsed = (System.nanoTime() - timestamp) / 1000000;
        DisplayContent.append("time (ms): " + elapsed + "\n");
        DisplayLabel.setText(DisplayContent);
        timestamp = System.nanoTime();
        GdxStage.act();
        GdxStage.draw();
      }

      if (OnlyRenderDisplayText) {
        // Early exit to render display current stage before it is actually performed
        OnlyRenderDisplayText = false;
        return;
      }

      // restrict viewport to draw the shaders
      Gdx.gl.glViewport(0, 0, getWidth(), getHeight());
      checkForGlError();

      boolean skipRender =
          (job != null && job.getImageJob() != null && job.getImageJob().isSkipRender());

      switch (state) {
      case NO_CONNECTION:
        if (waitIfNeeded(true)) {
          break;
        }

        boolean initOK = initClient();
        if (initOK) {
          assert (jobGetter != null);
          Gdx.app.log("Main","Got connection");
          updateState(WorkerState.SANITY_INIT_PREPARE);
        }
        break;

      case SANITY_INIT_PREPARE:
        Gdx.app.log("Main", "SANITY_INIT_PREPARE");
        clearProgram();
        // For sanity checking, always use GLSL ES 100 shaders.  It really should not matter,
        // and we can rely on all platforms supporting these.
        prepareProgram(defaultVertexShader100,
                sanityFragmentSource100,
                sanityUniformsInfo,
                false);
        sanityCounter = 0;
        updateState(WorkerState.SANITY_INIT_RENDER);
        break;

      case SANITY_INIT_RENDER:
        Gdx.app.log("Main", "SANITY_INIT_RENDER");
        renderCurrentShader("sanity", standardMesh);
        sanityCounter++;
        if (sanityCounter >= numSanityRender) {
          readPixels(sanityReferenceImage);
          updateState(WorkerState.GET_JOB);
        }
        break;

      case GET_JOB:
        if (waitIfNeeded(true)) {
          break;
        }
        clearProgram();
        doGetJob(); // (updates state field)
        break;

      case IMAGE_PREPARE:
        persistentData.setStage(JobStage.IMAGE_PREPARE);
        appendTimedErrMsg(state.toString());
        Gdx.app.log("Main","IMAGE_PREPARE");
        try {
          final String vertexSource = job.imageJob.isSetVertexSource()
                  ? job.imageJob.vertexSource
                  : chooseDefaultVertexShader(job.imageJob.fragmentSource);
          prepareProgram(vertexSource, job.imageJob.fragmentSource,
              job.imageJob.uniformsInfo,false);
          updateState(WorkerState.IMAGE_VALIDATE_PROGRAM);
          waitCounter = waitFramesDuringRender;
        } catch (PrepareShaderException e) {
          JobStatus status = JobStatus.UNEXPECTED_ERROR;
          if (e.resultConstant == ResultConstant.COMPILE_ERROR) {
            status = JobStatus.COMPILE_ERROR;
          } else if (e.resultConstant == ResultConstant.LINK_ERROR) {
            status = JobStatus.LINK_ERROR;
          }
          persistentData.appendErrMsg(status.toString() + " " + state.toString() + "\n"
              + "### START OF ERROR MESSAGE ###\n"
              + e.getMessage() + "\n"
              + "### END OF ERROR MESSAGE ###");
          job.getImageJob().setResult(
              new ImageJobResult()
                  .setStatus(status)
                  .setTimingInfo(timingInfo)
                  .setStage(JobStage.IMAGE_PREPARE));
          clearProgram();
          updateState(WorkerState.PREPARE_SANITY);
        }
        break;

      case IMAGE_VALIDATE_PROGRAM:
        Gdx.app.log("Main", "IMAGE_VALIDATE_PROGRAM");
        appendTimedErrMsg(state.toString());
        persistentData.setStage(JobStage.IMAGE_VALIDATE_PROGRAM);

        boolean valid = isProgramValid();
        if (!valid) {
          Gdx.app.log("Main", "Program is valid");
        } else {
          Gdx.app.log("Main", "Program is NOT valid");
          String progErr = Gdx.gl.glGetProgramInfoLog(program.getProgramHandle());
          Gdx.app.log("Main", "PROG ERR: " + progErr);
          persistentData.appendErrMsg("ERROR " + state.toString() + "\n"
              + "### START OF ERROR MESSAGE ###\n"
              + progErr + "\n"
              + "### END OF ERROR MESSAGE ###");
          job.getImageJob().setResult(
              new ImageJobResult()
                  .setStatus(JobStatus.UNEXPECTED_ERROR)
                  .setTimingInfo(timingInfo)
                  .setStage(JobStage.IMAGE_VALIDATE_PROGRAM));
          clearProgram();
          updateState(WorkerState.PREPARE_SANITY);
          break;
        }

        if (job.imageJob.skipRender) {
          synchronized (watchdogMutex) {
            appendTimedErrMsg("Skip Render");
          }
          Gdx.app.log("Main","Skip Render");
          updateState(WorkerState.PREPARE_SANITY);
        } else {
          watchdog.start(watchdogMutex, "RenderLoopWatchdog", RENDER_LOOP_WATCHDOG_TIME_SECONDS,
              persistentData);
          updateState(WorkerState.IMAGE_RENDER);
        }
        break;

      case IMAGE_RENDER:
        persistentData.setStage(JobStage.IMAGE_RENDER);
        synchronized (watchdogMutex) {
          appendTimedErrMsg(state.toString());
        }
        Gdx.app.log("Main","IMAGE_RENDER");

        Mesh mesh = getMeshFromJob();
        Texture texture = null;
        if (job.imageJob.isSetTexturePoints()) {
          if (!job.imageJob.isSetTextureBinary()) {
            throw new RuntimeException("Image job has texture points but no texture binary");
          }
          final FileHandle pngFile = Gdx.files.local("texture.png");
          pngFile.writeBytes(job.imageJob.getTextureBinary(), false);
          texture = new Texture(pngFile);
          pngFile.delete();
          texture.bind();
        }

        try { // Important to dispose "mesh", in the case of a custom mesh, in all cases.

          long render_time_start = System.nanoTime();
          renderCurrentShader(job.getImageJob().getName(), mesh);
          long render_time_end = System.nanoTime();

          if (waitCounter >= waitFramesDuringRender) {
            long first_render_time = (render_time_end - render_time_start) / 1000;
            timingInfo.setFirstRenderTime(new Long(first_render_time).intValue());
            persistentData.setInt(Constants.PERSISTENT_KEY_TIME_FIRST_RENDER, timingInfo.getFirstRenderTime());
            Gdx.app.log("Main", "First Render Time, in microsec: " + timingInfo.getFirstRenderTime());
          } else {
            long other_render_time = (render_time_end - render_time_start) / 1000;
            timingInfo.setOtherRendersTime(timingInfo.getOtherRendersTime() + new Long(other_render_time).intValue());
            if (waitCounter == 0) {
              Gdx.app.log("Main", "Others Render Time, in microsec: " + timingInfo.getOtherRendersTime());
              persistentData.setInt(Constants.PERSISTENT_KEY_TIME_OTHER_RENDER, timingInfo.getOtherRendersTime());
            }
          }

          getBackbuffer();
          if (previousBackbufferDiffers()) {
            watchdog.stop();
            // prepare images for report
            byte[] png1 = getPNG(getLatestPixelBuffer());
            byte[] png2 = getPNG(getSecondToLatestPixelBuffer());
            persistentData.appendErrMsg(JobStatus.NONDET + " " + state.toString());
            persistentData.setBinaryBlob(Constants.PERSISTENT_KEY_IMG1, png1);
            persistentData.setBinaryBlob(Constants.PERSISTENT_KEY_IMG2, png2);
            job.getImageJob().setResult(
                    new ImageJobResult()
                            .setTimingInfo(timingInfo)
                            .setStatus(JobStatus.NONDET)
                            .setStage(JobStage.IMAGE_RENDER)
                            .setPNG(png1)
                            .setPNG2(png2));
            updateState(WorkerState.PREPARE_SANITY);
            break;
          }
          if (!waitIfNeeded(false)) {

            watchdog.stop();

            long capture_time_start = System.nanoTime();

            otherPNG = getPNG(getBackbuffer());
            persistentData.setBinaryBlob(Constants.PERSISTENT_KEY_IMG1, otherPNG);
            updateState(WorkerState.PREPARE_SANITY);

            long capture_time_end = System.nanoTime();

            long capture_time = (capture_time_end - capture_time_start) / 1000;
            timingInfo.setCaptureTime(new Long(capture_time).intValue());
            persistentData.setInt(Constants.PERSISTENT_KEY_TIME_CAPTURE, new Long(capture_time).intValue());
          }
          break;
        } finally {
          if (mesh != standardMesh) {
            mesh.dispose();
          }
          if (texture != null) {
            texture.dispose();
          }
        }

      case PREPARE_SANITY:
        Gdx.app.log("Main", "PREPARE_SANITY");
        appendTimedErrMsg(state.toString());
        persistentData.setBool(Constants.PERSISTENT_KEY_SANITY, false);
        clearProgram();
        try {
          // For sanity checking, it should suffice to use shaders with version GLSL 100 ES.
          prepareProgram(defaultVertexShader100,
                  sanityFragmentSource100,
                  sanityUniformsInfo,
                  false);
        } catch (PrepareShaderException e) {
          Gdx.app.log("Main", "Sanity preparation failed");
          persistentData.appendErrMsg("ERROR PREPARE_SANITY");
          updateState(WorkerState.IMAGE_REPLY_JOB);
          break;
        }
        sanityCounter = 0;
        updateState(WorkerState.RENDER_SANITY);
        break;

      case RENDER_SANITY:
        Gdx.app.log("Main", "RENDER_SANITY");
        appendTimedErrMsg(state.toString());
        renderCurrentShader("sanity", standardMesh);
        sanityCheckImageTmp = createPixelBuffer();
        readPixels(sanityCheckImageTmp);
        if (sanityCounter > 0) {
          // check sanity determinism
          passSanityCheck = byteBuffersEqual(sanityCheckImageTmp, sanityCheckImage);
          if (!passSanityCheck) {
            Gdx.app.log("Main", "sanity check: NONDET");
            persistentData.appendErrMsg("NONDET RENDER_SANITY");
            persistentData.setBool(Constants.PERSISTENT_KEY_SANITY, false);
            updateState(WorkerState.IMAGE_REPLY_JOB);
            break;
          }
        }
        sanityCounter++;
        sanityCheckImage = sanityCheckImageTmp;
        passSanityCheck = byteBuffersEqual(sanityCheckImage, sanityReferenceImage);
        if (!passSanityCheck) {
          Gdx.app.log("Main", "sanity check: FAILED");
          persistentData.setBool(Constants.PERSISTENT_KEY_SANITY, false);
          updateState(WorkerState.IMAGE_REPLY_JOB);
          break;
        }
        if (sanityCounter > numSanityRender) {
          updateState(WorkerState.IMAGE_REPLY_JOB);
        }
        break;

      case IMAGE_REPLY_JOB:
        persistentData.setStage(JobStage.IMAGE_REPLY_JOB);
        appendTimedErrMsg(state.toString());
        Gdx.app.log("Main", "IMAGE_REPLY_JOB");
        // Safety programming: if there was any watchdog going on, stop it.
        watchdog.stop();

        persistentData.appendErrMsg("passSanityCheck: " + passSanityCheck);

        // If there was an issue earlier, then job already has a jobresult field.
        if (job.isSetImageJob() && job.getImageJob().isSetResult()) {
          job.getImageJob().getResult().setPassSanityCheck(passSanityCheck);
          job.getImageJob().getResult().setLog(persistentData.getString(Constants.PERSISTENT_KEY_ERROR_MSG));
          jobGetter.replyJob(job);
        } else {

          watchdog.start(watchdogMutex, "SendPNGWatchdog", SEND_PNG_WATCHDOG_TIME_SECONDS,
              persistentData);

          try {
            job.getImageJob().setResult(new ImageJobResult());

            // TODO: Check programBinaryWritten and set programBinary field.

           job.getImageJob().getResult().setStatus(JobStatus.SUCCESS);
            if (!skipRender) {
              job.getImageJob().getResult().setPNG(otherPNG);
            }

            job.getImageJob().getResult()
                .setTimingInfo(timingInfo)
                .setStage(JobStage.IMAGE_REPLY_JOB)
                .setPassSanityCheck(passSanityCheck);
            if (!passSanityCheck) {
              job.getImageJob().getResult().setStatus(JobStatus.SANITY_ERROR);
            }
            synchronized (watchdogMutex) {
                job.getImageJob().getResult()
                    .setLog(persistentData.getString(Constants.PERSISTENT_KEY_ERROR_MSG));
            }
            jobGetter.replyJob(job);
          } finally {
            watchdog.stop();
          }
        }

        if (passSanityCheck == false) {
          Gdx.app.log("Main", "Driver loosed sanity, force crash");
          // Job has already been replied, make sure to clear the persistent data
          persistentData.reset();
          // sleep a little to make sure changes are propagated before killing ourselves
          Thread.sleep(300);
          watchdog.killNow();
        }

        persistentData.reset();
        otherPNG = null;
        updateState(WorkerState.GET_JOB);
        break;

      case IMAGE_STANDALONE_PREPARE:
        Gdx.app.log("Main", "IMAGE_STANDALONE_PREPARE");
        appendTimedErrMsg(state.toString());
        try {
          prepareProgram(standaloneRenderJob.vertexSource,
              standaloneRenderJob.fragmentSource,
              standaloneRenderJob.uniformsInfo,
              false);
          updateState(WorkerState.IMAGE_STANDALONE_RENDER);
          waitCounter = waitFramesDuringRender;
        }
        catch (PrepareShaderException e) {
          clearProgram();
          throw new RuntimeException(e);
        }
        break;

      case IMAGE_STANDALONE_RENDER:
        Gdx.app.log("Main","IMAGE_STANDALONE_RENDER");
        appendTimedErrMsg(state.toString());
        renderCurrentShader("standalone", standardMesh);
        if(!waitIfNeeded(false)) {
          byte[] png = getPNG(getBackbuffer());
          FileHandle outputFileHandle = Gdx.files.absolute(standaloneOutputFilename);
          outputFileHandle.writeBytes(png, false);
          // TODO: Check programBinaryWritten and write to a file.
          updateState(WorkerState.IMAGE_STANDALONE_IDLE);
          Gdx.app.exit();
        }
        break;
      case IMAGE_STANDALONE_IDLE:
        Gdx.app.log("Main","IMAGE_STANDALONE_IDLE");
        appendTimedErrMsg(state.toString());
        break;

      case COMPUTE_PREPARE:
      case COMPUTE_EXECUTE:
      case COMPUTE_REPLY_JOB:
        updateState(computeJobManager.handle(state, persistentData, jobGetter, job));
        break;
      case COMPUTE_STANDALONE_PREPARE:
      case COMPUTE_STANDALONE_EXECUTE:
        updateState(
            computeJobManager.handle(
                state,
                persistentData,
                null,
                new Job().setImageJob(standaloneRenderJob)));
        break;
      case COMPUTE_STANDALONE_IDLE:
        updateState(WorkerState.COMPUTE_STANDALONE_IDLE);
        Gdx.app.exit();
      }

    } catch (com.graphicsfuzz.glesworker.GlErrorException err) {

      watchdog.stop();

      persistentData.appendErrMsg("GL_ERROR " + glErrorToString(err.glError));

      job.getImageJob().setResult(
          new ImageJobResult()
              .setTimingInfo(timingInfo)
              .setStatus(JobStatus.UNEXPECTED_ERROR)
              .setStage(persistentData.getStage())
              .setLog(persistentData.getString(Constants.PERSISTENT_KEY_ERROR_MSG)));
      try {
        jobGetter.replyJob(job);
      } catch (Exception e) {
        e.printStackTrace();
      }

      clearProgram();
      persistentData.reset();
      updateState(WorkerState.GET_JOB);

    } catch(final Exception e) {
      Gdx.app.error("Main", "Exception", e);
      watchdog.killNow();
      throw new RuntimeException(e);
    } finally {
      perFrameWatchdog.start(watchdogMutex, "FrameWatchdog", FRAME_WATCHDOG_TIME_SECONDS, persistentData);
    }
  }

  private boolean isStandalone(WorkerState state) {
    switch (state) {
      case IMAGE_STANDALONE_IDLE:
      case IMAGE_STANDALONE_PREPARE:
      case IMAGE_STANDALONE_RENDER:
      case COMPUTE_STANDALONE_IDLE:
      case COMPUTE_STANDALONE_PREPARE:
      case COMPUTE_STANDALONE_EXECUTE:
        return true;
      default:
        return false;
    }
  }

  private boolean isProgramValid() {
    Gdx.gl.glValidateProgram(program.getProgramHandle());
    int glErr = Gdx.gl.glGetError();
    if (glErr != GL20.GL_NO_ERROR) {
      Gdx.app.log("Main", "glValidateProgram RAISED ERROR");
    }
    ByteBuffer bb = ByteBuffer.allocateDirect(16);
    bb.order(ByteOrder.nativeOrder());
    IntBuffer res = bb.asIntBuffer();
    res.position(0);
    res.put(0);
    Gdx.gl.glGetProgramiv(program.getProgramHandle(), GL20.GL_VALIDATE_STATUS, res);
    glErr = Gdx.gl.glGetError();
    if (glErr != GL20.GL_NO_ERROR) {
      Gdx.app.log("Main", "glGetProgramiv RAISED ERROR");
    }
    res.position(0);
    int validInt = res.get();
    return (validInt == GL20.GL_TRUE);
  }

  private boolean previousBackbufferDiffers() {
    if (waitCounter >= waitFramesDuringRender) {
      Gdx.app.log("Main", "previousBackbufferDiffers: returning early.");
      return false;
    }
    int prevIndex = currentPixelBufferIndex - 1;
    if (prevIndex < 0) {
      prevIndex = pixelBuffers.length - 1;
    }

    Gdx.app.log("Main", "previousBackbufferDiffers: waitCounter="+waitCounter+" comparing index "+currentPixelBufferIndex+" with "+prevIndex+".");

    pixelBuffers[currentPixelBufferIndex].position(0);
    pixelBuffers[prevIndex].position(0);

    if (pixelBuffers[currentPixelBufferIndex].limit() != pixelBuffers[prevIndex].limit()) {
      // Assume something went wrong if the buffer sizes are not equal.
      // I think this might happen if the window is resizing/animating.
      return false;
    }

    int res = pixelBuffers[currentPixelBufferIndex].compareTo(pixelBuffers[prevIndex]);

    if (res != 0) {
      Gdx.app.log("Main", "previousBackbufferDiffers: DIFFERENT!");
    } else {
      Gdx.app.log("Main", "previousBackbufferDiffers: same");
    }

    return res != 0;
  }

  private ByteBuffer getLatestPixelBuffer() {
    ByteBuffer pixelBuffer = pixelBuffers[currentPixelBufferIndex];
    pixelBuffer.position(0);
    pixelBuffer.limit(pixelBuffer.capacity());
    return pixelBuffer;
  }

  private ByteBuffer getSecondToLatestPixelBuffer() {
    int prevIndex = currentPixelBufferIndex - 1;
    if (prevIndex < 0) {
      prevIndex = pixelBuffers.length - 1;
    }
    ByteBuffer pixelBuffer = pixelBuffers[prevIndex];
    pixelBuffer.position(0);
    pixelBuffer.limit(pixelBuffer.capacity());
    return pixelBuffer;
  }

  private ByteBuffer createPixelBuffer() {
    final int capacity = getWidth() * getHeight() * CHANNELS;
    ByteBuffer res = ByteBuffer.allocateDirect(capacity);
    res.limit(res.capacity());
    res.position(0);
    return res;
  }

  private void readPixels(ByteBuffer pixelBuffer) throws com.graphicsfuzz.glesworker.GlErrorException {
    pixelBuffer.limit(pixelBuffer.capacity());
    pixelBuffer.position(0);
    Gdx.gl.glReadPixels(0, 0, getWidth(), getHeight(), GL20.GL_RGBA, GL20.GL_UNSIGNED_BYTE, pixelBuffer);
    checkForGlError();
    pixelBuffer.limit(pixelBuffer.capacity());
    pixelBuffer.position(0);
  }

  private ByteBuffer getBackbuffer() throws GlErrorException {

    final int capacity = getWidth() * getHeight() * CHANNELS;
    currentPixelBufferIndex = (currentPixelBufferIndex + 1) % pixelBuffers.length;
    if (pixelBuffers[currentPixelBufferIndex] == null
        || pixelBuffers[currentPixelBufferIndex].capacity() != capacity) {
      Gdx.app.log("Main", "getBackbuffer: allocating buffer at index " + currentPixelBufferIndex);
      pixelBuffers[currentPixelBufferIndex] = createPixelBuffer();
    }

    ByteBuffer pixelBuffer = pixelBuffers[currentPixelBufferIndex];
    pixelBuffer.position(0);
    pixelBuffer.limit(pixelBuffer.capacity());
    Gdx.app.log("Main", "getBackbuffer: writing at index " + currentPixelBufferIndex);
    readPixels(pixelBuffer);
    return pixelBuffer;
  }

  private byte[] getPNG(ByteBuffer backBuffer) throws GetPNGException {
    Gdx.app.log("Main","getPNG enter");

    ImageInfo ii = new ImageInfo(getWidth(), getHeight(), 8, true);
    ByteArrayOutputStream stream = new ByteArrayOutputStream();
    PngWriter pngWriter = new PngWriter(stream, ii);
    pngWriter.setCompLevel(6);
    byte[] row = new byte[getWidth() * CHANNELS];
    for (int i = 0; i < getHeight(); ++i) {
      backBuffer.position((getHeight() - i - 1) * getWidth() * CHANNELS);
      backBuffer.get(row);
      pngWriter.writeRow(new ImageLineByte(ii, row));
    }
    pngWriter.end();

    return stream.toByteArray();
  }

  private void clearProgram() {
    if (program != null && program.isCompiled()) {
      Gdx.app.log("Main","Disposing program.");
      program.dispose();
      program = null;
      Gdx.app.log("Main","Disposed program.");
    }
    programBinaryWritten = false;
  }

  private boolean byteBuffersEqual(ByteBuffer bb1, ByteBuffer bb2) {
    bb1.position(0);
    bb2.position(0);
    boolean res = (bb1.compareTo(bb2) == 0);
    bb1.position(0);
    bb2.position(0);
    return res;
  }

  private void appendTimedErrMsg(String content) {
    persistentData.appendErrMsg( System.nanoTime() + " " + content);
  }

  @Override
  public void dispose() {
    watchdog.stop();
    persistentData.reset();
  }

  private Mesh getMeshFromJob() {
    Mesh mesh;
    if (job.imageJob.isSetPoints()) {
      if (job.imageJob.isSetTexturePoints()) {
        mesh = buildMeshFromVerticesAndTexCoords(job.imageJob.points, job.imageJob.texturePoints);
      } else {
        mesh = buildMeshFromVertices(job.imageJob.points);
      }
    } else {
      mesh = standardMesh;
    }
    return mesh;
  }

  private Mesh buildFullScreenQuadMesh() {
    List<Double> vertices = new ArrayList<Double>();

    vertices.add( -1.0); // x1
    vertices.add( -1.0); // y1
    vertices.add( 0.0);

    vertices.add( 1.0); // x2
    vertices.add( -1.0); // y2
    vertices.add( 0.0);

    vertices.add( 1.0); // x3
    vertices.add( 1.0); // y2
    vertices.add( 0.0);

    vertices.add( -1.0); // x1
    vertices.add( -1.0); // y1
    vertices.add( 0.0);

    vertices.add( -1.0); // x4
    vertices.add( 1.0); // y4
    vertices.add( 0.0);

    vertices.add( 1.0); // x3
    vertices.add( 1.0); // y2
    vertices.add( 0.0);

    return buildMeshFromVertices(vertices);
  }

  private Mesh buildMeshFromVertices(List<Double> vertexCoords) {
    if ((vertexCoords.size() % 3) != 0) {
      throw new RuntimeException("Vertex coordinates size is " + vertexCoords.size() + "; must be a multiple of 3");
    }
    float[] data = new float[vertexCoords.size()];
    for (int i = 0; i < vertexCoords.size(); i++) {
      data[i] = (float) vertexCoords.get(i).doubleValue();
    }
    Mesh mesh = new Mesh( true, vertexCoords.size() / 3, 0,
            new VertexAttribute(VertexAttributes.Usage.Position, 3, ShaderProgram.POSITION_ATTRIBUTE));
    mesh.setVertices(data);
    return mesh;
  }

  private Mesh buildMeshFromVerticesAndTexCoords(List<Double> vertexCoords, List<Double> texCoords) {
    if ((vertexCoords.size() % 3) != 0) {
      throw new RuntimeException("Vertex coordinates size is " + vertexCoords.size() + "; must be a multiple of 3");
    }
    if ((texCoords.size() % 2) != 0) {
      throw new RuntimeException("Texture coordinates size is " + texCoords.size() + "; must be a multiple of 2");
    }
    if (vertexCoords.size() / 3 != texCoords.size() / 2) {
      throw new RuntimeException("There is vertex data for " + vertexCoords.size() / 3 + " triangle(s), "
              + "and texture data for " + texCoords.size() / 2 + " triangle(s) -- these should match");
    }

    float[] data = new float[vertexCoords.size() + texCoords.size()];
    int vertexIndex = 0;
    int texIndex = 0;
    int dataIndex = 0;
    for (int i = 0; i < vertexCoords.size() / 3; i++) {
      data[dataIndex++] = (float) vertexCoords.get(vertexIndex++).doubleValue();
      data[dataIndex++] = (float) vertexCoords.get(vertexIndex++).doubleValue();
      data[dataIndex++] = (float) vertexCoords.get(vertexIndex++).doubleValue();
      data[dataIndex++] = (float) texCoords.get(texIndex++).doubleValue();
      data[dataIndex++] = (float) texCoords.get(texIndex++).doubleValue();
    }
    Mesh mesh = new Mesh( true, vertexCoords.size() / 3, 0,
            new VertexAttribute(VertexAttributes.Usage.Position, 3, ShaderProgram.POSITION_ATTRIBUTE),
            new VertexAttribute(VertexAttributes.Usage.TextureCoordinates, 2, ShaderProgram.TEXCOORD_ATTRIBUTE + "0"));
    mesh.setVertices(data);
    return mesh;
  }

  private static String chooseDefaultVertexShader(String fragmentSource) {
    if (fragmentSource.startsWith("#version 100")) {
      return defaultVertexShader100;
    }
    // TODO: This evidently won't be the right choice if the version string in the fragment shader is something
    // different from "#version 300 es".
    return defaultVertexShader300es;
  }

}
