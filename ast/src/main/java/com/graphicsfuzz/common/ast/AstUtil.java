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

package com.graphicsfuzz.common.ast;

import com.graphicsfuzz.common.ast.decl.Declaration;
import com.graphicsfuzz.common.ast.decl.FunctionDefinition;
import com.graphicsfuzz.common.ast.decl.FunctionPrototype;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class AstUtil {

  private AstUtil() {
    // Utility class should not be instantiable.
  }

  /**
   * Gets prototypes for all the functions declared, either via full declarations or
   * just prototypes, in the given shader.
   *
   * @param shader A shader from which function prototypes should be extracted
   * @return set of prototypes for all functions declared fully or via prototypes in the shader
   */
  public static List<FunctionPrototype> getFunctionPrototypesFromShader(TranslationUnit shader) {
    return getPrototypesForAllFunctions(shader.getTopLevelDeclarations());
  }

  /**
   * Gets prototypes for all the functions declarations or prototypes in the given list of
   * declarations.
   *
   * @param decls A list of declarations
   * @return set of prototypes for all functions declared fully or via prototypes in the declaration
   *         list
   */
  public static List<FunctionPrototype> getPrototypesForAllFunctions(List<Declaration> decls) {
    // Grab all prototypes associated with function definitions
    List<FunctionPrototype> result =
        getFunctionDefinitions(decls).map(x -> x.getPrototype()).collect(Collectors.toList());
    // Add any additional prototypes
    // TODO: we only check whether a function definition with a matching name is present;
    //       we should strengthen this to consider instead an exact prototype match.
    result.addAll(
        getFunctionPrototypes(decls).filter(
            x -> !getFunctionNames(result)
                .contains(x.getName())).collect(Collectors.toList()));
    return result;
  }

  private static Stream<FunctionDefinition> getFunctionDefinitions(List<Declaration> decls) {
    return decls.stream().filter(x -> x instanceof FunctionDefinition)
        .map(x -> ((FunctionDefinition) x));
  }

  private static Stream<FunctionPrototype> getFunctionPrototypes(List<Declaration> decls) {
    return decls.stream().filter(x -> x instanceof FunctionPrototype)
        .map(x -> (FunctionPrototype) x);
  }

  private static List<String> getFunctionNames(List<FunctionPrototype> prototypes) {
    return prototypes.stream().map(y -> y.getName()).collect(Collectors.toList());
  }

}
