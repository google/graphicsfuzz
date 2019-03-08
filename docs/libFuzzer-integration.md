# LibFuzzer Integration

**Note:** The libFuzzer integration is actively being developed and is
experimental.

GraphicsFuzz can be used with [libFuzzer](http://llvm.org/docs/LibFuzzer.html)
as a [custom mutator](https://cs.chromium.org/chromium/src/third_party/libFuzzer/src/FuzzerInterface.h)
for fuzzing that is both coverage-guided and [structure-aware](https://github.com/google/fuzzer-test-suite/blob/master/tutorial/structure-aware-fuzzing.md).

In examples below, the `graphicsfuzz/` directory is the unzipped release. If
building from source, this directory can be found at
`graphicsfuzz/target/graphicsfuzz/`.

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
# Must use clang-6.0 or greater (sudo apt install clang-6.0 && clang-6.0 -fsanitize=fuzzer...).
clang++ -g -fsanitize=fuzzer,address graphicsfuzz/examples/libFuzzer-integration/tcp_fuzzer.cc -o fuzzer
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

# Must use clang-6.0 or greater (sudo apt install clang-6.0 && clang-6.0 -fsanitize=fuzzer...).
clang++ -g -fsanitize=fuzzer,address \
  -I/usr/lib/jvm/java-8-openjdk-amd64/include/ \
  -I/usr/lib/jvm/java-8-openjdk-amd64/include/linux \
  -L/usr/lib/jvm/java-8-openjdk-amd64/jre/lib/amd64/server/ \
  -ljvm graphicsfuzz/examples/libFuzzer-integration/jni_fuzzer.cc -o fuzzer

```

3. Run the fuzzer, setting the appropriate environment variables (including
   GRAPHICSFUZZ_JAR_PATH which you must set to `tool-1.0.jar` yourself) and
   options:

```bash
export GRAPHICSFUZZ_JAR_PATH=<path>
export LD_LIBRARY_PATH=/usr/lib/jvm/java-8-openjdk-amd64/jre/lib/amd64/server/
# Disable leak detection because the fuzzer leaks memory
export ASAN_OPTIONS=detect_leaks=0
./fuzzer -max_total_time=10 -print_final_stats=1
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
