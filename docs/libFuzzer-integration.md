# LibFuzzer Integration

*** note
**Note:** The libFuzzer integration is actively being developed and is
experimental.
***

GraphicsFuzz can be used with [libFuzzer](http://llvm.org/docs/LibFuzzer.html)
as a [custom mutator](https://cs.chromium.org/chromium/src/third_party/libFuzzer/src/FuzzerInterface.h)
for fuzzing that is both coverage-guided and [structure-aware](https://github.com/google/fuzzer-test-suite/blob/master/tutorial/structure-aware-fuzzing.md).

## Using the Integration

1. Build GraphicsFuzz.

2. Start the GraphicsFuzz CustomMutatorServer with the following command:

```bash
java -ea -cp graphicsfuzz/target/graphicsfuzz/jar/tool-1.0.jar com.graphicsfuzz.generator.tool.CustomMutatorServer
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
#include <cstring>
#include <cerrno>
#include <fstream>
#include <iostream>

#include <iostream>
#include <string>

#define CHECK(COND, MSG) \
  if (!(COND)) {         \
    perror(MSG);         \
    exit(EXIT_FAILURE);  \
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
           sizeof(request_header), 0);
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
