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

  public static final String INJECTION_SWITCH = "injectionSwitch";

  public static final String OUTLINED_FUNCTION_PREFIX = "_GLF_outlined_";

  public static final String FLOAT_CONST = "_FLOAT_CONST";

  public static final String REDUCTION_INCOMPLETE = "REDUCTION_INCOMPLETE";

  // A key we use in .json files to identify the sub-dictionary containing compute shader input/
  // output information.
  public static final String COMPUTE_DATA_KEY = "$compute";

  // The entry within the compute shader input/output information that records the number of groups.
  public static final String COMPUTE_NUM_GROUPS = "num_groups";

  // The entry within the compute shader input/output information that records details of the
  // buffer used for input/output.
  public static final String COMPUTE_BUFFER = "buffer";

}
