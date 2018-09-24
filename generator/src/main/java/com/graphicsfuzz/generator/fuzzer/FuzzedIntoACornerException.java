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

package com.graphicsfuzz.generator.fuzzer;

/* This class is used to catch cases where fuzzing has failed and needs to backtrack,
 * e.g. where we need a mat4 l-value and do not have one.  An alternative to consider
 * is to ensure that fuzzing always succeeds by generating additional program constructs
 * where necessary
 */

public class FuzzedIntoACornerException extends RuntimeException {

  private static final long serialVersionUID = 1L;

}
