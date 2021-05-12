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

package com.graphicsfuzz.common.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.ast.decl.VariableDeclInfo;
import com.graphicsfuzz.common.ast.decl.VariablesDeclaration;
import com.graphicsfuzz.common.ast.type.BasicType;
import com.graphicsfuzz.common.ast.type.SamplerType;
import com.graphicsfuzz.common.ast.type.StructDefinitionType;
import com.graphicsfuzz.common.ast.type.StructNameType;
import com.graphicsfuzz.common.ast.type.Type;
import com.graphicsfuzz.util.Constants;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public final class PipelineInfo {

  private final JsonObject dictionary;

  private PipelineInfo(JsonObject dictionary) {
    this.dictionary = dictionary;
  }

  public PipelineInfo() {
    this(new JsonObject());
  }

  public PipelineInfo(File file) throws IOException {
    try (FileReader fr = new FileReader(file)) {
      dictionary = new Gson().fromJson(fr,
          JsonObject.class);
    }
  }

  public PipelineInfo(String string) {
    dictionary = new Gson().fromJson(string,
        JsonObject.class);
  }

  /**
   * Determines whether a uniform with the same name and associated function exists, throwing an
   * exception if there is a name match but a function clash.
   * @param name The name of a uniform, whose existence is to be checked.
   * @param func The function that an existing uniform with this name should have if it does exist.
   * @return True if and only if a uniform with matching name and function exists.
   * @throws RuntimeException if there is a name match but a function clash - this situation should
   *         never arise.
   */
  private boolean uniformAlreadyExists(String name, String func) {
    if (!dictionary.has(name)) {
      return false;
    }
    if (!((JsonObject)dictionary.get(name)).get("func").getAsString().equals(func)) {
      // Variable of this name already exists, but has different type
      throw new RuntimeException("Uniform redefined as a different type");
    }
    return true;
  }

  /**
   * Records the type and values for a non-sampler uniform.
   * @param name Name of the uniform.
   * @param basicType The base type of the uniform; e.g. float for a vec3, int for an ivec4.
   * @param arrayCount The number of array elements if this is a uniform array.
   * @param values A series of value of the base type with which the uniform should be populated.
   */
  public void addUniform(String name, BasicType basicType,
                         Optional<Integer> arrayCount, List<? extends Number> values) {
    assert isLegalUniformName(name);
    if (uniformAlreadyExists(name, PipelineInfo.getGlUniformFunctionName(basicType,
        arrayCount.isPresent()))) {
      // A uniform already exists and has the same type.  We don't need to do anything.
      return;
    }
    // Add uniform to dictionary
    JsonObject info = new JsonObject();
    info.addProperty("func", PipelineInfo.getGlUniformFunctionName(basicType,
        arrayCount.isPresent()));
    JsonArray jsonValues = new JsonArray();
    for (Number n : values) {
      jsonValues.add(n);
    }
    info.add("args", jsonValues);
    arrayCount.ifPresent(integer -> info.addProperty("count", integer));
    dictionary.add(name, info);
  }

  /**
   * Records the type and value for a sampler uniform.
   * @param name Name of the uniform.
   * @param samplerType The sampler type for the uniform.
   * @param value A GraphicsFuzz built-in texture that the sampler will sample from.
   */
  public void addUniform(String name, SamplerType samplerType, BuiltInTexture value) {
    assert isLegalUniformName(name);
    if (uniformAlreadyExists(name, samplerType.toString())) {
      // A uniform already exists and has the same type.  We don't need to do anything.
      return;
    }
    // Add uniform to dictionary
    JsonObject info = new JsonObject();
    info.addProperty("func", samplerType.toString());
    info.addProperty("texture", value.toString());
    dictionary.add(name, info);
  }

  private static boolean isLegalUniformName(String name) {
    if (name.length() == 0) {
      return false;
    }
    if (!(Character.isAlphabetic(name.charAt(0)) || name.charAt(0) == '_')) {
      return false;
    }
    for (char c : name.toCharArray()) {
      if (!(Character.isAlphabetic(c) || Character.isDigit(c) || c == '_')) {
        return false;
      }
    }
    return true;
  }

  public boolean hasUniform(String uniformName) {
    assert isLegalUniformName(uniformName);
    return dictionary.has(uniformName);
  }

  @Override
  public String toString() {
    return new GsonBuilder().setPrettyPrinting().create()
        .toJson(dictionary);
  }

  public void setUniforms(TranslationUnit tu,
      Supplier<Float> floatSupplier, Supplier<Integer> intSupplier,
      Supplier<Integer> uintSupplier,
      Supplier<Integer> boolSupplier,
      Supplier<BuiltInTexture> textureSupplier) {
    for (VariablesDeclaration vd : tu.getUniformDecls()) {
      final Type withoutQualifiers = vd.getBaseType().getWithoutQualifiers();
      if (withoutQualifiers instanceof StructNameType
          || withoutQualifiers instanceof StructDefinitionType) {
        // TODO(414) Need to work out how to do default initialization of structs.
        // For now, just leave them.
        continue;
      }
      if (withoutQualifiers instanceof BasicType) {
        final BasicType basicType = (BasicType) withoutQualifiers;
        for (VariableDeclInfo vdi : vd.getDeclInfos()) {
          if (hasUniform(vdi.getName())) {
            continue;
          }
          int arrayLength;
          if (vdi.hasArrayInfo()) {
            if (vdi.getArrayInfo().getDimensionality() != 1) {
              throw new RuntimeException("Unsupported: multi-dimensional array uniforms.");
            }
            arrayLength = vdi.getArrayInfo().getConstantSize(0);
          } else {
            arrayLength = 1;
          }
          List<Number> values = new ArrayList<>();
          for (int i = 0; i < basicType.getNumElements() * arrayLength; i++) {
            if (basicType.getElementType() == BasicType.FLOAT) {
              values.add(floatSupplier.get());
            } else if (basicType.getElementType() == BasicType.INT) {
              values.add(intSupplier.get());
            } else if (basicType.getElementType() == BasicType.UINT) {
              values.add(uintSupplier.get());
            } else {
              assert basicType.getElementType() == BasicType.BOOL;
              values.add(boolSupplier.get());
            }
          }
          addUniform(vdi.getName(), basicType, maybeGetArrayCount(vdi), values);
        }
      } else if (withoutQualifiers == SamplerType.SAMPLER2D) {
        // TODO(https://github.com/google/graphicsfuzz/issues/1075): This code should be generalised
        //  if we do support more sampler types.  For that reason we copy SAMPLER2D into a variable,
        //  to future-proof for this variable not necessarily always being SAMPLER2D.
        final SamplerType samplerType = SamplerType.SAMPLER2D;
        for (VariableDeclInfo vdi : vd.getDeclInfos()) {
          if (hasUniform(vdi.getName())) {
            continue;
          }
          if (vdi.hasArrayInfo()) {
            throw new RuntimeException("Arrays of samplers are not currently supported.");
          }
          addUniform(vdi.getName(), samplerType, textureSupplier.get());
        }
      } else {
        // We should be able to deal with other types as they crop up.
        throw new RuntimeException("Not able to deal with type "
            + withoutQualifiers);
      }
    }
  }

  private Optional<Integer> maybeGetArrayCount(VariableDeclInfo vdi) {
    if (vdi.hasArrayInfo()) {
      if (vdi.getArrayInfo().getDimensionality() != 1) {
        throw new RuntimeException("Unsupported: multi-dimensional array uniforms.");
      }
      return Optional.of(vdi.getArrayInfo().getConstantSize(0));
    }
    return Optional.empty();
  }

  public void zeroUnsetUniforms(TranslationUnit tu) {
    // Find all uniforms not yet set, and make them zero
    final Supplier<Float> floatSupplier = () -> 0.0f;
    final Supplier<Integer> intSupplier = () -> 0;
    final Supplier<Integer> uintSupplier = () -> 0;
    final Supplier<Integer> boolSupplier = () -> 0;
    final Supplier<BuiltInTexture> textureSupplier = () -> BuiltInTexture.DEFAULT;
    setUniforms(tu, floatSupplier, intSupplier, uintSupplier, boolSupplier, textureSupplier);
  }

  private static String getGlUniformFunctionName(BasicType type, boolean isArray) {
    String result = "glUniform";

    if (type.isMatrix()) {
      result += "Matrix";
      if (type == BasicType.MAT2X2) {
        result += "2";
      } else if (type == BasicType.MAT2X3) {
        result += "2x3";
      } else if (type == BasicType.MAT2X4) {
        result += "2x4";
      } else if (type == BasicType.MAT3X2) {
        result += "3x2";
      } else if (type == BasicType.MAT3X3) {
        result += "3";
      } else if (type == BasicType.MAT3X4) {
        result += "3x4";
      } else if (type == BasicType.MAT4X2) {
        result += "4x2";
      } else if (type == BasicType.MAT4X3) {
        result += "4x3";
      } else if (type == BasicType.MAT4X4) {
        result += "4";
      }
    } else {
      result += type.getNumElements();
    }

    if (type.getElementType() == BasicType.FLOAT) {
      result += "f";
    } else if (type.getElementType() == BasicType.INT) {
      result += "i";
    } else if (type.getElementType() == BasicType.UINT) {
      result += "ui";
    } else {
      assert type.getElementType() == BasicType.BOOL;
      result += "i";
    }

    if (type.isMatrix() || isArray) {
      result += "v";
    }

    return result;
  }

  /**
   * Assigns an uniform binding, or alternatively sets it as a push constant.
   * @param uniformName Name of the uniform.
   * @param pushConstant Boolean for whether this is a push constant or not.
   * @param number Binding number for the uniform. Ignored in case of push constants.
   */
  public void addUniformBinding(String uniformName, boolean pushConstant, int number) {
    assert hasUniform(uniformName);
    if (pushConstant) {
      dictionary.getAsJsonObject(uniformName).addProperty("push_constant", true);
    } else {
      dictionary.getAsJsonObject(uniformName).addProperty("binding", number);
    }
  }

  public void removeUniformBinding(String uniformName) {
    assert hasBindingOrIsPushConstant(uniformName);
    if (isPushConstant(uniformName)) {
      assert !hasBinding(uniformName);
      dictionary.getAsJsonObject(uniformName).remove("push_constant");
    } else {
      dictionary.getAsJsonObject(uniformName).remove("binding");
    }
  }

  /**
   * Inserts a new value into an existing uniform array.
   * @param uniformName Name of the uniform.
   * @param value A Number to be inserted.
   * @return The index of the new value in the uniform array.
   */
  public int appendValueToUniform(String uniformName, Number value) {

    if (!dictionary.has(uniformName)) {
      throw new IllegalArgumentException("Uniform declaration not found.");
    }

    dictionary.getAsJsonObject(uniformName).get("args").getAsJsonArray().add(value);

    return dictionary.getAsJsonObject(uniformName).get("args").getAsJsonArray().size() - 1;
  }

  public List<String> getUniformNames() {
    return dictionary.entrySet()
        .stream()
        .map(item -> item.getKey())
        .filter(PipelineInfo::isLegalUniformName)
        .sorted()
        .collect(Collectors.toList());
  }

  public int getNumUniforms() {
    return getUniformNames().size();
  }

  public void removeUniform(String uniformName) {
    assert isLegalUniformName(uniformName);
    dictionary.remove(uniformName);
  }

  public PipelineInfo renameUniforms(Map<String, String> uniformMapping) {
    final JsonObject newUniformsInfo = new JsonObject();
    for (String name : getUniformNames()) {
      newUniformsInfo.add(uniformMapping.getOrDefault(name, name), dictionary.get(name));
    }
    return new PipelineInfo(newUniformsInfo);
  }

  /**
   * Returns the contents of the numeric arguments associated with "uniformName" as strings.
   * Strings are used in order to insulate against internal details of the way numbers are
   * represented by the underlying JSON library.
   * @param uniformName The name of the uniform whose numeric arguments are to be retrieved.
   * @return The numeric arguments associated with the uniforms, as strings.
   */
  public List<String> getArgs(String uniformName) {
    final List<String> result = new ArrayList<>();
    final JsonArray args = lookupUniform(uniformName).get("args")
          .getAsJsonArray();
    for (int i = 0; i < args.size(); i++) {
      result.add(args.get(i).toString());
    }
    return result;
  }

  /**
   * Requires that a uniform of the given name exists, and returns true if and only if it is has a
   * descriptor set binding or is a push constant.
   * @param uniformName The name of a uniform that must already exist.
   * @return True if and only if the uniform has a descriptor set binding or is a push constant.
   */
  public boolean hasBindingOrIsPushConstant(String uniformName) {
    return hasBinding(uniformName) || isPushConstant(uniformName);
  }

  /**
   * Requires that a uniform of the given name exists, and returns true if and only if it has a
   * descriptor set binding.
   * @param uniformName The name of a uniform that must already exist.
   * @return True if and only if the uniform has a descriptor set binding.
   */
  public boolean hasBinding(String uniformName) {
    return lookupUniform(uniformName).has("binding");
  }

  /**
   * Requires that a uniform of the given name exists, and returns true if and only if it is a
   * push constant.
   * @param uniformName The name of a uniform that must already exist.
   * @return True if and only if the uniform is a push constant.
   */
  public boolean isPushConstant(String uniformName) {
    return lookupUniform(uniformName).has("push_constant");
  }

  /**
   * Requires that a uniform of the given name exists, and returns true if and only if it is a
   * sampler.
   * @param uniformName The name of a uniform that must already exist.
   * @return True if and only if the uniform is a sampler.
   */
  public boolean isSampler(String uniformName) {
    // TODO(https://github.com/google/graphicsfuzz/issues/1075): Ultimately we should support more
    //  sampler types.
    final List<String> supportedSamplers = Collections.singletonList(SamplerType
        .SAMPLER2D.toString());
    return supportedSamplers.contains(lookupUniform(uniformName).get("func").getAsString());
  }

  public int getBinding(String uniformName) {
    assert hasBinding(uniformName);
    return lookupUniform(uniformName).get("binding").getAsInt();
  }

  public void addComputeInfo(int numGroupsX, int numGroupsY, int numGroupsZ,
                             int ssboBinding,
                             List<SsboFieldData> ssboFields) {
    assert !dictionary.has(Constants.COMPUTE_DATA_KEY);
    final JsonObject buffer = new JsonObject();
    buffer.addProperty(Constants.COMPUTE_BINDING, ssboBinding);
    final JsonArray fields = new JsonArray();
    for (SsboFieldData fieldData : ssboFields) {
      final JsonObject fieldInfo = new JsonObject();
      fieldInfo.addProperty("type", fieldData.getBaseType().toString());
      final JsonArray data = new JsonArray();
      for (Number number : fieldData.getData()) {
        data.add(new JsonPrimitive(number));
      }
      fieldInfo.add("data", data);
      fields.add(fieldInfo);
    }
    buffer.add(Constants.COMPUTE_FIELDS, fields);
    final JsonObject computeData = new JsonObject();
    final JsonArray numGroups = new JsonArray();
    numGroups.add(numGroupsX);
    numGroups.add(numGroupsY);
    numGroups.add(numGroupsZ);
    computeData.add("num_groups", numGroups);
    computeData.add("buffer", buffer);
    dictionary.add(Constants.COMPUTE_DATA_KEY, computeData);
  }

  public boolean hasGridInfo() {
    return dictionary.has(Constants.GRID_DATA_KEY);
  }

  public int getGridColumns() {
    assert hasGridInfo();
    return ((JsonArray)((JsonObject)dictionary.get(Constants.GRID_DATA_KEY)).get("dimensions"))
        .get(0).getAsInt();
  }

  public int getGridRows() {
    assert hasGridInfo();
    return ((JsonArray)((JsonObject)dictionary.get(Constants.GRID_DATA_KEY)).get("dimensions"))
        .get(1).getAsInt();
  }

  public void addGridInfo(int columns, int rows) {
    assert !hasGridInfo();
    final JsonObject gridDimensions = new JsonObject();
    final JsonArray dimensions = new JsonArray();
    dimensions.add(columns);
    dimensions.add(rows);
    gridDimensions.add("dimensions", dimensions);
    dictionary.add(Constants.GRID_DATA_KEY, gridDimensions);
  }

  /**
   * Returns the next unused binding number.
   * @return The next unused binding number.
   */
  public int getUnusedBindingNumber() {
    List<Integer> bindings =
        dictionary.entrySet().stream()
            .filter(item -> item.getValue().getAsJsonObject().has("binding"))
            .map(item -> item.getValue().getAsJsonObject()
                .get("binding").getAsInt()).collect(Collectors.toList());

    for (int number = 0; number < Integer.MAX_VALUE; number++) {
      if (!bindings.contains(number)) {
        return number;
      }
    }

    throw new RuntimeException("Unreachable code. MAX_VALUE should never been used in bindings.");
  }

  public void addSamplerInfo(String name, String samplerType, String textureName) {
    assert isLegalUniformName(name);
    if (dictionary.has(name)) {
      if (!((JsonObject)dictionary.get(name)).get("func").getAsString().equals(samplerType)) {
        // A variable of this name already exists, but has different type
        throw new RuntimeException("Sampler redefined as a different type");
      }
      // If name and type matches, we don't need to do anything.
    } else {
      // Add sampler to dictionary
      // "foo": { "func": "sampler2D", "texture": "SOME_STRING" }
      JsonObject info = new JsonObject();
      info.addProperty("func", samplerType);
      info.addProperty("texture", textureName);
      dictionary.add(name, info);
    }
  }


  private JsonObject lookupUniform(String uniformName) {
    assert isLegalUniformName(uniformName);
    return (JsonObject) dictionary.get(uniformName);
  }

  @Override
  public PipelineInfo clone() {
    return new PipelineInfo(toString());
  }

}
