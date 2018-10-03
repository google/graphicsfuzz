/*
 * GraphicsFuzz vulkan worker
 * Copyright (C) 2018 Google, Inc.
 *
 * Based on the 15-draw_cube sample of https://github.com/googlesamples/vulkan-basic-samples
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
VULKAN_SAMPLE_SHORT_DESCRIPTION
GraphicsFuzz vulkan worker
*/

#include <util_init.hpp>
#include <assert.h>
#include <string.h>
#include <cstdlib>
#include <fstream>
#include "cube_data.h"

#include "cJSON.h"

char *getFileContent(const char *filename) {
    FILE *f = fopen(filename, "r");
    if (f == NULL) {
        return NULL;
    }

    fseek(f, 0, SEEK_END);
    long len = ftell(f);
    fseek(f, 0, SEEK_SET);
    char* content=(char *)malloc(len+1);
    memset(content,0,len+1);
    long readSoFar = 0;
    while (readSoFar < len) {
        long toRead = len - readSoFar;
        readSoFar += fread(content, 1, toRead, f);
    }

    return content;
}

void writeDoneFile() {
    FILE *f = fopen("/sdcard/graphicsfuzz/DONE", "w");
    if (f == NULL) {
        LOGE("Cannot open DONE file");
        return;
    }
    fprintf(f, "DONE\n");
    fflush(f);
    fclose(f);
}

void uniformInit(struct sample_info& info, int idx, std::string funcName, cJSON *args) {
    cJSON *x, *y, *z, *w;

    if (funcName == "glUniform1f") {
        assert (cJSON_GetArraySize(args) == 1);
        x = cJSON_GetArrayItem(args, 0);
        info.sample_uniforms[idx].size = sizeof(glm::float_t);
        info.sample_uniforms[idx].pval = new glm::float_t(x->valuedouble); // FIXME: memory leak
    } else if (funcName == "glUniform2f") {
        assert (cJSON_GetArraySize(args) == 2);
        x = cJSON_GetArrayItem(args, 0);
        y = cJSON_GetArrayItem(args, 1);
        info.sample_uniforms[idx].size = sizeof(glm::vec2);
        info.sample_uniforms[idx].pval = new glm::vec2(x->valuedouble, y->valuedouble); // FIXME: memory leak
    } else if (funcName == "glUniform3f") {
        assert (cJSON_GetArraySize(args) == 3);
        x = cJSON_GetArrayItem(args, 0);
        y = cJSON_GetArrayItem(args, 1);
        z = cJSON_GetArrayItem(args, 2);
        info.sample_uniforms[idx].size = sizeof(glm::vec3);
        info.sample_uniforms[idx].pval = new glm::vec3(x->valuedouble, y->valuedouble, z->valuedouble); // FIXME: memory leak
    } else if (funcName == "glUniform4f") {
        assert (cJSON_GetArraySize(args) == 4);
        x = cJSON_GetArrayItem(args, 0);
        y = cJSON_GetArrayItem(args, 1);
        z = cJSON_GetArrayItem(args, 2);
        w = cJSON_GetArrayItem(args, 3);
        info.sample_uniforms[idx].size = sizeof(glm::vec4);
        info.sample_uniforms[idx].pval = new glm::vec4(x->valuedouble, y->valuedouble,
                                                       z->valuedouble,
                                                       w->valuedouble); // FIXME: memory leak

    } else if (funcName == "glUniform1i") {
        assert (cJSON_GetArraySize(args) == 1);
        x = cJSON_GetArrayItem(args, 0);
        info.sample_uniforms[idx].size = sizeof(glm::int_t);
        info.sample_uniforms[idx].pval = new glm::int_t(x->valueint); // FIXME: memory leak
    } else if (funcName == "glUniform2i") {
        assert (cJSON_GetArraySize(args) == 2);
        x = cJSON_GetArrayItem(args, 0);
        y = cJSON_GetArrayItem(args, 1);
        info.sample_uniforms[idx].size = sizeof(glm::ivec2);
        info.sample_uniforms[idx].pval = new glm::ivec2(x->valueint, y->valueint); // FIXME: memory leak
    } else if (funcName == "glUniform3i") {
        assert (cJSON_GetArraySize(args) == 3);
        x = cJSON_GetArrayItem(args, 0);
        y = cJSON_GetArrayItem(args, 1);
        z = cJSON_GetArrayItem(args, 2);
        info.sample_uniforms[idx].size = sizeof(glm::ivec3);
        info.sample_uniforms[idx].pval = new glm::ivec3(x->valueint, y->valueint, z->valueint); // FIXME: memory leak
    } else if (funcName == "glUniform4i") {
        assert (cJSON_GetArraySize(args) == 4);
        x = cJSON_GetArrayItem(args, 0);
        y = cJSON_GetArrayItem(args, 1);
        z = cJSON_GetArrayItem(args, 2);
        w = cJSON_GetArrayItem(args, 3);
        info.sample_uniforms[idx].size = sizeof(glm::ivec4);
        info.sample_uniforms[idx].pval = new glm::ivec4(x->valueint, y->valueint, z->valueint, w->valueint); // FIXME: memory leak

    } else {
        assert("Unknown funcName" && 0);
    }
}

