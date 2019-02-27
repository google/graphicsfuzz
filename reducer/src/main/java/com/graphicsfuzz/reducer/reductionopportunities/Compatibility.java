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

import java.util.function.Predicate;

public final class Compatibility {

  private Compatibility() {
    // Utility class
  }

  private static boolean bothMatch(Class<? extends IReductionOpportunity> first,
      Class<? extends IReductionOpportunity> second,
      Predicate<Class<? extends IReductionOpportunity>> pred) {
    return pred.test(first) && pred.test(second);
  }

  private static boolean matchesEitherDirection(Class<? extends IReductionOpportunity> first,
      Class<? extends IReductionOpportunity> second,
      Predicate<Class<? extends IReductionOpportunity>> pred1,
      Predicate<Class<? extends IReductionOpportunity>> pred2) {
    return (pred1.test(first) && pred2.test(second))
        || (pred2.test(first) && pred1.test(second));
  }

  public static boolean compatible(Class<? extends IReductionOpportunity> first,
      Class<? extends IReductionOpportunity> second) {

    if (first.equals(VectorizationReductionOpportunity.class)
          || second.equals(VectorizationReductionOpportunity.class)) {
      // Removing vector transformations concurrently with other transformations is challenging.
      return false;
    }

    if (bothMatch(first, second, Compatibility::isStructRelated)) {
      // Many problems combining these, for example field removal and inlining can clash
      return false;
    }

    if (matchesEitherDirection(first, second,
        LoopMergeReductionOpportunity.class::equals,
        StmtReductionOpportunity.class::equals)) {
      // Things go wrong if we remove one of the loops we were trying to merge
      return false;
    }

    if (matchesEitherDirection(first, second,
        LoopMergeReductionOpportunity.class::equals,
        SimplifyExprReductionOpportunity.class::equals)) {
      // Things go wrong if we replace parts of the guards of the loops we wanted to merge with
      // constants
      return false;
    }

    if (matchesEitherDirection(first, second,
        Compatibility::isStructRelated,
        SimplifyExprReductionOpportunity.class::equals)) {
      return false;
    }

    if (matchesEitherDirection(first, second,
        IdentityMutationReductionOpportunity.class::equals,
        SimplifyExprReductionOpportunity.class::equals)) {
      return false;
    }

    if (matchesEitherDirection(first, second,
        Compatibility::isStructRelated,
        IdentityMutationReductionOpportunity.class::equals)) {
      return false;
    }

    if (matchesEitherDirection(first, second,
        StmtReductionOpportunity.class::equals,
        UnswitchifyReductionOpportunity.class::equals)) {
      return false;
    }

    return true;
  }

  private static boolean isStructRelated(Class<? extends IReductionOpportunity> op) {
    return op.equals(InlineStructifiedFieldReductionOpportunity.class)
        || op.equals(DestructifyReductionOpportunity.class)
        || op.equals(RemoveStructFieldReductionOpportunity.class)
        || op.equals(OutlinedStatementReductionOpportunity.class)
        || op.equals(SimplifyExprReductionOpportunity.class);
  }

}
