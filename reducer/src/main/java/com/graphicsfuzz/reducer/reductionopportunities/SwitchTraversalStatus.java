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

package com.graphicsfuzz.reducer.reductionopportunities;

/**
 * A helper enum for traversing switch statements that wrap original code in a particular case
 * label.
 */
enum SwitchTraversalStatus {

  NO_LABEL_YET, // Indicates that traversal has not yet reached any case label.
  IN_ORIGINAL_CODE, // Traversal is inside the original code.
  OUTSIDE_ORIGINAL_CODE // Traversal is outside the original code.

}
