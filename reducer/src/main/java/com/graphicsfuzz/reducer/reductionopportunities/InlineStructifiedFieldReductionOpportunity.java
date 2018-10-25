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

package com.graphicsfuzz.reducer.reductionopportunities;

import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.ast.expr.MemberLookupExpr;
import com.graphicsfuzz.common.ast.expr.TypeConstructorExpr;
import com.graphicsfuzz.common.ast.type.StructDefinitionType;
import com.graphicsfuzz.common.ast.type.StructNameType;
import com.graphicsfuzz.common.ast.type.Type;
import com.graphicsfuzz.common.ast.visitors.StandardVisitor;
import com.graphicsfuzz.common.ast.visitors.VisitationDepth;
import com.graphicsfuzz.common.glslversion.ShadingLanguageVersion;
import com.graphicsfuzz.common.typing.Typer;
import com.graphicsfuzz.util.Constants;
import java.util.HashMap;
import java.util.Map;

/**
 * Structification produces structs within structs within structs.
 * This reduction opportunity takes a struct within a struct, and inlines the fields of the inner
 * struct so that they appear directly as fields of the outer struct, modifying all initializers
 * in the process.
 *
 * <p>Note that this can only be used for destructification of structs introduced by
 * structification; it cannot be used for general struct field inlining.</p>
 */
public class InlineStructifiedFieldReductionOpportunity extends AbstractReductionOpportunity {

  private final StructDefinitionType outerStruct;
  private final StructDefinitionType innerStruct;
  private final String fieldToInline;
  private final TranslationUnit tu;

  public InlineStructifiedFieldReductionOpportunity(StructDefinitionType outerStruct,
                                                    StructDefinitionType innerStruct,
                                                    String fieldToInline, TranslationUnit tu,
                                                    VisitationDepth depth) {
    super(depth);
    this.outerStruct = outerStruct;
    this.innerStruct = innerStruct;
    this.fieldToInline = fieldToInline;
    this.tu = tu;
    assert outerStruct != innerStruct;
    assert fieldToInline.startsWith(Constants.STRUCTIFICATION_FIELD_PREFIX);
    assert outerStruct.getFieldType(fieldToInline).getWithoutQualifiers()
        instanceof StructNameType : "Can only inline a struct field of a struct";
  }

  @Override
  public void applyReductionImpl() {

    // The GLSL version is irrelevant; really we want a Typer that doesn't require this.
    final Typer typer = new Typer(tu, ShadingLanguageVersion.ESSL_100);

    final int indexOfInlinedField = outerStruct.getFieldIndex(fieldToInline);
    outerStruct.removeField(fieldToInline);

    final Map<String, String> oldToNewFieldNames = new HashMap<>();

    for (int i = 0; i < innerStruct.getNumFields(); i++) {
      final String oldFieldName = innerStruct.getFieldName(i);
      final String newFieldName = oldFieldName.startsWith(Constants.STRUCTIFICATION_FIELD_PREFIX)
          ? fieldToInline + oldFieldName
          : oldFieldName;
      oldToNewFieldNames.put(oldFieldName, newFieldName);
      outerStruct.insertField(indexOfInlinedField + i, newFieldName, innerStruct.getFieldType(i));
    }

    // Now need to replace all references to the old fields, and patch up all type constructors
    new StandardVisitor() {

      @Override
      public void visitTypeConstructorExpr(TypeConstructorExpr typeConstructorExpr) {
        super.visitTypeConstructorExpr(typeConstructorExpr);
        if (typeConstructorExpr.getTypename().equals(getOuterStructName())) {
          TypeConstructorExpr oldArg =
              (TypeConstructorExpr) typeConstructorExpr.removeArg(indexOfInlinedField);
          for (int i = 0; i < oldArg.getNumArgs(); i++) {
            typeConstructorExpr.insertArg(indexOfInlinedField + i, oldArg.getArg(i));
          }
        }
      }

      @Override
      public void visitMemberLookupExpr(MemberLookupExpr memberLookupExpr) {
        super.visitMemberLookupExpr(memberLookupExpr);
        final Type recordedType = typer.lookupType(memberLookupExpr.getStructure());
        if (recordedType == null) {
          return;
        }
        if (!(recordedType
            .getWithoutQualifiers() instanceof StructNameType)) {
          // The structure might be a vector or matrix, or we might not have a type.
          return;
        }
        final StructNameType structType = (StructNameType) recordedType
            .getWithoutQualifiers();
        if (!structType.equals(innerStruct.getStructNameType())) {
          return;
        }
        if (!(memberLookupExpr.getStructure() instanceof MemberLookupExpr)) {
          return;
        }
        MemberLookupExpr outerMemberLookupExpr = (MemberLookupExpr) memberLookupExpr.getStructure();
        if (!typer.lookupType(outerMemberLookupExpr.getStructure())
            .getWithoutQualifiers()
            .equals(outerStruct.getStructNameType())) {
          return;
        }
        if (!(outerMemberLookupExpr.getMember().equals(fieldToInline))) {
          return;
        }

        memberLookupExpr.setStructure(outerMemberLookupExpr.getStructure());
        memberLookupExpr.setMember(oldToNewFieldNames.get(memberLookupExpr.getMember()));
      }

    }.visit(tu);
  }

  @Override
  public boolean preconditionHolds() {
    if (!outerStruct.hasField(fieldToInline)) {
      // The field has been reduced away.
      return false;
    }
    return true;
  }

  public String getOuterStructName() {
    return outerStruct.getStructNameType().getName();
  }
}
