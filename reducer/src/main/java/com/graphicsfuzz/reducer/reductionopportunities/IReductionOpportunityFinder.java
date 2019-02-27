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

import com.graphicsfuzz.common.transformreduce.ShaderJob;
import java.util.List;

public interface IReductionOpportunityFinder<T extends IReductionOpportunity> {

  List<T> findOpportunities(ShaderJob shaderJob,
                            ReducerContext context);

  String getName();

  static IReductionOpportunityFinder<StmtReductionOpportunity> stmtFinder() {
    return new IReductionOpportunityFinder<StmtReductionOpportunity>() {
      @Override
      public List<StmtReductionOpportunity> findOpportunities(ShaderJob shaderJob,
            ReducerContext context) {
        return StmtReductionOpportunities.findOpportunities(shaderJob, context);
      }

      @Override
      public String getName() {
        return "stmt";
      }
    };
  }

  static IReductionOpportunityFinder<FunctionReductionOpportunity> functionFinder() {
    return new IReductionOpportunityFinder<FunctionReductionOpportunity>() {
      @Override
      public List<FunctionReductionOpportunity> findOpportunities(
          ShaderJob shaderJob,
          ReducerContext context) {
        return FunctionReductionOpportunities.findOpportunities(shaderJob, context);
      }

      @Override
      public String getName() {
        return "function";
      }
    };
  }

  static IReductionOpportunityFinder<VariableDeclReductionOpportunity> variableDeclFinder() {
    return new IReductionOpportunityFinder<VariableDeclReductionOpportunity>() {
      @Override
      public List<VariableDeclReductionOpportunity> findOpportunities(ShaderJob shaderJob,
            ReducerContext context) {
        return VariableDeclReductionOpportunities.findOpportunities(shaderJob, context);
      }

      @Override
      public String getName() {
        return "variableDecl";
      }
    };
  }

  static IReductionOpportunityFinder<GlobalVariablesDeclarationReductionOpportunity>
      globalVariablesDeclarationFinder() {
    return new IReductionOpportunityFinder<GlobalVariablesDeclarationReductionOpportunity>() {
      @Override
      public List<GlobalVariablesDeclarationReductionOpportunity> findOpportunities(
          ShaderJob shaderJob,
          ReducerContext context) {
        return GlobalVariablesDeclarationReductionOpportunities
            .findOpportunities(shaderJob, context);
      }

      @Override
      public String getName() {
        return "variableDecl";
      }
    };
  }

  static IReductionOpportunityFinder<SimplifyExprReductionOpportunity> exprToConstantFinder() {
    return new IReductionOpportunityFinder<SimplifyExprReductionOpportunity>() {
      @Override
      public List<SimplifyExprReductionOpportunity> findOpportunities(ShaderJob shaderJob,
            ReducerContext context) {
        return ExprToConstantReductionOpportunities.findOpportunities(shaderJob, context);
      }

      @Override
      public String getName() {
        return "exprToConstant";
      }
    };
  }

  static IReductionOpportunityFinder<SimplifyExprReductionOpportunity>
      compoundExprToSubExprFinder() {
    return new IReductionOpportunityFinder<SimplifyExprReductionOpportunity>() {
      @Override
      public List<SimplifyExprReductionOpportunity> findOpportunities(ShaderJob shaderJob,
            ReducerContext context) {
        return CompoundExprToSubExprReductionOpportunities.findOpportunities(shaderJob, context);
      }

      @Override
      public String getName() {
        return "compoundExprToSubExpr";
      }
    };
  }

  static IReductionOpportunityFinder<CompoundToBlockReductionOpportunity> compoundToBlockFinder() {
    return new IReductionOpportunityFinder<CompoundToBlockReductionOpportunity>() {
      @Override
      public List<CompoundToBlockReductionOpportunity> findOpportunities(ShaderJob shaderJob,
            ReducerContext context) {
        return CompoundToBlockReductionOpportunities.findOpportunities(shaderJob, context);
      }

      @Override
      public String getName() {
        return "compoundToBlock";
      }
    };
  }

