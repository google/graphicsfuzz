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
import com.graphicsfuzz.common.ast.decl.FunctionPrototype;
import com.graphicsfuzz.common.ast.decl.ParameterDecl;
import com.graphicsfuzz.common.ast.decl.VariableDeclInfo;
import com.graphicsfuzz.common.ast.decl.VariablesDeclaration;
import com.graphicsfuzz.common.ast.expr.FunctionCallExpr;
import com.graphicsfuzz.common.ast.expr.MemberLookupExpr;
import com.graphicsfuzz.common.ast.expr.TypeConstructorExpr;
import com.graphicsfuzz.common.ast.expr.VariableIdentifierExpr;
import com.graphicsfuzz.common.ast.type.StructDefinitionType;
import com.graphicsfuzz.common.ast.type.StructNameType;
import com.graphicsfuzz.common.ast.type.Type;
import com.graphicsfuzz.common.ast.type.TypeQualifier;
import com.graphicsfuzz.common.ast.visitors.StandardVisitor;
import com.graphicsfuzz.common.glslversion.ShadingLanguageVersion;
import com.graphicsfuzz.common.transformreduce.GlslShaderJob;
import com.graphicsfuzz.common.transformreduce.ShaderJob;
import com.graphicsfuzz.common.typing.ScopeEntry;
import com.graphicsfuzz.common.typing.ScopeTreeBuilder;
import com.graphicsfuzz.common.typing.Typer;
import com.graphicsfuzz.util.ExecHelper;
import com.graphicsfuzz.util.ExecHelper.RedirectType;
import com.graphicsfuzz.util.ExecResult;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.lang3.tuple.ImmutablePair;

public class Obfuscator {

  private static final int POOL_INITIAL_SIZE = 1000;
  private List<Integer> idPool;
  private int maxIdUsed;
  private final IRandom generator;
  private final Map<String, String> uniformMapping;

  private Obfuscator(IRandom generator) {
    this.idPool = new ArrayList<>();
    this.maxIdUsed = 0;
    this.generator = generator;
    this.uniformMapping = new HashMap<>();
  }

  public static ShaderJob obfuscate(
      ShaderJob shaderJob, IRandom generator) {
    return new Obfuscator(generator).obfuscate(shaderJob);
  }

  private ShaderJob obfuscate(
      ShaderJob shaderJob) {
    final List<TranslationUnit> clonedTus = new ArrayList<>();
    for (TranslationUnit tu : shaderJob.getShaders()) {
      final TranslationUnit clonedTu = tu.clone();
      new TranslationUnitObfuscator().obfuscateTranslationUnit(clonedTu);
      clonedTus.add(clonedTu);
    }
    for (String name : shaderJob.getUniformsInfo().getUniformNames()) {
      if (!uniformMapping.containsKey(name)) {
        uniformMapping.put(name, renameVariable(name));
      }
    }
    return new GlslShaderJob(Optional.empty(),
        shaderJob.getUniformsInfo().renameUniforms(uniformMapping),
        clonedTus);
  }

  private class TranslationUnitObfuscator extends ScopeTreeBuilder {
    private final Map<String, String> functionRenaming;
    private final Map<String, String> namedStructRenaming;
    private final List<StructDefinitionType> structDefinitionTypes;
    private final Map<StructNameType, Map<String, String>> structFieldRenaming;
    private final Map<VariableDeclInfo, String> varDeclMapping;
    private final Map<ParameterDecl, String> paramDeclMapping;
    private boolean inUniformDecl;
    private Typer typer;

    private TranslationUnitObfuscator() {
      this.functionRenaming = new HashMap<>();
      this.namedStructRenaming = new HashMap<>();
      this.structDefinitionTypes = new ArrayList<>();
      this.structFieldRenaming = new HashMap<>();
      this.varDeclMapping = new HashMap<>();
      this.paramDeclMapping = new HashMap<>();
      this.inUniformDecl = false;
      this.typer = null;
    }

    private void obfuscateTranslationUnit(TranslationUnit tu) {

      this.typer = new Typer(tu, tu.getShadingLanguageVersion());
      visit(tu);
      for (VariableDeclInfo declInfo : varDeclMapping.keySet()) {
        assert varDeclMapping.containsKey(declInfo);
        declInfo.setName(varDeclMapping.get(declInfo));
      }
      for (ParameterDecl parameterDecl : paramDeclMapping.keySet()) {
        assert paramDeclMapping.containsKey(parameterDecl);
        parameterDecl.setName(paramDeclMapping.get(parameterDecl));
      }
      for (StructDefinitionType structDefinitionType : structDefinitionTypes) {
        if (structDefinitionType.hasStructNameType()) {
          final StructNameType structNameType = structDefinitionType.getStructNameType();
          for (int i = 0; i < structDefinitionType.getNumFields(); i++) {
            assert structFieldRenaming.get(structNameType)
                .containsKey(structDefinitionType.getFieldName(i));
            structDefinitionType.setFieldName(i, structFieldRenaming.get(structNameType)
                .get(structDefinitionType.getFieldName(i)));
          }
        }
      }

      new StandardVisitor() {
        @Override
        public void visitStructNameType(StructNameType structNameType) {
          structNameType.setName(namedStructRenaming.get(structNameType.getName()));
        }
      }.visit(tu);
    }

    @Override
    public void visitTypeConstructorExpr(TypeConstructorExpr typeConstructorExpr) {
      super.visitTypeConstructorExpr(typeConstructorExpr);
      final String typename = typeConstructorExpr.getTypename();
      if (namedStructRenaming.containsKey(typename)) {
        typeConstructorExpr.setTypename(namedStructRenaming.get(typename));
      }
    }

