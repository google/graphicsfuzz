// Copyright 2018 The GraphicsFuzz Project Authors
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

// Entry point on Android

#include <android_native_app_glue.h>
#include "vulkan_worker.h"
#include "platform.h"

#include <assert.h>
#include <string>
#include <sstream>
#include <iostream>

typedef struct AppData {
  VulkanWorker *vulkan_worker;
  PlatformData *platform_data;
  FILE *vertex_file;
  FILE *fragment_file;
  FILE *uniform_file;
} AppData;

void ProcessAppCmd (struct android_app *app, int32_t cmd) {
  AppData *app_data = (AppData *)app->userData;
  switch (cmd) {
    case APP_CMD_INIT_WINDOW:
      if (FLAGS_info) {
        log("DUMP INFO");
        VulkanWorker::DumpWorkerInfo("/sdcard/graphicsfuzz/worker_info.json");
        ANativeActivity_finish(app->activity);
        break;
      }

      if (app_data->vulkan_worker == NULL) {
        log("Create vulkan worker");
        assert(app->window != nullptr);
        app_data->platform_data->window = app->window;
        app_data->vulkan_worker = new VulkanWorker(app_data->platform_data);
        assert(app_data->vertex_file != nullptr);
        assert(app_data->fragment_file != nullptr);
        assert(app_data->uniform_file != nullptr);
        app_data->vulkan_worker->RunTest(app_data->vertex_file, app_data->fragment_file, app_data->uniform_file, FLAGS_skip_render);
        ANativeActivity_finish(app->activity);
      }
      break;

    case APP_CMD_PAUSE:
    case APP_CMD_STOP:
    case APP_CMD_DESTROY:
      ANativeActivity_finish(app->activity);
      break;
  }
  return;
}

int32_t ProcessInputEvent (struct android_app *app, AInputEvent *event) {
  return 0;
}

// Extract command line arguments from the extra of Android intent:
//   adb shell am start -n <...> -e gfz "'list of arguments to be extracted'"
// Note the quoting: from host terminal adb command, we need to double-escape
// the extra args string, as it is first quoted by host terminal emulator
// (e.g. bash), then it must be quoted for the on-device shell.
void GetGflagsArgs(struct android_app* state, int *argc, char ***argv) {
  assert(argc != nullptr);
  assert(argv != nullptr);
  assert(*argv == nullptr);

  // The extra flag to indicate GraphicsFuzz arguments
  const char *intent_flag = "gfz";

  JavaVM* java_vm = state->activity->vm;
  JNIEnv* jni_env;
  assert(java_vm->AttachCurrentThread(&jni_env, nullptr) == JNI_OK);
  jobject activity_clazz = state->activity->clazz;
  jmethodID get_intent_method_id = jni_env->GetMethodID(jni_env->GetObjectClass(activity_clazz), "getIntent", "()Landroid/content/Intent;");
  jobject intent = jni_env->CallObjectMethod(activity_clazz, get_intent_method_id);
  jmethodID get_string_extra_method_id = jni_env->GetMethodID(jni_env->GetObjectClass(intent), "getStringExtra", "(Ljava/lang/String;)Ljava/lang/String;");
  jvalue get_string_extra_args;
  get_string_extra_args.l = jni_env->NewStringUTF(intent_flag);
  jstring extra_jstring = static_cast<jstring>(jni_env->CallObjectMethodA(intent, get_string_extra_method_id, &get_string_extra_args));

  std::string extra_string;
  if (extra_jstring) {
    const char* extra_cstr = jni_env->GetStringUTFChars(extra_jstring, nullptr);
    log("EXTRA_CSTR: %s", extra_cstr);
    extra_string = extra_cstr;
    jni_env->ReleaseStringUTFChars(extra_jstring, extra_cstr);
    jni_env->DeleteLocalRef(extra_jstring);
  }

  jni_env->DeleteLocalRef(get_string_extra_args.l);
  jni_env->DeleteLocalRef(intent);
  java_vm->DetachCurrentThread();

  // Prepare arguments with a value for argv[0], as gflags expects
  std::vector<std::string> args;
  args.push_back("android_vkworker"); // required but ignored by gflags

  // Split extra_string
  std::stringstream ss(extra_string);
  std::string arg;
  while (std::getline(ss, arg, ' ')) {
    if (!arg.empty()) {
      args.push_back(arg);
    }
  }

  // Convert to argc/argv, as gflags expects
  *argc = (int)(args.size());
  *argv = (char **)malloc(*argc * sizeof(char *));
  assert(*argv != nullptr);
  for (int i = 0; i < *argc; i++) {
    (*argv)[i] = strdup(args[i].c_str());
  }
}

