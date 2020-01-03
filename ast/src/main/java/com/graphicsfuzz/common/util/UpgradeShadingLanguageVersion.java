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
import com.graphicsfuzz.common.ast.decl.Declaration;
import com.graphicsfuzz.common.ast.decl.FunctionDefinition;
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
import java.util.List;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;

public class UpgradeShadingLanguageVersion extends ScopeTrackingVisitor {

  // The translation unit being upgraded
  private final TranslationUnit tu;

  // The shading language version to upgrade to.
  private final ShadingLanguageVersion newVersion;

  // Globals with initializers; move the initialization to start of main().
  private List<Stmt> globals = new ArrayList<>();

  private static Namespace parse(String[] args) throws ArgumentParserException {
    ArgumentParser parser = ArgumentParsers.newArgumentParser("PrettyPrint")
        .defaultHelp(true)
        .description("Pretty print a shader.");

    // Required arguments
    parser.addArgument("shader")
        .help("Path of shader to be pretty-printed.")
        .type(File.class);

    parser.addArgument("output")
        .help("Target file name.")
        .type(String.class);

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
      upgrade(tu, ShadingLanguageVersion.ESSL_310);
      endTime = System.currentTimeMillis();
      System.err.println("Time for upgrading: " + (endTime - startTime));

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

  private UpgradeShadingLanguageVersion(TranslationUnit tu, ShadingLanguageVersion newVersion) {
    this.tu = tu;
    this.newVersion = newVersion;
    if (newVersion != ShadingLanguageVersion.ESSL_310) {
      throw new RuntimeException("Only upgrading to ESSL 310 supported at present.");
    }
    if (tu.getShadingLanguageVersion() != ShadingLanguageVersion.ESSL_100) {
      throw new RuntimeException("Only upgrading from ESSL 100 supported at present.");
    }

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
      for (int i = 0; i < globals.size(); i++) {
        tu.getMainFunction().getBody().insertStmt(i, globals.get(i));
      }
    }

    // Modify the claimed shading language version of the translation unit.
    tu.setShadingLanguageVersion(newVersion);
  }

  @Override
  public void visitVariableIdentifierExpr(VariableIdentifierExpr variableIdentifierExpr) {
    super.visitVariableIdentifierExpr(variableIdentifierExpr);
    // Rename occurrences of gl_FragColor.
    if (variableIdentifierExpr.getName().equals(OpenGlConstants.GL_FRAG_COLOR)) {
      variableIdentifierExpr.setName(Constants.GLF_COLOR);
    }
  }

  @Override
  public void visitVariablesDeclaration(VariablesDeclaration variablesDeclaration) {
    final Type baseType = variablesDeclaration.getBaseType();
    if (baseType.hasQualifier(TypeQualifier.VARYING)) {
      ((QualifiedType) baseType).replaceQualifier(TypeQualifier.VARYING,
          TypeQualifier.SHADER_INPUT);
    }
    visit(baseType);
    for (VariableDeclInfo vdi : variablesDeclaration.getDeclInfos()) {
      if (vdi.hasArrayInfo()) {
        visit(vdi.getArrayInfo().getSizeExpr());
      } else if (baseType instanceof ArrayType) {
        visit(((ArrayType) baseType).getArrayInfo().getSizeExpr());
      }
      if (vdi.hasInitializer()) {
        visit(vdi.getInitializer());
        // If not constant and has initializer, move the initializer to main.
        if (!baseType.hasQualifier(TypeQualifier.CONST)) {
          globals.add(new ExprStmt(new BinaryExpr(new VariableIdentifierExpr(vdi.getName()),
              vdi.getInitializer().getExpr(), BinOp.ASSIGN)));
          vdi.setInitializer(null);
        }
      }
    }
  }

  @Override
  public void visitFunctionCallExpr(FunctionCallExpr functionCallExpr) {
    switch (functionCallExpr.getCallee()) {
      case "shadow1D":
      case "shadow2D":
      case "texture1D":
      case "texture2D":
      case "texture3D":
      case "textureCube":
        functionCallExpr.setCallee("texture");
        break;
      case "shadow1DProj":
      case "shadow2DProj":
      case "texture1DProj":
      case "texture2DProj":
      case "texture3DProj":
        functionCallExpr.setCallee("textureProj");
        break;
      case "shadow1DLod":
      case "shadow2DLod":
      case "texture1DLod":
      case "texture2DLod":
      case "texture3DLod":
      case "textureCubeLod":
        functionCallExpr.setCallee("textureLod");
        break;
      case "shadow1DProjLod":
      case "shadow2DProjLod":
      case "texture1DProjLod":
      case "texture2DProjLod":
      case "texture3DProjLod":
        functionCallExpr.setCallee("textureProjLod");
        break;
      default:
        break;
    }
    for (Expr e : functionCallExpr.getArgs()) {
      visit(e);
    }
  }

  public static void upgrade(TranslationUnit tu, ShadingLanguageVersion newVersion) {
    new UpgradeShadingLanguageVersion(tu, newVersion);
  }

}
