package com.graphicsfuzz.generator.tool;

import com.graphicsfuzz.common.util.IRandom;
import com.graphicsfuzz.common.util.PipelineInfo;
import com.graphicsfuzz.common.util.RandomWrapper;
import org.junit.Test;

import static org.junit.Assert.*;

public class PipelineInfoTest {

  IRandom generator = new RandomWrapper(0);

  @Test
  public void testReplaceRandomsInJson() throws Exception {

    final String floatJson = "{\n"
        + "  \"float\": {\n"
        + "    \"func\": \"glUniform1f\",\n"
        + "    \"args\": [\n"
        + "      \"rfloat(256.0)\"\n"
        + "    ]\n"
        + "  }\n"
        + "}\n";
    final String intJson = "{\n"
        + "  \"int\": {\n"
        + "    \"func\": \"glUniform1i\",\n"
        + "    \"args\": [\n"
        + "      \"rint(-256)\"\n"
        + "    ]\n"
        + "  }\n"
        + "}\n";
    final String uintJson = "{\n"
        + "  \"uint\": {\n"
        + "    \"func\": \"glUniform1ui\",\n"
        + "    \"args\": [\n"
        + "      \"rint(256)\"\n"
        + "    ]\n"
        + "  }\n"
        + "}\n";
    final String boolJson = "{\n"
        + "  \"bool\": {\n"
        + "    \"func\": \"glUniform1i\",\n"
        + "    \"args\": [\n"
        + "      \"rbool()\"\n"
        + "    ]\n"
        + "  }\n"
        + "}\n";
    final String computeJson = "{\n"
        + " \"$compute\": {\n"
        + "   \"num_groups\": [ 4, 1, 1 ],\n"
        + "   \"buffer\": {\n"
        + "     \"binding\": 0,\n"
        + "     \"fields\":\n"
        + "      [\n"
        + "        { \"type\": \"int\", \"data\": [ \"rint(256)\" ] }\n"
        + "      ]\n"
        + "    }\n"
        + "  }\n"
        + "}\n";

    final PipelineInfo floatPipeline = new PipelineInfo(floatJson);
    final PipelineInfo intPipeline = new PipelineInfo(intJson);
    final PipelineInfo uintPipeline = new PipelineInfo(uintJson);
    final PipelineInfo boolPipeline = new PipelineInfo(boolJson);
    final PipelineInfo computePipeline = new PipelineInfo(computeJson);

    assertTrue(floatPipeline.hasUniform("float"));
    assertTrue(intPipeline.hasUniform("int"));
    assertTrue(uintPipeline.hasUniform("uint"));
    assertTrue(boolPipeline.hasUniform("bool"));
    // due to isValidUniformName(), we can't check for the $compute element in the JSON directly.
    assertTrue(computePipeline.toString().contains("buffer"));

    floatPipeline.replaceRandomsInJson(generator);
    intPipeline.replaceRandomsInJson(generator);
    uintPipeline.replaceRandomsInJson(generator);
    boolPipeline.replaceRandomsInJson(generator);
    computePipeline.replaceRandomsInJson(generator);

    assertFalse(floatPipeline.toString().contains("rfloat"));
    assertFalse(intPipeline.toString().contains("rint"));
    assertFalse(uintPipeline.toString().contains("rint"));
    assertFalse(boolPipeline.toString().contains("rbool"));
    assertFalse(computePipeline.toString().contains("rint"));
  }
}