void FreeGflagsArgs(int argc, char **argv) {
  for (int i = 0; i < argc; i++) {
    free(argv[i]);
  }
  free(argv);
}

bool CanReadWriteExternalStorage() {
  static bool first_try = true;

  if (!first_try) {
    return true;
  } else {
    first_try = false;
  }

  const char* filename = "/sdcard/graphicsfuzz/test_permission";

  FILE *f = fopen(filename, "w");

  if (f == nullptr) {
    return false;
  } else {
    fclose(f);
    remove(filename);
    return true;
  }
}

void android_main(struct android_app* state) {

  if (!CanReadWriteExternalStorage()) {
    log("ERROR: cannot write in /sdcard/graphicsfuzz/, please double check App permission to access external storage");
    std::terminate();
  }

  // Reset all default values, as any change may survive the exiting of this
  // android_main() function and still be set when android_main() is called
  // again.
  FLAGS_coherence_before = "/sdcard/graphicsfuzz/coherence_before.png";
  FLAGS_coherence_after = "/sdcard/graphicsfuzz/coherence_after.png";
  FLAGS_png_template = "/sdcard/graphicsfuzz/image";
  FLAGS_info = false;
  FLAGS_skip_render = false;
  FLAGS_num_render = 3;

  int argc = 0;
  char **argv = nullptr;
  GetGflagsArgs(state, &argc, &argv);
  gflags::SetUsageMessage("GraphicsFuzz Vulkan worker http://github.com/google/graphicsfuzz");
  gflags::ParseCommandLineFlags(&argc, &argv, true);

  AppData *app_data = new AppData;
  app_data->vulkan_worker = nullptr;
  state->userData = (void *)app_data;

  PlatformData platform_data = {};
  // state->window will be ready later, when processing APP_CMD_INIT_WINDOW in processAppCmd()
  app_data->platform_data = &platform_data;

  // Android: setup main callbacks
  state->onAppCmd = ProcessAppCmd;
  state->onInputEvent = ProcessInputEvent;

  if (!FLAGS_info) {
    log("NOT DUMP INFO");
    app_data->vertex_file = fopen("/sdcard/graphicsfuzz/test.vert.spv", "r");
    assert(app_data->vertex_file != nullptr);

    app_data->fragment_file = fopen("/sdcard/graphicsfuzz/test.frag.spv", "r");
    assert(app_data->fragment_file != nullptr);

    app_data->uniform_file = fopen("/sdcard/graphicsfuzz/test.json", "r");
    assert(app_data->uniform_file != nullptr);
  }

  // Write STARTED file.
  {
    FILE *started = fopen("/sdcard/graphicsfuzz/STARTED", "w");
    assert(started != nullptr);
    fprintf(started, "STARTED\n");
    fclose(started);
  }

  // Android: loop on things to do
  while (1) {
    int events;
    int timeoutMillis = 0;
    struct android_poll_source *source;

    while ((ALooper_pollOnce(timeoutMillis, nullptr, &events, (void **)&source)) >= 0) {

      if (source != nullptr) {
        source->process(state, source);
      }

      if (state->destroyRequested != 0) {
        // Terminate
        if (app_data->vulkan_worker != nullptr) {
          delete app_data->vulkan_worker;
        }
        if (app_data->vertex_file != nullptr) {
          fclose(app_data->vertex_file);
        }
        if (app_data->fragment_file != nullptr) {
        fclose(app_data->fragment_file);
        }
        if (app_data->uniform_file != nullptr) {
          fclose(app_data->uniform_file);
        }
        delete app_data;

        log("\nANDROID TERMINATE OK\n");

        FILE *done = fopen("/sdcard/graphicsfuzz/DONE", "w");
        assert(done != nullptr);
        fprintf(done, "DONE\n");
        fclose(done);

        FreeGflagsArgs(argc, argv);

        return;
      }
    }
  }
}
