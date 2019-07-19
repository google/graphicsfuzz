package com.graphicsfuzz.generator.tool;

import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.ast.decl.FunctionDefinition;
import com.graphicsfuzz.common.ast.decl.FunctionPrototype;
import com.graphicsfuzz.common.ast.decl.PrecisionDeclaration;
import com.graphicsfuzz.common.ast.expr.BinOp;
import com.graphicsfuzz.common.ast.expr.BinaryExpr;
import com.graphicsfuzz.common.ast.expr.Expr;
import com.graphicsfuzz.common.ast.expr.FloatConstantExpr;
import com.graphicsfuzz.common.ast.expr.TypeConstructorExpr;
import com.graphicsfuzz.common.ast.expr.VariableIdentifierExpr;
import com.graphicsfuzz.common.ast.stmt.BlockStmt;
import com.graphicsfuzz.common.ast.stmt.ExprStmt;
import com.graphicsfuzz.common.ast.stmt.Stmt;
import com.graphicsfuzz.common.ast.type.BasicType;
import com.graphicsfuzz.common.ast.type.VoidType;
import com.graphicsfuzz.common.glslversion.ShadingLanguageVersion;
import com.graphicsfuzz.common.tool.PrettyPrinterVisitor;
import com.graphicsfuzz.common.util.GlslParserException;
import com.graphicsfuzz.common.util.IRandom;
import com.graphicsfuzz.common.util.ParseHelper;
import com.graphicsfuzz.common.util.ParseTimeoutException;
import com.graphicsfuzz.common.util.PipelineInfo;
import com.graphicsfuzz.common.util.RandomWrapper;
import com.graphicsfuzz.generator.mutateapi.FactManager;
import com.graphicsfuzz.generator.mutateapi.PrimitiveValue;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import net.sourceforge.argparse4j.inf.ArgumentParserException;

public class ShaderGenerator {

  public static void mainHelper(String[] args) throws ArgumentParserException, IOException,
      ParseTimeoutException, InterruptedException, GlslParserException {

    final TranslationUnit tu = new TranslationUnit(Optional.of(ShadingLanguageVersion.ESSL_300),
        Arrays.asList(
            new PrecisionDeclaration("precision mediump float;"),
            new FunctionDefinition(
                new FunctionPrototype("main", VoidType.VOID, Collections.emptyList()),
                new BlockStmt(new ArrayList<>(), false))));

    final PipelineInfo pipelineInfo = new PipelineInfo();
    final IRandom generator = new RandomWrapper(0);
    FactManager factManager = new FactManager(null);

    ExprStmt colorAssignment = new ExprStmt(null);

    Expr rValue = FactManager.generateExpr(tu,
        pipelineInfo,
        factManager,
        tu.getMainFunction(),
        colorAssignment,
        new PrimitiveValue(BasicType.FLOAT, Arrays.asList(Optional.of(0.5))),
        generator);

    Expr gValue = FactManager.generateExpr(tu,
        pipelineInfo,
        factManager,
        tu.getMainFunction(),
        colorAssignment,
        new PrimitiveValue(BasicType.FLOAT, Arrays.asList(Optional.of(0.2))),
        generator);

    colorAssignment.setExpr(new BinaryExpr(new VariableIdentifierExpr("_GLF_color")
        , new TypeConstructorExpr("vec4",
        rValue,
        gValue,
        new FloatConstantExpr("0.0"),
        new FloatConstantExpr("1.0")),
        BinOp.ASSIGN));

    tu.getMainFunction().getBody().addStmt(colorAssignment);
    System.out.println(PrettyPrinterVisitor.prettyPrintAsString(tu));
  }


  private static Expr generateExpr(TranslationUnit tu, PipelineInfo pipelineInfo,
                                   FunctionDefinition mainFunction, Float rValue) {

    return new FloatConstantExpr("0.0");
  }


  public static void main(String[] args) {
    try {
      mainHelper(args);
    } catch (ArgumentParserException exception) {
      exception.getParser().handleError(exception);
      System.exit(1);
    } catch (IOException | ParseTimeoutException | InterruptedException
        | GlslParserException exception) {
      exception.printStackTrace();
      System.exit(1);
    }
  }
}
