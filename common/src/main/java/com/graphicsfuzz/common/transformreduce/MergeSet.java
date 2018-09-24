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

package com.graphicsfuzz.common.transformreduce;

import com.graphicsfuzz.common.ast.type.BasicType;
import com.graphicsfuzz.common.ast.type.Type;
import com.graphicsfuzz.common.typing.ScopeEntry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class MergeSet {

  public static final String GLF_MERGED_PREFIX = "GLF_merged";
  private final BasicType elementType;

  private final List<ScopeEntry> entries;

  private static final int MAX_WIDTH = 4;

  public MergeSet(ScopeEntry entry) {
    this.elementType = ((BasicType) entry.getType().getWithoutQualifiers()).getElementType();
    this.entries = new ArrayList<>();
    entries.add(entry);
  }

  public boolean canAccept(ScopeEntry entry) {
    BasicType basicType = (BasicType) entry.getType().getWithoutQualifiers();
    if (!(basicType.getElementType().equals(elementType))) {
      return false;
    }
    if (entries.stream()
        .anyMatch(x -> x.getVariablesDeclaration() == entry.getVariablesDeclaration())) {
      return false;
    }
    return currentWidth() + basicType.getNumElements() <= MAX_WIDTH;
  }

  private int currentWidth() {
    return entries.stream()
        .map(this::getWidth).reduce(0, (first, second) -> first + second);
  }

  private int getWidth(ScopeEntry entry) {
    return ((BasicType) entry.getType().getWithoutQualifiers()).getNumElements();
  }

  public void add(ScopeEntry entry) {
    assert canAccept(entry);
    entries.add(entry);
  }

  @Override
  public String toString() {
    return entries.stream().map(item -> item.getVariableDeclInfo().getName())
        .collect(Collectors.toList()).toString();
  }

  public int getNumVars() {
    return entries.size();
  }

  public Type getMergedType() {
    return BasicType.makeVectorType(elementType, currentWidth());
  }

  public String getMergedName() {
    final List<MergedVariablesComponentData> mergedVariablesComponentDataList
        = new ArrayList<>();
    int currentOffset = 0;
    for (ScopeEntry entry : entries) {
      mergedVariablesComponentDataList.add(
          new MergedVariablesComponentData(currentOffset,
              getWidth(entry),
              entry.getVariableDeclInfo().getName()));
      currentOffset += getWidth(entry);
    }
    return getMergedName(mergedVariablesComponentDataList);
  }

  /**
   * <p>Produces a mangled name for the mergeset that encodes the widths of each
   * component, and their names.</p>
   *
   * <p>The format is as follows:</p>
   *
   * <p>GLF_merged{n}_{o1}_{w1}_{s1}_{o2}_{w2}_{s2}_..._{on}_{wn}_{sn}{name1}{name2}...{namen}</p>
   *
   * <p>where:</p>
   *
   * <p>
   * - GLF_merged is a prefix
   * - {n} denotes the number of variables in the merge set
   * - {oi} is the offset into the packed vector at which variable i starts
   * - {wi} is the vector width of variable i
   * - {si} is the length of the name of variable i
   * - {namei} is the name of variable i, and must have length si
   * </p>
   *
   * <p>Note that variables are, for now, tightly packed into vectors.  The offsets are useful
   * during the reduction process.</p>
   *
   * <p>TODO: in future, we could try fuzzing with offsets, inserting some padding into vectors</p>
   *
   * @return Mangled name for the merge set
   */
  private static String getMergedName(List<MergedVariablesComponentData> components) {
    return GLF_MERGED_PREFIX
        + components.size() + "_"
        + String.join("_", components.stream().map(component ->
            component.getOffset() + "_" + component.getWidth() + "_" + component.getName().length())
            .collect(Collectors.toList()))
        + String.join("", components.stream().map(component -> component.getName())
            .collect(Collectors.toList()));
  }

  public List<Integer> getIndices(String name) {
    int startIndex = 0;
    for (ScopeEntry entry : entries) {
      BasicType type = (BasicType) entry.getType().getWithoutQualifiers();
      if (entry.getVariableDeclInfo().getName().equals(name)) {
        List<Integer> result = new ArrayList<>();
        for (int i = 0; i < type.getNumElements(); i++) {
          result.add(startIndex + i);
        }
        return result;
      }
      startIndex += type.getNumElements();
    }
    throw new RuntimeException("Should be unreachable.");
  }

  public static boolean isMergedVariable(String name) {
    return name.startsWith(GLF_MERGED_PREFIX);
  }

  private static List<String> getComponentNames(String name) {
    return getComponentData(name).stream().map(item -> item.getName()).collect(Collectors.toList());
  }

  public static List<MergedVariablesComponentData> getComponentData(String name) {
    final int numComponents = getNumComponents(name);
    if (numComponents == 0) {
      return new ArrayList<>();
    }
    Matcher matcher = getMatcher(name);
    String componentData = matcher.group("componentData");
    assert componentData.startsWith("_");
    componentData = componentData.substring(1);
    List<Integer> componentIntegerData = Arrays.stream(componentData.split("_"))
        .map(item -> Integer.parseInt(item))
        .collect(Collectors.toList());
    String nameData = matcher.group("names");
    assert (componentIntegerData.size() >= numComponents * 3);
    List<MergedVariablesComponentData> result = new ArrayList<>();
    for (int i = 0; i < numComponents; i++) {
      int componentOffset = componentIntegerData.get(i * 3);
      int componentWidth = componentIntegerData.get(i * 3 + 1);
      int componentNameLength = componentIntegerData.get(i * 3 + 2);
      String componentName = nameData.substring(0, componentNameLength);
      result.add(new MergedVariablesComponentData(componentOffset, componentWidth, componentName));
      if (nameData.length() != componentNameLength) {
        nameData = nameData.substring(componentNameLength);
      }
    }
    return result;
  }

  private static int getNumComponents(String name) {
    assert isMergedVariable(name);
    Matcher matcher = getMatcher(name);
    return Integer.parseInt(matcher.group("numComponents"));
  }

  private static Matcher getMatcher(String name) {
    Matcher matcher = Pattern.compile(GLF_MERGED_PREFIX
        + "(?<numComponents>\\d+)(?<componentData>(?:_\\d+_\\d+_\\d+)*)(?<names>\\D.*)")
        .matcher(name);
    if (!matcher.find()) {
      throw new RuntimeException("Invalid merged variable name: " + name);
    }
    return matcher;
  }

  public static boolean isMergedVariableWithNamedComponent(String name, String componentName) {
    if (!isMergedVariable(name)) {
      return false;
    }
    return getComponentNames(name).contains(componentName);
  }

  public static String removeComponentFromName(String name, String componentName) {
    assert isMergedVariableWithNamedComponent(name, componentName);
    return getMergedName(
        getComponentData(name).stream().filter(item -> !item.getName().equals(componentName))
            .collect(Collectors.toList()));
  }

  public List<ScopeEntry> getIndidualScopeEntries() {
    return Collections.unmodifiableList(entries);
  }

}
