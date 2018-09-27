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
import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.ast.decl.VariableDeclInfo;
import com.graphicsfuzz.common.ast.decl.VariablesDeclaration;
import com.graphicsfuzz.common.ast.type.BasicType;
import com.graphicsfuzz.common.ast.type.SamplerType;
import com.graphicsfuzz.common.ast.type.Type;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public final class UniformsInfo {

  private final JsonObject uniformsInfo;

  private UniformsInfo(JsonObject uniformsInfo) {
    this.uniformsInfo = uniformsInfo;
  }

  public UniformsInfo() {
    this(new JsonObject());
  }

  public UniformsInfo(File file) throws FileNotFoundException {
    uniformsInfo = new Gson().fromJson(new FileReader(file),
          JsonObject.class);
  }

  public UniformsInfo(String string) {
    uniformsInfo = new Gson().fromJson(string,
        JsonObject.class);
  }

  public void addUniform(String name, BasicType basicType,
      Optional<Integer> arrayCount, List<? extends Number> values) {
    JsonObject info = new JsonObject();
    info.addProperty("func", UniformsInfo.get(basicType, arrayCount.isPresent()));
    JsonArray jsonValues = new JsonArray();
    for (Number n : values) {
      jsonValues.add(n);
    }
    info.add("args", jsonValues);
    if (arrayCount.isPresent()) {
      info.addProperty("count", arrayCount.get());
    }
    uniformsInfo.add(name, info);
  }

  public boolean containsKey(String key) {
    return uniformsInfo.has(key);
  }

  @Override
  public String toString() {
    return new GsonBuilder().setPrettyPrinting().create()
        .toJson(uniformsInfo);
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
      final String typeName = basicType.toString();
      for (VariableDeclInfo vdi : vd.getDeclInfos()) {
        if (containsKey(vdi.getName())) {
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
      throw new RuntimeException("Not dealing with matrices yet.");
    }

    result += type.getNumElements();

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

    if (isArray) {
      result += "v";
    }

    return result;
  }

  public void addBinding(String uniformName, int number) {
    assert containsKey(uniformName);
    uniformsInfo.getAsJsonObject(uniformName).addProperty("binding", number);
  }

  public void removeBinding(String uniformName) {
    assert hasBinding(uniformName);
    uniformsInfo.getAsJsonObject(uniformName).remove("binding");
  }

  public List<String> getUniformNames() {
    return uniformsInfo.entrySet()
        .stream().map(item -> item.getKey()).collect(Collectors.toList());
  }

  public int getNumUniforms() {
    return uniformsInfo.entrySet().size();
  }

  public void removeUniform(String name) {
    uniformsInfo.remove(name);
  }

  public UniformsInfo renameUniforms(Map<String, String> uniformMapping) {
    final JsonObject newUniformsInfo = new JsonObject();
    for (String name : getUniformNames()) {
      if (uniformMapping.containsKey(name)) {
        newUniformsInfo.add(uniformMapping.get(name), uniformsInfo.get(name));
      } else {
        newUniformsInfo.add(name, uniformsInfo.get(name));
      }
    }
    return new UniformsInfo(newUniformsInfo);
  }

  public List<Number> getArgs(String name) {
    final List<Number> result = new ArrayList<>();
    final JsonArray args = lookupUniform(name).get("args")
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

  private JsonObject lookupUniform(String uniformName) {
    return (JsonObject) uniformsInfo.get(uniformName);
  }

}
