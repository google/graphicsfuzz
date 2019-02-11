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

package com.graphicsfuzz.generator.semanticschanging.astfuzzer.tool;

import static junit.framework.TestCase.assertTrue;

import com.graphicsfuzz.common.ast.type.BasicType;
import com.graphicsfuzz.common.ast.type.SamplerType;
import com.graphicsfuzz.common.glslversion.ShadingLanguageVersion;
import com.graphicsfuzz.generator.semanticschanging.astfuzzer.util.ExprInterchanger;
import com.graphicsfuzz.generator.semanticschanging.astfuzzer.util.FunctionCallInterchanger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Test;

public class TestFunctionsGroupedBySignature {

  InterchangeablesGroupedBySignature interchangeablesGroupedBySignature;
  Map<Signature, List<ExprInterchanger>> functionsBySignature;

  static List<ExprInterchanger> getSpecificInterchangeableForSignatureGivenMapping(
      InterchangeablesGroupedBySignature interchangeablesGroupedBySignature,
      Signature signature,
      Class interchangerClass,
      Map<Signature, List<ExprInterchanger>> mapping) {

    return interchangeablesGroupedBySignature
        .getInterchangeableForSignatureGivenMapping(signature, mapping)
        .stream()
        .filter(item -> item.getClass().equals(interchangerClass))
        .collect(Collectors.toList());

  }

  @Before
  public void setUp() {

    interchangeablesGroupedBySignature = new InterchangeablesGroupedBySignature(
        ShadingLanguageVersion.ESSL_100);
    functionsBySignature = new HashMap<>();
    addDummyMappings();
  }

  private void addDummyMappings() {

    functionsBySignature
        .put(new Signature(null,
                new ArrayList<>(Arrays.asList(BasicType.INT))
            ),
            new ArrayList<>(Arrays.asList(new FunctionCallInterchanger("match"))));
    functionsBySignature
        .put(new Signature(BasicType.FLOAT,
                new ArrayList<>(Arrays.asList(BasicType.INT))
            ),
            new ArrayList<>(Arrays.asList(new FunctionCallInterchanger("float"),
                new FunctionCallInterchanger("floatCast"),
                new FunctionCallInterchanger("CastFloat"))));
    functionsBySignature
        .put(new Signature(BasicType.BOOL,
                new ArrayList<>(Arrays.asList(BasicType.INT))
            ),
            new ArrayList<>(Arrays.asList(new FunctionCallInterchanger("isNull"),
                new FunctionCallInterchanger("isNotNull"),
                new FunctionCallInterchanger("isOrIsNotNull"))));
    functionsBySignature
        .put(new Signature(null,
                new ArrayList<>(Arrays.asList(BasicType.INT, null))
            ),
            new ArrayList<>(Arrays.asList(new FunctionCallInterchanger("nullIntNull"),
                new FunctionCallInterchanger("NullintNull"),
                new FunctionCallInterchanger("nullintNull"))));
    functionsBySignature
        .put(new Signature(SamplerType.ISAMPLER1D,
                new ArrayList<>(Arrays.asList(BasicType.INT, SamplerType.SAMPLER2DARRAY))
            ),
            new ArrayList<>(Arrays.asList(new FunctionCallInterchanger("sampIntSamp"),
                new FunctionCallInterchanger("SampintSamp"),
                new FunctionCallInterchanger("sampintSamp"))));
    functionsBySignature
        .put(new Signature(null,
                new ArrayList<>(Arrays.asList(BasicType.FLOAT, null))
            ),
            new ArrayList<>(Arrays.asList(new FunctionCallInterchanger("nullFloatNull"),
                new FunctionCallInterchanger("NullFloatNull"),
                new FunctionCallInterchanger("nullfloatnull"))));
  }

  private List<String> getFunctionNames(List<ExprInterchanger> matches) {
    return matches.stream()
        .filter(item -> item instanceof FunctionCallInterchanger)
        .map(item -> (FunctionCallInterchanger) item)
        .map(item -> item.getName())
        .collect(Collectors.toList());
  }

  @Test
  public void testBasicFunctionality() {
    List<ExprInterchanger> matches = getSpecificInterchangeableForSignatureGivenMapping(
        interchangeablesGroupedBySignature,
        new Signature(BasicType.FLOAT,
            new ArrayList<>(Arrays.asList(BasicType.INT))
        )
        , FunctionCallInterchanger.class, functionsBySignature
    );

    List<String> names = getFunctionNames(matches);
    assertTrue(names.contains("match"));
    assertTrue(names.contains("float"));
    assertTrue(names.contains("floatCast"));
    assertTrue(names.contains("CastFloat"));

    assertTrue(matches.size() == 4);

  }

  @Test
  public void testNullMatchesEverything1Argument() {
    List<ExprInterchanger> matches = getSpecificInterchangeableForSignatureGivenMapping(
        interchangeablesGroupedBySignature,
        new Signature(null,
            new ArrayList<>(Arrays.asList(BasicType.INT))
        )
        , FunctionCallInterchanger.class, functionsBySignature
    );

    List<String> names = getFunctionNames(matches);

    assertTrue(names.contains("float"));
    assertTrue(names.contains("floatCast"));
    assertTrue(names.contains("CastFloat"));
    assertTrue(names.contains("isNull"));
    assertTrue(names.contains("isNotNull"));
    assertTrue(names.contains("isOrIsNotNull"));
    assertTrue(matches.size() == 7);
  }

  @Test
  public void testNullMatchesEverything2Arguments() {
    List<ExprInterchanger> matches = getSpecificInterchangeableForSignatureGivenMapping(
        interchangeablesGroupedBySignature,
        new Signature(null,
            new ArrayList<>(Arrays.asList(BasicType.INT, null))
        )
        , FunctionCallInterchanger.class
        , functionsBySignature
    );

    List<String> names = getFunctionNames(matches);

    assertTrue(names.contains("nullIntNull"));
    assertTrue(names.contains("NullintNull"));
    assertTrue(names.contains("nullintNull"));
    assertTrue(names.contains("sampIntSamp"));
    assertTrue(names.contains("SampintSamp"));
    assertTrue(names.contains("sampintSamp"));
    assertTrue(matches.size() == 6);
  }

  @Test
  public void testNullAnythingMatchesNull2Arguments() {
    List<ExprInterchanger> matches = getSpecificInterchangeableForSignatureGivenMapping(
        interchangeablesGroupedBySignature,
        new Signature(SamplerType.ISAMPLER1D,
            new ArrayList<>(Arrays.asList(BasicType.INT, SamplerType.SAMPLER2DARRAY))
        )
        , FunctionCallInterchanger.class
        , functionsBySignature
    );

    List<String> names = getFunctionNames(matches);

    assertTrue(names.contains("nullIntNull"));
    assertTrue(names.contains("NullintNull"));
    assertTrue(names.contains("nullintNull"));
    assertTrue(names.contains("sampIntSamp"));
    assertTrue(names.contains("SampintSamp"));
    assertTrue(names.contains("sampintSamp"));
    assertTrue(matches.size() == 6);
  }


}

