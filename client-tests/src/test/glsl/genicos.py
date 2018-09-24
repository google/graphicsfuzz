
# Copyright 2018 The GraphicsFuzz Project Authors
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

faces = [ 2, 1, 0,
          3, 2, 0,
          4, 3, 0,
          5, 4, 0,
          1, 5, 0,
          11, 6,  7,
          11, 7,  8,
          11, 8,  9,
          11, 9,  10,
          11, 10, 6,
          1, 2, 6,
          2, 3, 7,
          3, 4, 8,
          4, 5, 9,
          5, 1, 10,
          2,  7, 6,
          3,  8, 7,
          4,  9, 8,
          5, 10, 9,
          1, 6, 10 ]

verts = [ "0.000",  "0.000",  "1.000",
          "0.894",  "0.000",  "0.447",
          "0.276",  "0.851",  "0.447",
          "-0.724",  "0.526",  "0.447",
          "-0.724", "-0.526",  "0.447",
          "0.276", "-0.851",  "0.447",
          "0.724",  "0.526", "-0.447",
          "-0.276",  "0.851", "-0.447",
          "-0.894",  "0.000", "-0.447",
          "-0.276", "-0.851", "-0.447",
          "0.724", "-0.526", "-0.447",
          "0.000",  "0.000", "-1.000" ]

print("{")
print("  \"points\": [\n")
for i in range(0, len(faces)):
    str = "    "
    for j in range(0, 3):
        str += verts[3 * faces[i] + j]
        if j < 2 or i < len(faces) - 1:
            str += ", "
    if (i % 3) == 2:
       str += "\n"
    print(str)
print("],")
            
texTriangles = [ "0.0, 0.0, 1.0, 0.0, 1.0, 1.0", "0.0, 0.0, 0.0, 1.0, 1.0, 1.0", "0.0, 1.0, 1.0, 1.0, 1.0, 0.0", "0.0, 1.0, 0.0, 0.0, 1.0, 0.0" ]
    
print("  \"texPoints\": [")
index = 0
for i in range(0, len(faces) / 3):
    print("    " + texTriangles[index] + (", " if i < (len(faces) / 3) - 1 else " ],"))
    index = (index + 1) % len(texTriangles)
    
print("  \"texture\": \"colorgrid_modulo.png\"")
print("}")
