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

import com.graphicsfuzz.common.ast.TranslationUnit;
import java.util.List;

public interface IReductionOpportunityFinder<T extends IReductionOpportunity> {

  List<T> findOpportunities(TranslationUnit tu,
        ReductionOpportunityContext context);

  String getName();

  static IReductionOpportunityFinder<StmtReductionOpportunity> stmtFinder() {
    return new IReductionOpportunityFinder<StmtReductionOpportunity>() {
      @Override
      public List<StmtReductionOpportunity> findOpportunities(TranslationUnit tu,
            ReductionOpportunityContext context) {
        return StmtReductionOpportunities.findOpportunities(tu, context);
      }

      @Override
      public String getName() {
        return "stmt";
      }
    };
  }

  static IReductionOpportunityFinder<FunctionOrStructReductionOpportunity> functionFinder() {
    return new IReductionOpportunityFinder<FunctionOrStructReductionOpportunity>() {
      @Override
      public List<FunctionOrStructReductionOpportunity> findOpportunities(TranslationUnit tu,
            ReductionOpportunityContext context) {
        return FunctionReductionOpportunities.findOpportunities(tu, context);
      }

      @Override
      public String getName() {
        return "function";
      }
    };
  }

  static IReductionOpportunityFinder<DeclarationReductionOpportunity> declarationFinder() {
    return new IReductionOpportunityFinder<DeclarationReductionOpportunity>() {
      @Override
      public List<DeclarationReductionOpportunity> findOpportunities(TranslationUnit tu,
            ReductionOpportunityContext context) {
        return DeclarationReductionOpportunities.findOpportunities(tu, context);
      }

      @Override
      public String getName() {
        return "declaration";
      }
    };
  }

  static IReductionOpportunityFinder<SimplifyExprReductionOpportunity> exprToConstantFinder() {
    return new IReductionOpportunityFinder<SimplifyExprReductionOpportunity>() {
      @Override
      public List<SimplifyExprReductionOpportunity> findOpportunities(TranslationUnit tu,
            ReductionOpportunityContext context) {
        return ExprToConstantReductionOpportunities.findOpportunities(tu, context);
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
      public List<SimplifyExprReductionOpportunity> findOpportunities(TranslationUnit tu,
            ReductionOpportunityContext context) {
        return CompoundExprToSubExprReductionOpportunities.findOpportunities(tu, context);
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
      public List<CompoundToBlockReductionOpportunity> findOpportunities(TranslationUnit tu,
            ReductionOpportunityContext context) {
        return CompoundToBlockReductionOpportunities.findOpportunities(tu, context);
      }

      @Override
      public String getName() {
        return "compoundToBlock";
      }
    };
  }

  static IReductionOpportunityFinder<InlineInitializerReductionOpportunity>
      inlineInitializerFinder() {
    return new IReductionOpportunityFinder<InlineInitializerReductionOpportunity>() {
      @Override
      public List<InlineInitializerReductionOpportunity> findOpportunities(TranslationUnit tu,
            ReductionOpportunityContext context) {
        return InlineInitializerReductionOpportunities.findOpportunities(tu, context);
      }

      @Override
      public String getName() {
        return "inlineInitializer";
      }
    };
  }

  static IReductionOpportunityFinder<FunctionOrStructReductionOpportunity> unusedStructFinder() {
    return new IReductionOpportunityFinder<FunctionOrStructReductionOpportunity>() {
      @Override
      public List<FunctionOrStructReductionOpportunity> findOpportunities(TranslationUnit tu,
            ReductionOpportunityContext context) {
        return UnusedStructReductionOpportunities.findOpportunities(tu, context);
      }

      @Override
      public String getName() {
        return "unusedStruct";
      }
    };
  }

  static IReductionOpportunityFinder<MutationReductionOpportunity> mutationFinder() {
    return new IReductionOpportunityFinder<MutationReductionOpportunity>() {
      @Override
      public List<MutationReductionOpportunity> findOpportunities(TranslationUnit tu,
            ReductionOpportunityContext context) {
        return MutationReductionOpportunities.findOpportunities(tu, context);
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
      public List<OutlinedStatementReductionOpportunity> findOpportunities(TranslationUnit tu,
            ReductionOpportunityContext context) {
        return OutlinedStatementReductionOpportunities.findOpportunities(tu, context);
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
      public List<UnwrapReductionOpportunity> findOpportunities(TranslationUnit tu,
            ReductionOpportunityContext context) {
        return UnwrapReductionOpportunities.findOpportunities(tu, context);
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
      public List<RemoveStructFieldReductionOpportunity> findOpportunities(TranslationUnit tu,
            ReductionOpportunityContext context) {
        return RemoveStructFieldReductionOpportunities.findOpportunities(tu, context);
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
      public List<DestructifyReductionOpportunity> findOpportunities(TranslationUnit tu,
            ReductionOpportunityContext context) {
        return DestructifyReductionOpportunities.findOpportunities(tu, context);
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
      public List<InlineStructifiedFieldReductionOpportunity> findOpportunities(TranslationUnit tu,
            ReductionOpportunityContext context) {
        return InlineStructifiedFieldReductionOpportunities.findOpportunities(tu, context);
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
      public List<VectorizationReductionOpportunity> findOpportunities(TranslationUnit tu,
            ReductionOpportunityContext context) {
        return VectorizationReductionOpportunities.findOpportunities(tu, context);
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
      public List<UnswitchifyReductionOpportunity> findOpportunities(TranslationUnit tu,
            ReductionOpportunityContext context) {
        return UnswitchifyReductionOpportunities.findOpportunities(tu, context);
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
      public List<LiveOutputVariableWriteReductionOpportunity> findOpportunities(TranslationUnit tu,
            ReductionOpportunityContext context) {
        return LiveOutputVariableWriteReductionOpportunities.findOpportunities(tu, context);
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
      public List<InlineFunctionReductionOpportunity> findOpportunities(TranslationUnit tu,
            ReductionOpportunityContext context) {
        return InlineFunctionReductionOpportunities.findOpportunities(tu, context);
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
      public List<LoopMergeReductionOpportunity> findOpportunities(TranslationUnit tu,
            ReductionOpportunityContext context) {
        return LoopMergeReductionOpportunities.findOpportunities(tu, context);
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
          TranslationUnit tu,
          ReductionOpportunityContext context) {
        return RemoveUnusedParameterReductionOpportunities.findOpportunities(tu, context);
      }

      @Override
      public String getName() {
        return "unusedParam";
      }
    };
  }

}
