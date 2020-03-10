# -*- coding: utf-8 -*-

# Copyright 2020 The GraphicsFuzz Project Authors
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

"""Outputs C++ structs from the JSON produced by 'adb shell cmd gpu vkjson'."""

import argparse
import json
import sys
import xml.etree.ElementTree as ET
import attr
from pathlib import Path
from typing import List, Any, TextIO, Dict, Optional

from attr import dataclass

from gfauto import util
from gfauto.util import check

_HEADER = """/*
 * Copyright (C) 2020 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

#include "vulkan/vulkan.h"

namespace advance_portability {
"""


_FOOTER = """
}  // namespace advance_portability
"""


def main(argv) -> int:
    parser = argparse.ArgumentParser(
        description='Outputs compile_commands.json from the stderr output of "bazel build -s ...".'
    )

    parser.add_argument(
        "--vk_xml",
        help="The vk.xml file from the Vulkan-Headers repo.",
        default="vk.xml",
    )

    parser.add_argument(
        "--output",
        help="The output C++ header.",
        default="advance_portability_layer_structs.h",
    )

    parser.add_argument(
        "json_files", help="One or more .json files from an Android device.", nargs="+",
    )

    parsed_args = parser.parse_args(argv)

    vk_xml = Path(parsed_args.vk_xml)
    output = Path(parsed_args.output)
    json_files = [Path(f) for f in parsed_args.json_files]

    json_contents = [
        json.loads(util.file_read_text(json_file)) for json_file in json_files
    ]

    json_contents = [j["devices"][0] for j in json_contents]

    tree = ET.parse(vk_xml)
    root: ET.Element = tree.getroot()

    with util.file_open_text(output, "w") as f:
        writer = Processor(f, root, json_contents)
        writer.process()


@dataclass
class MemoryHeap:
    flags: int
    size: int


@dataclass
class MemoryType:
    heap_index: int
    property_flags: int


def bits_in(subset: int, bits: int) -> bool:
    return (subset & bits) == subset


