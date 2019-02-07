package com.graphicsfuzz.generator.semanticspreserving;

import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.util.IRandom;
import com.graphicsfuzz.common.util.IdGenerator;
import com.graphicsfuzz.generator.mutateapi.MutationFinder;
import com.graphicsfuzz.generator.transformation.injection.InjectionPoints;
import java.util.List;
import java.util.stream.Collectors;

public class SplitForLoopMutationFinder implements MutationFinder<SplitForLoopMutation> {

  private final TranslationUnit tu;
  private final IRandom random;
  private final IdGenerator idGenerator;

  public SplitForLoopMutationFinder(TranslationUnit tu, IRandom random, IdGenerator idGenerator) {
    this.tu = tu;
    this.random = random;
    this.idGenerator = idGenerator;
  }

  @Override
  public List<SplitForLoopMutation> findMutations() {
    return new InjectionPoints(tu, random, SplitForLoopMutation::suitableForSplitting)
        .getAllInjectionPoints()
        .stream()
        .map(item -> new SplitForLoopMutation(item, random, idGenerator))
        .collect(Collectors.toList());
  }

}
