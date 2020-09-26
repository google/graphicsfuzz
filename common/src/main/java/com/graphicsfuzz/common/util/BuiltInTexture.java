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

package com.graphicsfuzz.common.util;

// Enumeration to track the built-in textures which are supported in GraphicsFuzz.  These are
// textures for which a specific image will be created, typically by running a graphics pipeline
// that writes to an image, in the Amber file that gfauto generates.
public enum BuiltInTexture {
  // TODO(https://github.com/google/graphicsfuzz/issues/1074): At present only one texture is
  //  supported, but it would be good to have some more.
  DEFAULT
}
