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
import com.graphicsfuzz.common.ast.decl.StructDeclaration;
import com.graphicsfuzz.common.ast.decl.VariableDeclInfo;
import com.graphicsfuzz.common.ast.decl.VariablesDeclaration;
import com.graphicsfuzz.common.ast.expr.FunctionCallExpr;
import com.graphicsfuzz.common.ast.expr.MemberLookupExpr;
import com.graphicsfuzz.common.ast.expr.TypeConstructorExpr;
import com.graphicsfuzz.common.ast.expr.VariableIdentifierExpr;
import com.graphicsfuzz.common.ast.type.StructType;
import com.graphicsfuzz.common.ast.type.Type;
import com.graphicsfuzz.common.ast.type.TypeQualifier;
import com.graphicsfuzz.common.glslversion.ShadingLanguageVersion;
import com.graphicsfuzz.common.typing.ScopeEntry;
import com.graphicsfuzz.common.typing.ScopeTreeBuilder;
import com.graphicsfuzz.common.typing.Typer;
import com.graphicsfuzz.util.ExecHelper;
import com.graphicsfuzz.util.ExecHelper.RedirectType;
import com.graphicsfuzz.util.ExecResult;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.apache.commons.lang3.tuple.ImmutablePair;

public class Obfuscator extends ScopeTreeBuilder {

  private static final int POOL_INITIAL_SIZE = 1000;
  private List<Integer> idPool;
  private int maxIdUsed;
  private final IRandom generator;

  private final Map<String, String> functionRenaming;
  private final Map<String, String> structRenaming;
  private final List<StructType> structs;
  private final Map<String, Map<String, String>> structFieldRenaming;
  private final Map<VariableDeclInfo, String> varDeclMapping;
  private final Map<ParameterDecl, String> paramDeclMapping;
  private final Map<String, String> uniformMapping;

  private boolean inUniformDecl;
  private Typer typer;

  private Obfuscator(IRandom generator) {
    this.idPool = new ArrayList<>();
    this.maxIdUsed = 0;
    this.generator = generator;
    this.functionRenaming = new HashMap<>();
    this.structRenaming = new HashMap<>();
    this.structs = new ArrayList<>();
    this.structFieldRenaming = new HashMap<>();
    this.varDeclMapping = new HashMap<>();
    this.paramDeclMapping = new HashMap<>();
    this.uniformMapping = new HashMap<>();
    this.inUniformDecl = false;
    this.typer = null;
  }

  @Override
  public void visitTypeConstructorExpr(TypeConstructorExpr typeConstructorExpr) {
    super.visitTypeConstructorExpr(typeConstructorExpr);
    final String typename = typeConstructorExpr.getTypename();
    if (structRenaming.containsKey(typename)) {
      typeConstructorExpr.setTypename(structRenaming.get(typename));
    }
  }

  @Override
  public void visitStructDeclaration(StructDeclaration structDeclaration) {
    super.visitStructDeclaration(structDeclaration);
    final String oldName = structDeclaration.getType().getName();
    final String newName = renameStruct(oldName);
    structRenaming.put(oldName, newName);
    structs.add(structDeclaration.getType());
    final Map<String, String> fieldRenaming = new HashMap<>();
    for (String fieldName : structDeclaration.getType().getFieldNames()) {
      fieldRenaming.put(fieldName, renameStructField(fieldName));
    }
    structFieldRenaming.put(oldName, fieldRenaming);
  }

