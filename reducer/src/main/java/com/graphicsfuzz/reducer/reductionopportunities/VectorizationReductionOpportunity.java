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

package com.graphicsfuzz.reducer.reductionopportunities;

import com.graphicsfuzz.common.ast.ChildDoesNotExistException;
import com.graphicsfuzz.common.ast.IParentMap;
import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.ast.decl.VariableDeclInfo;
import com.graphicsfuzz.common.ast.decl.VariablesDeclaration;
import com.graphicsfuzz.common.ast.expr.MemberLookupExpr;
import com.graphicsfuzz.common.ast.expr.VariableIdentifierExpr;
import com.graphicsfuzz.common.ast.stmt.BlockStmt;
import com.graphicsfuzz.common.ast.stmt.DeclarationStmt;
import com.graphicsfuzz.common.ast.type.BasicType;
import com.graphicsfuzz.common.ast.type.Type;
import com.graphicsfuzz.common.ast.visitors.VisitationDepth;
import com.graphicsfuzz.common.transformreduce.MergedVariablesComponentData;
import com.graphicsfuzz.common.typing.Scope;
import com.graphicsfuzz.common.typing.ScopeEntry;
import com.graphicsfuzz.common.typing.ScopeTreeBuilder;
import com.graphicsfuzz.common.util.ListConcat;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class VectorizationReductionOpportunity extends AbstractReductionOpportunity {

  // The translation unit we are working on.
  private final TranslationUnit tu;

  // The block in which the vector variable is declared
  private final BlockStmt block;

  // The vector variable declaration
  private final VariablesDeclaration vectorVariablesDeclaration;
  private final VariableDeclInfo vectorVariableDeclInfo;

  // The component of the vector that is to be pulled out
  private final MergedVariablesComponentData componentData;

  // Child-to-parent mapping for the AST
  private final IParentMap parentMap;

  public VectorizationReductionOpportunity(
        TranslationUnit tu,
        BlockStmt block,
        VariablesDeclaration vectorVariablesDeclaration,
        VariableDeclInfo vectorVariableDeclInfo,
        MergedVariablesComponentData componentData,
        IParentMap parentMap,
        VisitationDepth depth) {
    super(depth);
    this.tu = tu;
    this.block = block;
    this.vectorVariablesDeclaration = vectorVariablesDeclaration;
    this.vectorVariableDeclInfo = vectorVariableDeclInfo;
    this.componentData = componentData;
    this.parentMap = parentMap;
  }

  @Override
  public void applyReductionImpl() {
    addComponentVariableIfNotPresent();
    pullOutComponent();
  }

  private void addComponentVariableIfNotPresent() {
    if (getAllVariableDeclInfosInBlock()
          .stream()
          .anyMatch(item -> item.getName().equals(getComponentName()))) {
      return;
    }
    block.insertStmt(0, new DeclarationStmt(new VariablesDeclaration(
          getComponentType(),
          new VariableDeclInfo(getComponentName(), null, null))));
  }

  private void pullOutComponent() {
    // This is the name the vector variable had before the reduction opportunity was applied.
    final String oldVectorName = vectorVariableDeclInfo.getName();

    // Look for cases where we are accessing the component we want to remove, and replace
    // those with the component variable.
    new ScopeTreeBuilder() {

      @Override
      public void visitMemberLookupExpr(MemberLookupExpr memberLookupExpr) {
        super.visitMemberLookupExpr(memberLookupExpr);
        if (isComponentAccess(memberLookupExpr, currentScope)) {
          try {
            parentMap.getParent(memberLookupExpr).replaceChild(memberLookupExpr,
                  new VariableIdentifierExpr(getComponentName()));
          } catch (ChildDoesNotExistException exception) {
            // The child must have already been eliminated from its parent by some other reduction
            // step
          }
        }
      }
    }.visit(block);
  }

  private boolean isScopeEntryForVectorVariable(ScopeEntry scopeEntry) {
    return scopeEntry != null
          && scopeEntry.hasVariableDeclInfo()
          && scopeEntry.getVariableDeclInfo() == vectorVariableDeclInfo;
  }


  @Override
  public boolean preconditionHolds() {
    // (1) Check whether a variable for the component we are pulling out exists.
    //     If it does, and has the right type, that is fine.  If it does, but has
    //     some other type, that is a problem.  If it does not exist, that is OK;
    //     we will add it in due course
    if (incompatibleComponentVariableIsDeclaredInBlock()) {
      return false;
    }
    // (2) Check whether a variable with the same name as the component we are pulling out (a)
    //     is in scope when the block is entered, and (b) is used somewhere in the block.  If
    //     so then we cannot apply the reduction opportunity.
    if (componentMightShadowExistingVariable()) {
      return false;
    }
    // (3) Check that the component of the vector is actually used somewhere.  If not,
    //     the reduction would have no effect so we do not want it.
    if (!componentIsUsed()) {
      return false;
    }
    // (4) Check that every usage of the vector variable is a field lookup.  The vectorization
    //     transformation ensures this is the case after it is applied, and we can only
    //     reduce in this scenario.  If other transformations have manipulated the vector
    //     then they need to be reversed first.
    if (vectorIsUsedWithoutFieldLookup()) {
      return false;
    }

    return true;
  }

  private boolean componentIsUsed() {
    return new ScopeTreeBuilder() {
      private boolean isUsed = false;

      @Override
      public void visitMemberLookupExpr(MemberLookupExpr memberLookupExpr) {
        super.visitMemberLookupExpr(memberLookupExpr);
        isUsed |= isComponentAccess(memberLookupExpr, currentScope);
      }

      public boolean componentIsUsed() {
        visit(tu);
        return isUsed;
      }
    }.componentIsUsed();
  }

  private List<VariableDeclInfo> getAllVariableDeclInfosInBlock() {
    return block.getStmts()
          .stream()
          .filter(item -> item instanceof DeclarationStmt)
          .map(item -> (DeclarationStmt) item)
          .map(item -> item.getVariablesDeclaration())
          .map(item -> item.getDeclInfos())
          .reduce(Arrays.asList(), ListConcat::concatenate);
  }

  private boolean componentMightShadowExistingVariable() {
    return !new ShadowChecker(block, getComponentName()).isOk(tu);
  }

  private boolean incompatibleComponentVariableIsDeclaredInBlock() {
    final BasicType expectedType = getComponentType();
    for (VariablesDeclaration variablesDeclaration : block.getStmts()
          .stream()
          .filter(item -> item instanceof DeclarationStmt)
          .map(item -> (DeclarationStmt) item)
          .map(item -> item.getVariablesDeclaration())
          .collect(Collectors.toList())) {
      final Type actualType = variablesDeclaration.getBaseType().getWithoutQualifiers();
      for (VariableDeclInfo vdi : variablesDeclaration.getDeclInfos()
            .stream()
            .filter(item -> item.getName().equals(getComponentName()))
            .collect(Collectors.toList())) {
        if (actualType != expectedType || vdi.hasArrayInfo()) {
          return true;
        }
      }
    }
    return false;
  }

  private boolean vectorIsUsedWithoutFieldLookup() {
    return
      new ScopeTreeBuilder() {
        private boolean vectorUsedDirectly = false;
        @Override
        public void visitVariableIdentifierExpr(VariableIdentifierExpr variableIdentifierExpr) {
          super.visitVariableIdentifierExpr(variableIdentifierExpr);
          if (parentMap.getParent(variableIdentifierExpr) instanceof MemberLookupExpr) {
            return;
          }
          if (!variableIdentifierExpr.getName().equals(vectorVariableDeclInfo.getName())) {
            return;
          }
          final ScopeEntry se = currentScope.lookupScopeEntry(variableIdentifierExpr.getName());
          if (se != null && se.hasVariableDeclInfo()
                && se.getVariableDeclInfo() == vectorVariableDeclInfo) {
            vectorUsedDirectly = true;
          }
        }

        private boolean isVectorUsedDirectly(TranslationUnit tu) {
          visit(tu);
          return vectorUsedDirectly;
        }
      }.isVectorUsedDirectly(tu);
  }

  /**
   * Yields the basic type that the component we are pulling out should have.
   * @return The basic type of the component.
   */
  public BasicType getComponentType() {
    return BasicType.makeVectorType(
          ((BasicType) vectorVariablesDeclaration.getBaseType().getWithoutQualifiers())
                .getElementType(),
          componentData.getWidth());
  }

  /**
   * Yields the name of the component we are pulling out.
   * @return The name of the component.
   */
  public String getComponentName() {
    return componentData.getName();
  }

  public String getVectorName() {
    return vectorVariableDeclInfo.getName();
  }

  private static int getOffsetFromSwizzle(String swizzle) {
    assert swizzle.length() > 0;
    assert swizzle.length() <= 4;
    if (!Pattern.compile("(x|y|z|w|r|g|b|a|s|t|p|q)+").matcher(swizzle).matches()) {
      throw new FailedReductionException("Ill-formed swizzle: " + swizzle);
    }
    switch (swizzle.charAt(0)) {
      case 'x':
      case 'r':
      case 's':
        return 0;
      case 'y':
      case 'g':
      case 't':
        return 1;
      case 'z':
      case 'b':
      case 'p':
        return 2;
      case 'w':
      case 'a':
      case 'q':
        return 3;
      default:
        throw new FailedReductionException("Ill-formed swizzle" + swizzle);
    }
  }

  private static int getWidthFromSwizzle(String swizzle) {
    return swizzle.length();
  }

  private boolean isComponentAccess(MemberLookupExpr memberLookupExpr, Scope currentScope) {
    if (!(memberLookupExpr.getStructure() instanceof VariableIdentifierExpr)) {
      return false;
    }
    final String structureName = ((VariableIdentifierExpr) memberLookupExpr.getStructure())
          .getName();
    final ScopeEntry scopeEntry = currentScope.lookupScopeEntry(structureName);
    if (!isScopeEntryForVectorVariable(scopeEntry)) {
      return false;
    }
    // We've established that this is a lookup on the vector.
    // Now we need to look at the swizzle to see whether it hits the right components.
    int swizzleOffset = getOffsetFromSwizzle(memberLookupExpr.getMember());
    int swizzleWidth = getWidthFromSwizzle(memberLookupExpr.getMember());
    // This is the component we care about.  There are now two scenarios:
    // (1) The swizzle was generated during vectorization, as a proxy for an original variable
    // (2) The swizzle was generated by our expression fuzzer (e.g. as part of identity
    //     function
    //     application
    // In case (1), we want to replace the swizzle with the original variable
    // In case (2), we should do nothing.
    // It's OK if we mistake case (2) for case (1), as arbitrary type-safe changes to fuzzed
    // expressions are OK.
    return swizzleOffset == componentData.getOffset() && swizzleWidth == componentData.getWidth();
  }

}