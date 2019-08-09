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

package com.graphicsfuzz.generator.knownvaluegeneration;

import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.ast.decl.FunctionDefinition;
import com.graphicsfuzz.common.ast.decl.FunctionPrototype;
import com.graphicsfuzz.common.ast.decl.PrecisionDeclaration;
import com.graphicsfuzz.common.ast.decl.VariableDeclInfo;
import com.graphicsfuzz.common.ast.decl.VariablesDeclaration;
import com.graphicsfuzz.common.ast.expr.BinOp;
import com.graphicsfuzz.common.ast.expr.BinaryExpr;
import com.graphicsfuzz.common.ast.expr.Expr;
import com.graphicsfuzz.common.ast.expr.TypeConstructorExpr;
import com.graphicsfuzz.common.ast.expr.VariableIdentifierExpr;
import com.graphicsfuzz.common.ast.stmt.BlockStmt;
import com.graphicsfuzz.common.ast.stmt.ExprStmt;
import com.graphicsfuzz.common.ast.stmt.NullStmt;
import com.graphicsfuzz.common.ast.stmt.Stmt;
import com.graphicsfuzz.common.ast.type.BasicType;
import com.graphicsfuzz.common.ast.type.LayoutQualifierSequence;
import com.graphicsfuzz.common.ast.type.LocationLayoutQualifier;
import com.graphicsfuzz.common.ast.type.QualifiedType;
import com.graphicsfuzz.common.ast.type.TypeQualifier;
import com.graphicsfuzz.common.ast.type.VoidType;
import com.graphicsfuzz.common.glslversion.ShadingLanguageVersion;
import com.graphicsfuzz.common.transformreduce.GlslShaderJob;
import com.graphicsfuzz.common.transformreduce.ShaderJob;
import com.graphicsfuzz.common.util.IRandom;
import com.graphicsfuzz.common.util.PipelineInfo;
import com.graphicsfuzz.common.util.RandomWrapper;
import com.graphicsfuzz.common.util.ShaderJobFileOperations;
import com.graphicsfuzz.util.ArgsUtil;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KnownValueShaderGenerator {

  private static final Logger LOGGER = LoggerFactory.getLogger(KnownValueShaderGenerator.class);

  private static Namespace parse(String[] args) throws ArgumentParserException {
    ArgumentParser parser = ArgumentParsers.newArgumentParser("GlslGenerate")
        .defaultHelp(true)
        .description("Generate a known value shader with the desired RGBA color.");

    parser.addArgument("r")
        .help("Floating point number of red value.")
        .type(Float.class);

    parser.addArgument("g")
        .help("Floating point number of green value.")
        .type(Float.class);

    parser.addArgument("b")
        .help("Floating point number of blue value.")
        .type(Float.class);

    parser.addArgument("a")
        .help("Floating point number of alpha value.")
        .type(Float.class);

    parser.addArgument("--version")
        .help("Shading language version of a generated fragment shader.")
        .setDefault("410")
        .type(String.class);

    parser.addArgument("--seed")
        .help("Seed (unsigned 64 bit long integer) for the random number generator.")
        .type(String.class);

    parser.addArgument("--output-dir")
        .help("Output directory for the generated shader, it not specified the current directory "
            + "is used.")
        .type(File.class);

    return parser.parseArgs(args);
  }

  public static void mainHelper(String[] args) throws ArgumentParserException, IOException {
    final Namespace ns = parse(args);
    final float rFloat = ns.getFloat("r");
    final float gFloat = ns.getFloat("g");
    final float bFloat = ns.getFloat("b");
    final float aFloat = ns.getFloat("a");
    final IRandom generator = new RandomWrapper(ArgsUtil.getSeedArgument(ns));
    final File outputDir = ns.get("output_dir") == null ? new File(".") : ns.get("output_dir");
    final String version = ns.getString("version");
    final File shaderJobFile = new File(".", "knownvalue_shader" + ".json");
    final ShaderJobFileOperations fileOps = new ShaderJobFileOperations();
    fileOps.forceMkdir(outputDir);

    final TranslationUnit tu =
        new TranslationUnit(Optional.of(ShadingLanguageVersion.fromVersionString(version)),
            Arrays.asList(
                new PrecisionDeclaration("precision highp float;"),
                new PrecisionDeclaration("precision highp int;"),
                new VariablesDeclaration(new QualifiedType(BasicType.VEC4,
                    Arrays.asList(new LayoutQualifierSequence(
                        new LocationLayoutQualifier(0)), TypeQualifier.SHADER_OUTPUT)),
                    new VariableDeclInfo("_GLF_color", null, null)),
                new FunctionDefinition(
                    new FunctionPrototype("main", VoidType.VOID, Collections.emptyList()),
                    new BlockStmt(new ArrayList<>(), false))));

    final PipelineInfo pipelineInfo = new PipelineInfo();
    final FactManager globalFactManager = new FactManager(null);

    // A placeholder statement for what will eventually be the color assignment.
    final Stmt placeholderForColorAssignment = new NullStmt();
    tu.getMainFunction().getBody().addStmt(placeholderForColorAssignment);

    LOGGER.info("About to generate the known value fragment shader"
        + " with the parameters R = " + rFloat + ", G = " + gFloat + ", B = " + bFloat + " and"
        + " A = " + aFloat + ".");

    final ExpressionGenerator expressionGenerator = new
        ExpressionGenerator(tu, pipelineInfo, generator, globalFactManager);
    final FactManager mainFactManager = globalFactManager.newScope();
    final Expr rValue = expressionGenerator.generateExpr(
        mainFactManager,
        tu.getMainFunction(),
        placeholderForColorAssignment,
        new NumericValue(BasicType.FLOAT, Optional.of(rFloat)));

    final Expr gValue = expressionGenerator.generateExpr(
        mainFactManager,
        tu.getMainFunction(),
        placeholderForColorAssignment,
        new NumericValue(BasicType.FLOAT, Optional.of(gFloat)));

    final Expr bValue = expressionGenerator.generateExpr(
        mainFactManager,
        tu.getMainFunction(),
        placeholderForColorAssignment,
        new NumericValue(BasicType.FLOAT, Optional.of(bFloat)));

    final Expr aValue = expressionGenerator.generateExpr(
        mainFactManager,
        tu.getMainFunction(),
        placeholderForColorAssignment,
        new NumericValue(BasicType.FLOAT, Optional.of(aFloat))
    );

    tu.getMainFunction().getBody().replaceChild(placeholderForColorAssignment,
        new ExprStmt(new BinaryExpr(new VariableIdentifierExpr("gl_FragColor"),
            new TypeConstructorExpr("vec4",
                rValue,
                gValue,
                bValue,
                aValue
            ), BinOp.ASSIGN)));

    final ShaderJob newShaderJob = new GlslShaderJob(Optional.empty(), new PipelineInfo(), tu);
    fileOps.writeShaderJobFile(newShaderJob, shaderJobFile);
    LOGGER.info("Generation complete.");
  }

  public static void main(String[] args) {
    try {
      mainHelper(args);
    } catch (ArgumentParserException exception) {
      exception.getParser().handleError(exception);
      System.exit(1);
    } catch (IOException exception) {
      exception.printStackTrace();
      System.exit(1);
    }
  }
}