  @Override
  public void visitMemberLookupExpr(MemberLookupExpr memberLookupExpr) {
    super.visitMemberLookupExpr(memberLookupExpr);
    Type type = typer.lookupType(memberLookupExpr.getStructure());
    if (type == null) {
      return;
    }
    type = type.getWithoutQualifiers();
    if (!(type instanceof StructType)) {
      return;
    }
    final StructType structType = (StructType) type;
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
    final String renamedVariable = renameVariable(declInfo.getName());
    varDeclMapping.put(declInfo, renamedVariable);
    if (inUniformDecl) {
      uniformMapping.put(declInfo.getName(), renamedVariable);
    }
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

  private String renameVariable(String name) {
    return "v" + freshId();
  }

  private String renameStruct(String name) {
    return "S" + freshId();
  }

  private String renameStructField(String name) {
    return "l" + freshId();
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

  public static ImmutablePair<TranslationUnit, UniformsInfo> obfuscate(
        TranslationUnit tu, UniformsInfo uniformsInfo, IRandom generator,
        ShadingLanguageVersion shadingLanguageVersion) {
    return new Obfuscator(generator).obfuscate(tu, uniformsInfo, shadingLanguageVersion);
  }

  private ImmutablePair<TranslationUnit, UniformsInfo> obfuscate(
        TranslationUnit tu, UniformsInfo uniformsInfo,
        ShadingLanguageVersion shadingLanguageVersion) {
    TranslationUnit clonedTu = tu.cloneAndPatchUp();
    this.typer = new Typer(clonedTu, shadingLanguageVersion);
    visit(clonedTu);
    for (VariableDeclInfo declInfo : varDeclMapping.keySet()) {
      assert varDeclMapping.containsKey(declInfo);
      declInfo.setName(varDeclMapping.get(declInfo));
    }
    for (ParameterDecl parameterDecl : paramDeclMapping.keySet()) {
      assert paramDeclMapping.containsKey(parameterDecl);
      parameterDecl.setName(paramDeclMapping.get(parameterDecl));
    }
    for (StructType structType : structs) {
      assert structRenaming.containsKey(structType.getName());
      final String oldName = structType.getName();
      final String newName = structRenaming.get(oldName);
      structType.setName(newName);
      for (int i = 0; i < structType.getNumFields(); i++) {
        assert structFieldRenaming.get(oldName).containsKey(structType.getFieldName(i));
        structType.setFieldName(i, structFieldRenaming.get(oldName)
              .get(structType.getFieldName(i)));
      }
    }
    for (String name : uniformsInfo.getUniformNames()) {
      if (!uniformMapping.containsKey(name)) {
        uniformMapping.put(name, renameVariable(name));
      }
    }
    return new ImmutablePair<>(clonedTu, uniformsInfo.renameUniforms(uniformMapping));
  }

  public static void main(String[] args)
        throws IOException, ParseTimeoutException, InterruptedException {
    final File shader = new File(args[0]);
    final String licenseFilename = args[1];
    final ShadingLanguageVersion shadingLanguageVersion =
        ShadingLanguageVersion.getGlslVersionFromShader(shader);
    ExecResult execResult = new ExecHelper().exec(
          RedirectType.TO_BUFFER,
          null,
          false,
          "cpp",
          "-P",
          args[0]);

    final TranslationUnit tu = Helper.parse(execResult.stdout.toString(),
          false);
    final UniformsInfo uniformsInfo = new UniformsInfo(
          new File(Helper.jsonFilenameForShader(args[0])));
    ImmutablePair<TranslationUnit, UniformsInfo> obfuscated =
          obfuscate(tu, uniformsInfo, new RandomWrapper(Integer.parseInt(args[2])),
              shadingLanguageVersion);
    final String obfuscatedFilename = "obfuscated.frag";
    final Supplier<String> newlineSupplier = new Supplier<String>() {

      private int count;

      @Override
      public String get() {
        return (count++ % 10) == 0 ? "\n" : " ";
      }
    };

    Helper.emitShader(shadingLanguageVersion, ShaderKind.FRAGMENT, obfuscated.getLeft(),
          Helper.readLicenseFile(new File(licenseFilename)),
          new File(obfuscatedFilename), 0, newlineSupplier, false);
    Helper.emitUniformsInfo(obfuscated.getRight(),
          new PrintStream(new FileOutputStream(
                new File(Helper.jsonFilenameForShader(obfuscatedFilename)))));
  }

}
