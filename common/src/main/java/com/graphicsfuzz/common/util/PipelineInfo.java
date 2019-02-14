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
import com.graphicsfuzz.common.ast.type.Type;
import com.graphicsfuzz.util.Constants;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.ArrayList;
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

  public PipelineInfo(File file) throws FileNotFoundException {
    dictionary = new Gson().fromJson(new FileReader(file),
          JsonObject.class);
  }

  public PipelineInfo(String string) {
    dictionary = new Gson().fromJson(string,
        JsonObject.class);
  }

  /**
   * Records the type and values for a uniform.
   * @param name Name of the uniform.
   * @param basicType The base type of the uniform; e.g. float for a vec3, int for an ivec4.
   * @param arrayCount The number of array elements if this is a uniform array.
   * @param values A series of value of the base type with which the uniform should be populated.
   */
  public void addUniform(String name, BasicType basicType,
      Optional<Integer> arrayCount, List<? extends Number> values) {
    assert isLegalUniformName(name);
    JsonObject info = new JsonObject();
    info.addProperty("func", PipelineInfo.get(basicType, arrayCount.isPresent()));
    JsonArray jsonValues = new JsonArray();
    for (Number n : values) {
      jsonValues.add(n);
    }
    info.add("args", jsonValues);
    if (arrayCount.isPresent()) {
      info.addProperty("count", arrayCount.get());
    }
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
      Supplier<Integer> boolSupplier) {
    for (VariablesDeclaration vd : tu.getUniformDecls()) {
      final Type withoutQualifiers = vd.getBaseType().getWithoutQualifiers();
      if (!(withoutQualifiers instanceof BasicType)) {
        if (withoutQualifiers instanceof SamplerType) {
          // Need to work out how to do default initialization of samplers.
          // For now, just leave them.
          continue;
        }
        // We should be able to deal with other types as they crop up.
        throw new RuntimeException("Not able to deal with type "
            + withoutQualifiers);
      }
      final BasicType basicType = (BasicType) withoutQualifiers;
      for (VariableDeclInfo vdi : vd.getDeclInfos()) {
        if (hasUniform(vdi.getName())) {
          continue;
        }
        int arrayLength;
        if (vdi.hasArrayInfo()) {
          arrayLength = vdi.getArrayInfo().getSize();
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
    }
  }

  private Optional<Integer> maybeGetArrayCount(VariableDeclInfo vdi) {
    if (vdi.hasArrayInfo()) {
      return Optional.of(vdi.getArrayInfo().getSize());
    }
    return Optional.empty();
  }

  public void zeroUnsetUniforms(TranslationUnit tu) {
    // Find all uniforms not yet set, and make them zero
    final Supplier<Float> floatSupplier = () -> new Float(0.0);
    final Supplier<Integer> intSupplier = () -> new Integer(0);
    final Supplier<Integer> uintSupplier = () -> new Integer(0);
    final Supplier<Integer> boolSupplier = () -> new Integer(0);
    setUniforms(tu, floatSupplier, intSupplier, uintSupplier, boolSupplier);
  }

  private static String get(BasicType type, boolean isArray) {
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

  public void addUniformBinding(String uniformName, int number) {
    assert hasUniform(uniformName);
    dictionary.getAsJsonObject(uniformName).addProperty("binding", number);
  }

  public void removeUniformBinding(String uniformName) {
    assert hasBinding(uniformName);
    dictionary.getAsJsonObject(uniformName).remove("binding");
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

  public List<Number> getArgs(String uniformName) {
    final List<Number> result = new ArrayList<>();
    final JsonArray args = lookupUniform(uniformName).get("args")
          .getAsJsonArray();
    for (int i = 0; i < args.size(); i++) {
      result.add(args.get(i).getAsNumber());
    }
    return result;
  }

  public boolean hasBinding(String uniformName) {
    return lookupUniform(uniformName).has("binding");
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

  private JsonObject lookupUniform(String uniformName) {
    assert isLegalUniformName(uniformName);
    return (JsonObject) dictionary.get(uniformName);
  }

  @Override
  public PipelineInfo clone() {
    return new PipelineInfo(toString());
  }

}
