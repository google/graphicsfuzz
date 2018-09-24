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

package com.graphicsfuzz.server;

import com.graphicsfuzz.server.thrift.Job;

public interface IServerJob {

  /**
   * Get a job.
   * @return null if this IServerJob should be removed from the job list.
   * @throws ServerJobException to handle communication problems with server
   */
  Job getJob() throws ServerJobException;

  /**
   * Report that job is finished.
   * @return true if this IServerJob should be removed from the job list.
   */
  boolean finishJob(Job job) throws ServerJobException;
}
