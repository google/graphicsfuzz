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
import com.graphicsfuzz.common.ast.type.VoidType;

public interface IAstVisitor {

  void visit(IAstNode node);

  void visitFunctionDefinition(FunctionDefinition functionDefinition);

  void visitTranslationUnit(TranslationUnit translationUnit);

  void visitBlockStmt(BlockStmt stmt);

  void visitFunctionPrototype(FunctionPrototype functionPrototype);

  void visitIfStmt(IfStmt ifStmt);

  void visitDeclarationStmt(DeclarationStmt declarationStmt);

  void visitVariablesDeclaration(VariablesDeclaration variablesDeclaration);

  void visitPrecisionDeclaration(PrecisionDeclaration precisionDeclaration);

  void visitInitializer(Initializer initializer);

  void visitBinaryExpr(BinaryExpr binaryExpr);

  void visitParenExpr(ParenExpr parenExpr);

  void visitVariableIdentifierExpr(VariableIdentifierExpr variableIdentifierExpr);

  void visitUnaryExpr(UnaryExpr unaryExpr);

  void visitMemberLookupExpr(MemberLookupExpr memberLookupExpr);

  void visitDiscardStmt(DiscardStmt discardStmt);

  void visitBreakStmt(BreakStmt breakStmt);

  void visitContinueStmt(ContinueStmt continueStmt);

  void visitReturnStmt(ReturnStmt returnStmt);

  void visitFunctionCallExpr(FunctionCallExpr functionCallExpr);

  void visitExprStmt(ExprStmt exprStmt);

  void visitTypeConstructorExpr(TypeConstructorExpr typeConstructorExpr);

  void visitBasicType(BasicType basicType);

  void visitSamplerType(SamplerType samplerType);

  void visitImageType(ImageType imageType);

  void visitVoidType(VoidType voidType);

  void visitAtomicIntType(AtomicIntType atomicIntType);

  void visitQualifiedType(QualifiedType qualifiedType);

  void visitForStmt(ForStmt forStmt);

  void visitNullStmt(NullStmt nullStmt);

  void visitDoStmt(DoStmt doStmt);

  void visitWhileStmt(WhileStmt whileStmt);

  void visitTernaryExpr(TernaryExpr ternaryExpr);

  void visitParameterDecl(ParameterDecl parameterDecl);

  void visitArrayInfo(ArrayInfo arrayInfo);

  void visitVariableDeclInfo(VariableDeclInfo variableDeclInfo);

  void visitArrayIndexExpr(ArrayIndexExpr arrayIndexExpr);

  void visitIntConstantExpr(IntConstantExpr intConstantExpr);

  void visitUIntConstantExpr(UIntConstantExpr uintConstantExpr);

  void visitFloatConstantExpr(FloatConstantExpr floatConstantExpr);

  void visitBoolConstantExpr(BoolConstantExpr boolConstantExpr);

  void visitStructDefinitionType(StructDefinitionType structDefinitionType);

  void visitArrayConstructorExpr(ArrayConstructorExpr arrayConstructorExpr);

  void visitArrayType(ArrayType arrayType);

  void visitSwitchStmt(SwitchStmt switchStmt);

  void visitDefaultCaseLabel(DefaultCaseLabel defaultCaseLabel);

  void visitExprCaseLabel(ExprCaseLabel exprCaseLabel);

  void visitInterfaceBlock(InterfaceBlock interfaceBlock);

  void visitDefaultLayout(DefaultLayout defaultLayout);

  void visitStructNameType(StructNameType structNameType);

  void visitExtensionStatement(ExtensionStatement extensionStatement);

  void visitPragmaStatement(PragmaStatement pragmaStatement);

  void visitLengthExpr(LengthExpr lengthExpr);

}
