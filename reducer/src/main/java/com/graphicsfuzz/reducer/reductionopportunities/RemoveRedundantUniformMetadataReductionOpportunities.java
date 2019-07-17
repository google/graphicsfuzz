/*
 * Copyright 2019 The GraphicsFuzz Project Authors
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
import com.graphicsfuzz.common.ast.decl.VariableDeclInfo;
import com.graphicsfuzz.common.ast.decl.VariablesDeclaration;
import com.graphicsfuzz.common.ast.type.TypeQualifier;
import com.graphicsfuzz.common.ast.visitors.StandardVisitor;
import com.graphicsfuzz.common.ast.visitors.VisitationDepth;
import com.graphicsfuzz.common.transformreduce.ShaderJob;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class RemoveRedundantUniformMetadataReductionOpportunities {

  private RemoveRedundantUniformMetadataReductionOpportunities() {
    // This class just provides a static method; there is no cause to create an instance of the
    // class.
  }

  static List<RemoveRedundantUniformMetadataReductionOpportunity> findOpportunities(
      ShaderJob shaderJob,
      ReducerContext context) {

    // We initially grab names of all uniforms in the pipeline info.
    final List<String> canBeRemoved =
        new ArrayList<>(shaderJob.getPipelineInfo().getUniformNames());

    for (TranslationUnit tu : shaderJob.getShaders()) {
      new StandardVisitor() {
        @Override
        public void visitVariablesDeclaration(VariablesDeclaration variablesDeclaration) {
          super.visitVariablesDeclaration(variablesDeclaration);
          // A uniform in the pipeline info that its declaration is found in shaders
          // will not be removed by the reducer.
          if (variablesDeclaration.getBaseType().hasQualifier(TypeQualifier.UNIFORM)) {
            for (VariableDeclInfo vdi : variablesDeclaration.getDeclInfos()) {
              canBeRemoved.remove(vdi.getName());
            }
          }
        }
      }.visit(tu);
    }

    // Since we are going to remove redundant uniforms in the pipeline info that do not
    // belong to any shader, the visitation depth should be zero.
    return canBeRemoved
        .stream()
        .map(item -> new RemoveRedundantUniformMetadataReductionOpportunity(item,
            shaderJob.getPipelineInfo(),
            new VisitationDepth(0))
        ).collect(Collectors.toList());
  }

}