  static IReductionOpportunityFinder<SimplifyExprReductionOpportunity>
      inlineInitializerFinder() {
    return new IReductionOpportunityFinder<SimplifyExprReductionOpportunity>() {
      @Override
      public List<SimplifyExprReductionOpportunity> findOpportunities(ShaderJob shaderJob,
            ReducerContext context) {
        return InlineInitializerReductionOpportunities.findOpportunities(shaderJob, context);
      }

      @Override
      public String getName() {
        return "inlineInitializer";
      }
    };
  }

  static IReductionOpportunityFinder<IdentityMutationReductionOpportunity> mutationFinder() {
    return new IReductionOpportunityFinder<IdentityMutationReductionOpportunity>() {
      @Override
      public List<IdentityMutationReductionOpportunity> findOpportunities(ShaderJob shaderJob,
                                                                          ReducerContext context) {
        return IdentityMutationReductionOpportunities.findOpportunities(shaderJob, context);
      }

      @Override
      public String getName() {
        return "mutation";
      }
    };
  }

  static IReductionOpportunityFinder<OutlinedStatementReductionOpportunity>
      outlinedStatementFinder() {
    return new IReductionOpportunityFinder<OutlinedStatementReductionOpportunity>() {
      @Override
      public List<OutlinedStatementReductionOpportunity> findOpportunities(ShaderJob shaderJob,
            ReducerContext context) {
        return OutlinedStatementReductionOpportunities.findOpportunities(shaderJob, context);
      }

      @Override
      public String getName() {
        return "outlinedStatement";
      }
    };
  }

  static IReductionOpportunityFinder<UnwrapReductionOpportunity> unwrapFinder() {
    return new IReductionOpportunityFinder<UnwrapReductionOpportunity>() {
      @Override
      public List<UnwrapReductionOpportunity> findOpportunities(ShaderJob shaderJob,
            ReducerContext context) {
        return UnwrapReductionOpportunities.findOpportunities(shaderJob, context);
      }

      @Override
      public String getName() {
        return "unwrap";
      }
    };
  }

  static IReductionOpportunityFinder<RemoveStructFieldReductionOpportunity>
      removeStructFieldFinder() {
    return new IReductionOpportunityFinder<RemoveStructFieldReductionOpportunity>() {
      @Override
      public List<RemoveStructFieldReductionOpportunity> findOpportunities(ShaderJob shaderJob,
            ReducerContext context) {
        return RemoveStructFieldReductionOpportunities.findOpportunities(shaderJob, context);
      }

      @Override
      public String getName() {
        return "removeStructField";
      }
    };
  }

  static IReductionOpportunityFinder<DestructifyReductionOpportunity> destructifyFinder() {
    return new IReductionOpportunityFinder<DestructifyReductionOpportunity>() {
      @Override
      public List<DestructifyReductionOpportunity> findOpportunities(ShaderJob shaderJob,
            ReducerContext context) {
        return DestructifyReductionOpportunities.findOpportunities(shaderJob, context);
      }

      @Override
      public String getName() {
        return "destructify";
      }
    };
  }

  static IReductionOpportunityFinder<InlineStructifiedFieldReductionOpportunity>
      inlineStructFieldFinder() {
    return new IReductionOpportunityFinder<InlineStructifiedFieldReductionOpportunity>() {
      @Override
      public List<InlineStructifiedFieldReductionOpportunity> findOpportunities(ShaderJob shaderJob,
            ReducerContext context) {
        return InlineStructifiedFieldReductionOpportunities.findOpportunities(shaderJob, context);
      }

      @Override
      public String getName() {
        return "inlineStructField";
      }
    };
  }

  static IReductionOpportunityFinder<VectorizationReductionOpportunity> vectorizationFinder() {
    return new IReductionOpportunityFinder<VectorizationReductionOpportunity>() {
      @Override
      public List<VectorizationReductionOpportunity> findOpportunities(ShaderJob shaderJob,
            ReducerContext context) {
        return VectorizationReductionOpportunities.findOpportunities(shaderJob, context);
      }

      @Override
      public String getName() {
        return "vectorization";
      }
    };
  }

