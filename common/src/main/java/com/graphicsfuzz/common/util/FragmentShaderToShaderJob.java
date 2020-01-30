/*
 * Copyright 2020 The GraphicsFuzz Project Authors
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
import com.graphicsfuzz.common.ast.decl.FunctionDefinition;
import com.graphicsfuzz.common.ast.decl.FunctionPrototype;
import com.graphicsfuzz.common.ast.decl.PrecisionDeclaration;
import com.graphicsfuzz.common.ast.decl.VariableDeclInfo;
import com.graphicsfuzz.common.ast.decl.VariablesDeclaration;
import com.graphicsfuzz.common.ast.expr.BinOp;
import com.graphicsfuzz.common.ast.expr.BinaryExpr;
import com.graphicsfuzz.common.ast.expr.Expr;
import com.graphicsfuzz.common.ast.expr.FloatConstantExpr;
import com.graphicsfuzz.common.ast.expr.IntConstantExpr;
import com.graphicsfuzz.common.ast.expr.TypeConstructorExpr;
import com.graphicsfuzz.common.ast.expr.VariableIdentifierExpr;
import com.graphicsfuzz.common.ast.stmt.BlockStmt;
import com.graphicsfuzz.common.ast.stmt.ExprStmt;
import com.graphicsfuzz.common.ast.type.BasicType;
import com.graphicsfuzz.common.ast.type.QualifiedType;
import com.graphicsfuzz.common.ast.type.TypeQualifier;
import com.graphicsfuzz.common.ast.type.VoidType;
import com.graphicsfuzz.common.glslversion.ShadingLanguageVersion;
import com.graphicsfuzz.common.transformreduce.GlslShaderJob;
import com.graphicsfuzz.common.transformreduce.ShaderJob;
import com.graphicsfuzz.util.ArgsUtil;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;

public final class FragmentShaderToShaderJob {

  private FragmentShaderToShaderJob() {
    // Utility class
  }

  private static Optional<Integer> maybeGetArrayCount(VariableDeclInfo vdi) {
    if (vdi.hasArrayInfo()) {
      return Optional.of(vdi.getArrayInfo().getConstantSize());
    }
    return Optional.empty();
  }

  /**
   * Creates a shader job with the given translation unit as its fragment shader, a pipeline state
   * that provides a random value for every uniform declared in the shader, and if needed a vertex
   * shader that provides outputs for the fragment shader's inputs.
   *
   * @param tu A fragment shader.
   * @return A shader job that includes the fragment shader.
   */
  public static ShaderJob createShaderJob(TranslationUnit tu, IRandom generator) {
    assert (tu.getShaderKind() == ShaderKind.FRAGMENT);
    final PipelineInfo pipelineInfo = new PipelineInfo();
    final List<TranslationUnit> shaders = new ArrayList<>();
    shaders.add(tu);

    // Iterate through all uniforms in 'tu'.  For each, add an entry to 'pipelineInfo' with a
    // randomized value (using 'generator' as the source of randomness).
    for (VariablesDeclaration vd : tu.getUniformDecls()) {
      if (!(vd.getBaseType().getWithoutQualifiers() instanceof BasicType)) {
        throw new RuntimeException("Non-basic types not implemented, found "
            + vd.getBaseType().toString());
      }
      BasicType basicType = (BasicType) vd.getBaseType().getWithoutQualifiers();
      for (VariableDeclInfo vdi : vd.getDeclInfos()) {
        // Non-array uniforms are handled as single-element arrays.
        final int arrayLength = vdi.hasArrayInfo() ? vdi.getArrayInfo().getConstantSize() : 1;
        final List<Number> values = new ArrayList<>();
        for (int i = 0; i < basicType.getNumElements() * arrayLength; i++) {
          if (basicType.getElementType() == BasicType.FLOAT) {
            values.add(generator.nextFloat());
          } else if (basicType.getElementType() == BasicType.INT) {
            values.add(generator.nextInt(0xffffff));
          } else if (basicType.getElementType() == BasicType.UINT) {
            values.add(generator.nextPositiveInt(0xffffff));
          } else {
            assert basicType.getElementType() == BasicType.BOOL;
            values.add(generator.nextBoolean() ? 1 : 0);
          }
        }
        pipelineInfo.addUniform(vdi.getName(), basicType, maybeGetArrayCount(vdi), values);
      }
    }


    // If the translation unit uses 'in' global variables, declare a vertex shader
    // TranslationUnit with corresponding 'out' variables and add it to 'shaders'.
    Optional<TranslationUnit> vertexShader = Optional.empty();

    for (VariablesDeclaration vd : tu.getGlobalVariablesDeclarations()) {
      if (vd.getBaseType().hasQualifier(TypeQualifier.SHADER_INPUT)) {
        final BasicType basicType = (BasicType) vd.getBaseType().getWithoutQualifiers();
        // Create clone of the variables declaration and change the input to output:
        final QualifiedType varType = (QualifiedType) vd.getBaseType().clone();
        varType.replaceQualifier(TypeQualifier.SHADER_INPUT, TypeQualifier.SHADER_OUTPUT);
        // Check if this is the first input we're handling
        if (!vertexShader.isPresent()) {
          // Since we're going to need it, initialize vertexShader with
          // skeleton definition
          vertexShader = Optional.of(new TranslationUnit(ShaderKind.VERTEX,
              Optional.of(ShadingLanguageVersion.ESSL_310), Arrays.asList(
              new PrecisionDeclaration("precision mediump float;"),
              new FunctionDefinition(
                  new FunctionPrototype("main", VoidType.VOID, Collections.emptyList()),
                  new BlockStmt(Collections.emptyList(), false)))));
        }

        for (VariableDeclInfo vdi : vd.getDeclInfos()) {
          if (vdi.hasArrayInfo()) {
            throw new RuntimeException("Arrays not implemented with " + vd.getBaseType().toString()
                + " " + vdi.getName());
          }
          // Add variables one by one, so the initialization code is more convenient
          vertexShader.get().addDeclarationBefore(
              new VariablesDeclaration(varType, new VariableDeclInfo(vdi.getName(),
                  vdi.getArrayInfo(), null)),
              vertexShader.get().getMainFunction());
          // Figure out the initializer
          Expr initExpr = null;
          if (basicType == BasicType.FLOAT) {
            initExpr = new FloatConstantExpr(Float.toString(generator.nextFloat()));
          } else if (basicType == BasicType.INT) {
            initExpr = new IntConstantExpr(Integer.toString(generator.nextInt(0xffffff)));
          } else if (basicType == BasicType.UINT) {
            initExpr = new IntConstantExpr(Integer.toString(generator.nextPositiveInt(0xffffff)));
          } else if (basicType == BasicType.VEC2) {
            initExpr = new TypeConstructorExpr("vec2", Arrays.asList(
                new FloatConstantExpr(Float.toString(generator.nextFloat())),
                new FloatConstantExpr(Float.toString(generator.nextFloat()))));
          } else if (basicType == BasicType.VEC3) {
            initExpr = new TypeConstructorExpr("vec3", Arrays.asList(
                new FloatConstantExpr(Float.toString(generator.nextFloat())),
                new FloatConstantExpr(Float.toString(generator.nextFloat())),
                new FloatConstantExpr(Float.toString(generator.nextFloat()))));
          } else if (basicType == BasicType.VEC4) {
            initExpr = new TypeConstructorExpr("vec4", Arrays.asList(
                new FloatConstantExpr(Float.toString(generator.nextFloat())),
                new FloatConstantExpr(Float.toString(generator.nextFloat())),
                new FloatConstantExpr(Float.toString(generator.nextFloat())),
                new FloatConstantExpr(Float.toString(generator.nextFloat()))));
          } else {
            throw new RuntimeException("Unimplemented variable type with "
                + vd.getBaseType().toString() + " " + vdi.getName());
          }
          vertexShader.get().getMainFunction().getBody().insertStmt(0,
              new ExprStmt(new BinaryExpr(
                  new VariableIdentifierExpr(vdi.getName()),
                  initExpr,
                  BinOp.ASSIGN)));
        }
      }
    }

    if (vertexShader.isPresent()) {
      shaders.add(vertexShader.get());
    }
    return new GlslShaderJob(Optional.empty(), pipelineInfo, shaders);
  }

  private static Namespace parse(String[] args) throws ArgumentParserException {
    ArgumentParser parser = ArgumentParsers.newArgumentParser("FragmentShaderToShaderJob")
        .defaultHelp(true)
        .description("Turns a fragment shader into a shader job with randomized uniforms and "
            + "(if needed) a suitable vertex shader.");

    // Required arguments
    parser.addArgument("shader")
        .help("Path of .frag shader to be turned into a shader job.")
        .type(File.class);

    parser.addArgument("output")
        .help("Target shader job .json file.")
        .type(String.class);

    parser.addArgument("--seed")
        .help("Seed (unsigned 64 bit long integer) for the random number generator.")
        .type(String.class);

    return parser.parseArgs(args);
  }

  public static void main(String[] args) throws IOException, InterruptedException {

    try {
      Namespace ns = parse(args);
      long startTime = System.currentTimeMillis();
      final TranslationUnit tu = ParseHelper.parse(new File(ns.getString("shader")));
      long endTime = System.currentTimeMillis();
      System.err.println("Time for parsing: " + (endTime - startTime));

      startTime = System.currentTimeMillis();
      final ShaderJob result = createShaderJob(tu, new RandomWrapper(ArgsUtil.getSeedArgument(ns)));
      endTime = System.currentTimeMillis();
      System.err.println("Time for creating shader job: " + (endTime - startTime));

      final ShaderJobFileOperations fileOperations = new ShaderJobFileOperations();
      fileOperations.writeShaderJobFile(result, new File(ns.getString("output")));
    } catch (Throwable exception) {
      exception.printStackTrace();
      System.exit(1);
    }
  }

}
