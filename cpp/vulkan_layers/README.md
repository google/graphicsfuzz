# Using apic to generate the boiler-plate for a layer

We use GAPID to generate a lot of the boiler-plate for a Vulkan layer, and check in the generated code.  If the boiler-plate needs to be re-generated - e.g. because additional Vulkan API functions must be intercepted, this can be achieved as follows.

Clone [GAPID](https://github.com/google/gapid) and build it.

Make sure that `apic` is on your path.  `apic` is built as part of GAPID, and e.g. could be located at `/data/gapid/bazel-out/host/bin/cmd/apic/linux_amd64_stripped`.

Set `GAPID_SRC` to the root of the GAPID source code, e.g.:

```
export GAPID_SRC=/data/gapid
```

Navigate to the layer whose boilerplate you wish to generate, e.g.:

```
cd shader_fuzzer
```

Invoke the following command, specialized according to the name of the layer you wish to build (the following is for `shader_fuzzer`):

```
apic template -templatesearch $GAPID_SRC $GAPID_SRC/gapis/api/vulkan/vulkan.api graphicsfuzz_shader_fuzzer.tmpl
```

This will re-generate `layer.cpp` and `layer.h` for the associated layer.
