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

import com.graphicsfuzz.common.ast.IParentMap;
import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.ast.decl.ArrayInfo;
import com.graphicsfuzz.common.ast.decl.Declaration;
import com.graphicsfuzz.common.ast.decl.InterfaceBlock;
import com.graphicsfuzz.common.ast.decl.VariableDeclInfo;
import com.graphicsfuzz.common.ast.decl.VariablesDeclaration;
import com.graphicsfuzz.common.ast.expr.MemberLookupExpr;
import com.graphicsfuzz.common.ast.expr.VariableIdentifierExpr;
import com.graphicsfuzz.common.ast.type.ArrayType;
import com.graphicsfuzz.common.ast.type.LayoutQualifier;
import com.graphicsfuzz.common.ast.type.QualifiedType;
import com.graphicsfuzz.common.ast.type.Type;
import com.graphicsfuzz.common.ast.type.TypeQualifier;
import com.graphicsfuzz.common.typing.ScopeEntry;
import com.graphicsfuzz.common.typing.ScopeTreeBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ConvertToVulkanFormat extends ScopeTreeBuilder {

  private final Map<String, String> uniformToInterface;
  private final Map<String, VariableDeclInfo> uniformToOriginalDecl;
  private final IParentMap parentMap;
  private int interfaceBlockCounter;

  private ConvertToVulkanFormat(TranslationUnit tu, UniformsInfo uniformsInfo) {
    this.uniformToInterface = new HashMap<>();
    this.uniformToOriginalDecl = new HashMap<>();
    this.parentMap = IParentMap.createParentMap(tu);
    this.interfaceBlockCounter = 0;

    List<Declaration> newTopLevelDecls = new ArrayList<>();
    for (Declaration decl : tu.getTopLevelDeclarations()) {
      if (!isUniformDeclaration(decl)) {
        newTopLevelDecls.add(decl);
        continue;
      }
      VariablesDeclaration variablesDeclaration = (VariablesDeclaration) decl;
      if (variablesDeclaration.getNumDecls() != 1) {
        throw new RuntimeException("Only equipped to convert single-declaration uniforms now");
      }
      final String interfaceName = getInterfaceIdentifierName();
      final String uniformName = variablesDeclaration.getDeclInfo(0).getName();
      uniformsInfo.addBindingToUniform(uniformName, interfaceBlockCounter);
      assert !uniformToInterface.containsKey(uniformName);
      uniformToInterface.put(uniformName, interfaceName);
      assert !uniformToOriginalDecl.containsKey(uniformName);
      uniformToOriginalDecl.put(uniformName, variablesDeclaration.getDeclInfo(0));
      newTopLevelDecls.add(
          new InterfaceBlock(
              new LayoutQualifier("binding = " + interfaceBlockCounter), TypeQualifier.UNIFORM,
              getInterfaceBlockName(), uniformName,
              getCombinedType(variablesDeclaration.getBaseType(),
                  variablesDeclaration.getDeclInfo(0).getArrayInfo()),
              interfaceName
          )
      );
      interfaceBlockCounter++;
    }

    visit(tu);

    tu.setTopLevelDeclarations(newTopLevelDecls);
  }

  private Type getCombinedType(Type baseType, ArrayInfo arrayInfo) {
    Type result = baseType.clone();
    assert result.hasQualifier(TypeQualifier.UNIFORM);
    ((QualifiedType) result).removeQualifier(TypeQualifier.UNIFORM);
    return arrayInfo == null
        ? result
        : new ArrayType(result, arrayInfo.clone());
  }

  private String getInterfaceBlockName() {
    return "_GLF_UniformBufferObject" + interfaceBlockCounter;
  }

  private String getInterfaceIdentifierName() {
    return "_GLF_ubo" + interfaceBlockCounter;
  }

  @Override
  public void visitVariableIdentifierExpr(VariableIdentifierExpr variableIdentifierExpr) {
    super.visitVariableIdentifierExpr(variableIdentifierExpr);
    final String originalName = variableIdentifierExpr.getName();
    if (!uniformToOriginalDecl.containsKey(originalName)) {
      // We haven't found a uniform with this name.
      return;
    }
    final ScopeEntry scopeEntry = currentScope.lookupScopeEntry(originalName);
    if (scopeEntry.hasParameterDecl()
          || (scopeEntry.hasVariableDeclInfo()
                && uniformToOriginalDecl.get(originalName) != scopeEntry.getVariableDeclInfo())) {
      // Due to name shadowing, this is a different declaration.
      return;
    }
    parentMap.getParent(variableIdentifierExpr).replaceChild(
        variableIdentifierExpr, new MemberLookupExpr(
            new VariableIdentifierExpr(uniformToInterface.get(originalName)),
                originalName));
  }

  public static void convert(TranslationUnit tu, UniformsInfo uniformsInfo) {
    new ConvertToVulkanFormat(tu, uniformsInfo);
  }

  private static boolean isUniformDeclaration(Declaration decl) {
    return decl instanceof VariablesDeclaration
        && ((VariablesDeclaration) decl).getBaseType().hasQualifier(TypeQualifier.UNIFORM);
  }

}
