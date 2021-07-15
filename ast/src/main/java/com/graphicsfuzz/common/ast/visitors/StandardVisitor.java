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

package com.graphicsfuzz.common.ast.visitors;

import com.graphicsfuzz.common.ast.IAstNode;
import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.ast.decl.ArrayInfo;
import com.graphicsfuzz.common.ast.decl.Declaration;
import com.graphicsfuzz.common.ast.decl.DefaultLayout;
import com.graphicsfuzz.common.ast.decl.FunctionDefinition;
import com.graphicsfuzz.common.ast.decl.FunctionPrototype;
import com.graphicsfuzz.common.ast.decl.Initializer;
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
import com.graphicsfuzz.common.ast.expr.LengthExpr;
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
import com.graphicsfuzz.common.ast.type.VoidType;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public abstract class StandardVisitor implements IAstVisitor {

  private int currentDepth;

  public StandardVisitor() {
    currentDepth = 0;
  }

  public VisitationDepth getVistitationDepth() {
    return new VisitationDepth(currentDepth);
  }

  @Override
  public void visit(IAstNode node) {
    if (node == null) {
      throw new RuntimeException("Attempt to visit null node");
    }
    currentDepth++;
    node.accept(this);
    currentDepth--;
  }

  @Override
  public void visitFunctionDefinition(FunctionDefinition functionDefinition) {
    visitChildFromParent(this::visitFunctionPrototype, functionDefinition.getPrototype(),
        functionDefinition);
    visitChildFromParent(this::visitBlockStmt, functionDefinition.getBody(), functionDefinition);
  }

  @Override
  public void visitTranslationUnit(TranslationUnit translationUnit) {
    for (Declaration d : translationUnit.getTopLevelDeclarations()) {
      assert d != null;
      visitChildFromParent(this::visit, d, translationUnit);
    }
  }

  @Override
  public void visitFunctionPrototype(FunctionPrototype functionPrototype) {
    for (int i = 0; i < functionPrototype.getNumParameters(); i++) {
      visitChildFromParent(functionPrototype.getParameters().get(i), functionPrototype);
    }
    visitChildFromParent(functionPrototype.getReturnType(), functionPrototype);
  }

  @Override
  public void visitBlockStmt(BlockStmt stmt) {
    // We make a shallow copy of the children of the block statement, to allow a subclass of the
    // visitor to potentially add or remove children of the original block statement.
    List<Stmt> children = new ArrayList<>();
    children.addAll(stmt.getStmts());
    for (Stmt child : children) {
      visitChildFromParent(child, stmt);
    }
  }

  @Override
  public void visitIfStmt(IfStmt ifStmt) {
    visitChildFromParent(ifStmt.getCondition(), ifStmt);
    visitChildFromParent(ifStmt.getThenStmt(), ifStmt);
    if (ifStmt.hasElseStmt()) {
      visitChildFromParent(ifStmt.getElseStmt(), ifStmt);
    }
  }

  @Override
  public void visitDeclarationStmt(DeclarationStmt declarationStmt) {
    visitChildFromParent(this::visitVariablesDeclaration, declarationStmt.getVariablesDeclaration(),
        declarationStmt);
  }

  @Override
  public void visitVariablesDeclaration(VariablesDeclaration variablesDeclaration) {
    visitChildFromParent(variablesDeclaration.getBaseType(), variablesDeclaration);
    List<VariableDeclInfo> children = new ArrayList<>();
    children.addAll(variablesDeclaration.getDeclInfos());
    for (VariableDeclInfo vd : children) {
      visitChildFromParent(vd, variablesDeclaration);
    }
  }

  @Override
  public void visitPrecisionDeclaration(PrecisionDeclaration precisionDeclaration) {
  }

  @Override
  public void visitInitializer(Initializer initializer) {
    visitChildFromParent(initializer.getExpr(), initializer);
  }

  @Override
  public void visitBinaryExpr(BinaryExpr binaryExpr) {
    visitChildFromParent(binaryExpr.getLhs(), binaryExpr);
    visitChildFromParent(binaryExpr.getRhs(), binaryExpr);
  }

  @Override
  public void visitParenExpr(ParenExpr parenExpr) {
    visitChildFromParent(parenExpr.getExpr(), parenExpr);
  }

  @Override
  public void visitVariableIdentifierExpr(VariableIdentifierExpr variableIdentifierExpr) {
  }

  @Override
  public void visitUnaryExpr(UnaryExpr unaryExpr) {
    visitChildFromParent(unaryExpr.getExpr(), unaryExpr);
  }

  @Override
  public void visitMemberLookupExpr(MemberLookupExpr memberLookupExpr) {
    visitChildFromParent(memberLookupExpr.getStructure(), memberLookupExpr);
  }

  @Override
  public void visitDiscardStmt(DiscardStmt discardStmt) {
  }

  @Override
  public void visitBreakStmt(BreakStmt breakStmt) {
  }

  @Override
  public void visitContinueStmt(ContinueStmt continueStmt) {
  }

  @Override
  public void visitReturnStmt(ReturnStmt returnStmt) {
    if (returnStmt.hasExpr()) {
      visitChildFromParent(returnStmt.getExpr(), returnStmt);
    }
  }

  @Override
  public void visitFunctionCallExpr(FunctionCallExpr functionCallExpr) {
    for (Expr arg : functionCallExpr.getArgs()) {
      visitChildFromParent(arg, functionCallExpr);
    }
  }

  @Override
  public void visitExprStmt(ExprStmt exprStmt) {
    assert exprStmt.getExpr() != null;
    visitChildFromParent(exprStmt.getExpr(), exprStmt);
  }

  @Override
  public void visitTypeConstructorExpr(TypeConstructorExpr typeConstructorExpr) {
    for (Expr e : typeConstructorExpr.getArgs()) {
      visitChildFromParent(e, typeConstructorExpr);
    }
  }

  @Override
  public void visitBasicType(BasicType basicType) {
  }

  @Override
  public void visitSamplerType(SamplerType samplerType) {
  }

  @Override
  public void visitImageType(ImageType imageType) {
  }

  @Override
  public void visitVoidType(VoidType voidType) {
  }

  @Override
  public void visitAtomicIntType(AtomicIntType atomicIntType) {
  }

  @Override
  public void visitQualifiedType(QualifiedType qualifiedType) {
    visitChildFromParent(qualifiedType.getTargetType(), qualifiedType);
  }

  @Override
  public void visitForStmt(ForStmt forStmt) {
    // A ForStmt always has an initializer; it will be a NullStmt in the case of "for( ; cond; inc)"
    visitChildFromParent(forStmt.getInit(), forStmt);
    if (forStmt.hasCondition()) {
      visitChildFromParent(forStmt.getCondition(), forStmt);
    }
    if (forStmt.hasIncrement()) {
      visitChildFromParent(forStmt.getIncrement(), forStmt);
    }
    visitChildFromParent(forStmt.getBody(), forStmt);
  }

  @Override
  public void visitDoStmt(DoStmt doStmt) {
    visitChildFromParent(doStmt.getBody(), doStmt);
    visitChildFromParent(doStmt.getCondition(), doStmt);
  }

  @Override
  public void visitWhileStmt(WhileStmt whileStmt) {
    visitChildFromParent(whileStmt.getCondition(), whileStmt);
    visitChildFromParent(whileStmt.getBody(), whileStmt);
  }

  @Override
  public void visitNullStmt(NullStmt nullStmt) {
  }

  @Override
  public void visitTernaryExpr(TernaryExpr ternaryExpr) {
    visitChildFromParent(ternaryExpr.getTest(), ternaryExpr);
    visitChildFromParent(ternaryExpr.getThenExpr(), ternaryExpr);
    visitChildFromParent(ternaryExpr.getElseExpr(), ternaryExpr);
  }

  @Override
  public void visitArrayInfo(ArrayInfo arrayInfo) {
    for (int i = 0; i < arrayInfo.getDimensionality(); i++) {
      if (arrayInfo.hasSizeExpr(i)) {
        visitChildFromParent(arrayInfo.getSizeExpr(i), arrayInfo);
      }
    }
  }

  @Override
  public void visitVariableDeclInfo(VariableDeclInfo variableDeclInfo) {
    if (variableDeclInfo.hasArrayInfo()) {
      visitChildFromParent(this::visitArrayInfo, variableDeclInfo.getArrayInfo(), variableDeclInfo);
    }
    if (variableDeclInfo.hasInitializer()) {
      visitChildFromParent(variableDeclInfo.getInitializer(), variableDeclInfo);
    }
  }

  @Override
  public void visitParameterDecl(ParameterDecl parameterDecl) {
    visitChildFromParent(parameterDecl.getType(), parameterDecl);
    if (parameterDecl.hasArrayInfo()) {
      // Only visit the parameter's array information if there actually is array information.
      visitChildFromParent(this::visitArrayInfo, parameterDecl.getArrayInfo(), parameterDecl);
    }
  }

  @Override
  public void visitArrayIndexExpr(ArrayIndexExpr arrayIndexExpr) {
    visitChildFromParent(arrayIndexExpr.getArray(), arrayIndexExpr);
    visitChildFromParent(arrayIndexExpr.getIndex(), arrayIndexExpr);
  }

  @Override
  public void visitIntConstantExpr(IntConstantExpr intConstantExpr) {
  }

  @Override
  public void visitUIntConstantExpr(UIntConstantExpr uintConstantExpr) {
  }

  @Override
  public void visitFloatConstantExpr(FloatConstantExpr floatConstantExpr) {
  }

  @Override
  public void visitBoolConstantExpr(BoolConstantExpr boolConstantExpr) {
  }

  @Override
  public void visitStructDefinitionType(StructDefinitionType structDefinitionType) {
    if (structDefinitionType.hasStructNameType()) {
      visitChildFromParent(structDefinitionType.getStructNameType(), structDefinitionType);
    }
    for (String name : structDefinitionType.getFieldNames()) {
      visitChildFromParent(structDefinitionType.getFieldType(name), structDefinitionType);
    }
  }

  @Override
  public void visitArrayConstructorExpr(ArrayConstructorExpr arrayConstructorExpr) {
    for (Expr e : arrayConstructorExpr.getArgs()) {
      visitChildFromParent(e, arrayConstructorExpr);
    }
  }

  @Override
  public void visitArrayType(ArrayType arrayType) {
    visitChildFromParent(arrayType.getBaseType(), arrayType);
  }

  @Override
  public void visitSwitchStmt(SwitchStmt switchStmt) {
    visitChildFromParent(switchStmt.getExpr(), switchStmt);
    visitChildFromParent(switchStmt.getBody(), switchStmt);
  }

  @Override
  public void visitDefaultCaseLabel(DefaultCaseLabel defaultCaseLabel) {
  }

  @Override
  public void visitExprCaseLabel(ExprCaseLabel exprCaseLabel) {
    visitChildFromParent(exprCaseLabel.getExpr(), exprCaseLabel);
  }

  @Override
  public void visitInterfaceBlock(InterfaceBlock interfaceBlock) {
    for (Type type : interfaceBlock.getMemberTypes()) {
      visitChildFromParent(type, interfaceBlock);
    }
  }

  @Override
  public void visitDefaultLayout(DefaultLayout defaultLayout) {

  }

  @Override
  public void visitStructNameType(StructNameType structNameType) {

  }

  @Override
  public void visitExtensionStatement(ExtensionStatement extensionStatement) {

  }

  @Override
  public void visitPragmaStatement(PragmaStatement pragmaStatement) {

  }

  @Override
  public void visitLengthExpr(LengthExpr lengthExpr) {
    visitChildFromParent(lengthExpr.getReceiver(), lengthExpr);
  }

  protected <T extends IAstNode> void visitChildFromParent(Consumer<T> visitorMethod, T child,
      IAstNode parent) {
    visitorMethod.accept(child);
  }

  protected void visitChildFromParent(IAstNode child, IAstNode parent) {
    visitChildFromParent(this::visit, child, parent);
  }

}