void dumpPhysicalDeviceProperties(struct sample_info& info) {
    // Depends on init_enumerate_device()
    VkPhysicalDeviceProperties *prop = &info.gpu_props;
    FILE *f = fopen("/sdcard/graphicsfuzz/vkPhysicalDeviceProperties.txt", "w");
    fprintf(f, "Name: %s\n", prop->deviceName);
    fprintf(f, "DriverVersion: %u\n", prop->driverVersion);
    uint32_t api = prop->apiVersion;
    fprintf(f, "APIVersion: %d.%d.%d\n", VK_VERSION_MAJOR(api), VK_VERSION_MINOR(api), VK_VERSION_PATCH(api));
    fclose(f);
}

void clearVkLog() {
    FILE *f = fopen(VKLOGFILE, "w");
    if (f == NULL) {
        LOGE("Cannot open vk log file");
        exit(1);
    }
    fclose(f);
}

bool fileExists(const char *filename) {
    FILE *f = fopen(filename, "r");
    if (f == NULL) {
        return false;
    } else {
        fclose(f);
        return true;
    }
}

int sample_main(int argc, char *argv[]) {
    VkResult U_ASSERT_ONLY res;
    struct sample_info info = {};
    char sample_title[] = "Draw Cube";
    const bool depthPresent = true;

    clearVkLog();

    vklog("process_command_line_args()\n");
    process_command_line_args(info, argc, argv);
    vklog("init_global_layer_properties()\n");
    init_global_layer_properties(info);
    vklog("init_instance_extension_names()\n");
    init_instance_extension_names(info);
    vklog("init_device_extension_names()\n");
    init_device_extension_names(info);
    vklog("init_instance()\n");
    init_instance(info, sample_title);
    vklog("init_enumerate_device()\n");
    init_enumerate_device(info);
    vklog("dumpPhysicalDeviceProperties()\n");
    dumpPhysicalDeviceProperties(info);


    //init_window_size(info, 500, 500);
    // Hugues: force small window
    info.width = 256;
    info.height = 256;

    vklog("init_connection()\n");
    init_connection(info);
    vklog("init_window()\n");
    init_window(info);
    vklog("init_swapchain_extension()\n");
    init_swapchain_extension(info);
    vklog("init_device()\n");
    init_device(info);
    vklog("init_command_pool()\n");
    init_command_pool(info);
    vklog("init_command_buffer()\n");
    init_command_buffer(info);
    vklog("execute_begin_command_buffer()\n");
    execute_begin_command_buffer(info);
    vklog("init_device_queue()\n");
    init_device_queue(info);

    vklog("init_swap_chain()\n");
    init_swap_chain(info);
    vklog("init_depth_buffer()\n");
    init_depth_buffer(info);

    //========================================================================================
    // Hugues: JSON uniform support work-in-progress

    {
        FILE *f;

        char *jsonSrc = getFileContent("/sdcard/graphicsfuzz/test.json");
        cJSON *json = cJSON_Parse(jsonSrc);

        cJSON *uniformName;
        cJSON *uniformEntry;
        cJSON *uniformFunc;
        cJSON *uniformArgs;
        char *entryName;
        char *funcName;
        cJSON *argVal;
        int numUniforms;
        int bindingIdx;
        cJSON *bindingEntry;

        if (cJSON_HasObjectItem(json, "uniformOrder")) {
            cJSON *uniformOrder = cJSON_GetObjectItemCaseSensitive(json, "uniformOrder");
            assert(cJSON_IsArray(uniformOrder));
            numUniforms = cJSON_GetArraySize(uniformOrder);
            info.sample_uniforms.resize(numUniforms);

            for (int i = 0; i < numUniforms; i++) {
                uniformName = cJSON_GetArrayItem(uniformOrder, i);
                entryName = cJSON_GetStringValue(uniformName);
                uniformEntry = cJSON_GetObjectItemCaseSensitive(json, entryName);
                assert(cJSON_IsObject(uniformEntry));
                uniformFunc = cJSON_GetObjectItemCaseSensitive(uniformEntry, "func");
                assert(cJSON_IsString(uniformFunc));
                funcName = cJSON_GetStringValue(uniformFunc);
                uniformArgs = cJSON_GetObjectItemCaseSensitive(uniformEntry, "args");
                uniformInit(info, i, std::string(funcName), uniformArgs);
            }
        } else {
            vklog("Using the new JSON format!\n");
            int numUniforms = cJSON_GetArraySize(json);
            info.sample_uniforms.resize(numUniforms);

            for (int i = 0; i < numUniforms; i++) {
                uniformEntry = cJSON_GetArrayItem(json, i);
                assert(cJSON_IsObject(uniformEntry));
                uniformFunc = cJSON_GetObjectItemCaseSensitive(uniformEntry, "func");
                assert(cJSON_IsString(uniformFunc));
                funcName = cJSON_GetStringValue(uniformFunc);
                uniformArgs = cJSON_GetObjectItemCaseSensitive(uniformEntry, "args");
                bindingEntry = cJSON_GetObjectItemCaseSensitive(uniformEntry, "binding");
                bindingIdx = bindingEntry->valueint;
                uniformInit(info, bindingIdx, std::string(funcName), uniformArgs);
            }
        }

        cJSON_Delete(json);

        f = fopen("/sdcard/graphicsfuzz/OK", "w");
        fprintf(f, "%s:%d\n", __FILE__, __LINE__);
        fclose(f);
    }

    //========================================================================================

    vklog("init_uniform_buffer()\n");
    init_uniform_buffer(info);
    vklog("init_descriptor_and_pipeline_layouts()\n");
    init_descriptor_and_pipeline_layouts(info, false);
    vklog("init_renderpass()\n");
    init_renderpass(info, depthPresent);

    //init_shaders(info, vertShaderText, fragShaderText);
    // Hugues: read shader source from file
    const char *vertShaderSrc = "";
    const char *vertFileName = "/sdcard/graphicsfuzz/test.vert";
    if (fileExists(vertFileName)) {
         vertShaderSrc = getFileContent(vertFileName);
    } else {
        assert(fileExists("/sdcard/graphicsfuzz/test.vert.spv"));
    }

    const char *fragShaderSrc = "";
    const char *fragFileName = "/sdcard/graphicsfuzz/test.frag";
    if (fileExists(fragFileName)) {
        fragShaderSrc = getFileContent(fragFileName);
    } else {
        assert(fileExists("/sdcard/graphicsfuzz/test.frag.spv"));
    }

    vklog("init_shaders()\n");
    init_shaders(info, vertShaderSrc, fragShaderSrc);

    vklog("init_framebuffers()\n");
    init_framebuffers(info, depthPresent);
    vklog("init_vertex_buffer()\n");
    init_vertex_buffer(info, g_vb_solid_face_colors_Data, sizeof(g_vb_solid_face_colors_Data),
                       sizeof(g_vb_solid_face_colors_Data[0]), false);
    vklog("init_descriptor_pool()\n");
    init_descriptor_pool(info, false);
    vklog("init_descriptor_set()\n");
    init_descriptor_set(info, false);
    vklog("init_pipeline_cache()\n");
    init_pipeline_cache(info);
    vklog("init_pipeline()\n");
    init_pipeline(info, depthPresent);

    /* VULKAN_KEY_START */

    VkClearValue clear_values[2];
    clear_values[0].color.float32[0] = 0.2f;
    clear_values[0].color.float32[1] = 0.2f;
    clear_values[0].color.float32[2] = 0.2f;
    clear_values[0].color.float32[3] = 0.2f;
    clear_values[1].depthStencil.depth = 1.0f;
    clear_values[1].depthStencil.stencil = 0;

    VkSemaphore imageAcquiredSemaphore;
    VkSemaphoreCreateInfo imageAcquiredSemaphoreCreateInfo;
    imageAcquiredSemaphoreCreateInfo.sType = VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO;
    imageAcquiredSemaphoreCreateInfo.pNext = NULL;
    imageAcquiredSemaphoreCreateInfo.flags = 0;

    res = VKCALL(vkCreateSemaphore(info.device, &imageAcquiredSemaphoreCreateInfo, NULL, &imageAcquiredSemaphore));
    assert(res == VK_SUCCESS);

    // Get the index of the next available swapchain image:
    res = VKCALL(vkAcquireNextImageKHR(info.device, info.swap_chain, UINT64_MAX, imageAcquiredSemaphore, VK_NULL_HANDLE,
                                &info.current_buffer));
    // TODO: Deal with the VK_SUBOPTIMAL_KHR and VK_ERROR_OUT_OF_DATE_KHR
    // return codes
    assert(res == VK_SUCCESS);

    VkRenderPassBeginInfo rp_begin;
    rp_begin.sType = VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO;
    rp_begin.pNext = NULL;
    rp_begin.renderPass = info.render_pass;
    rp_begin.framebuffer = info.framebuffers[info.current_buffer];
    rp_begin.renderArea.offset.x = 0;
    rp_begin.renderArea.offset.y = 0;
    rp_begin.renderArea.extent.width = info.width;
    rp_begin.renderArea.extent.height = info.height;
    rp_begin.clearValueCount = 2;
    rp_begin.pClearValues = clear_values;

    vkCmdBeginRenderPass(info.cmd, &rp_begin, VK_SUBPASS_CONTENTS_INLINE);

    vkCmdBindPipeline(info.cmd, VK_PIPELINE_BIND_POINT_GRAPHICS, info.pipeline);
    vkCmdBindDescriptorSets(info.cmd, VK_PIPELINE_BIND_POINT_GRAPHICS, info.pipeline_layout, 0, NUM_DESCRIPTOR_SETS,
                            info.desc_set.data(), 0, NULL);

    const VkDeviceSize offsets[1] = {0};
    vkCmdBindVertexBuffers(info.cmd, 0, 1, &info.vertex_buffer.buf, offsets);

    vklog("init_viewports()\n");
    init_viewports(info);
    vklog("init_scissors()\n");
    init_scissors(info);

    vkCmdDraw(info.cmd, 12 * 3, 1, 0, 0);
    vkCmdEndRenderPass(info.cmd);
    res = VKCALL(vkEndCommandBuffer(info.cmd));
    const VkCommandBuffer cmd_bufs[] = {info.cmd};
    VkFenceCreateInfo fenceInfo;
    VkFence drawFence;
    fenceInfo.sType = VK_STRUCTURE_TYPE_FENCE_CREATE_INFO;
    fenceInfo.pNext = NULL;
    fenceInfo.flags = 0;
    vkCreateFence(info.device, &fenceInfo, NULL, &drawFence);

    VkPipelineStageFlags pipe_stage_flags = VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT;
    VkSubmitInfo submit_info[1] = {};
    submit_info[0].pNext = NULL;
    submit_info[0].sType = VK_STRUCTURE_TYPE_SUBMIT_INFO;
    submit_info[0].waitSemaphoreCount = 1;
    submit_info[0].pWaitSemaphores = &imageAcquiredSemaphore;
    submit_info[0].pWaitDstStageMask = &pipe_stage_flags;
    submit_info[0].commandBufferCount = 1;
    submit_info[0].pCommandBuffers = cmd_bufs;
    submit_info[0].signalSemaphoreCount = 0;
    submit_info[0].pSignalSemaphores = NULL;

    /* Queue the command buffer for execution */
    res = VKCALL(vkQueueSubmit(info.graphics_queue, 1, submit_info, drawFence));
    assert(res == VK_SUCCESS);

    /* Now present the image in the window */

    VkPresentInfoKHR present;
    present.sType = VK_STRUCTURE_TYPE_PRESENT_INFO_KHR;
    present.pNext = NULL;
    present.swapchainCount = 1;
    present.pSwapchains = &info.swap_chain;
    present.pImageIndices = &info.current_buffer;
    present.pWaitSemaphores = NULL;
    present.waitSemaphoreCount = 0;
    present.pResults = NULL;

    /* Make sure command buffer is finished before presenting */
    do {
        res = VKCALL(vkWaitForFences(info.device, 1, &drawFence, VK_TRUE, FENCE_TIMEOUT));
    } while (res == VK_TIMEOUT);

    assert(res == VK_SUCCESS);
    res = VKCALL(vkQueuePresentKHR(info.present_queue, &present));
    assert(res == VK_SUCCESS);

    //wait_seconds(2);
    /* VULKAN_KEY_END */
    //if (info.save_images) write_ppm(info, "15-draw_cube");
    // Hugues: always write file. NB: write_ppm() adds the ".ppm" suffix to filename.
    vklog("write_ppm()\n");
    write_ppm(info, "/sdcard/graphicsfuzz/image");

    vklog("vkDestroySemaphore()\n");
    vkDestroySemaphore(info.device, imageAcquiredSemaphore, NULL);
    vklog("vkDestroyFence()\n");
    vkDestroyFence(info.device, drawFence, NULL);
    vklog("destroy_pipeline()\n");
    destroy_pipeline(info);
    vklog("destroy_pipeline_cache()\n");
    destroy_pipeline_cache(info);
    vklog("destroy_descriptor_pool()\n");
    destroy_descriptor_pool(info);
    vklog("destroy_vertex_buffer()\n");
    destroy_vertex_buffer(info);
    vklog("destroy_framebuffers()\n");
    destroy_framebuffers(info);
    vklog("destroy_shaders()\n");
    destroy_shaders(info);
    vklog("destroy_renderpass()\n");
    destroy_renderpass(info);
    vklog("destroy_descriptor_and_pipeline_layouts()\n");
    destroy_descriptor_and_pipeline_layouts(info);
    vklog("destroy_uniform_buffer()\n");
    destroy_uniform_buffer(info);
    vklog("destroy_depth_buffer()\n");
    destroy_depth_buffer(info);
    vklog("destroy_swap_chain()\n");
    destroy_swap_chain(info);
    vklog("destroy_command_buffer()\n");
    destroy_command_buffer(info);
    vklog("destroy_command_pool()\n");
    destroy_command_pool(info);
    vklog("destroy_device()\n");
    destroy_device(info);
    vklog("destroy_window()\n");
    destroy_window(info);
    vklog("destroy_instance()\n");
    destroy_instance(info);

    // Hugues: Android does not offer a good way to detect when an app terminate. We resort to
    // writing a DONE file, and the host will monitor the creation of that file.
    writeDoneFile();

    // Hugues: kill ourselves? I am not sure this works.
    requestAppFinish();

    return 0;
}
