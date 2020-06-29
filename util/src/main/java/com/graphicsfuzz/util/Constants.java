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

package com.graphicsfuzz.util;

public final class Constants {

  private Constants() {
    // Prohibit instantiation of this class
  }

  public static final String GLF_COLOR = "_GLF_color";

  // Used to replace gl_FragCoord in vertex shades converted from fragment shaders
  public static final String GLF_FRAGCOORD = "_GLF_FragCoord";
  // Used to replace gl_FragDepth in vertex shades converted from fragment shaders
  public static final String GLF_FRAGDEPTH = "_GLF_FragDepth";

  public static final String GLF_POS = "_GLF_pos";

  public static final String GLF_OUT_VAR_BACKUP_PREFIX = "_GLF_outVarBackup";

  public static final String LIVE_PREFIX = "GLF_live";
  public static final String DEAD_PREFIX = "GLF_dead";
  public static final String SPLIT_LOOP_COUNTER_PREFIX = "_GLF_SPLIT_LOOP_COUNTER";
  public static final String STRUCTIFICATION_FIELD_PREFIX = "_f";
  public static final String STRUCTIFICATION_STRUCT_PREFIX = "_GLF_struct_";
  public static final String GLF_STRUCT_REPLACEMENT = "_GLF_struct_replacement_";

  public static final String GLF_DEAD = "_GLF_DEAD";
  public static final String GLF_ZERO = "_GLF_ZERO";
  public static final String GLF_ONE = "_GLF_ONE";
  public static final String GLF_FALSE = "_GLF_FALSE";
  public static final String GLF_TRUE = "_GLF_TRUE";
  public static final String GLF_FUZZED = "_GLF_FUZZED";
  public static final String GLF_WRAPPED_LOOP = "_GLF_WRAPPED_LOOP";
  public static final String GLF_WRAPPED_IF_TRUE = "_GLF_WRAPPED_IF_TRUE";
  public static final String GLF_WRAPPED_IF_FALSE = "_GLF_WRAPPED_IF_FALSE";
  public static final String GLF_IDENTITY = "_GLF_IDENTITY";
  public static final String GLF_SWITCH = "_GLF_SWITCH";

  // Macro names used to make array accesses in bounds
  public static final String GLF_MAKE_IN_BOUNDS_INT = "_GLF_MAKE_IN_BOUNDS_INT";
  public static final String GLF_MAKE_IN_BOUNDS_UINT = "_GLF_MAKE_IN_BOUNDS_UINT";

  public static final String GLF_PRIMITIVE = "_GLF_PRIMITIVE";
  public static final String GLF_PRIMITIVE_GLOBAL = "_GLF_PRIMITIVE_GLOBAL";
  public static final String GLF_COMPUTE = "_GLF_COMPUTE";
  public static final String GLF_PARAM = "_GLF_PARAM";
  public static final String GLF_UNKNOWN_PARAM = "GLF_UNKNOWN_PARAM";
  public static final String GLF_INIT_GLOBALS = "_GLF_init_globals";
  public static final String GLF_UNIFORM = "_GLF_UNIFORM";

  public static final String INT_LITERAL_UNIFORM_VALUES = "_GLF_uniform_int_values";
  public static final String UINT_LITERAL_UNIFORM_VALUES = "_GLF_uniform_uint_values";
  public static final String FLOAT_LITERAL_UNIFORM_VALUES = "_GLF_uniform_float_values";

  public static final String INJECTED_LOOP_COUNTER = "_injected_loop_counter";

  public static final String INJECTION_SWITCH = "injectionSwitch";

  public static final String OUTLINED_FUNCTION_PREFIX = "_GLF_outlined_";

  public static final String FLOAT_CONST = "_FLOAT_CONST";

  public static final String REDUCTION_INCOMPLETE = "REDUCTION_INCOMPLETE";

  // A key we use in .json files to identify the sub-dictionary containing compute shader input/
  // output information.
  public static final String COMPUTE_DATA_KEY = "$compute";

  // A key we use in .json files to instruct rendering of a grid instead of a quad.
  public static final String GRID_DATA_KEY = "$grid";

  // The entry within the compute shader input/output information that records the number of groups.
  public static final String COMPUTE_NUM_GROUPS = "num_groups";

  // The entry within the compute shader input/output information that records details of the
  // buffer used for input/output.
  public static final String COMPUTE_BUFFER = "buffer";

  // The entry within the compute shader input/output information that records the binding for
  // the buffer.
  public static final String COMPUTE_BINDING = "binding";

  // The entry within the compute shader input/output information that records the fields of
  // the buffer.
  public static final String COMPUTE_FIELDS = "fields";

  // The maximum number of total loop iterations when using a global loop limiter.
  public static final int GLF_GLOBAL_LOOP_BOUND_VALUE = 100;

  // Name of the global loop counter variable when using a global loop limiter.
  public static final String GLF_GLOBAL_LOOP_COUNT_NAME = "_GLF_global_loop_count";

  // Name of the constant storing the total loop bound when using a global loop limiter.
  public static final String GLF_GLOBAL_LOOP_BOUND_NAME = "_GLF_global_loop_bound";

  // We do not want to spend too long generating expressions for array constructors, so we set a
  // limit on how many distinct expressions we will generate for this purpose.
  public static final int MAX_GENERATED_EXPRESSIONS_FOR_ARRAY_CONSTRUCTOR = 20;

  // When donating one compute shader to another, we may need to donate an unsized array that
  // appeared in an SSBO of the donor.  We handle this by declaring a sized array, and this
  // constant provides a size for said array.
  public static final int DUMMY_SIZE_FOR_UNSIZED_ARRAY_DONATION = 10;

  // String used inside live-injected variable to indicate that it is a loop limiter.
  public static final String LOOP_LIMITER = "looplimiter";

  public static boolean isLiveInjectedVariableName(String name) {
    return name.startsWith(Constants.LIVE_PREFIX);
  }

  public static boolean isLooplimiterVariableName(String name) {
    return isLiveInjectedVariableName(name)
        && name.contains(Constants.LOOP_LIMITER);
  }

}
