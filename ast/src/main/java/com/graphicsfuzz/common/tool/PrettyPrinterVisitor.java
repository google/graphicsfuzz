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

package com.graphicsfuzz.common.tool;

import com.graphicsfuzz.common.ast.IAstNode;
import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.ast.decl.ArrayInfo;
import com.graphicsfuzz.common.ast.decl.DefaultLayout;
import com.graphicsfuzz.common.ast.decl.FunctionDefinition;
import com.graphicsfuzz.common.ast.decl.FunctionPrototype;
import com.graphicsfuzz.common.ast.decl.InterfaceBlock;
import com.graphicsfuzz.common.ast.decl.ParameterDecl;
import com.graphicsfuzz.common.ast.decl.PrecisionDeclaration;
import com.graphicsfuzz.common.ast.decl.VariableDeclInfo;
import com.graphicsfuzz.common.ast.decl.VariablesDeclaration;
import com.graphicsfuzz.common.ast.expr.ArrayConstructorExpr;
import com.graphicsfuzz.common.ast.expr.ArrayIndexExpr;
import com.graphicsfuzz.common.ast.expr.BinaryExpr;
import com.graphicsfuzz.common.ast.expr.BoolConstantExpr;
import com.graphicsfuzz.common.ast.expr.Expr;
import com.graphicsfuzz.common.ast.expr.FloatConstantExpr;
import com.graphicsfuzz.common.ast.expr.FunctionCallExpr;
import com.graphicsfuzz.common.ast.expr.IntConstantExpr;
import com.graphicsfuzz.common.ast.expr.MemberLookupExpr;
import com.graphicsfuzz.common.ast.expr.ParenExpr;
import com.graphicsfuzz.common.ast.expr.TernaryExpr;
import com.graphicsfuzz.common.ast.expr.TypeConstructorExpr;
import com.graphicsfuzz.common.ast.expr.UIntConstantExpr;
import com.graphicsfuzz.common.ast.expr.UnaryExpr;
import com.graphicsfuzz.common.ast.expr.VariableIdentifierExpr;
import com.graphicsfuzz.common.ast.stmt.BlockStmt;
import com.graphicsfuzz.common.ast.stmt.BreakStmt;
import com.graphicsfuzz.common.ast.stmt.ContinueStmt;
import com.graphicsfuzz.common.ast.stmt.DeclarationStmt;
import com.graphicsfuzz.common.ast.stmt.DefaultCaseLabel;
import com.graphicsfuzz.common.ast.stmt.DiscardStmt;
import com.graphicsfuzz.common.ast.stmt.DoStmt;
import com.graphicsfuzz.common.ast.stmt.ExprCaseLabel;
import com.graphicsfuzz.common.ast.stmt.ExprStmt;
import com.graphicsfuzz.common.ast.stmt.ExtensionStatement;
import com.graphicsfuzz.common.ast.stmt.ForStmt;
import com.graphicsfuzz.common.ast.stmt.IfStmt;
import com.graphicsfuzz.common.ast.stmt.NullStmt;
import com.graphicsfuzz.common.ast.stmt.PragmaStatement;
import com.graphicsfuzz.common.ast.stmt.ReturnStmt;
import com.graphicsfuzz.common.ast.stmt.Stmt;
import com.graphicsfuzz.common.ast.stmt.SwitchStmt;
import com.graphicsfuzz.common.ast.stmt.WhileStmt;
import com.graphicsfuzz.common.ast.type.ArrayType;
import com.graphicsfuzz.common.ast.type.AtomicIntType;
import com.graphicsfuzz.common.ast.type.BasicType;
import com.graphicsfuzz.common.ast.type.ImageType;
import com.graphicsfuzz.common.ast.type.QualifiedType;
import com.graphicsfuzz.common.ast.type.SamplerType;
import com.graphicsfuzz.common.ast.type.StructDefinitionType;
import com.graphicsfuzz.common.ast.type.StructNameType;
import com.graphicsfuzz.common.ast.type.Type;
import com.graphicsfuzz.common.ast.type.TypeQualifier;
import com.graphicsfuzz.common.ast.type.VoidType;
import com.graphicsfuzz.common.ast.visitors.CheckPredicateVisitor;
import com.graphicsfuzz.common.ast.visitors.StandardVisitor;
import com.graphicsfuzz.common.glslversion.ShadingLanguageVersion;
import com.graphicsfuzz.common.util.MacroNames;
import com.graphicsfuzz.common.util.ParseHelper;
import com.graphicsfuzz.util.Constants;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class PrettyPrinterVisitor extends StandardVisitor {

  public static final int DEFAULT_INDENTATION_WIDTH = 1;
  public static final Supplier<String> DEFAULT_NEWLINE_SUPPLIER = () -> "\n";
  private final Supplier<String> newLineSupplier;
  private final int indentationWidth;
  private int indentationCount = 0;
  private final PrintStream out;
  private boolean inFunctionDefinition = false;
  private final boolean emitGraphicsFuzzDefines;
  private final Optional<String> license;
  private final Optional<UniformValueSupplier> uniformValues;

  // Allows different formatting of a declaration when part of the header of a for statement.
  private boolean insideForStatementHeader = false;

  public PrettyPrinterVisitor(PrintStream out) {
    this(out, DEFAULT_INDENTATION_WIDTH,
        DEFAULT_NEWLINE_SUPPLIER,
        false,
        Optional.empty(),
        Optional.empty());
  }

  public PrettyPrinterVisitor(PrintStream out,
                              int indentationWidth,
                              Supplier<String> newLineSupplier,
                              boolean emitGraphicsFuzzDefines,
                              Optional<String> license,
                              Optional<UniformValueSupplier> uniformValues) {
    this.out = out;
    this.indentationWidth = indentationWidth;
    this.newLineSupplier = newLineSupplier;
    this.emitGraphicsFuzzDefines = emitGraphicsFuzzDefines;
    this.license = license;
    this.uniformValues = uniformValues;
  }

  /**
   * Returns, via pretty printing, a string representation of the given node.
   *
   * @param node Node for which string representation is required
   * @return String representation of the node
   */
  public static String prettyPrintAsString(IAstNode node) {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    new PrettyPrinterVisitor(new PrintStream(bytes)).visit(node);
    return new String(bytes.toByteArray(), StandardCharsets.UTF_8);
  }

  /**
   * Returns, via pretty printing, a string representation of the given node.
   *
   * @param node Node for which string representation is required
   * @param uniformValues Supplier that provides values for uniforms
   * @return String representation of the node
   */
  public static String prettyPrintAsString(IAstNode node, UniformValueSupplier uniformValues) {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    new PrettyPrinterVisitor(new PrintStream(bytes),
        DEFAULT_INDENTATION_WIDTH,
        DEFAULT_NEWLINE_SUPPLIER,
        false,
        Optional.empty(),
        Optional.of(uniformValues)).visit(node);
    return new String(bytes.toByteArray(), StandardCharsets.UTF_8);
  }

  public static void emitShader(TranslationUnit shader,
                                Optional<String> license,
                                PrintStream stream,
                                int indentationWidth,
                                Supplier<String> newlineSupplier) {
    emitShader(shader, license, stream, indentationWidth, newlineSupplier, Optional.empty());
  }

  public static void emitShader(TranslationUnit shader,
                                Optional<String> license,
                                PrintStream stream,
                                int indentationWidth,
                                Supplier<String> newlineSupplier,
                                Optional<UniformValueSupplier> uniformValueSupplier) {
    final boolean usesGraphicsFuzzDefines = new CheckPredicateVisitor() {
      @Override
      public void visitFunctionCallExpr(FunctionCallExpr functionCallExpr) {
        if (MacroNames.isGraphicsFuzzMacro(functionCallExpr)) {
          predicateHolds();
        }
        super.visitFunctionCallExpr(functionCallExpr);
      }
    }.test(shader);

    new PrettyPrinterVisitor(stream, indentationWidth, newlineSupplier,
        usesGraphicsFuzzDefines, license, uniformValueSupplier).visit(shader);
  }

  private void newLine() {
    out.append(newLineSupplier.get());
  }

  @Override
  public void visitPrecisionDeclaration(PrecisionDeclaration precisionDeclaration) {
    indent();
    out.append(precisionDeclaration.getText()).append("\n\n");
  }

  @Override
  public void visitDeclarationStmt(DeclarationStmt declarationStmt) {
    if (!insideForStatementHeader) {
      indent();
    }
    super.visitDeclarationStmt(declarationStmt);
    out.append(";");
    if (!insideForStatementHeader) {
      newLine();
    }
  }

  @Override
  public void visitVariablesDeclaration(VariablesDeclaration variablesDeclaration) {

    // If uniform variables are being declared and we have a uniform supplier that provides known
    // values for one or more of the uniforms, we emit details of those known values in comments,
    // one line per uniform.  Sampler uniforms are ignored, as they cannot have associated known
    // values.
    if (variablesDeclaration.getBaseType().hasQualifier(TypeQualifier.UNIFORM)
        && !(variablesDeclaration.getBaseType().getWithoutQualifiers() instanceof SamplerType)
        && uniformValues.isPresent()) {
      // Find the variable declarations for which we have known uniform values.
      final List<VariableDeclInfo> knownUniforms =
          variablesDeclaration.getDeclInfos().stream()
              .filter(uniform -> uniformValues.get()
                  .getValues(uniform.getName()).isPresent()).collect(Collectors.toList());

      // Emit a comment per known uniform.
      for (VariableDeclInfo knownUniform : knownUniforms) {
        final Optional<List<String>> values = uniformValues.get().getValues(knownUniform.getName());
        values.ifPresent(item -> {
          out.append("// Contents of ")
              .append(knownUniform.getName())
              .append(": ");
          if (item.size() > 1) {
            out.append("[");
          }
          out.append(item.stream().map(Object::toString)
              .reduce((item1, item2) -> item1 + ", " + item2).orElse(""));
          if (item.size() > 1) {
            out.append("]");
          }
        });
        out.append("\n");
      }
    }

    final Type baseType = variablesDeclaration.getBaseType();
    visit(baseType);
    out.append(" ");
    boolean first = true;
    for (VariableDeclInfo vdi : variablesDeclaration.getDeclInfos()) {
      if (!first) {
        out.append(", ");
      }
      first = false;
      out.append(vdi.getName());
      if (vdi.hasArrayInfo()) {
        printArrayInfo(vdi.getArrayInfo());
        assert !(baseType instanceof ArrayType);
      } else if (baseType instanceof ArrayType) {
        printArrayInfo(((ArrayType) baseType).getArrayInfo());
      }
      if (vdi.hasInitializer()) {
        out.append(" = ");
        visit(vdi.getInitializer());
      }
    }
  }

  @Override
  public void visitFunctionDefinition(FunctionDefinition functionDefinition) {
    assert !inFunctionDefinition;
    inFunctionDefinition = true;
    super.visitFunctionDefinition(functionDefinition);
    assert inFunctionDefinition;
    inFunctionDefinition = false;
  }

  @Override
  public void visitFunctionPrototype(FunctionPrototype functionPrototype) {
    visit(functionPrototype.getReturnType());
    out.append(" ").append(functionPrototype.getName()).append("(");
    boolean first = true;
    for (ParameterDecl p : functionPrototype.getParameters()) {
      if (!first) {
        out.append(", ");
      }
      first = false;
      visit(p);
    }
    out.append(")");
    if (!inFunctionDefinition) {
      out.append(";");
    }
    newLine();
  }

  @Override
  public void visitParameterDecl(ParameterDecl parameterDecl) {
    visit(parameterDecl.getType());
    if (parameterDecl.getName() != null) {
      out.append(" ").append(parameterDecl.getName());
    }
    if (parameterDecl.hasArrayInfo()) {
      printArrayInfo(parameterDecl.getArrayInfo());
    }
  }

  @Override
  public void visitBlockStmt(BlockStmt stmt) {
    indent();
    out.append("{");
    newLine();
    increaseIndent();
    for (Stmt s : stmt.getStmts()) {
      visit(s);
    }
    decreaseIndent();
    indent();
    out.append("}");
    newLine();
  }

  void indent() {
    for (int i = 0; i < indentationCount; i++) {
      out.append(" ");
    }
  }

  @Override
  public void visitIfStmt(IfStmt ifStmt) {
    indent();
    out.append("if(");
    visit(ifStmt.getCondition());
    out.append(")");
    newLine();
    increaseIndent();
    visit(ifStmt.getThenStmt());
    decreaseIndent();
    if (ifStmt.hasElseStmt()) {
      indent();
      out.append("else");
      newLine();
      increaseIndent();
      visit(ifStmt.getElseStmt());
      decreaseIndent();
    }
  }

  @Override
  public void visitBinaryExpr(BinaryExpr binaryExpr) {
    visit(binaryExpr.getLhs());
    out.append(" ").append(binaryExpr.getOp().getText()).append(" ");
    visit(binaryExpr.getRhs());
  }

  @Override
  public void visitParenExpr(ParenExpr parenExpr) {
    out.append("(");
    visit(parenExpr.getExpr());
    out.append(")");
  }

  @Override
  public void visitVariableIdentifierExpr(VariableIdentifierExpr variableIdentifierExpr) {
    out.append(variableIdentifierExpr.getName());
  }

  @Override
  public void visitUnaryExpr(UnaryExpr unaryExpr) {
    switch (unaryExpr.getOp()) {
      case PRE_INC:
      case PRE_DEC:
      case PLUS:
      case MINUS:
      case BNEG:
      case LNOT:
        out.append(unaryExpr.getOp().getText()).append(" ");
        break;
      case POST_DEC:
      case POST_INC:
        break;
      default:
        assert false : "Unknown unary operator " + unaryExpr.getOp();
    }
    visit(unaryExpr.getExpr());
    switch (unaryExpr.getOp()) {
      case POST_DEC:
      case POST_INC:
        out.append(" ").append(unaryExpr.getOp().getText());
        break;
      case PRE_INC:
      case PRE_DEC:
      case PLUS:
      case MINUS:
      case BNEG:
      case LNOT:
        break;
      default:
        assert false : "Unknown unary operator " + unaryExpr.getOp();
    }
  }

  @Override
  public void visitMemberLookupExpr(MemberLookupExpr memberLookupExpr) {
    visit(memberLookupExpr.getStructure());
    out.append(".").append(memberLookupExpr.getMember());
  }

  @Override
  public void visitIntConstantExpr(IntConstantExpr intConstantExpr) {
    out.append(intConstantExpr.getValue());
  }

  @Override
  public void visitUIntConstantExpr(UIntConstantExpr intConstantExpr) {
    out.append(intConstantExpr.getValue());
  }

  @Override
  public void visitFloatConstantExpr(FloatConstantExpr floatConstantExpr) {
    out.append(floatConstantExpr.getValue());
  }

  @Override
  public void visitBoolConstantExpr(BoolConstantExpr boolConstantExpr) {
    out.append(boolConstantExpr.toString());
  }

  @Override
  public void visitBreakStmt(BreakStmt breakStmt) {
    indent();
    out.append("break");
    out.append(";");
    newLine();
  }

  @Override
  public void visitContinueStmt(ContinueStmt continueStmt) {
    indent();
    out.append("continue");
    out.append(";");
    newLine();
  }

  @Override
  public void visitDiscardStmt(DiscardStmt discardStmt) {
    indent();
    out.append("discard");
    out.append(";");
    newLine();
  }

  @Override
  public void visitReturnStmt(ReturnStmt returnStmt) {
    indent();
    out.append("return");
    if (returnStmt.hasExpr()) {
      out.append(" ");
      visit(returnStmt.getExpr());
    }
    out.append(";");
    newLine();
  }

  @Override
  public void visitExprStmt(ExprStmt exprStmt) {
    indent();
    visit(exprStmt.getExpr());
    out.append(";");
    newLine();
  }

  @Override
  public void visitFunctionCallExpr(FunctionCallExpr functionCallExpr) {
    out.append(functionCallExpr.getCallee()).append("(");
    boolean first = true;
    for (Expr e : functionCallExpr.getArgs()) {
      if (!first) {
        out.append(", ");
      }
      first = false;
      visit(e);
    }
    out.append(")");
  }

  @Override
  public void visitTypeConstructorExpr(TypeConstructorExpr typeConstructorExpr) {
    out.append(typeConstructorExpr.getTypename()).append("(");
    boolean first = true;
    for (Expr e : typeConstructorExpr.getArgs()) {
      if (!first) {
        out.append(", ");
      }
      first = false;
      visit(e);
    }
    out.append(")");
  }

  @Override
  public void visitQualifiedType(QualifiedType qualifiedType) {
    for (TypeQualifier q : qualifiedType.getQualifiers()) {
      out.append(q.toString()).append(" ");
    }
    visit(qualifiedType.getTargetType());
  }

  @Override
  public void visitBasicType(BasicType basicType) {
    out.append(basicType.toString());
  }

  @Override
  public void visitSamplerType(SamplerType samplerType) {
    out.append(samplerType.toString());
  }

  @Override
  public void visitImageType(ImageType imageType) {
    out.append(imageType.toString());
  }

  @Override
  public void visitVoidType(VoidType voidType) {
    out.append(voidType.toString());
  }

  @Override
  public void visitAtomicIntType(AtomicIntType atomicIntType) {
    out.append(atomicIntType.toString());
  }

  @Override
  public void visitNullStmt(NullStmt nullStmt) {
    indent();
    out.append(";");
    newLine();
  }

  @Override
  public void visitWhileStmt(WhileStmt whileStmt) {
    indent();
    out.append("while(");
    visit(whileStmt.getCondition());
    out.append(")");
    newLine();
    increaseIndent();
    visit(whileStmt.getBody());
    decreaseIndent();
  }

  @Override
  public void visitForStmt(ForStmt forStmt) {
    insideForStatementHeader = true;
    indent();
    out.append("for(");
    visit(forStmt.getInit());
    out.append(" ");
    if (forStmt.hasCondition()) {
      visit(forStmt.getCondition());
    }
    out.append("; ");
    if (forStmt.hasIncrement()) {
      visit(forStmt.getIncrement());
    }
    out.append(")");
    newLine();
    insideForStatementHeader = false;
    increaseIndent();
    visit(forStmt.getBody());
    decreaseIndent();
  }

  @Override
  public void visitDoStmt(DoStmt doStmt) {
    indent();
    out.append("do");
    newLine();
    increaseIndent();
    visit(doStmt.getBody());
    decreaseIndent();
    indent();
    out.append("while(");
    visit(doStmt.getCondition());
    out.append(");");
    newLine();
  }

  @Override
  public void visitTernaryExpr(TernaryExpr ternaryExpr) {
    visit(ternaryExpr.getTest());
    out.append(" ? ");
    visit(ternaryExpr.getThenExpr());
    out.append(" : ");
    visit(ternaryExpr.getElseExpr());
  }

  private boolean maybeEmitKnownUniformMacro(ArrayIndexExpr arrayIndexExpr,
                                           String knownUniformArrayName, String prefix) {
    /* If arrayIndexExpr indexes into a literal-to-uniform array, output a macro name instead. */
    if (arrayIndexExpr.getArray() instanceof VariableIdentifierExpr
        && ((VariableIdentifierExpr) arrayIndexExpr.getArray()).getName()
        .contains(knownUniformArrayName)
        && uniformValues.isPresent()
        && uniformValues.get().getValues(knownUniformArrayName).isPresent()) {

      int index = ((IntConstantExpr) arrayIndexExpr.getIndex()).getNumericValue();
      List<String> values = uniformValues.get().getValues(knownUniformArrayName).get();
      out.append("_").append(prefix).append("_").append(values.get(index).replace(".", "_"));
      return true;
    }
    return false;
  }

  @Override
  public void visitArrayIndexExpr(ArrayIndexExpr arrayIndexExpr) {
    if (maybeEmitKnownUniformMacro(arrayIndexExpr, Constants.INT_LITERAL_UNIFORM_VALUES, "int")) {
      return;
    }
    if (maybeEmitKnownUniformMacro(arrayIndexExpr, Constants.UINT_LITERAL_UNIFORM_VALUES, "uint")) {
      return;
    }
    if (maybeEmitKnownUniformMacro(arrayIndexExpr, Constants.FLOAT_LITERAL_UNIFORM_VALUES,
        "float")) {
      return;
    }

    visit(arrayIndexExpr.getArray());
    out.append("[");
    visit(arrayIndexExpr.getIndex());
    out.append("]");
  }

  @Override
  public void visitStructNameType(StructNameType structNameType) {
    out.append(structNameType.getName());
  }

  @Override
  public void visitArrayType(ArrayType arrayType) {
    // Do not generate array info, as this has to come after the associated variable name
    visit(arrayType.getBaseType());
  }

  @Override
  public void visitArrayConstructorExpr(ArrayConstructorExpr arrayConstructorExpr) {
    final List<Expr> dimensions = new ArrayList<>();
    ArrayType arrayType = arrayConstructorExpr.getArrayType();
    while (true) {
      assert arrayType.getArrayInfo().getDimensionality() == 1;
      dimensions.add(arrayType.getArrayInfo().getSizeExpr(0));
      if (!(arrayType.getBaseType() instanceof ArrayType)) {
        break;
      }
      arrayType = (ArrayType) arrayType.getBaseType();
    }
    visit(arrayType.getBaseType());
    for (Expr dimension : dimensions) {
      out.append("[");
      visit(dimension);
      out.append("]");
    }
    out.append("(");
    boolean first = true;
    for (Expr e : arrayConstructorExpr.getArgs()) {
      if (!first) {
        out.append(", ");
      }
      first = false;
      visit(e);
    }
    out.append(")");
  }

  @Override
  public void visitStructDefinitionType(StructDefinitionType structDefinitionType) {
    out.append("struct ");
    if (structDefinitionType.hasStructNameType()) {
      visit(structDefinitionType.getStructNameType());
      out.append(" ");
    }
    out.append("{");
    newLine();
    increaseIndent();
    for (String name : structDefinitionType.getFieldNames()) {
      indent();
      visit(structDefinitionType.getFieldType(name));
      out.append(" ").append(name);
      emitArrayInfoAfterType(structDefinitionType.getFieldType(name));
      out.append(";");
      newLine();
    }
    decreaseIndent();
    indent();
    out.append("}");
  }

  @Override
  public void visitSwitchStmt(SwitchStmt switchStmt) {
    indent();
    out.append("switch(");
    visit(switchStmt.getExpr());
    out.append(")");
    newLine();
    increaseIndent();
    visitBlockStmt(switchStmt.getBody());
    decreaseIndent();
  }

  @Override
  public void visitDefaultCaseLabel(DefaultCaseLabel defaultCaseLabel) {
    indent();
    out.append("default:");
    newLine();
  }

  @Override
  public void visitExprCaseLabel(ExprCaseLabel exprCaseLabel) {
    indent();
    out.append("case ");
    visit(exprCaseLabel.getExpr());
    out.append(":");
    newLine();
  }

  @Override
  public void visitInterfaceBlock(InterfaceBlock interfaceBlock) {

    // If a uniform block is being declared and we have a uniform supplier that provides known
    // values for the uniform wrapped in, we emit details of those known values in comments.
    if (interfaceBlock.getInterfaceQualifier().equals(TypeQualifier.UNIFORM)) {
      // It is guaranteed that a block, for which getInterfaceQualifier() returns "uniform",
      // has a single field.
      assert interfaceBlock.getMemberNames().size() == 1;

      final String memberName = interfaceBlock.getMemberNames().get(0);
      if (uniformValues.isPresent()) {
        final Optional<List<String>> values = uniformValues.get().getValues(memberName);
        values.ifPresent(item -> {
          out.append("// Contents of ")
              .append(memberName)
              .append(": ");
          if (item.size() > 1) {
            out.append("[");
          }
          out.append(item.stream().map(Object::toString)
              .reduce((item1, item2) -> item1 + ", " + item2).orElse(""));
          if (item.size() > 1) {
            out.append("]");
          }
          newLine();
        });
      }
    }

    indent();
    if (interfaceBlock.hasLayoutQualifierSequence()) {
      out.append(interfaceBlock.getLayoutQualifierSequence().toString()).append(" ");
    }
    out.append(interfaceBlock.getInterfaceQualifier().toString())
        .append(" ")
        .append(interfaceBlock.getStructName())
        .append(" {");
    newLine();

    increaseIndent();

    for (String memberName : interfaceBlock.getMemberNames()) {
      indent();
      final Type memberType = interfaceBlock.getMemberType(memberName).get();
      visit(memberType);
      out.append(" ").append(memberName);
      emitArrayInfoAfterType(memberType);
      out.append(";");
      newLine();
    }

    decreaseIndent();

    out.append("}");
    if (interfaceBlock.hasIdentifierName()) {
      out.append(" ").append(interfaceBlock.getInstanceName());
    }
    out.append(";");
    newLine();
  }

  @Override
  public void visitDefaultLayout(DefaultLayout defaultLayout) {
    indent();
    out.append(defaultLayout.getLayoutQualifierSequence().toString());
    out.append(" ");
    out.append(defaultLayout.getTypeQualifier().toString());
    out.append(";");
    newLine();
  }

  private void emitKnownUniformDefines(String knownUniformArrayName, String prefix) {
    final Optional<List<String>> values =
        uniformValues.get().getValues(knownUniformArrayName);
    if (values.isPresent()) {
      int index = 0;
      for (String value : values.get()) {
        out.append("#define").append(" ").append("_").append(prefix).append("_")
            .append(value.replace(".", "_"))
            .append(" ").append(knownUniformArrayName)
            .append("[").append(String.valueOf(index)).append("]");
        newLine();
        index++;
      }

    }
  }

  @Override
  public void visitTranslationUnit(TranslationUnit translationUnit) {

    if (translationUnit.hasShadingLanguageVersion()) {
      out.append("#version ")
          .append(translationUnit.getShadingLanguageVersion().getVersionString())
          .append("\n");
      if (translationUnit.getShadingLanguageVersion().isWebGl()) {
        out.append("//WebGL\n");
      }
    }

    license.ifPresent(s -> out.append(s).append("\n"));

    if (emitGraphicsFuzzDefines) {
      emitGraphicsFuzzDefines(out, translationUnit.getShadingLanguageVersion());
    }

    // Emit a #define for each value in the literal-to-uniform arrays.
    if (uniformValues.isPresent()) {
      emitKnownUniformDefines(Constants.INT_LITERAL_UNIFORM_VALUES, "int");
      emitKnownUniformDefines(Constants.UINT_LITERAL_UNIFORM_VALUES, "uint");
      emitKnownUniformDefines(Constants.FLOAT_LITERAL_UNIFORM_VALUES, "float");
      newLine();
    }

    super.visitTranslationUnit(translationUnit);
  }

  @Override
  public void visitPragmaStatement(PragmaStatement pragmaStatement) {
    super.visitPragmaStatement(pragmaStatement);
    out.append(pragmaStatement.getText());
  }

  @Override
  public void visitExtensionStatement(ExtensionStatement extensionStatement) {
    super.visitExtensionStatement(extensionStatement);
    out.append("#extension ")
        .append(extensionStatement.getExtensionName())
        .append(" : ").append(extensionStatement.getExtensionStatus())
        .append("\n");
  }

  private void decreaseIndent() {
    indentationCount -= indentationWidth;
  }

  private void increaseIndent() {
    indentationCount += indentationWidth;
  }

  @Override
  public String toString() {
    return out.toString();
  }

  @Override
  protected <T extends IAstNode> void visitChildFromParent(Consumer<T> visitorMethod, T child,
                                                           IAstNode parent) {
    super.visitChildFromParent(visitorMethod, child, parent);
    if (parent instanceof TranslationUnit && child instanceof VariablesDeclaration) {
      out.append(";");
      newLine();
      newLine();
    }
  }

  /**
   * Used by test classes to mimic default indentation.
   *
   * @param level The number of times to indent.
   * @return An appropriate string of blanks.
   */
  public static String defaultIndent(int level) {
    StringBuilder result = new StringBuilder();
    for (int i = 0; i < level; i++) {
      for (int j = 0; j < PrettyPrinterVisitor.DEFAULT_INDENTATION_WIDTH; j++) {
        result.append(" ");
      }
    }
    return result.toString();
  }

  public static void emitGraphicsFuzzDefines(PrintStream out,
                                             ShadingLanguageVersion shadingLanguageVersion) {
    out.append("\n");
    out.append("#ifndef REDUCER\n");
    out.append("#define ").append(Constants.GLF_ZERO).append("(X, Y)                   (Y)\n");
    out.append("#define ").append(Constants.GLF_ONE).append("(X, Y)                    (Y)\n");
    out.append("#define ").append(Constants.GLF_FALSE).append("(X, Y)                  (Y)\n");
    out.append("#define ").append(Constants.GLF_TRUE).append("(X, Y)                   (Y)\n");
    out.append("#define ").append(Constants.GLF_IDENTITY).append("(X, Y)               (Y)\n");
    out.append("#define ").append(Constants.GLF_DEAD).append("(X)                      (X)\n");
    out.append("#define ").append(Constants.GLF_FUZZED).append("(X)                    (X)\n");
    out.append("#define ").append(Constants.GLF_WRAPPED_LOOP).append("(X)              X\n");
    out.append("#define ").append(Constants.GLF_WRAPPED_IF_TRUE).append("(X)           X\n");
    out.append("#define ").append(Constants.GLF_WRAPPED_IF_FALSE).append("(X)          X\n");
    out.append("#define ").append(Constants.GLF_SWITCH).append("(X)                    X\n");

    // The preferred way to make array accesses in bounds is to use 'clamp'.  However, 'clamp'
    // on integer types is not available in all shading language versions.  When it is not
    // available, we resort to a solution based on the ternary operator.  This has the disadvantage
    // of evaluating the array index multiple times, so that if it has side effects these will
    // occur multiple times (potentially leading to the access ending up being out of bounds if the
    // side effects change the index).  If deemed worth prioritizing, this could be worked around
    // by declaring appropriate 'clamp' functions (but with appropriately obfuscated names to avoid
    // collisions with user-defined integer clamp functions).

    if (shadingLanguageVersion.supportedClampInt()) {
      out.append("#define ").append(Constants.GLF_MAKE_IN_BOUNDS_INT).append("(IDX, SZ)  clamp"
          + "(IDX, 0, SZ - 1)").append("\n");
    } else {
      out.append("#define ").append(Constants.GLF_MAKE_IN_BOUNDS_INT).append("(IDX, SZ)  ((IDX) <"
          + " 0 ? 0 : (").append("(IDX) >= SZ ? SZ - 1 : (IDX)))\n");
    }

    if (shadingLanguageVersion.supportedClampUint()) {
      out.append("#define ").append(Constants.GLF_MAKE_IN_BOUNDS_UINT).append("(IDX, SZ) clamp"
          + "(IDX, 0u, SZ - ").append("1u)\n");
    } else {
      out.append("#define ").append(Constants.GLF_MAKE_IN_BOUNDS_UINT).append("(IDX, SZ)  ((IDX) "
          + ">= SZ ? SZ - ").append("1u : (IDX))\n");
    }

    out.append("#endif\n");
    out.append("\n");
    out.append(ParseHelper.END_OF_GRAPHICSFUZZ_DEFINES).append("\n");
  }

  private void printArrayInfo(ArrayInfo arrayInfo) {
    for (int i = 0; i < arrayInfo.getDimensionality(); i++) {
      out.append("[");
      if (arrayInfo.hasSizeExpr(i)) {
        visit(arrayInfo.getSizeExpr(i));
      }
      out.append("]");
    }
  }

  private void emitArrayInfoAfterType(Type type) {
    if (!(type.getWithoutQualifiers() instanceof ArrayType)) {
      return;
    }
    for (ArrayType arrayType = (ArrayType) type.getWithoutQualifiers();
         arrayType != null; arrayType =
             arrayType.getBaseType().getWithoutQualifiers() instanceof ArrayType
                 ? (ArrayType) arrayType.getBaseType().getWithoutQualifiers() : null) {
      assert (arrayType.getArrayInfo().getDimensionality() == 1);
      out.append("[");
      if (arrayType.getArrayInfo().hasSizeExpr(0)) {
        visit(arrayType.getArrayInfo().getSizeExpr(0));
      }
      out.append("]");
    }
  }

}
