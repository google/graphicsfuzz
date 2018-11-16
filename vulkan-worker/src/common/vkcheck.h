// Copyright 2018 The GraphicsFuzz Project Authors
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

#ifndef __VKCHECK__
#define __VKCHECK__

#include <vulkan/vulkan.h>

const char *getVkResultString (VkResult result);

void __VK_CHECK_LOG_CALL(const char *file, int line, const char *expr);
void __VK_CHECK_LOG_RETURN(const char *file, int line, VkResult result);
void __VK_CHECK_LOG_VOID_RETURN(const char *file, int line);

#define VKCHECK(expr) do { \
  __VK_CHECK_LOG_CALL(__FILE__, __LINE__, #expr); \
  __VK_CHECK_LOG_RETURN(__FILE__, __LINE__, (expr));  \
  } while (false)

#define VKLOG(expr) do { \
  __VK_CHECK_LOG_CALL(__FILE__, __LINE__, #expr); \
  (expr); \
  __VK_CHECK_LOG_VOID_RETURN(__FILE__, __LINE__); \
  } while (false)

#endif
