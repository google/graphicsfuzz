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

// See: https://wiki.apache.org/thrift/Tutorial/

namespace java com.graphicsfuzz.server.thrift
namespace js graphicsfuzzserver
namespace py graphicsfuzzserver
namespace cocoa graphicsfuzzserver
namespace cpp graphicsfuzzserver
//namespace swift graphicsfuzzserver
//namespace php graphicsfuzzserver
//namespace perl graphicsfuzzserver
//namespace d graphicsfuzzserver
//namespace dart graphicsfuzzserver
//namespace haxe graphicsfuzzserver

const string IDENTIFIER_DESKTOP = "desktop";
const string IDENTIFIER_ANDROID = "android";
const string IDENTIFIER_IOS = "ios";

const string UPLOAD_FIELD_NAME_FILE = "file";
const string UPLOAD_FIELD_NAME_TOKEN = "token";
const string UPLOAD_FIELD_NAME_ID = "id"; // the shader set id

const string DOWNLOAD_FIELD_NAME_TOKEN = UPLOAD_FIELD_NAME_TOKEN;

// get_image exit codes.
const i32 COMPILE_ERROR_EXIT_CODE = 101;
const i32 LINK_ERROR_EXIT_CODE = 102;
const i32 RENDER_ERROR_EXIT_CODE = 103;

enum ResultConstant {
    ERROR,
    COMPILE_ERROR,
    LINK_ERROR,
    NONDET,
    TIMEOUT,
    UNEXPECTED_ERROR,
    SKIPPED,
}

enum ReductionKind {
    IDENTICAL,
    NOT_IDENTICAL,
    ABOVE_THRESHOLD,
    BELOW_THRESHOLD,
    ERROR, // given a ResultConstant or some other regex
    VALIDATOR_ERROR
}

enum ImageComparisonMetric {
    HISTOGRAM_CHISQR,
    PSNR
}

enum TokenError {
  SERVER_ERROR = 0,
  INVALID_PLATFORM_INFO,
  INVALID_PROVIDED_TOKEN,
  PLATFORM_INFO_CHANGED,
}

struct CommandInfo {
  1 : optional string name,
  2 : optional list<string> command,
  3 : optional string logFile,
}

struct WorkerInfo {
  1 : optional string token,
  2 : optional list<CommandInfo> commandQueue,
  3 : optional list<string> jobQueue,
  4 : optional bool live,
}

struct ServerInfo {
  1 : optional list<CommandInfo> reductionQueue,
  2 : optional list<WorkerInfo> workers
}

struct GetTokenResult {
  1 : optional string token,
  2 : optional TokenError error,
}

enum JobStatus {
  UNKNOWN = 0 // default value: should not be seen

  // Usually means "image was rendered", but could also mean
  // "compiled and linked shader successfully" if skipRender was true.
  SUCCESS = 10

  // The rendering process crashed.
  // `stage` will indicate when the crash occurred.
  CRASH = 20

  COMPILE_ERROR = 30
  LINK_ERROR = 40
  SANITY_ERROR = 45

  NONDET = 50 // image changed when rendering a few times

  // Timeout occurred.
  // `stage` will indicate when the timeout occurred.
  // `timeoutInfo` will give more details of each stage.
  TIMEOUT = 60

  // Something weird.
  // Often means reference shader failed to render
  // or vertex shader failed to compile.
  UNEXPECTED_ERROR = 70

  // Set by the server if the client fetched the job too many times
  // without giving a result.
  // I.e. the client keeps crashing and retrying this job.
  SKIPPED = 80

  SAME_AS_REFERENCE = 90
}

// Before starting a stage,
// a worker/client should record the stage that it is about to attempt.
enum JobStage {
  NOT_STARTED = 0
  GET_JOB = 10
  START_JOB = 20
  IMAGE_PREPARE = 50
  IMAGE_VALIDATE_PROGRAM = 60
  IMAGE_RENDER = 70
  IMAGE_REPLY_JOB = 80
  COMPUTE_PREPARE = 90
  COMPUTE_VALIDATE_PROGRAM = 100
  COMPUTE_EXECUTE = 110
  COMPUTE_REPLY_JOB = 120
}

typedef i32 TimeInterval

