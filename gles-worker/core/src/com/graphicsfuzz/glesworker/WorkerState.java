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

package com.graphicsfuzz.glesworker;

enum WorkerState {
  NO_CONNECTION,
  GET_JOB,
  COHERENCE_INIT_PREPARE,
  COHERENCE_INIT_RENDER,
  PREPARE_COHERENCE,
  RENDER_COHERENCE,
  IMAGE_PREPARE,
  IMAGE_VALIDATE_PROGRAM,
  IMAGE_RENDER,
  IMAGE_REPLY_JOB,
  IMAGE_STANDALONE_PREPARE,
  IMAGE_STANDALONE_RENDER,
  IMAGE_STANDALONE_IDLE,
  COMPUTE_PREPARE,
  COMPUTE_EXECUTE,
  COMPUTE_REPLY_JOB,
  COMPUTE_STANDALONE_PREPARE,
  COMPUTE_STANDALONE_EXECUTE,
  COMPUTE_STANDALONE_IDLE,
}