  static IReductionOpportunityFinder<UnswitchifyReductionOpportunity> unswitchifyFinder() {
    return new IReductionOpportunityFinder<UnswitchifyReductionOpportunity>() {
      @Override
      public List<UnswitchifyReductionOpportunity> findOpportunities(ShaderJob shaderJob,
            ReducerContext context) {
        return UnswitchifyReductionOpportunities.findOpportunities(shaderJob, context);
      }

      @Override
      public String getName() {
        return "unswitchify";
      }
    };
  }

  static IReductionOpportunityFinder<LiveOutputVariableWriteReductionOpportunity>
      liveFragColorWriteFinder() {
    return new IReductionOpportunityFinder<LiveOutputVariableWriteReductionOpportunity>() {
      @Override
      public List<LiveOutputVariableWriteReductionOpportunity> findOpportunities(
          ShaderJob shaderJob,
          ReducerContext context) {
        return LiveOutputVariableWriteReductionOpportunities.findOpportunities(shaderJob, context);
      }

      @Override
      public String getName() {
        return "liveFragColorWrite";
      }
    };
  }

  static IReductionOpportunityFinder<InlineFunctionReductionOpportunity> inlineFunctionFinder() {
    return new IReductionOpportunityFinder<InlineFunctionReductionOpportunity>() {
      @Override
      public List<InlineFunctionReductionOpportunity> findOpportunities(ShaderJob shaderJob,
            ReducerContext context) {
        return InlineFunctionReductionOpportunities.findOpportunities(shaderJob, context);
      }

      @Override
      public String getName() {
        return "inlineFunction";
      }
    };
  }

  static IReductionOpportunityFinder<LoopMergeReductionOpportunity> loopMergeFinder() {
    return new IReductionOpportunityFinder<LoopMergeReductionOpportunity>() {
      @Override
      public List<LoopMergeReductionOpportunity> findOpportunities(ShaderJob shaderJob,
            ReducerContext context) {
        return LoopMergeReductionOpportunities.findOpportunities(shaderJob, context);
      }

      @Override
      public String getName() {
        return "loopMerge";
      }
    };
  }

  static IReductionOpportunityFinder<RemoveUnusedParameterReductionOpportunity>
      unusedParamFinder() {
    return new IReductionOpportunityFinder<RemoveUnusedParameterReductionOpportunity>() {
      @Override
      public List<RemoveUnusedParameterReductionOpportunity> findOpportunities(
          ShaderJob shaderJob,
          ReducerContext context) {
        return RemoveUnusedParameterReductionOpportunities.findOpportunities(shaderJob, context);
      }

      @Override
      public String getName() {
        return "unusedParam";
      }
    };
  }

  static IReductionOpportunityFinder<SimplifyExprReductionOpportunity>
      foldConstantFinder() {
    return new IReductionOpportunityFinder<SimplifyExprReductionOpportunity>() {
      @Override
      public List<SimplifyExprReductionOpportunity> findOpportunities(
          ShaderJob shaderJob,
          ReducerContext context) {
        return FoldConstantReductionOpportunities.findOpportunities(shaderJob, context);
      }

      @Override
      public String getName() {
        return "foldConstant";
      }
    };
  }

  static IReductionOpportunityFinder<SimplifyExprReductionOpportunity>
      inlineUniformFinder() {
    return new IReductionOpportunityFinder<SimplifyExprReductionOpportunity>() {
      @Override
      public List<SimplifyExprReductionOpportunity> findOpportunities(
          ShaderJob shaderJob,
          ReducerContext context) {
        return InlineUniformReductionOpportunities.findOpportunities(shaderJob, context);
      }

      @Override
      public String getName() {
        return "inlineUniforms";
      }
    };
  }

}