struct TimingInfo {
  // In microseconds.
  1 : optional TimeInterval compilationTime
  2 : optional TimeInterval linkingTime
  3 : optional TimeInterval firstRenderTime
  4 : optional TimeInterval otherRendersTime
  5 : optional TimeInterval captureTime
}

struct ImageJobResult {
    1 : optional JobStatus status
    2 : optional string log
    // Last stage that was attempted (but not completed)
    3 : optional JobStage stage = JobStage.NOT_STARTED
    4 : optional TimingInfo timingInfo
    5 : optional bool passSanityCheck
    6 : optional binary PNG
    // Provide a second image, e.g. in case of NONDET
    7 : optional binary PNG2
}

struct ImageJob {
    1 : optional string name; // e.g. the variant name. Useful to identify a job in logs, etc.
    2 : optional string fragmentSource;
    3 : optional string vertexSource;
    4 : optional string uniformsInfo;         // contents of the uniform JSON file
    5 : optional list<double> points;         // unused for now
    6 : optional list<double> texturePoints;  // unused for now
    7 : optional binary textureBinary;        // unused for now
    8 : optional bool skipRender = false;
    9 : optional ImageJobResult result;
}

struct ComputeJobResult {
    10 : optional JobStatus status
    20 : optional string log
    // Last stage that was attempted (but not completed)
    30 : optional JobStage stage = JobStage.NOT_STARTED
    40 : optional bool passSanityCheck
    50 : optional string outputs // JSON representing outputs
    // Provide a second result, e.g. in case of NONDET
    60 : optional string outputs2
}

struct ComputeJob {
    10 : optional string name;
    20 : optional string computeSource;
    30 : optional string environment;          // JSON containing everything the compute shader needs in order to run
    40 : optional ComputeJobResult result;     // we should get rid of this when we get rid of ImageJobResult, but copying for uniformity for now
}

// The server does not expect a reply for a NoJob
struct NoJob {
}

// The server does expect a reply for a SkipJob
struct SkipJob {
}

struct Job {
    1 : required i64 jobId,
    2 : optional NoJob noJob,
    3 : optional ImageJob imageJob,
    4 : optional ComputeJob computeJob,
    5 : optional SkipJob skipJob,
}

struct CommandResult {
  1 : optional string output,
  2 : optional string error,
  3 : optional i32 exitCode,
}

exception TokenNotFoundException {
  1 : optional string token
}


/**
* Our public FuzzerService interface.
**/
service FuzzerService {

  GetTokenResult getToken(1 : string platformInfo, 2 : string token),

  Job getJob(1 : string token) throws (1 : TokenNotFoundException ex),

  void jobDone(1 : string token, 2 : Job job) throws (1 : TokenNotFoundException ex),
}

/**
* Each worker (previously client) (e.g. greylaptop, androidnexus, etc.)
* has two queues.
*
* A job queue typically only contains one or two jobs
* of type ImageJob. An ImageJob contains a shader and is
* retrieved by workers/clients who then render the shader
* and return the resulting PNG image.
*
* A command queue (previously work queue, and it is still WorkQueue in the code)
* contains potentially many commands (previously work items).
* Each command is literally a command;
* the command is run with the arguments given.
* E.g.
*   - run_shader_family shaderfamilies/bbb --output processing/greylaptop/bbb_exp/
*
* The commands will typically queue jobs to a job queue for workers.
*
**/
service FuzzerServiceManager {

  /**
  * Submit a job (i.e. ImageJob) to a worker job queue.
  **/
  Job submitJob(1 : Job job, 2 : string forClient, 3 : i32 retryLimit) throws (1 : TokenNotFoundException ex),

  /**
  * Clears a worker job queue.
  **/
  void clearClientJobQueue(1 : string forClient),

  /**
  * Queues a command to a worker's command queue.
  **/
  void queueCommand(
    // E.g. "Run shader family bbb"
    1 : string name,
    // E.g. run_shader_family shaderfamilies/bbb --output processing/greylaptop/bbb_exp/
    2 : list<string> command,
    // This should generally be set to the token.
    3 : string queueName,
    // Optional: path to log file. E.g. processing/aaa/bbb_ccc_inv/command.log
    4 : string logFile),

  /**
  * Execute a command on the server immediately and return the result (including stdout).
  * No queue is used.
  **/
  CommandResult executeCommand(
    // E.g. "Querying results"
    1 : string name,
    //
    2 : list<string> command),

   ServerInfo getServerState(),

}
