/*
 * Copyright 2019 The GraphicsFuzz Project Authors
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
import com.graphicsfuzz.common.ast.decl.Declaration;
import com.graphicsfuzz.common.ast.decl.FunctionPrototype;
import com.graphicsfuzz.common.ast.decl.ParameterDecl;
import com.graphicsfuzz.common.ast.decl.PrecisionDeclaration;
import com.graphicsfuzz.common.ast.decl.VariableDeclInfo;
import com.graphicsfuzz.common.ast.decl.VariablesDeclaration;
import com.graphicsfuzz.common.ast.expr.BinOp;
import com.graphicsfuzz.common.ast.expr.BinaryExpr;
import com.graphicsfuzz.common.ast.expr.Expr;
import com.graphicsfuzz.common.ast.expr.FunctionCallExpr;
import com.graphicsfuzz.common.ast.expr.VariableIdentifierExpr;
import com.graphicsfuzz.common.ast.stmt.ExprStmt;
import com.graphicsfuzz.common.ast.stmt.Stmt;
import com.graphicsfuzz.common.ast.type.ArrayType;
import com.graphicsfuzz.common.ast.type.BasicType;
import com.graphicsfuzz.common.ast.type.LayoutQualifierSequence;
import com.graphicsfuzz.common.ast.type.LocationLayoutQualifier;
import com.graphicsfuzz.common.ast.type.QualifiedType;
import com.graphicsfuzz.common.ast.type.Type;
import com.graphicsfuzz.common.ast.type.TypeQualifier;
import com.graphicsfuzz.common.glslversion.ShadingLanguageVersion;
import com.graphicsfuzz.common.tool.PrettyPrinterVisitor;
import com.graphicsfuzz.common.typing.ScopeTrackingVisitor;
import com.graphicsfuzz.util.Constants;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;

public class UpgradeShadingLanguageVersion extends ScopeTrackingVisitor {

  // Flag whether to rename user defined variables and functions.
  private boolean renameUserDefined;

  /*
   * Non-const globals with initializers; move the initialization to start of main().
   *
   * For example, if we have in global scope:
   *   vec2 foo = vec2(1.0, 0.0);
   *
   * That will be converted to:
   *   vec2 foo;
   *
   * And in main(), we add:
   *   foo = vec2(1.0, 0.0);
   */
  private List<Stmt> globalVariableInitializers = new ArrayList<>();

  // Variable name renaming map. Maps original names with new names.
  private Map<String, String> variableRename = new HashMap<>();

  // Function name renaming map. Maps original names with new names.
  private Map<String, String> functionRename = new HashMap<>();

  /**
   * Populate functionRename with texture function rename data
   * Originally texture fetch functions included information about the kind of sampler
   * to be used, but later on these were simplified and the remaining texture fetch
   * functions work with various samplers.
   */
  private void populateTextureFunctionRenames() {
    functionRename.put("shadow1D", "texture");
    functionRename.put("shadow2D", "texture");
    functionRename.put("texture1D", "texture");
    functionRename.put("texture2D", "texture");
    functionRename.put("texture3D", "texture");
    functionRename.put("textureCube", "texture");
    functionRename.put("shadow1DProj", "textureProj");
    functionRename.put("shadow2DProj", "textureProj");
    functionRename.put("texture1DProj", "textureProj");
    functionRename.put("texture2DProj", "textureProj");
    functionRename.put("texture3DProj", "textureProj");
    functionRename.put("shadow1DLod", "textureLod");
    functionRename.put("shadow2DLod", "textureLod");
    functionRename.put("texture1DLod", "textureLod");
    functionRename.put("texture2DLod", "textureLod");
    functionRename.put("texture3DLod", "textureLod");
    functionRename.put("textureCubeLod", "textureLod");
    functionRename.put("shadow1DProjLod", "textureProjLod");
    functionRename.put("shadow2DProjLod", "textureProjLod");
    functionRename.put("texture1DProjLod", "textureProjLod");
    functionRename.put("texture2DProjLod", "textureProjLod");
    functionRename.put("texture3DProjLod", "textureProjLod");
  }

  private static Namespace parse(String[] args) throws ArgumentParserException {
    ArgumentParser parser = ArgumentParsers.newArgumentParser("UpgradeShadingLanguageVersion")
        .defaultHelp(true)
        .description("Upgrade shading language version.");

    // Required arguments
    parser.addArgument("shader")
        .help("Path of shader to be upgraded.")
        .type(File.class);

    parser.addArgument("output")
        .help("Target file name.")
        .type(String.class);

    // Optional arguments
    parser.addArgument("--norename")
        .help("Do not rename user-defined variables and functions")
        .action(Arguments.storeTrue());

    return parser.parseArgs(args);
  }

  public static void main(String[] args) throws IOException, InterruptedException {

    try {
      Namespace ns = parse(args);

      long startTime = System.currentTimeMillis();
      TranslationUnit tu = ParseHelper.parse(new File(ns.getString("shader")));
      long endTime = System.currentTimeMillis();
      System.err.println("Time for parsing: " + (endTime - startTime));

      startTime = System.currentTimeMillis();
      upgrade(tu, ShadingLanguageVersion.ESSL_320, ns.getBoolean("norename"));
      endTime = System.currentTimeMillis();
      System.err.println("Time for upgrading: " + (endTime - startTime));

      // After upgrading, pretty print the shader for output
      prettyPrintShader(ns, tu);
    } catch (Throwable exception) {
      exception.printStackTrace();
      System.exit(1);
    }
  }

  private static void prettyPrintShader(Namespace ns, TranslationUnit tu)
      throws FileNotFoundException {
    try (PrintStream stream =
             new PrintStream(new FileOutputStream(new File(ns.getString("output"))))) {
      PrettyPrinterVisitor ppv = new PrettyPrinterVisitor(stream);
      ppv.visit(tu);
    }
  }

  private UpgradeShadingLanguageVersion(TranslationUnit tu, ShadingLanguageVersion newVersion,
                                        boolean renameUserDefined) {
    this.renameUserDefined = renameUserDefined;
    if (newVersion != ShadingLanguageVersion.ESSL_320) {
      throw new RuntimeException("Only upgrading to ESSL 320 supported at present.");
    }
    if (tu.getShadingLanguageVersion() != ShadingLanguageVersion.ESSL_100) {
      throw new RuntimeException("Only upgrading from ESSL 100 supported at present.");
    }

    // Populate function renaming map with the texture function renames
    populateTextureFunctionRenames();

    // Rename occurrences of gl_FragColor.
    variableRename.put(OpenGlConstants.GL_FRAG_COLOR, Constants.GLF_COLOR);

    // Traverse the translation unit to apply the upgrade to its content.
    visit(tu);

    if (tu.getShaderKind() == ShaderKind.FRAGMENT) {

      // We only consider shaders with main() to be valid.
      if (!tu.hasMainFunction()) {
        throw new RuntimeException("Shader has no main() function");
      }

      // Declare 'layout(location = 0) out vec4 _GLF_color;' at the start of the translation unit,
      // but after any initial precision declarations.

      // Find the first declaration that is not a precision declaration.
      Declaration firstNonPrecisionDeclaration = null;
      for (Declaration decl : tu.getTopLevelDeclarations()) {
        if (decl instanceof PrecisionDeclaration) {
          continue;
        }
        firstNonPrecisionDeclaration = decl;
        break;
      }

      // Add a declaration of '_GLF_color' before this declaration.
      tu.addDeclarationBefore(new VariablesDeclaration(
          new QualifiedType(BasicType.VEC4, Arrays.asList(
              // 'layout(location = 0)'
              new LayoutQualifierSequence(new LocationLayoutQualifier(0)),
              // 'out'
              TypeQualifier.SHADER_OUTPUT
          )), new VariableDeclInfo(Constants.GLF_COLOR, null, null)),
          firstNonPrecisionDeclaration);

      // Add global non-const initializers (if any) to start of main
      // (taking care of doing them in the same order they were introduced)
      for (int i = 0; i < globalVariableInitializers.size(); i++) {
        tu.getMainFunction().getBody().insertStmt(i, globalVariableInitializers.get(i));
      }
    }

    // Modify the claimed shading language version of the translation unit.
    tu.setShadingLanguageVersion(newVersion);
  }

  @Override
  public void visitVariableIdentifierExpr(VariableIdentifierExpr variableIdentifierExpr) {
    super.visitVariableIdentifierExpr(variableIdentifierExpr);
    // If a new name is in the map, rename the variable
    if (variableRename.containsKey(variableIdentifierExpr.getName())) {
      variableIdentifierExpr.setName(variableRename.get(variableIdentifierExpr.getName()));
    }
  }

  @Override
  public void visitVariablesDeclaration(VariablesDeclaration variablesDeclaration) {
    final Type baseType = variablesDeclaration.getBaseType();
    /*
     Convert 'varying' variables to 'in' variables. Both are shader input
     variable types, 'varying' is the old name, 'in' is the more generic new name.
    */
    if (baseType.hasQualifier(TypeQualifier.VARYING)) {
      ((QualifiedType) baseType).replaceQualifier(TypeQualifier.VARYING,
          TypeQualifier.SHADER_INPUT);
    }
    /*
     Make sure we visit the variable declaration hierarchy. We need to loop through
     all the variables, array declarations and initializers.
    */
    visit(baseType);
    for (VariableDeclInfo vdi : variablesDeclaration.getDeclInfos()) {
      if (renameUserDefined) {
        // TODO(https://github.com/google/graphicsfuzz/issues/842): only rename if name collides
        //  with builtin (TyperHelper.getBuiltins())
        // To avoid collisions with builtins, add underscore to variable names
        final String newname = vdi.getName() + "_";
        // Rename any occurrences of the variable name too
        variableRename.put(vdi.getName(), newname);
        vdi.setName(newname);
      }
      if (vdi.hasArrayInfo()) {
        for (int i = 0; i < vdi.getArrayInfo().getDimensionality(); i++) {
          visit(vdi.getArrayInfo().getSizeExpr(i));
        }
      } else if (baseType instanceof ArrayType) {
        final ArrayInfo arrayInfo = ((ArrayType) baseType).getArrayInfo();
        assert arrayInfo.getDimensionality() == 1;
        visit(arrayInfo.getSizeExpr(0));
      }
      if (vdi.hasInitializer()) {
        visit(vdi.getInitializer());
        // If not constant, at global scope and has initializer, move the initializer to main.
        if (!baseType.hasQualifier(TypeQualifier.CONST) && !getCurrentScope().hasParent()) {
          globalVariableInitializers.add(new ExprStmt(new BinaryExpr(
              new VariableIdentifierExpr(vdi.getName()), vdi.getInitializer().getExpr(),
              BinOp.ASSIGN)));
          vdi.removeInitializer();
        }
      }
    }
  }

  @Override
  public void visitFunctionCallExpr(FunctionCallExpr functionCallExpr) {
    // If a new name is in the map, rename the function
    if (functionRename.containsKey(functionCallExpr.getCallee())) {
      functionCallExpr.setCallee(functionRename.get(functionCallExpr.getCallee()));
    }
    for (Expr e : functionCallExpr.getArgs()) {
      visit(e);
    }
  }

  @Override
  public void visitFunctionPrototype(FunctionPrototype functionPrototype) {

    // TODO(https://github.com/google/graphicsfuzz/issues/842): only rename if name collides
    //  with builtin (TyperHelper.getBuiltins())
    // Rename all user-defined functions (except main) to avoid collisions with builtins
    if (!functionPrototype.getName().equals("main")) {
      if (renameUserDefined) {
        final String newname = functionPrototype.getName() + "_";
        // Rename any occurrences of the function name too
        functionRename.put(functionPrototype.getName(), newname);
        functionPrototype.setName(newname);
      }
    }
    // Traverse the rest of the hierarchy
    visit(functionPrototype.getReturnType());
    for (ParameterDecl p : functionPrototype.getParameters()) {
      visit(p);
    }
  }

  @Override
  public void visitParameterDecl(ParameterDecl parameterDecl) {
    visit(parameterDecl.getType());
    if (parameterDecl.getName() != null) {
      if (renameUserDefined) {
        // TODO(https://github.com/google/graphicsfuzz/issues/842): only rename if name collides
        //  with builtin (TyperHelper.getBuiltins())
        // Rename all variables to avoid collisions with builtins
        final String newname = parameterDecl.getName() + "_";
        // Rename any occurrences of the variable name too
        variableRename.put(parameterDecl.getName(), newname);
        parameterDecl.setName(newname);
      }
    }
    if (parameterDecl.hasArrayInfo()) {
      for (int i = 0; i < parameterDecl.getArrayInfo().getDimensionality(); i++) {
        visit(parameterDecl.getArrayInfo().getSizeExpr(i));
      }
    }
  }

  public static void upgrade(TranslationUnit tu, ShadingLanguageVersion newVersion,
                             boolean renameUserDefined) {
    new UpgradeShadingLanguageVersion(tu, newVersion, renameUserDefined);
  }

  public static void upgrade(TranslationUnit tu, ShadingLanguageVersion newVersion) {
    new UpgradeShadingLanguageVersion(tu, newVersion, true);
  }

}