    @Override
    public void visitStructDefinitionType(StructDefinitionType structDefinitionType) {
      super.visitStructDefinitionType(structDefinitionType);
      // TODO: right now we do not obfuscate fields of anonymous structs.
      if (structDefinitionType.hasStructNameType()) {
        final String oldName = structDefinitionType.getStructNameType().getName();
        final String newName = renameStruct(oldName);
        namedStructRenaming.put(oldName, newName);
        structDefinitionTypes.add(structDefinitionType);
        final Map<String, String> fieldRenaming = new HashMap<>();
        for (String fieldName : structDefinitionType.getFieldNames()) {
          fieldRenaming.put(fieldName, renameStructField(fieldName));
        }
        structFieldRenaming.put(structDefinitionType.getStructNameType(), fieldRenaming);
      }
    }

    @Override
    public void visitMemberLookupExpr(MemberLookupExpr memberLookupExpr) {
      super.visitMemberLookupExpr(memberLookupExpr);
      Type type = typer.lookupType(memberLookupExpr.getStructure());
      if (type == null) {
        return;
      }
      type = type.getWithoutQualifiers();
      if (!(type instanceof StructNameType)) {
        return;
      }
      final StructNameType structType = (StructNameType) type;
      assert structFieldRenaming.containsKey(structType.getName());
      final Map<String, String> fieldRenaming = structFieldRenaming.get(structType.getName());
      assert fieldRenaming.containsKey(memberLookupExpr.getMember());
      memberLookupExpr.setMember(fieldRenaming
          .get(memberLookupExpr.getMember()));
    }

    @Override
    public void visitFunctionPrototype(FunctionPrototype functionPrototype) {
      if (!functionRenaming.containsKey(functionPrototype.getName())) {
        functionRenaming.put(functionPrototype.getName(),
            renameFunction(functionPrototype.getName()));
      }
      functionPrototype.setName(functionRenaming.get(functionPrototype.getName()));
      super.visitFunctionPrototype(functionPrototype);
    }

    @Override
    public void visitFunctionCallExpr(FunctionCallExpr functionCallExpr) {
      functionCallExpr.setCallee(applyFunctionNameMapping(functionCallExpr.getCallee()));
      super.visitFunctionCallExpr(functionCallExpr);
    }

    @Override
    public void visitVariablesDeclaration(VariablesDeclaration variablesDeclaration) {
      if (variablesDeclaration.getBaseType().hasQualifier(TypeQualifier.UNIFORM)) {
        inUniformDecl = true;
      }
      super.visitVariablesDeclaration(variablesDeclaration);
      inUniformDecl = false;
    }

    @Override
    protected void visitVariableDeclInfoAfterAddedToScope(VariableDeclInfo declInfo) {
      String renamedVariable;
      if (inUniformDecl && uniformMapping.containsKey(declInfo.getName())) {
        renamedVariable = uniformMapping.get(declInfo.getName());
      } else {
        renamedVariable = renameVariable(declInfo.getName());
        if (inUniformDecl) {
          uniformMapping.put(declInfo.getName(), renamedVariable);
        }
      }
      varDeclMapping.put(declInfo, renamedVariable);
    }

    @Override
    public void visitParameterDecl(ParameterDecl parameterDecl) {
      super.visitParameterDecl(parameterDecl);
      // It is permissible for a parameter to have no name in some cases, e.g. void main(void)
      if (parameterDecl.getName() != null) {
        paramDeclMapping.put(parameterDecl, renameVariable(parameterDecl.getName()));
      }
    }

    @Override
    public void visitVariableIdentifierExpr(VariableIdentifierExpr variableIdentifierExpr) {
      super.visitVariableIdentifierExpr(variableIdentifierExpr);
      variableIdentifierExpr.setName(applyVariableNameMapping(variableIdentifierExpr.getName()));
    }

    private String applyFunctionNameMapping(String name) {
      return functionRenaming.getOrDefault(name, name);
    }

    private String applyVariableNameMapping(String name) {
      ScopeEntry scopeEntry = currentScope.lookupScopeEntry(name);
      if (scopeEntry == null) {
        return name;
      }
      if (scopeEntry.hasVariableDeclInfo()) {
        return varDeclMapping.getOrDefault(scopeEntry.getVariableDeclInfo(), name);
      }
      assert scopeEntry.hasParameterDecl();
      return paramDeclMapping.getOrDefault(scopeEntry.getParameterDecl(), name);
    }

    private String renameFunction(String name) {
      if (name.equals("main")) {
        return name;
      }
      return "f" + freshId();
    }

    private String renameStruct(String name) {
      return "S" + freshId();
    }

    private String renameStructField(String name) {
      return "l" + freshId();
    }

  }

  private int freshId() {
    if (idPool.isEmpty()) {
      for (int i = 0; i < POOL_INITIAL_SIZE; i++) {
        idPool.add(maxIdUsed + i);
      }
      maxIdUsed += POOL_INITIAL_SIZE;
    }
    return idPool.remove(generator.nextInt(idPool.size()));
  }

  private String renameVariable(String name) {
    return "v" + freshId();
  }

  public static void main(String[] args)
        throws IOException, ParseTimeoutException, InterruptedException {
    final File shader = new File(args[0]);
    final String licenseFilename = args[1];
    ExecResult execResult = new ExecHelper().exec(
          RedirectType.TO_BUFFER,
          null,
          false,
          "cpp",
          "-P",
          args[0]);
    ShaderJobFileOperations fileOps = new ShaderJobFileOperations();

    ShaderJob shaderJob = fileOps.readShaderJobFile(
        new File(args[0])
    );

    // TODO: Reimplement obfuscator if needed.

    throw new RuntimeException("Obfuscator command-line tool no longer implemented.");
  }

}
