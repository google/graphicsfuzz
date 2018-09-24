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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class StructType extends Type {

  private String name;
  private final List<String> fieldNames;
  private final List<Type> fieldTypes;

  /**
   * Makes a named struct with the given field names and types.
   *
   * @param name Name of the struct
   * @param fieldNames Ordered field names
   * @param fieldTypes Ordered field types, one per field name
   */
  public StructType(String name, List<String> fieldNames, List<Type> fieldTypes) {
    assert fieldNames.size() == fieldTypes.size();
    this.name = name;
    this.fieldNames = new ArrayList<>();
    this.fieldNames.addAll(fieldNames);
    this.fieldTypes = new ArrayList<>();
    this.fieldTypes.addAll(fieldTypes);
  }

  public String getName() {
    return name;
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

  @Override
  public void accept(IAstVisitor visitor) {
    visitor.visitStructType(this);
  }

  @Override
  public StructType clone() {
    List<String> newFieldNames = new ArrayList<>();
    List<Type> newFieldTypes = new ArrayList<>();
    newFieldNames.addAll(fieldNames);
    newFieldTypes
        .addAll(fieldTypes.stream().map(item -> item.clone()).collect(Collectors.toList()));
    return new StructType(name, newFieldNames, newFieldTypes);
  }

  @Override
  public boolean hasCanonicalConstant() {
    for (Type t : fieldTypes) {
      if (!t.hasCanonicalConstant()) {
        return false;
      }
    }
    return true;
  }

  @Override
  public Expr getCanonicalConstant() {
    return new TypeConstructorExpr(name,
        fieldTypes.stream().map(item -> item.getCanonicalConstant())
            .collect(Collectors.toList()));
  }

  @Override
  public Type getWithoutQualifiers() {
    return this;
  }

  @Override
  public boolean hasQualifier(TypeQualifier qualifier) {
    return false;
  }

  @Override
  public boolean equals(Object that) {
    if (this == that) {
      return true;
    }
    if (!(that instanceof StructType)) {
      return false;
    }
    StructType thatStruct = (StructType) that;
    return name.equals(thatStruct.name)
        && fieldNames.equals(thatStruct.fieldNames)
        && fieldTypes.equals(thatStruct.fieldTypes);
  }

  @Override
  public int hashCode() {
    // TODO: revisit if we find performance is an issue and need a better hash code
    return name.hashCode()
        ^ fieldNames.hashCode()
        ^ fieldTypes.hashCode();
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
        + " type " + name;
  }

  public void setName(String name) {
    assert name != null;
    this.name = name;
  }
}
