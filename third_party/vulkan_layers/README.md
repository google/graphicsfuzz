# Using apic to generate the boiler-plate for a layer

We use AGI to generate a lot of the boiler-plate for a Vulkan layer, and check
in the generated code.  If the boiler-plate needs to be re-generated - e.g.
because additional Vulkan API functions must be intercepted, this can be achieved as follows.

Clone [AGI](https://github.com/google/agi) and build it.

Make sure that `apic` is on your path.  `apic` is built as part of AGI, and e.g.
could be located at:

    /data/agi/bazel-out/host/bin/cmd/apic/linux_amd64_stripped

Set `AGI_SRC` to the root of the AGI source code, e.g.:

```
export AGI_SRC=/data/agi
```

Navigate to the layer whose boilerplate you wish to generate, e.g.:

```
cd shader_fuzzer
```

Invoke the following command, specialized according to the name of the layer you
wish to build (the following is for `shader_fuzzer`):

```
apic template -templatesearch $AGI_SRC $AGI_SRC/gapis/api/vulkan/vulkan.api graphicsfuzz_shader_fuzzer.tmpl
```

This will re-generate `layer.cpp` and `layer.h` for the associated layer.



# Using the shader fuzzer layer

(Instructions for the shader scraper layer are similar.)

Copy VkLayer_graphicsfuzz_shader_fuzzer.json to:

```
/path/to/vulkansdk/x86_64/etc/vulkan/explicit_layer.d/
```

Ensure that `LD_LIBRARY_PATH` includes the directory containing `libVkLayer_graphicsfuzz_shader_fuzzer.so`.

Enable the layer:

```
export VK_INSTANCE_LAYERS=$VK_INSTANCE_LAYERS:VK_LAYER_GRAPHICSFUZZ_shader_fuzzer
```

Set `GRAPHICSFUZZ_SHADER_FUZZER_WORK_DIR` to the directory where you would like shaders to be saved to.

