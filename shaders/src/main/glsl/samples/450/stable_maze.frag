#version 450

/*
 * Copyright 2020 The GraphicsFuzz Project Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

precision highp float;
precision highp int;

layout(location = 0) out vec4 _GLF_color;
uniform vec2 resolution;

/*
This shader generates a little maze into the local array,
and if it detects that the current pixel is part of the
maze, it exits early. Otherwise, when the whole maze
has been generated (there's nowhere to "walk" anymore),
the other color is sent out.

During each iteration, the walker (vector p) checks
the four cardinal directions whether it can take a step
in those directions. Should there be no way to move,
the whole map is checked for empty space, and should
such be found, the walker is teleported there.

If there are directions to walk to, steps are taken
with a little bit of randomness based on how many
legal moves have been possible and how many directions
may be taken. All of this is done with integer math,
so the result is deterministic.

(For different kinds of mazes, just change the
starting position of the walker.)
*/

int map[16 * 16];

void main() {
  // pos is screen position in 0..1 range
  vec2 pos = gl_FragCoord.xy / resolution;
  // ipos is screen position in 0..15 range, in integer steps.
  // This creates a tile pattern.
  ivec2 ipos = ivec2(int(pos.x * 16.0), int(pos.y * 16.0));

  // Initialize map to zero values.
  int i;
  for (i = 0; i < 16 * 16; i++) {
    map[i] = 0;
  }

  // Start walker at 0,0
  ivec2 p = ivec2(0, 0);
  bool canwalk = true;
  // Counter which creates our apparent randomness
  int v = 0;
  do {
    v++;
    int directions = 0;
    // For each direction we could possibly walk, increment directions
    if (p.x > 0 && map[(p.x - 2) + (p.y) * 16] == 0) {
      directions += 1;
    }
    if (p.y > 0 && map[(p.x) + (p.y - 2) * 16] == 0) {
      directions += 1;
    }
    if (p.x < 14 && map[(p.x + 2) + (p.y) * 16] == 0) {
      directions += 1;
    }
    if (p.y < 14 && map[(p.x) + (p.y + 2) * 16] == 0) {
      directions += 1;
    }

    if (directions == 0) {
      // If all directions are exhausted, assume we're done..
      canwalk = false;
      // ..but if we can find an unused tile, hop there and
      // keep going:
      int j;
      for (i = 0; i < 8; i++) {
        for (j = 0; j < 8; j++) {
          if (map[j * 2 + i * 2 * 16] == 0) {
            p.x = j * 2;
            p.y = i * 2;
            canwalk = true;
          }
        }
      }
      // Either way, mark current position as wall.
      map[(p.x) + (p.y) * 16] = 1;
    } else {
      // Number of steps to take (based on our counter and
      // possible steps to take)
      int d = v % directions;
      // Increment the counter by the possible directions
      v += directions;

      // If we have steps left and it's legal to move left, do so
      if (d >= 0 && p.x > 0 && map[(p.x - 2) + (p.y) * 16] == 0) {
        d--;
        map[(p.x) + (p.y) * 16] = 1;
        map[(p.x - 1) + (p.y) * 16] = 1;
        map[(p.x - 2) + (p.y) * 16] = 1;
        p.x -= 2;
      }
      // If we have steps left and it's legal to move up, do so
      if (d >= 0 && p.y > 0 && map[(p.x) + (p.y - 2) * 16] == 0) {
        d--;
        map[(p.x) + (p.y) * 16] = 1;
        map[(p.x) + (p.y - 1) * 16] = 1;
        map[(p.x) + (p.y - 2) * 16] = 1;
        p.y -= 2;
      }
      // If we have steps left and it's legal to move right, do so
      if (d >= 0 && p.x < 14 && map[(p.x + 2) + (p.y) * 16] == 0) {
        d--;
        map[(p.x) + (p.y) * 16] = 1;
        map[(p.x + 1) + (p.y) * 16] = 1;
        map[(p.x + 2) + (p.y) * 16] = 1;
        p.x += 2;
      }
      // If we have steps left and it's legal to move down, do so
      if (d >= 0 && p.y < 14 && map[(p.x) + (p.y + 2) * 16] == 0) {
        d--;
        map[(p.x) + (p.y) * 16] = 1;
        map[(p.x) + (p.y + 1) * 16] = 1;
        map[(p.x) + (p.y + 2) * 16] = 1;
        p.y += 2;
      }
    }
    if (map[ipos.y * 16 + ipos.x] == 1) {
      // Early out: our pixel is in a wall
      _GLF_color = vec4(1.0, 1.0, 1.0, 1.0);
      return;
    }
  }
  while (canwalk);

  // If we manage to get here, there are no legal moves left,
  // and our pixel is in a non-wall tile.
  _GLF_color = vec4(0.0, 0.0, 0.0, 1.0);
}

