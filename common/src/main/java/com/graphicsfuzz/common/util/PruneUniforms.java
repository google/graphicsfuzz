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

import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.ast.decl.ArrayInfo;
import com.graphicsfuzz.common.ast.decl.Initializer;
import com.graphicsfuzz.common.ast.decl.ScalarInitializer;
import com.graphicsfuzz.common.ast.decl.VariableDeclInfo;
import com.graphicsfuzz.common.ast.decl.VariablesDeclaration;
import com.graphicsfuzz.common.ast.expr.ArrayConstructorExpr;
import com.graphicsfuzz.common.ast.expr.Expr;
import com.graphicsfuzz.common.ast.expr.FloatConstantExpr;
import com.graphicsfuzz.common.ast.expr.IntConstantExpr;
import com.graphicsfuzz.common.ast.expr.TypeConstructorExpr;
import com.graphicsfuzz.common.ast.type.ArrayType;
import com.graphicsfuzz.common.ast.type.BasicType;
import com.graphicsfuzz.common.ast.type.QualifiedType;
import com.graphicsfuzz.common.ast.type.TypeQualifier;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class PruneUniforms {

  private int remainingUniforms;

  public PruneUniforms(TranslationUnit tu, UniformsInfo uniformsInfo,
        int limit, List<String> prunablePrefixes) {
    this.remainingUniforms = 0;
    for (VariablesDeclaration decl : getUniforms(tu)) {
      if (decl.getDeclInfos().stream()
            .anyMatch(item -> !isPrunable(prunablePrefixes, item))) {
        // At least one of the declarations is not prunable, so don't prune any of them.
        remainingUniforms += decl.getNumDecls();
      }
    }
    for (VariablesDeclaration decl : getUniforms(tu)) {
      if (decl.getDeclInfos().stream()
            .allMatch(item -> isPrunable(prunablePrefixes, item))) {
        if (remainingUniforms + decl.getNumDecls() <= limit) {
          remainingUniforms += decl.getNumDecls();
        } else {
          ((QualifiedType) decl.getBaseType()).removeQualifier(
                TypeQualifier.UNIFORM);
          for (VariableDeclInfo vdi : decl.getDeclInfos()) {
            assert !vdi.hasInitializer();
            vdi.setInitializer(makeInitializer(
                  (BasicType) decl.getBaseType()
                  .getWithoutQualifiers(),
                  vdi.getArrayInfo(), uniformsInfo.getArgs(vdi.getName())));
            uniformsInfo.removeUniform(vdi.getName());
          }
        }
      }
    }

  }

  private Initializer makeInitializer(BasicType baseType, ArrayInfo arrayInfo, List<Number> args) {
    if (arrayInfo != null) {
      assert arrayInfo.getSize() * baseType.getNumElements() == args.size();
      List<Expr> argExprs = new ArrayList<>();
      for (int index = 0; index < arrayInfo.getSize(); index++) {
        argExprs.add(getTypeConstructorExpr(baseType,
              args.subList(index * baseType.getNumElements(),
                    (index + 1) * baseType.getNumElements())));
      }
      return new ScalarInitializer(new ArrayConstructorExpr(
            new ArrayType(baseType.getWithoutQualifiers(), arrayInfo.clone()),
            argExprs));
    }
    return new ScalarInitializer(getTypeConstructorExpr(baseType, args));
  }

  private TypeConstructorExpr getTypeConstructorExpr(BasicType baseType, List<Number> args) {
    List<Expr> argExprs;
    if (baseType.getElementType() == BasicType.FLOAT) {
      argExprs = args.stream().map(item -> new FloatConstantExpr(item.toString()))
            .collect(Collectors.toList());
    } else {
      argExprs = args.stream().map(item -> new IntConstantExpr(item.toString()))
            .collect(Collectors.toList());
    }
    return new TypeConstructorExpr(baseType.toString(),
          argExprs);
  }

  private boolean isPrunable(List<String> prunablePrefixes, VariableDeclInfo vdi) {
    return prunablePrefixes.stream()
          .anyMatch(item -> vdi.getName().startsWith(item));
  }

  private List<VariablesDeclaration> getUniforms(TranslationUnit tu) {
    return tu.getTopLevelDeclarations().stream()
          .filter(item -> item instanceof VariablesDeclaration)
          .map(item -> (VariablesDeclaration) item)
          .filter(item -> item.getBaseType().hasQualifier(TypeQualifier.UNIFORM))
          .collect(Collectors.toList());
  }

  public static boolean prune(TranslationUnit tu, UniformsInfo uniformsInfo,
        int limit, List<String> prunablePrefixes) {
    return new PruneUniforms(tu, uniformsInfo, limit, prunablePrefixes)
          .remainingUniforms <= limit;
  }

}
