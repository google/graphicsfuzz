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

package com.graphicsfuzz.common.ast.type;

import com.graphicsfuzz.common.ast.expr.Expr;
import com.graphicsfuzz.common.ast.expr.TypeConstructorExpr;
import com.graphicsfuzz.common.ast.visitors.IAstVisitor;
import com.graphicsfuzz.common.typing.Scope;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

public class StructDefinitionType extends UnqualifiedType {

  private Optional<StructNameType> structNameType;
  private final List<String> fieldNames;
  private final List<Type> fieldTypes;

  public StructDefinitionType(Optional<StructNameType> structNameType,
                               List<String> fieldNames,
                               List<Type> fieldTypes) {
    assert fieldNames.size() == fieldTypes.size();
    this.structNameType = structNameType;
    this.fieldNames = new ArrayList<>();
    this.fieldNames.addAll(fieldNames);
    this.fieldTypes = new ArrayList<>();
    this.fieldTypes.addAll(fieldTypes);
  }

  public StructDefinitionType(StructNameType structNameType,
                              List<String> fieldNames,
                              List<Type> fieldTypes) {
    this(Optional.of(structNameType), fieldNames, fieldTypes);
  }

  public boolean hasStructNameType() {
    return structNameType.isPresent();
  }

  public StructNameType getStructNameType() {
    assert hasStructNameType();
    return structNameType.get();
  }

  public List<String> getFieldNames() {
    return Collections.unmodifiableList(fieldNames);
  }

  public List<Type> getFieldTypes() {
    return Collections.unmodifiableList(fieldTypes);
  }

  /**
   * Gives the field type corresponding to a field name.
   *
   * @param name The field name of interest
   * @return The corresponding field type
   */
  public Type getFieldType(String name) {
    for (int i = 0; i < fieldNames.size(); i++) {
      if (fieldNames.get(i).equals(name)) {
        return fieldTypes.get(i);
      }
    }
    throw new RuntimeException("Unknown field " + name);
  }

  public Type getFieldType(int index) {
    return fieldTypes.get(index);
  }

  public String getFieldName(int index) {
    return fieldNames.get(index);
  }

  public int getNumFields() {
    assert fieldNames.size() == fieldTypes.size();
    return fieldNames.size();
  }

  /**
   * Inserts a field at the given index, moving all fields at that index and beyond down by one.
   *
   * @param index The index at which to insert
   * @param name Name of the new field
   * @param type Type of the new field
   */
  public void insertField(int index, String name, Type type) {
    if (index < 0 || index > fieldNames.size()) {
      throw new IndexOutOfBoundsException("Cannot insert field at index " + index
          + " when struct has " + fieldNames.size() + " fields");
    }
    fieldNames.add(index, name);
    fieldTypes.add(index, type);
  }

  public void removeField(String fieldToRemove) {
    if (!fieldNames.contains(fieldToRemove)) {
      throw new IllegalArgumentException(unknownFieldMessage(fieldToRemove));
    }
    final int index = fieldNames.indexOf(fieldToRemove);
    fieldNames.remove(index);
    fieldTypes.remove(index);
  }

  public void setFieldName(int index, String fieldName) {
    fieldNames.set(index, fieldName);
  }

  public void setFieldType(int index, Type type) {
    fieldTypes.set(index, type);
  }

  public int getFieldIndex(String fieldToRemove) {
    if (!fieldNames.contains(fieldToRemove)) {
      throw new IllegalArgumentException(unknownFieldMessage(fieldToRemove));
    }
    return fieldNames.indexOf(fieldToRemove);
  }

  public boolean hasField(String nameToCheck) {
    return fieldNames.contains(nameToCheck);
  }

  private String unknownFieldMessage(String fieldName) {
    return "Field " + fieldName + " not found in struct"
        + (hasStructNameType() ? " " + structNameType : "") + ".";
  }

  @Override
  public void accept(IAstVisitor visitor) {
    visitor.visitStructDefinitionType(this);
  }

  @Override
  public StructDefinitionType clone() {
    return new StructDefinitionType(structNameType.map(StructNameType::clone),
        fieldNames,
        fieldTypes.stream().map(item -> item.clone()).collect(Collectors.toList()));
  }

  @Override
  public boolean hasCanonicalConstant(Scope scope) {
    // To give a constant for a struct, the struct needs to have a name and it must be possible
    // to make a constant for every field of the struct.
    return hasStructNameType() && fieldTypes
        .stream()
        .allMatch(item -> item.hasCanonicalConstant(scope));
  }

  @Override
  public Expr getCanonicalConstant(Scope scope) {
    return new TypeConstructorExpr(getStructNameType().getName(),
        fieldTypes.stream()
            .map(item -> item.getCanonicalConstant(scope))
            .collect(Collectors.toList()));
  }

  @Override
  public boolean equals(Object that) {
    if (this == that) {
      return true;
    }
    if (!(that instanceof StructDefinitionType)) {
      return false;
    }
    final StructDefinitionType thatStructDefinitionType = (StructDefinitionType) that;
    // The struct definition types must either both have equal struct name types, or both not have
    // struct name types.
    if (!this.structNameType.equals(thatStructDefinitionType.structNameType)) {
      return false;
    }
    if (hasStructNameType()) {
      // Coherence check: in the event that the types have the same struct name, the fields must
      // match.
      assert equalFields(thatStructDefinitionType);
      return true;
    }
    // Unnamed structs are equal if their fields match.
    return equalFields(thatStructDefinitionType);
  }

  @Override
  public int hashCode() {
    return Objects.hash(structNameType, fieldNames, fieldTypes);
  }

  /**
   * Checks whether this and the given type have the same number of fields, with equal names and
   * types.
   */
  private boolean equalFields(StructDefinitionType that) {
    if (fieldNames.size() != that.fieldNames.size()) {
      return false;
    }
    for (int i = 0; i < fieldNames.size(); i++) {
      if (!fieldNames.get(i).equals(that.fieldNames.get(i))) {
        return false;
      }
      if (!fieldTypes.get(i).equals(that.fieldTypes.get(i))) {
        return false;
      }
    }
    return true;
  }

}