@attr.dataclass
class Processor:
    f: TextIO
    root: ET.Element
    device_jsons: List[Any]

    indent = 0
    indent_size = 2

    def line(self, s: str = "") -> None:
        indent = " " * self.indent
        self.f.write(f"{indent}{s}\n")

    def chunk(self, s: str) -> None:
        assumed_indent_size = 4
        one_indent_str = " " * self.indent_size
        lines = s.splitlines()

        # Skip first line if empty.
        if not lines[0]:
            lines = lines[1:]

        for i in range(0, len(lines)):
            space_count = 0
            for c in lines[i]:
                if c != " ":
                    break
                space_count += 1
            indent_count = space_count // assumed_indent_size
            # Remove initial spaces.
            lines[i] = lines[i][space_count:]
            # Re-add indentation.
            lines[i] = (one_indent_str * indent_count) + lines[i]

            self.line(lines[i])

    def increase_indent(self):
        self.indent += self.indent_size

    def decrease_indent(self):
        self.indent -= self.indent_size

    def process(self) -> None:
        self.line(_HEADER)
        self._vk_physical_device_features()
        self._vk_get_physical_device_format_properties()
        self._vk_get_physical_device_memory_properties()
        self.line(_FOOTER)

    def _vk_physical_device_features(self) -> None:
        name_elems: List[ET.Element] = self.root.findall(
            "./types/type[@name='VkPhysicalDeviceFeatures']/member/name"
        )
        names = [n.text for n in name_elems]
        self.line("VkPhysicalDeviceFeatures features = {")
        self.increase_indent()
        for name in names:
            self.line(f"// {name}")
            values = self.device_jsons
            values = [v["features"][name] for v in values]
            values = [False if v == 0 else True for v in values]
            value = all(values)
            value_str = "VK_TRUE" if value else "VK_FALSE"
            self.line(f"{value_str},")
        self.decrease_indent()
        self.line("};")

    @staticmethod
    def _and_json_fields(
        json_to_modify: Any, other_json: Any, fields: List[str]
    ) -> bool:
        all_zero = True
        for field in fields:
            json_to_modify[field] &= other_json[field]
            if json_to_modify[field] != 0:
                all_zero = False
        return all_zero

    def _vk_get_physical_device_format_properties(self) -> None:
        def formats_json_to_dict(formats_json: Any) -> Dict[int, Any]:
            formats_dict: Dict[int, Any] = {}
            for format_entry in formats_json:
                format_number = format_entry[0]
                format_properties = format_entry[1]
                formats_dict[format_number] = format_properties
            return formats_dict

        first_json = self.device_jsons[0]
        remaining_jsons = self.device_jsons[1:]

        # Start with the formats from the first json.
        simulated_formats: Dict[int, Any] = formats_json_to_dict(first_json["formats"])

        # Merge the remaining jsons.
        for other_json in remaining_jsons:
            other_format_dict = formats_json_to_dict(other_json["formats"])
            # We will iterate over simulated_formats because we only want formats that are present in both jsons.
            # Create a copy of the format_numbers so we can modify simulated_formats as we go.
            format_numbers = list(simulated_formats.keys())[:]
            for format_number in format_numbers:
                if format_number not in other_format_dict:
                    del simulated_formats[format_number]
                    continue
                simulated_format = simulated_formats[format_number]
                other_format = other_format_dict[format_number]
                all_zero = Processor._and_json_fields(
                    simulated_format,
                    other_format,
                    ["bufferFeatures", "linearTilingFeatures", "optimalTilingFeatures"],
                )
                if all_zero:
                    del simulated_formats[format_number]

        # Now we can write out our simulated formats.
        # VkFormatPropertiesWrapper format_properties[] = {
        #     {
        #         // format
        #         1,
        #         // properties
        #         {
        #             // linearTilingFeatures
        #             1,
        #             // optimalTilingFeatures
        #             1,
        #             // bufferFeatures
        #             1,
        #         }
        #     },
        # };

        # TODO: Could write out the proper format names instead of numbers like 1, 2, etc.
        # TODO: Could write out the named features like X | Y | Z instead of numbers like 50177.
        self.line()
        self.chunk(
            """
struct VkFormatPropertiesWrapper {
    VkFormat format;
    VkFormatProperties properties;
};
"""
        )
        self.line()
        self.line("VkFormatPropertiesWrapper format_properties[] = {")
        self.increase_indent()
        for format_number, format_properties in simulated_formats.items():
            self.chunk(
                f"""
{{
    // format
    VkFormat({format_number}),
    // properties
    {{
        // linearTilingFeatures
        {format_properties['linearTilingFeatures']},
        // optimalTilingFeatures
        {format_properties['optimalTilingFeatures']},
        // bufferFeatures
        {format_properties['bufferFeatures']},
    }},
}},"""
            )

        self.decrease_indent()
        self.line("};")

    def _vk_get_physical_device_memory_properties(self) -> None:

        VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT = 0x00000001
        VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT = 0x00000002
        VK_MEMORY_PROPERTY_HOST_COHERENT_BIT = 0x00000004
        VK_MEMORY_PROPERTY_HOST_CACHED_BIT = 0x00000008
        VK_MEMORY_PROPERTY_LAZILY_ALLOCATED_BIT = 0x00000010
        VK_MEMORY_PROPERTY_PROTECTED_BIT = 0x00000020
        VK_MEMORY_PROPERTY_DEVICE_COHERENT_BIT_AMD = 0x00000040
        VK_MEMORY_PROPERTY_DEVICE_UNCACHED_BIT_AMD = 0x00000080

        # Defined in the spec.
        memory_type_flags_all_possible: List[int] = [
            0,
            VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT,
            VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_CACHED_BIT,
            VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT
            | VK_MEMORY_PROPERTY_HOST_CACHED_BIT
            | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT,
            VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT,
            VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT
            | VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT
            | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT,
            VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT
            | VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT
            | VK_MEMORY_PROPERTY_HOST_CACHED_BIT,
            VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT
            | VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT
            | VK_MEMORY_PROPERTY_HOST_CACHED_BIT
            | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT,
            VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT
            | VK_MEMORY_PROPERTY_LAZILY_ALLOCATED_BIT,
            VK_MEMORY_PROPERTY_PROTECTED_BIT,
            VK_MEMORY_PROPERTY_PROTECTED_BIT | VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT,
            VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT
            | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT
            | VK_MEMORY_PROPERTY_DEVICE_COHERENT_BIT_AMD,
            VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT
            | VK_MEMORY_PROPERTY_HOST_CACHED_BIT
            | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT
            | VK_MEMORY_PROPERTY_DEVICE_COHERENT_BIT_AMD,
            VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT
            | VK_MEMORY_PROPERTY_DEVICE_COHERENT_BIT_AMD,
            VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT
            | VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT
            | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT
            | VK_MEMORY_PROPERTY_DEVICE_COHERENT_BIT_AMD,
            VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT
            | VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT
            | VK_MEMORY_PROPERTY_HOST_CACHED_BIT
            | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT
            | VK_MEMORY_PROPERTY_DEVICE_COHERENT_BIT_AMD,
            VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT
            | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT
            | VK_MEMORY_PROPERTY_DEVICE_COHERENT_BIT_AMD
            | VK_MEMORY_PROPERTY_DEVICE_UNCACHED_BIT_AMD,
            VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT
            | VK_MEMORY_PROPERTY_HOST_CACHED_BIT
            | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT
            | VK_MEMORY_PROPERTY_DEVICE_COHERENT_BIT_AMD
            | VK_MEMORY_PROPERTY_DEVICE_UNCACHED_BIT_AMD,
            VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT
            | VK_MEMORY_PROPERTY_DEVICE_COHERENT_BIT_AMD
            | VK_MEMORY_PROPERTY_DEVICE_UNCACHED_BIT_AMD,
            VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT
            | VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT
            | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT
            | VK_MEMORY_PROPERTY_DEVICE_COHERENT_BIT_AMD
            | VK_MEMORY_PROPERTY_DEVICE_UNCACHED_BIT_AMD,
            VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT
            | VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT
            | VK_MEMORY_PROPERTY_HOST_CACHED_BIT
            | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT
            | VK_MEMORY_PROPERTY_DEVICE_COHERENT_BIT_AMD
            | VK_MEMORY_PROPERTY_DEVICE_UNCACHED_BIT_AMD,
        ]

        heap = MemoryHeap(flags=1, size=(1 << 30))

        # Heap 0 is usually appropriate, so we will just use that.
        for d in self.device_jsons:
            heap_0 = d["memory"]["memoryHeaps"][0]
            check(
                heap_0["flags"] == heap.flags and int(heap_0["size"], 16) >= heap.size,
                AssertionError("heap incompatible"),
            )

        memory_types: List[MemoryType] = []
        for memory_type_flags in memory_type_flags_all_possible:
            memory_types.append(
                MemoryType(heap_index=0, property_flags=memory_type_flags)
            )

        VK_MEMORY_HEAP_DEVICE_LOCAL_BIT = 0x00000001

        # This property must hold, so filter out memory types where it does not:
        #   VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT iff VK_MEMORY_HEAP_DEVICE_LOCAL_BIT
        # In other words, we remove all memory types without VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT because our single heap
        # is always a device local heap.
        memory_types = [
            t
            for t in memory_types
            if bool(t.property_flags & VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT)
            == bool(heap.flags & VK_MEMORY_HEAP_DEVICE_LOCAL_BIT)
        ]

        # Now filter out memory types that we can't achieve on all devices.
        memory_types = [
            t for t in memory_types if self._all_devices_handle_memory_type(t)
        ]

        self.line()

        self.chunk(
            f"""
VkMemoryHeap heaps[] = {{
    {{
        // size
        VkDeviceSize({heap.size}),
        // flags
        VkMemoryHeapFlags({heap.flags}),
    }},
}};
"""
        )
        self.line()
        self.line("VkMemoryType memory_types[] = {")
        self.increase_indent()
        for memory_type in memory_types:
            self.chunk(
                f"""
{{
    // propertyFlags
    VkMemoryPropertyFlags({memory_type.property_flags}),
    // heapIndex
    uint32_t({memory_type.heap_index}),
}},"""
            )

        self.decrease_indent()
        self.line("};")

    def _all_devices_handle_memory_type(self, memory_type: MemoryType) -> bool:
        for d in self.device_jsons:
            device_memory_types = d["memory"]["memoryTypes"]
            if not Processor._device_handles_memory_type(
                memory_type, device_memory_types
            ):
                return False
        return True

    @staticmethod
    def _device_handles_memory_type(
        memory_type: MemoryType, device_memory_types: Any
    ) -> bool:
        for device_memory_type in device_memory_types:
            if device_memory_type["heapIndex"] == 0 and bits_in(
                memory_type.property_flags, device_memory_type["propertyFlags"]
            ):
                return True
        return False


if __name__ == "__main__":
    main(sys.argv[1:])
