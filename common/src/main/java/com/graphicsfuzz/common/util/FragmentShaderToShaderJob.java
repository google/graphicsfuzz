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
import com.graphicsfuzz.common.ast.expr.BoolConstantExpr;
import com.graphicsfuzz.common.ast.expr.Expr;
import com.graphicsfuzz.common.ast.expr.FloatConstantExpr;
import com.graphicsfuzz.common.ast.expr.IntConstantExpr;
import com.graphicsfuzz.common.ast.expr.TypeConstructorExpr;
import com.graphicsfuzz.common.ast.expr.UIntConstantExpr;
import com.graphicsfuzz.common.ast.expr.VariableIdentifierExpr;
import com.graphicsfuzz.common.ast.stmt.BlockStmt;
import com.graphicsfuzz.common.ast.stmt.ExprStmt;
import com.graphicsfuzz.common.ast.type.BasicType;
import com.graphicsfuzz.common.ast.type.LayoutQualifierSequence;
import com.graphicsfuzz.common.ast.type.LocationLayoutQualifier;
import com.graphicsfuzz.common.ast.type.QualifiedType;
import com.graphicsfuzz.common.ast.type.SamplerType;
import com.graphicsfuzz.common.ast.type.TypeQualifier;
import com.graphicsfuzz.common.ast.type.VoidType;
import com.graphicsfuzz.common.glslversion.ShadingLanguageVersion;
import com.graphicsfuzz.common.transformreduce.GlslShaderJob;
import com.graphicsfuzz.common.transformreduce.ShaderJob;
import com.graphicsfuzz.util.ArgsUtil;
import com.graphicsfuzz.util.Constants;
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
      if (vdi.getArrayInfo().getDimensionality() != 1) {
        throw new RuntimeException("Multi-dimensional array uniforms are not supported.");
      }
      return Optional.of(vdi.getArrayInfo().getConstantSize(0));
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
    // For samplers, we set the texture to "DEFAULT"
    for (VariablesDeclaration vd : tu.getUniformDecls()) {
      if (vd.getBaseType().getWithoutQualifiers() instanceof SamplerType) {
        for (VariableDeclInfo vdi : vd.getDeclInfos()) {
          if (vdi.hasArrayInfo()) {
            if (vdi.getArrayInfo().getDimensionality() != 1) {
              throw new RuntimeException("Multi-dimensional sampler arrays are not supported.");
            }
            if (!vdi.getArrayInfo().hasConstantSize(0)
                || vdi.getArrayInfo().getConstantSize(0) > 1) {
              throw new RuntimeException("Sampler arrays not implemented");
            }
          }
          pipelineInfo.addSamplerInfo(vdi.getName(),
              vd.getBaseType().getWithoutQualifiers().getText(), BuiltInTexture.DEFAULT.toString());
        }
      } else {
        if (!(vd.getBaseType().getWithoutQualifiers() instanceof BasicType)) {
          throw new RuntimeException("Non-basic types not implemented, found "
              + vd.getBaseType().toString());
        }
        BasicType basicType = (BasicType) vd.getBaseType().getWithoutQualifiers();
        for (VariableDeclInfo vdi : vd.getDeclInfos()) {
          // Non-array uniforms are handled as single-element arrays.
          if (vdi.hasArrayInfo() && vdi.getArrayInfo().getDimensionality() != 1) {
            throw new RuntimeException("Multi-dimensional array uniforms are not supported.");
          }
          final int arrayLength = vdi.hasArrayInfo() ? vdi.getArrayInfo().getConstantSize(0) : 1;
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
    }


    // If the translation unit uses 'in' global variables, declare a vertex shader
    // TranslationUnit with corresponding 'out' variables and add it to 'shaders'.
    Optional<TranslationUnit> vertexShader = Optional.empty();

    // Start layout locations from index 1
    int locationIndex = 1;


    for (VariablesDeclaration vd : tu.getGlobalVariablesDeclarations()) {
      if (vd.getBaseType().hasQualifier(TypeQualifier.SHADER_INPUT)) {
        for (VariableDeclInfo vdi : vd.getDeclInfos()) {
          if (vdi.hasArrayInfo()) {
            throw new RuntimeException("Arrays not implemented with " + vd.getBaseType().toString()
                + " " + vdi.getName());
          }

          final BasicType basicType = (BasicType) vd.getBaseType().getWithoutQualifiers();
          // Create clone of the variables declaration and change the input to output:
          final QualifiedType varTypeIn = (QualifiedType) vd.getBaseType().clone();
          final QualifiedType varTypeOut = (QualifiedType) vd.getBaseType().clone();
          varTypeOut.replaceQualifier(TypeQualifier.SHADER_INPUT, TypeQualifier.SHADER_OUTPUT);

          // Set same layout location for vertex output and fragment input
          varTypeIn.setLocationQualifier(locationIndex);
          varTypeOut.setLocationQualifier(locationIndex);
          // Increment location index
          if (BasicType.allMatrixTypes().contains(basicType)) {
            // For matrices, we'll skip ahead location indices to make sure
            // there's no problem with them. Smaller matrices (like 2x3)
            // don't need as much, but this makes testing easier.
            locationIndex += 4;
          } else {
            locationIndex++;
          }
          // Check if this is the first input we're handling
          if (!vertexShader.isPresent()) {
            // Since we're going to need it, initialize vertexShader with
            // skeleton definition
            vertexShader = Optional.of(new TranslationUnit(ShaderKind.VERTEX,
                Optional.of(ShadingLanguageVersion.ESSL_320), Arrays.asList(
                new PrecisionDeclaration("precision mediump float;"),
                new VariablesDeclaration(new QualifiedType(BasicType.VEC4,
                    Arrays.asList(new LayoutQualifierSequence(
                        new LocationLayoutQualifier(0)),
                    TypeQualifier.SHADER_INPUT)),
                    new VariableDeclInfo(Constants.GLF_POS, null, null)),
                new FunctionDefinition(
                    new FunctionPrototype("main", VoidType.VOID, Collections.emptyList()),
                    new BlockStmt(Arrays.asList(
                        new ExprStmt(new BinaryExpr(
                        new VariableIdentifierExpr(OpenGlConstants.GL_POSITION),
                        new VariableIdentifierExpr(Constants.GLF_POS),
                        BinOp.ASSIGN))), false)))));
          }

          // Add variables one by one, so the initialization code is more convenient
          vertexShader.get().addDeclarationBefore(
              new VariablesDeclaration(varTypeOut, new VariableDeclInfo(vdi.getName(),
                  vdi.getArrayInfo(), null)),
              vertexShader.get().getMainFunction());
          // Add variables one by one to the fragment shader too to get matching layout
          tu.addDeclarationBefore(
              new VariablesDeclaration(varTypeIn, new VariableDeclInfo(vdi.getName(),
                  vdi.getArrayInfo(), null)),
              vd);
          // Figure out the initializer
          final List<Expr> constructorParams = new ArrayList<>();
          if (basicType.getElementType() == BasicType.INT) {
            for (int i = 0; i < basicType.getNumElements(); i++) {
              constructorParams.add(new IntConstantExpr(Integer.toString(
                  generator.nextInt(0xffffff))));
            }
          } else if (basicType.getElementType() == BasicType.UINT) {
            for (int i = 0; i < basicType.getNumElements(); i++) {
              constructorParams.add(new UIntConstantExpr(Integer.toUnsignedString(
                  generator.nextPositiveInt(0xffffff)) + "u"));
            }
          } else if (basicType.getElementType() == BasicType.BOOL) {
            for (int i = 0; i < basicType.getNumElements(); i++) {
              constructorParams.add(new BoolConstantExpr(
                  generator.nextBoolean()));
            }
          } else if (basicType.getElementType() == BasicType.FLOAT) {
            for (int i = 0; i < basicType.getNumElements(); i++) {
              constructorParams.add(new FloatConstantExpr(Float.toString(
                  generator.nextFloat())));
            }
          } else {
            throw new RuntimeException("Unimplemented variable type with "
                + vd.getBaseType().toString() + " " + vdi.getName());
          }
          final Expr initExpr = new TypeConstructorExpr(basicType.toString(), constructorParams);
          vertexShader.get().getMainFunction().getBody().insertStmt(0,
              new ExprStmt(new BinaryExpr(
                  new VariableIdentifierExpr(vdi.getName()),
                  initExpr,
                  BinOp.ASSIGN)));
        }
        // Remove old variable declaration from fragment shader
        tu.removeTopLevelDeclaration(vd);
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
