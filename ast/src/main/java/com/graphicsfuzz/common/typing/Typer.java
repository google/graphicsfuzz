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

package com.graphicsfuzz.common.typing;

import com.graphicsfuzz.common.ast.IAstNode;
import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.ast.decl.FunctionDefinition;
import com.graphicsfuzz.common.ast.decl.FunctionPrototype;
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
import com.graphicsfuzz.common.ast.type.ArrayType;
import com.graphicsfuzz.common.ast.type.BasicType;
import com.graphicsfuzz.common.ast.type.QualifiedType;
import com.graphicsfuzz.common.ast.type.StructDefinitionType;
import com.graphicsfuzz.common.ast.type.StructNameType;
import com.graphicsfuzz.common.ast.type.Type;
import com.graphicsfuzz.common.ast.type.TypeQualifier;
import com.graphicsfuzz.common.glslversion.ShadingLanguageVersion;
import com.graphicsfuzz.common.util.OpenGlConstants;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class Typer extends ScopeTreeBuilder {

  private Map<Expr, Type> types;

  private Map<String, Set<FunctionPrototype>> userDefinedFunctions;

  private Map<StructNameType, StructDefinitionType> structDeclarationMap;

  private ShadingLanguageVersion shadingLanguageVersion;

  public Map<String, Set<FunctionPrototype>> getUserDefinedFunctions() {
    return userDefinedFunctions;
  }

  public Typer(IAstNode node, ShadingLanguageVersion shadingLanguageVersion) {
    this.types = new HashMap<>();
    this.userDefinedFunctions = new HashMap<>();
    this.structDeclarationMap = new HashMap<>();
    this.shadingLanguageVersion = shadingLanguageVersion;
    visit(node);
  }

  public Typer(TranslationUnit tu) {
    this(tu, tu.getShadingLanguageVersion());
  }

  @Override
  public void visitParenExpr(ParenExpr parenExpr) {
    super.visitParenExpr(parenExpr);
    Type type = lookupType(parenExpr.getExpr());
    if (type != null) {
      types.put(parenExpr, type);
    }
  }

  @Override
  public void visitFunctionPrototype(FunctionPrototype functionPrototype) {
    super.visitFunctionPrototype(functionPrototype);
    String name = functionPrototype.getName();
    if (!userDefinedFunctions.containsKey(name)) {
      userDefinedFunctions.put(name, new HashSet<>());
    }
    userDefinedFunctions.get(name).add(functionPrototype);
  }

  @Override
  public void visitFunctionDefinition(FunctionDefinition functionDefinition) {
    super.visitFunctionDefinition(functionDefinition);
    String name = functionDefinition.getPrototype().getName();
    if (!userDefinedFunctions.containsKey(name)) {
      userDefinedFunctions.put(name, new HashSet<>());
    }
    userDefinedFunctions.get(name).add(functionDefinition.getPrototype());
  }

  @Override
  public void visitFunctionCallExpr(FunctionCallExpr functionCallExpr) {
    super.visitFunctionCallExpr(functionCallExpr);

    List<FunctionPrototype> candidateBuiltins = TyperHelper.getBuiltins(shadingLanguageVersion)
        .get(functionCallExpr.getCallee());
    if (candidateBuiltins != null) {
      for (FunctionPrototype prototype : candidateBuiltins) {
        if (prototypeMatches(prototype, functionCallExpr)) {
          types.put(functionCallExpr, prototype.getReturnType());
        }
      }
    }

    Set<FunctionPrototype> candidateUserDefined =
        userDefinedFunctions.get(functionCallExpr.getCallee());
    if (candidateUserDefined != null) {
      for (FunctionPrototype prototype : candidateUserDefined) {
        if (prototypeMatches(prototype, functionCallExpr)) {
          types.put(functionCallExpr, prototype.getReturnType());
        }
      }
    }

  }

  /**
   * Determines whether a given function prototype might correspond to the function being invoked
   * by a function call expression.
   *
   * <p>Ignores type qualifiers.</p>
   *
   * <p>Behaves in an approximate manner when type information is
   * incomplete.</p>
   *
   * @param prototype Function prototype to be checked
   * @param functionCallExpr Function call expression to be checked
   * @return True if there is a possible match
   */
  public boolean prototypeMatches(FunctionPrototype prototype, FunctionCallExpr functionCallExpr) {
    if (prototype.getNumParameters() != functionCallExpr.getNumArgs()) {
      return false;
    }
    for (int i = 0; i < prototype.getNumParameters(); i++) {
      Type argType = lookupType(functionCallExpr.getArg(i));
      if (argType == null) {
        return false;
      }
      // Not yet worked out how to deal with array info
      assert prototype.getParameters().get(i).getArrayInfo() == null;
      if (!argType.getWithoutQualifiers()
          .equals(prototype.getParameters().get(i).getType().getWithoutQualifiers())) {
        return false;
      }
    }
    return true;
  }

  @Override
  public void visitVariableIdentifierExpr(VariableIdentifierExpr variableIdentifierExpr) {
    super.visitVariableIdentifierExpr(variableIdentifierExpr);
    Type type = currentScope.lookupType(variableIdentifierExpr.getName());
    if (type != null) {
      types.put(variableIdentifierExpr, type);
      return;
    }
    maybeGetTypeOfBuiltinVariable(variableIdentifierExpr.getName())
        .ifPresent(item -> types.put(variableIdentifierExpr, item));
  }

  @Override
  public void visitTypeConstructorExpr(TypeConstructorExpr typeConstructorExpr) {
    super.visitTypeConstructorExpr(typeConstructorExpr);
    switch (typeConstructorExpr.getTypename()) {
      case "float":
        types.put(typeConstructorExpr, BasicType.FLOAT);
        return;
      case "int":
        types.put(typeConstructorExpr, BasicType.INT);
        return;
      case "uint":
        types.put(typeConstructorExpr, BasicType.UINT);
        return;
      case "bool":
        types.put(typeConstructorExpr, BasicType.BOOL);
        return;
      case "vec2":
        types.put(typeConstructorExpr, BasicType.VEC2);
        return;
      case "vec3":
        types.put(typeConstructorExpr, BasicType.VEC3);
        return;
      case "vec4":
        types.put(typeConstructorExpr, BasicType.VEC4);
        return;
      case "ivec2":
        types.put(typeConstructorExpr, BasicType.IVEC2);
        return;
      case "ivec3":
        types.put(typeConstructorExpr, BasicType.IVEC3);
        return;
      case "ivec4":
        types.put(typeConstructorExpr, BasicType.IVEC4);
        return;
      case "uvec2":
        types.put(typeConstructorExpr, BasicType.UVEC2);
        return;
      case "uvec3":
        types.put(typeConstructorExpr, BasicType.UVEC3);
        return;
      case "uvec4":
        types.put(typeConstructorExpr, BasicType.UVEC4);
        return;
      case "bvec2":
        types.put(typeConstructorExpr, BasicType.BVEC2);
        return;
      case "bvec3":
        types.put(typeConstructorExpr, BasicType.BVEC3);
        return;
      case "bvec4":
        types.put(typeConstructorExpr, BasicType.BVEC4);
        return;
      case "mat2x2":
      case "mat2":
        types.put(typeConstructorExpr, BasicType.MAT2X2);
        return;
      case "mat2x3":
        types.put(typeConstructorExpr, BasicType.MAT2X3);
        return;
      case "mat2x4":
        types.put(typeConstructorExpr, BasicType.MAT2X4);
        return;
      case "mat3x2":
        types.put(typeConstructorExpr, BasicType.MAT3X2);
        return;
      case "mat3x3":
      case "mat3":
        types.put(typeConstructorExpr, BasicType.MAT3X3);
        return;
      case "mat3x4":
        types.put(typeConstructorExpr, BasicType.MAT3X4);
        return;
      case "mat4x2":
        types.put(typeConstructorExpr, BasicType.MAT4X2);
        return;
      case "mat4x3":
        types.put(typeConstructorExpr, BasicType.MAT4X3);
        return;
      case "mat4x4":
      case "mat4":
        types.put(typeConstructorExpr, BasicType.MAT4X4);
        return;
      default:
        final StructNameType maybeStructType =
            new StructNameType(typeConstructorExpr.getTypename());
        if (structDeclarationMap.containsKey(maybeStructType)) {
          types.put(typeConstructorExpr, maybeStructType);
          return;
        }
        throw new RuntimeException("Unknown type constructor " + typeConstructorExpr.getTypename());
    }
  }

  @Override
  public void visitUnaryExpr(UnaryExpr unaryExpr) {
    super.visitUnaryExpr(unaryExpr);

    // TODO: need to check, but as a first approximation a unary always returns the same type as
    // its argument

    Type argType = types.get(unaryExpr.getExpr());
    if (argType != null) {
      types.put(unaryExpr, argType);
    }
  }

  @Override
  public void visitTernaryExpr(TernaryExpr ternaryExpr) {
    super.visitTernaryExpr(ternaryExpr);
    Type thenType = types.get(ternaryExpr.getThenExpr());
    if (thenType != null) {
      types.put(ternaryExpr, thenType);
    } else {
      Type elseType = types.get(ternaryExpr.getElseExpr());
      if (elseType != null) {
        types.put(ternaryExpr, elseType);
      }
    }
  }

  @Override
  public void visitBinaryExpr(BinaryExpr binaryExpr) {
    super.visitBinaryExpr(binaryExpr);
    Type lhsType = types.get(binaryExpr.getLhs());
    Type rhsType = types.get(binaryExpr.getRhs());
    if (lhsType instanceof QualifiedType) {
      lhsType = ((QualifiedType) lhsType).getTargetType();
    }
    if (rhsType instanceof QualifiedType) {
      rhsType = ((QualifiedType) rhsType).getTargetType();
    }
    switch (binaryExpr.getOp()) {
      case MUL: {
        Type resolvedType = TyperHelper.resolveTypeOfMul(lhsType, rhsType);
        if (resolvedType != null) {
          types.put(binaryExpr, resolvedType);
        }
        return;
      }
      case ADD:
      case SUB:
      case DIV:
      case SHL:
      case SHR:
      case MOD:
      case BAND:
      case BOR:
      case BXOR:
      case ADD_ASSIGN:
      case ASSIGN:
      case DIV_ASSIGN:
      case MUL_ASSIGN:
      case SUB_ASSIGN: {
        Type resolvedType = TyperHelper.resolveTypeOfCommonBinary(lhsType, rhsType);
        if (resolvedType != null) {
          types.put(binaryExpr, resolvedType);
        }
        return;
      }
      case EQ:
      case GE:
      case GT:
      case LAND:
      case LE:
      case LOR:
      case LT:
      case LXOR:
      case NE:
        types.put(binaryExpr, resolveBooleanResultType(lhsType, rhsType));
        return;
      case COMMA:
        // The type of "e1, e2" is the type of "e2".
        types.put(binaryExpr, rhsType);
        return;
      case BAND_ASSIGN:
        break;
      case BOR_ASSIGN:
        break;
      case BXOR_ASSIGN:
        break;
      case MOD_ASSIGN:
        break;
      case SHL_ASSIGN:
        break;
      case SHR_ASSIGN:
        break;
      default:
        break;

    }
  }

  @Override
  public void visitBoolConstantExpr(BoolConstantExpr boolConstantExpr) {
    types.put(boolConstantExpr, BasicType.BOOL);
  }

  @Override
  public void visitIntConstantExpr(IntConstantExpr intConstantExpr) {
    types.put(intConstantExpr, BasicType.INT);
  }

  @Override
  public void visitUIntConstantExpr(UIntConstantExpr uintConstantExpr) {
    types.put(uintConstantExpr, BasicType.UINT);
  }

  @Override
  public void visitFloatConstantExpr(FloatConstantExpr floatConstantExpr) {
    types.put(floatConstantExpr, BasicType.FLOAT);
  }

  @Override
  public void visitMemberLookupExpr(MemberLookupExpr memberLookupExpr) {
    super.visitMemberLookupExpr(memberLookupExpr);
    Type structureType = lookupType(memberLookupExpr.getStructure());

    if (structureType == null) {
      // In due course we should extend the typer so that it can type everything.
      return;
    }

    // The structure type is either a builtin, like a vector, or an actual struct

    if (BasicType.allVectorTypes().contains(structureType.getWithoutQualifiers())) {
      BasicType vecType = (BasicType) structureType.getWithoutQualifiers();
      // It is a swizzle, so lookups must be xyzw, rgba or stpq
      for (int i = 0; i < memberLookupExpr.getMember().length(); i++) {
        assert ("xyzw" + "rgba" + "stpq")
            .contains(String.valueOf(memberLookupExpr.getMember().charAt(i)));
      }
      types.put(memberLookupExpr, BasicType
          .makeVectorType(vecType.getElementType(), memberLookupExpr.getMember().length()));
    }

    if (structureType.getWithoutQualifiers() instanceof StructNameType) {
      types.put(memberLookupExpr,
          structDeclarationMap.get(structureType.getWithoutQualifiers())
              .getFieldType(memberLookupExpr.getMember()));
    }

    if (structureType.getWithoutQualifiers() instanceof StructDefinitionType) {
      types.put(memberLookupExpr,
          ((StructDefinitionType) structureType.getWithoutQualifiers())
              .getFieldType(memberLookupExpr.getMember()));
    }

    // take care of cases where you get the x coordinate of a vec2 variable and similar
    if (structureType.getWithoutQualifiers() instanceof BasicType) {
      BasicType vecType = (BasicType) structureType.getWithoutQualifiers();
      for (int i = 0; i < memberLookupExpr.getMember().length(); i++) {
        assert ("xyzw" + "rgba" + "stpq")
            .contains(String.valueOf(memberLookupExpr.getMember().charAt(i)));
      }

      final BasicType v = BasicType.makeVectorType(vecType.getElementType(),
          memberLookupExpr.getMember().length());
      types.put(memberLookupExpr, v);
    }
  }

  private Type resolveBooleanResultType(Type lhsType, Type rhsType) {
    return maybeComputeBooleanVectorType(lhsType)
        .orElse(maybeComputeBooleanVectorType(rhsType)
            .orElse(BasicType.BOOL));
  }

  private Optional<Type> maybeComputeBooleanVectorType(Type lhsType) {
    if (lhsType instanceof BasicType) {
      final int numElements = ((BasicType) lhsType).getNumElements();
      if (1 < numElements && numElements <= 4) {
        return Optional.of(BasicType.makeVectorType(BasicType.BOOL, numElements));
      }
    }
    return Optional.empty();
  }

  public Set<Expr> getTypedExpressions() {
    return Collections.unmodifiableSet(types.keySet());
  }

  public Type lookupType(Expr expr) {
    return types.get(expr);
  }

  public boolean hasType(Expr expr) {
    return lookupType(expr) != null;
  }

  public Set<FunctionPrototype> getPrototypes(String name) {
    Set<FunctionPrototype> result = new HashSet<>();
    if (userDefinedFunctions.containsKey(name)) {
      result.addAll(userDefinedFunctions.get(name));
    }
    if (TyperHelper.getBuiltins(shadingLanguageVersion).containsKey(name)) {
      result.addAll(TyperHelper.getBuiltins(shadingLanguageVersion).get(name));
    }
    return result;
  }

  @Override
  public void visitStructDefinitionType(StructDefinitionType structDefinitionType) {
    super.visitStructDefinitionType(structDefinitionType);
    if (structDefinitionType.hasStructNameType()) {
      assert !structDeclarationMap.containsKey(structDefinitionType.getStructNameType());
      structDeclarationMap.put(structDefinitionType.getStructNameType(), structDefinitionType);
    }
  }

  @Override
  public void visitArrayIndexExpr(ArrayIndexExpr arrayIndexExpr) {
    super.visitArrayIndexExpr(arrayIndexExpr);
    Type arrayType = lookupType(arrayIndexExpr.getArray());
    if (arrayType == null) {
      return;
    }
    arrayType = arrayType.getWithoutQualifiers();
    Type elementType;
    if (BasicType.allVectorTypes().contains(arrayType)) {
      elementType = ((BasicType) arrayType).getElementType();
    } else if (BasicType.allMatrixTypes().contains(arrayType)) {
      elementType = ((BasicType) arrayType).getColumnType();
    } else {
      assert arrayType instanceof ArrayType;
      elementType = ((ArrayType) arrayType).getBaseType();
    }
    types.put(arrayIndexExpr, elementType);
  }

  /**
   * If the given name corresponds to an OpenGL builtin variable, yields the type of the
   * variable.
   * @param name The name of a candidate builtin variable.
   * @return The type of the variable if it is indeed a builtin, otherwise empty.
   */
  public static Optional<Type> maybeGetTypeOfBuiltinVariable(String name) {
    switch (name) {
      case OpenGlConstants.GL_POINT_SIZE:
        return Optional.of(BasicType.FLOAT);
      case OpenGlConstants.GL_FRAG_COORD:
      case OpenGlConstants.GL_FRAG_COLOR:
      case OpenGlConstants.GL_POSITION:
        return Optional.of(BasicType.VEC4);
      case OpenGlConstants.GL_NUM_WORK_GROUPS:
      case OpenGlConstants.GL_WORK_GROUP_ID:
      case OpenGlConstants.GL_LOCAL_INVOCATION_ID:
      case OpenGlConstants.GL_GLOBAL_INVOCATION_ID:
        return Optional.of(new QualifiedType(BasicType.UVEC3,
            Collections.singletonList(TypeQualifier.SHADER_INPUT)));
      case OpenGlConstants.GL_WORK_GROUP_SIZE:
        return Optional.of(new QualifiedType(BasicType.UVEC3,
            Collections.singletonList(TypeQualifier.CONST)));
      case OpenGlConstants.GL_LOCAL_INVOCATION_INDEX:
        return Optional.of(new QualifiedType(BasicType.UINT,
            Collections.singletonList(TypeQualifier.SHADER_INPUT)));
      default:
        return Optional.empty();
    }
  }

}
