// Copyright 2019 The GraphicsFuzz Project Authors
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


// TODO(381): Fix memory leaks.
#include <jni.h>

#include <cerrno>
#include <cstdlib>
#include <cstdint>
#include <cstring>
#include <iostream>
#include <string>

class JVM {
 private:
  std::string kCustomMutatorServerClassName =
      "com/graphicsfuzz/generator/tool/CustomMutatorServer";
  std::string kMutateMethodName = "mutate";
  std::string kMutateMethodSignature =
      "(Ljava/lang/String;IZ)Ljava/lang/String;";
  std::string kClassPathOption = "-Djava.class.path=";
  char* option_c_str;

  JavaVM* java_vm;
  JavaVMInitArgs vm_args;
  JavaVMOption vm_options;

 public:
  JNIEnv* jni_env;
  jclass server_class;
  jmethodID mutate_method;

  JVM() {
    vm_args.version = JNI_VERSION_1_6;
    vm_args.nOptions = 1;

    const char* jar_path = getenv("GRAPHICSFUZZ_JAR_PATH");
    if (!jar_path) {
      std::cout << "GRAPHICSFUZZ_JAR_PATH not Specified" << std::endl;
      exit(1);
    }
    size_t option_size = strlen(jar_path) + 1 + kClassPathOption.size();
    option_c_str = new char[option_size];
    std::string option_string = kClassPathOption.append(jar_path);
    memcpy(option_c_str, option_string.c_str(), option_string.size() + 1);
    vm_options.optionString = option_c_str;
    vm_args.options = &vm_options;
    int result = JNI_CreateJavaVM(&java_vm, reinterpret_cast<void**>(&jni_env),
                                  &vm_args);
    if (!jni_env || result < 0) {
      fprintf(stderr, "Failed to create JVM\n");
      exit(1);
    }
    server_class = jni_env->FindClass(kCustomMutatorServerClassName.c_str());
    mutate_method =
        jni_env->GetStaticMethodID(server_class, kMutateMethodName.c_str(),
                                   kMutateMethodSignature.c_str());
  }

  // TODO(381): Figure out how to do this without crashing.
  // ~JVM() { java_vm->DestroyJavaVM(); }
};

extern "C" size_t LLVMFuzzerCustomMutator(uint8_t* data, size_t size,
                                          size_t max_size, unsigned int seed) {
  static JVM jvm;

  if (size <= 1) {
    // Handle common invalid testcases gracefully.
    static const std::string basic_shader = "void main(void) { }";
    if (basic_shader.size() < max_size) {
      memcpy(reinterpret_cast<char*>(data), basic_shader.c_str(),
             basic_shader.size());
      return basic_shader.size();
    }
  }

  jint j_seed = seed;

  // TODO(381): Allow use of vertex shaders. This might break if someone uses a
  // corpus with fragment shaders.
  jboolean is_fragment = true;

  // Convert data to a Java string, call mutate and then free it.
  std::string shader_string = std::string(reinterpret_cast<char*>(data), size);
  jstring input_shader = jvm.jni_env->NewStringUTF(shader_string.c_str());
  jstring j_mutated_shader = reinterpret_cast<jstring>(
      jvm.jni_env->CallStaticObjectMethod(jvm.server_class, jvm.mutate_method,
                                          input_shader, j_seed, is_fragment));
  if (!j_mutated_shader) {
    return size;
  }

  jboolean is_copy;
  const char* mutated_shader =
      jvm.jni_env->GetStringUTFChars(j_mutated_shader, &is_copy);
  size_t mutated_size = jvm.jni_env->GetStringUTFLength(j_mutated_shader);
  if (mutated_size > max_size) return size;
  memcpy(reinterpret_cast<char*>(data), mutated_shader, mutated_size);
  if (is_copy) {
    jvm.jni_env->ReleaseStringUTFChars(j_mutated_shader, mutated_shader);
  }
  return mutated_size;
}

extern "C" int LLVMFuzzerTestOneInput(const uint8_t* data, size_t size) {
  std::string shader(reinterpret_cast<const char*>(data), size);
  std::cout << shader << std::endl;
  return 0;
}
