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

    void visitArrayConstructorExpr(ArrayConstructorExpr arrayConstructorExpr);

    void visitArrayIndexExpr(ArrayIndexExpr arrayIndexExpr);

    void visitArrayInfo(ArrayInfo arrayInfo);

    void visitArrayType(ArrayType arrayType);

    void visitAtomicIntType(AtomicIntType atomicIntType);

    void visitBasicType(BasicType basicType);

    void visitBinaryExpr(BinaryExpr binaryExpr);

    void visitBlockStmt(BlockStmt stmt);

    void visitBoolConstantExpr(BoolConstantExpr boolConstantExpr);

    void visitBreakStmt(BreakStmt breakStmt);

    void visitContinueStmt(ContinueStmt continueStmt);

    void visitDeclarationStmt(DeclarationStmt declarationStmt);

    void visitDefaultCaseLabel(DefaultCaseLabel defaultCaseLabel);

    void visitDefaultLayout(DefaultLayout defaultLayout);

    void visitDiscardStmt(DiscardStmt discardStmt);

    void visitDoStmt(DoStmt doStmt);

    void visitExprCaseLabel(ExprCaseLabel exprCaseLabel);

    void visitExprStmt(ExprStmt exprStmt);

    void visitExtensionStatement(ExtensionStatement extensionStatement);

    void visitFloatConstantExpr(FloatConstantExpr floatConstantExpr);

    void visitForStmt(ForStmt forStmt);

    void visitFunctionCallExpr(FunctionCallExpr functionCallExpr);

    void visitFunctionDefinition(FunctionDefinition functionDefinition);

    void visitFunctionPrototype(FunctionPrototype functionPrototype);

    void visitIfStmt(IfStmt ifStmt);

    void visitImageType(ImageType imageType);

    void visitInitializer(Initializer initializer);

    void visitIntConstantExpr(IntConstantExpr intConstantExpr);

    void visitInterfaceBlock(InterfaceBlock interfaceBlock);

    void visitMemberLookupExpr(MemberLookupExpr memberLookupExpr);

    void visitNullStmt(NullStmt nullStmt);

    void visitParameterDecl(ParameterDecl parameterDecl);

    void visitParenExpr(ParenExpr parenExpr);

    void visitPragmaStatement(PragmaStatement pragmaStatement);

    void visitPrecisionDeclaration(PrecisionDeclaration precisionDeclaration);

    void visitQualifiedType(QualifiedType qualifiedType);

    void visitReturnStmt(ReturnStmt returnStmt);

    void visitSamplerType(SamplerType samplerType);

    void visitStructDefinitionType(StructDefinitionType structDefinitionType);

    void visitStructNameType(StructNameType structNameType);

    void visitSwitchStmt(SwitchStmt switchStmt);

    void visitTernaryExpr(TernaryExpr ternaryExpr);

    void visitTranslationUnit(TranslationUnit translationUnit);

    void visitTypeConstructorExpr(TypeConstructorExpr typeConstructorExpr);

    void visitUIntConstantExpr(UIntConstantExpr uintConstantExpr);

    void visitUnaryExpr(UnaryExpr unaryExpr);

    void visitVariableDeclInfo(VariableDeclInfo variableDeclInfo);

    void visitVariableIdentifierExpr(VariableIdentifierExpr variableIdentifierExpr);

    void visitVariablesDeclaration(VariablesDeclaration variablesDeclaration);

    void visitVoidType(VoidType voidType);

    void visitWhileStmt(WhileStmt whileStmt);

}
