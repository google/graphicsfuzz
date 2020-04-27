/*
 * Copyright 2018 The GraphicsFuzz Project Authors
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

public final class OpenGlConstants {


  private OpenGlConstants() {
    // Utility class
  }

  public static final String GL_FRAG_COLOR = "gl_FragColor";
  public static final String GL_FRAG_COORD = "gl_FragCoord";
  public static final String GL_FRAG_DEPTH = "gl_FragDepth";
  public static final String GL_FRONT_FACING = "gl_FrontFacing";
  public static final String GL_POSITION = "gl_Position";
  public static final String GL_POINT_SIZE = "gl_PointSize";
  public static final String GL_POINT_COORD = "gl_PointCoord";

  // Compute shaders
  public static final String GL_NUM_WORK_GROUPS = "gl_NumWorkGroups";
  public static final String GL_WORK_GROUP_SIZE = "gl_WorkGroupSize";
  public static final String GL_WORK_GROUP_ID = "gl_WorkGroupID";
  public static final String GL_LOCAL_INVOCATION_ID = "gl_LocalInvocationID";
  public static final String GL_GLOBAL_INVOCATION_ID = "gl_GlobalInvocationID";
  public static final String GL_LOCAL_INVOCATION_INDEX = "gl_LocalInvocationIndex";

}
