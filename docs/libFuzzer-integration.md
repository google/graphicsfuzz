# LibFuzzer Integration

**Note:** The libFuzzer integration is actively being developed and is
experimental.

GraphicsFuzz can be used with [libFuzzer](http://llvm.org/docs/LibFuzzer.html)
as a [custom mutator](https://cs.chromium.org/chromium/src/third_party/libFuzzer/src/FuzzerInterface.h)
for fuzzing that is both coverage-guided and [structure-aware](https://github.com/google/fuzzer-test-suite/blob/master/tutorial/structure-aware-fuzzing.md).

## Using the Integration

1. Build GraphicsFuzz.

2. Start the GraphicsFuzz CustomMutatorServer with the following command:

```bash
java -ea -cp graphicsfuzz/target/graphicsfuzz/jar/tool-1.0.jar \
  com.graphicsfuzz.generator.tool.CustomMutatorServer
Listening on port: 8666
```

3. Build a fuzz target (in a new shell, since the previous example won't
   terminate). An example is provided below:

```bash
cat << EOF > fuzzer.cc
#include <arpa/inet.h>
#include <sys/socket.h>
#include <sys/types.h>
#include <unistd.h>

#include <algorithm>
#include <cerrno>
#include <cstring>
#include <fstream>
#include <iostream>
#include <string>

void CHECK(bool condition, const char* message) {
  if (condition)
    return;
  perror(message);
  exit(EXIT_FAILURE);
}

static int sock;

void ReadBuffer(uint8_t* data, size_t size) {
  size_t bytes_read = 0;
  while (bytes_read < size) {
    void* buf = data + bytes_read;
    size_t count = size - bytes_read;
    ssize_t result = TEMP_FAILURE_RETRY(read(sock, buf, count));
    CHECK(result >= 1, "short read");
    bytes_read += static_cast<size_t>(result);
  }
}

void Discard(size_t total_bytes_to_discard) {
  if (!total_bytes_to_discard) return;

  static const size_t discard_buffer_size = 1024;
  static uint8_t discard_buffer[discard_buffer_size];

  size_t num_discarded = 0;
  while (num_discarded < total_bytes_to_discard) {
    size_t num_to_discard =
        std::min(total_bytes_to_discard - num_discarded, discard_buffer_size);
    ReadBuffer(discard_buffer, num_to_discard);
    num_discarded += num_to_discard;
  }
}

struct __attribute__((packed)) MutateRequestHeader {
  uint64_t size;
  uint32_t seed;
  uint8_t is_fragment;
};

static struct sockaddr_in serv_addr;
extern "C" size_t LLVMFuzzerCustomMutator(uint8_t* data, size_t size,
                                          size_t max_size, unsigned int seed) {
  if (size <= 1) {
    // Handle common invalid testcases gracefully.
    static const std::string basic_shader = "void main(void) { }";
    if (basic_shader.size() < max_size) {
      memcpy(reinterpret_cast<char*>(data), basic_shader.c_str(),
             basic_shader.size());
      return basic_shader.size();
    }
  }

  // Open a connection to the CustomMutatorServer.
  if (!sock) {
    sock = socket(AF_INET, SOCK_STREAM, 0);
    CHECK(sock >= 0, "Could not create socket");
    static struct sockaddr_in serv_addr;
    serv_addr.sin_family = AF_INET;
    static const int port = 8666;
    serv_addr.sin_port = htons(port);
    CHECK(inet_pton(AF_INET, "0.0.0.0", &serv_addr.sin_addr) != -1,
          "invalid address");

    CHECK(connect(sock, reinterpret_cast<const struct sockaddr*>(&serv_addr),
                  sizeof(serv_addr)) >= 0,
          "connection failed");
  }

  static MutateRequestHeader request_header;
  request_header.size = size;
  request_header.seed = seed;
  // In this example we only start with a fragment shader, so every shader must
  // be a fragment shader.
  request_header.is_fragment = true;
  static int flags = 0;
  // Send the mutation request.
  ssize_t num_bytes_sent =
      send(sock, reinterpret_cast<const void*>(&request_header),
           sizeof(request_header), flags);
  CHECK(num_bytes_sent == sizeof(request_header), "short write");
  // Send the shader.
  num_bytes_sent = send(sock, reinterpret_cast<const void*>(data), size, flags);
  CHECK(static_cast<size_t>(num_bytes_sent) == size, "short write");

  // Read the response shader size and contents.
  uint8_t mutated_size_bytes[sizeof(size_t)];
  ReadBuffer(mutated_size_bytes, sizeof(size_t));
  size_t mutated_buf_length = *(reinterpret_cast<size_t*>(mutated_size_bytes));
  if (mutated_buf_length > max_size) {
    Discard(mutated_buf_length);
    std::cout << "discard" << std::endl;
    return size;
  }
  size_t read_size = std::min(max_size, mutated_buf_length);
  ReadBuffer(data, read_size);
  return read_size;
}

extern "C" int LLVMFuzzerTestOneInput(const uint8_t* data, size_t size) {
  std::string shader(reinterpret_cast<const char*>(data), size);
  // Give the user some feedback since the coverage won't grow much in an empty
  // fuzzer.
  std::cout << shader << std::endl;
  return 0;
}
EOF

# Must use clang-6.0 or greater (sudo apt install clang-6.0 && clang-6.0 -fsanitize=fuzzer...).
clang++ -g -fsanitize=fuzzer,address fuzzer.cc -o fuzzer
```

4. Run the fuzz target (using a corpus is optional with the example above, but
   is recommended):

```bash
./fuzzer
INFO: Seed: 1327188846
INFO: Loaded 1 modules   (33 inline 8-bit counters): 33 [0x78d460, 0x78d481),
INFO: Loaded 1 PC tables (33 PCs): 33 [0x56a438,0x56a648),
INFO: -max_len is not provided; libFuzzer will not generate inputs larger than 4096 bytes

INFO: A corpus is not provided, starting from an empty corpus


#2      INITED cov: 3 ft: 3 corp: 1/1b exec/s: 0 rss: 50Mb
void main(void) { }
void main(void)
{
 if(_GLF_DEAD(false))
  {
   gl_FragColor = vec4(-3.0, -4.7, -3.1, 11.74);
  }
}
...
```

You should also see log messages from the server.
For now the server can only accept one connection per life time. So if you quit
out of libFuzzer or it dies on its own you must restart the server.
Note that it may appear as though libFuzzer is "stuck" on an invalid input and
repeatedly asks GraphicsFuzz to mutate it. libFuzzer is not actually stuck it
should progress within 30 seconds.

### (Bonus) Using Java Native Interface for Better Performance

**Note**: This example is known to leak memory.

Although the example above can be implemented easily and without any libraries
it is pretty slow because of the overhead introduced by TCP. We can use Java
Native Interface (JNI) to call GraphicsFuzz directly from the libFuzzer process.
Below are instructions on how this can be done, with an example. As you can see
building this is a lot more complicated, so it is more likely that some
instructions may not be exactly right on your machine.

1. Build GraphicsFuzz.

2. Build a fuzz target (in a new shell, since the previous example won't
   terminate). An example is provided below:
```bash
cat << EOF > jni-fuzzer.cc
// TODO(381): Fix memory leaks.
#include <unistd.h>

#include <cerrno>
#include <cstring>
#include <ctime>
#include <iostream>
#include <string>

#include <jni.h>

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
  std::string* shader_string =
      new std::string(reinterpret_cast<char*>(data), size);
  jstring input_shader = jvm.jni_env->NewStringUTF(shader_string->c_str());
  jstring j_mutated_shader = reinterpret_cast<jstring>(
      jvm.jni_env->CallStaticObjectMethod(jvm.server_class, jvm.mutate_method,
                                          input_shader, j_seed, is_fragment));
  jvm.jni_env->ReleaseStringUTFChars(input_shader, shader_string->c_str());
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
EOF

# Must use clang-6.0 or greater (sudo apt install clang-6.0 && clang-6.0 -fsanitize=fuzzer...).
clang++ -g -fsanitize=fuzzer,address \
  -I/usr/lib/jvm/java-8-openjdk-amd64/include/ \
  -I/usr/lib/jvm/java-8-openjdk-amd64/include/linux \
  -L/usr/lib/jvm/java-8-openjdk-amd64/jre/lib/amd64/server/ \
  -ljvm jni-fuzzer.cc -o jni-fuzzer

```

3. Run the fuzzer, setting the appropriate environment variables (including
   GRAPHICSFUZZ_JAR_PATH which you must set yourself) and options:

```bash
export GRAPHICSFUZZ_JAR_PATH=<path>
export LD_LIBRARY_PATH=/usr/lib/jvm/java-8-openjdk-amd64/jre/lib/amd64/server/
# Disable leak detection because the fuzzer leaks memory
export ASAN_OPTIONS=detect_leaks=0
./jni-fuzzer -max_total_time=10 -print_final_stats=1
INFO: Seed: 4061094416
INFO: Loaded 1 modules   (50 inline 8-bit counters): 50 [0x78e320, 0x78e352),
INFO: Loaded 1 PC tables (50 PCs): 50 [0x56b4a8,0x56b7c8),
INFO: -max_len is not provided; libFuzzer will not generate inputs larger than 4096 bytes

INFO: A corpus is not provided, starting from an empty corpus
...
Done 652 runs in 11 second(s)
stat::number_of_executed_units: 652
stat::average_exec_per_sec:     59
```

Here you can see we have sped up the fuzzer by more than 5X, from 11 executions
per second to 59 executions per second.
