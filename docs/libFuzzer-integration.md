# LibFuzzer Integration

*** note
**Note:** The libFuzzer integration is experimental.
***

GraphicsFuzz can be used with [libFuzzer](http://llvm.org/docs/LibFuzzer.html)
using libFuzzer's [custom mutators](https://cs.chromium.org/chromium/src/third_party/libFuzzer/src/FuzzerInterface.h)
for fuzzing that is both coverage-guided and [structure-aware](https://github.com/google/fuzzer-test-suite/blob/master/tutorial/structure-aware-fuzzing.md).

