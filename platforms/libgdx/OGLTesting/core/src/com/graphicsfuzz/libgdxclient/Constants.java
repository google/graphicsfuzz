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

package com.graphicsfuzz.libgdxclient;

public class Constants
{
  public static int TIMEOUT_EXIT_CODE = 7;

  public static String PERSISTENT_DATA_FILENAME = "GraphicsFuzzPersistent";
  public static String PERSISTENT_KEY_ERROR_MSG = "errorMsg";
  public static String PERSISTENT_KEY_STAGE = "stage";
  public static String PERSISTENT_KEY_JOB_ID = "jobId";
  public static String PERSISTENT_KEY_TIMEOUT = "timeout";
  public static String PERSISTENT_KEY_TIME_COMPILE = "timeCompile";
  public static String PERSISTENT_KEY_TIME_FIRST_RENDER = "timeFirstRender";
  public static String PERSISTENT_KEY_TIME_OTHER_RENDER = "timeOrderRender";
  public static String PERSISTENT_KEY_TIME_CAPTURE = "timeCapture";
  public static String PERSISTENT_KEY_SANITY = "sanity";
  public static String PERSISTENT_KEY_IMG1 = "img1";
  public static String PERSISTENT_KEY_IMG2 = "img2";
}
