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

package com.graphicsfuzz.generator.transformation;

import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.ast.decl.ArrayInfo;
import com.graphicsfuzz.common.ast.decl.Declaration;
import com.graphicsfuzz.common.ast.decl.DefaultLayout;
import com.graphicsfuzz.common.ast.decl.FunctionPrototype;
import com.graphicsfuzz.common.ast.decl.Initializer;
import com.graphicsfuzz.common.ast.decl.InterfaceBlock;
import com.graphicsfuzz.common.ast.decl.ParameterDecl;
import com.graphicsfuzz.common.ast.decl.VariableDeclInfo;
import com.graphicsfuzz.common.ast.decl.VariablesDeclaration;
import com.graphicsfuzz.common.ast.expr.FunctionCallExpr;
import com.graphicsfuzz.common.ast.expr.IntConstantExpr;
import com.graphicsfuzz.common.ast.expr.TypeConstructorExpr;
import com.graphicsfuzz.common.ast.expr.VariableIdentifierExpr;
import com.graphicsfuzz.common.ast.stmt.NullStmt;
import com.graphicsfuzz.common.ast.stmt.Stmt;
import com.graphicsfuzz.common.ast.type.ArrayType;
import com.graphicsfuzz.common.ast.type.BasicType;
import com.graphicsfuzz.common.ast.type.LayoutQualifierSequence;
import com.graphicsfuzz.common.ast.type.QualifiedType;
import com.graphicsfuzz.common.ast.type.StructDefinitionType;
import com.graphicsfuzz.common.ast.type.StructNameType;
import com.graphicsfuzz.common.ast.type.Type;
import com.graphicsfuzz.common.ast.type.TypeQualifier;
import com.graphicsfuzz.common.ast.visitors.StandardVisitor;
import com.graphicsfuzz.common.glslversion.ShadingLanguageVersion;
import com.graphicsfuzz.common.typing.Scope;
import com.graphicsfuzz.common.typing.ScopeTrackingVisitor;
import com.graphicsfuzz.common.typing.Typer;
import com.graphicsfuzz.common.typing.TyperHelper;
import com.graphicsfuzz.common.util.GlslParserException;
import com.graphicsfuzz.common.util.IRandom;
import com.graphicsfuzz.common.util.MakeArrayAccessesInBounds;
import com.graphicsfuzz.common.util.OpenGlConstants;
import com.graphicsfuzz.common.util.ParseHelper;
import com.graphicsfuzz.common.util.ParseTimeoutException;
import com.graphicsfuzz.common.util.ShaderKind;
import com.graphicsfuzz.generator.fuzzer.FuzzedIntoACornerException;
import com.graphicsfuzz.generator.fuzzer.Fuzzer;
import com.graphicsfuzz.generator.fuzzer.FuzzingContext;
import com.graphicsfuzz.generator.fuzzer.OpaqueExpressionGenerator;
import com.graphicsfuzz.generator.transformation.donation.DonationContext;
import com.graphicsfuzz.generator.transformation.donation.DonationContextFinder;
import com.graphicsfuzz.generator.transformation.injection.IInjectionPoint;
import com.graphicsfuzz.generator.transformation.injection.InjectionPoints;
import com.graphicsfuzz.generator.util.GenerationParams;
import com.graphicsfuzz.generator.util.TransformationProbabilities;
import com.graphicsfuzz.util.Constants;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public abstract class DonateCodeTransformation implements ITransformation {

  private final Function<IRandom, Boolean> probabilityOfDonation;

  // During a single donation pass, this is populated on demand with the donors that are used.
  private final Map<File, TranslationUnit> donorsToTranslationUnits;

  // 'donorFiles' contains those donors that have not yet been used for a code donation.  Once a
  // donor has been used, it is moved to 'usedDonorFiles' (unless it is found to be incompatible,
  // in which case it is discarded).  If 'donorFiles' becomes empty, it and 'usedDonorFiles' are
  // swapped, so that previously used donors can be used again.
  private List<File> donorFiles;
  private List<File> usedDonorFiles;

  final GenerationParams generationParams;
  private int translationUnitCount;

  public DonateCodeTransformation(Function<IRandom, Boolean> probabilityOfDonation,
                                  File donorsDirectory, GenerationParams generationParams) {
    this.probabilityOfDonation = probabilityOfDonation;
    this.donorsToTranslationUnits = new HashMap<>();
    assert donorsDirectory.exists();
    this.donorFiles = new ArrayList<>();
    this.donorFiles.addAll(Arrays.asList(donorsDirectory.listFiles(
        pathname -> pathname.getName().endsWith("."
            + generationParams.getShaderKind().getFileExtension()))));
    this.donorFiles.sort(Comparator.naturalOrder());
    this.usedDonorFiles = new ArrayList<>();
    this.generationParams = generationParams;
    this.translationUnitCount = 0;
  }

  /**
   * Allows a translation unit to be modified in a donation-specific manner.
   * The motivating example for including this is to avoid long-running loops
   * during live code injection.
   *
   * @param tu Translation unit to be adapted.
   */
  abstract void adaptTranslationUnitForSpecificDonation(TranslationUnit tu, IRandom generator);

  private TranslationUnit prepareTranslationUnit(File donorFile, IRandom generator)
      throws IOException, ParseTimeoutException, InterruptedException, GlslParserException {
    final TranslationUnit tu = ParseHelper.parse(donorFile);

    // First, simplify the array info objects used by the shader so that they only refer to const
    // expressions.  This is to avoid the situation where an array with e.g. a constant SOME_SIZE
    // as its size expression gets donated into a context where SOME_SIZE is not declared.
    simplifyArrayInfo(tu);

    // Add a prefix to every identifier used in the shader.
    addPrefixes(tu);

    // Add prefixed versions of these builtins, in case they are used.
    // Use explicit precision qualifiers to avoid introducing errors if there are no float precision
    // qualifiers.
    if (generationParams.getShaderKind() == ShaderKind.FRAGMENT) {
      tu.addDeclaration(new VariablesDeclaration(
          new QualifiedType(BasicType.VEC2, Collections.singletonList(TypeQualifier.MEDIUMP)),
          new VariableDeclInfo(addPrefix(OpenGlConstants.GL_POINT_COORD), null, null)));
      tu.addDeclaration(new VariablesDeclaration(
          new QualifiedType(BasicType.VEC4, Collections.singletonList(TypeQualifier.MEDIUMP)),
          new VariableDeclInfo(addPrefix(OpenGlConstants.GL_FRAG_COORD), null, null)));
      tu.addDeclaration(new VariablesDeclaration(
          BasicType.BOOL,
          new VariableDeclInfo(addPrefix(OpenGlConstants.GL_FRONT_FACING), null, null)));
      if (tu.getShadingLanguageVersion().supportedGlFragColor()) {
        tu.addDeclaration(new VariablesDeclaration(
            new QualifiedType(BasicType.VEC4, Collections.singletonList(TypeQualifier.MEDIUMP)),
            new VariableDeclInfo(addPrefix(OpenGlConstants.GL_FRAG_COLOR), null, null)));
      }
    } else if (generationParams.getShaderKind() == ShaderKind.VERTEX) {
      tu.addDeclaration(new VariablesDeclaration(
          new QualifiedType(BasicType.FLOAT, Collections.singletonList(TypeQualifier.HIGHP)),
          new VariableDeclInfo(addPrefix(OpenGlConstants.GL_POINT_SIZE), null, null)));
      tu.addDeclaration(new VariablesDeclaration(
          new QualifiedType(BasicType.VEC4, Collections.singletonList(TypeQualifier.HIGHP)),
          new VariableDeclInfo(addPrefix(OpenGlConstants.GL_POSITION), null, null)));
    } else if (generationParams.getShaderKind() == ShaderKind.COMPUTE) {
      tu.addDeclaration(new VariablesDeclaration(BasicType.UVEC3,
          new VariableDeclInfo(addPrefix(OpenGlConstants.GL_GLOBAL_INVOCATION_ID), null, null)));
      tu.addDeclaration(new VariablesDeclaration(BasicType.UVEC3,
          new VariableDeclInfo(addPrefix(OpenGlConstants.GL_LOCAL_INVOCATION_ID), null, null)));
      tu.addDeclaration(new VariablesDeclaration(BasicType.UVEC3,
          new VariableDeclInfo(addPrefix(OpenGlConstants.GL_WORK_GROUP_SIZE), null, null)));
      tu.addDeclaration(new VariablesDeclaration(BasicType.UVEC3,
          new VariableDeclInfo(addPrefix(OpenGlConstants.GL_WORK_GROUP_ID), null, null)));
      tu.addDeclaration(new VariablesDeclaration(BasicType.UVEC3,
          new VariableDeclInfo(addPrefix(OpenGlConstants.GL_NUM_WORK_GROUPS), null, null)));
      tu.addDeclaration(new VariablesDeclaration(BasicType.UINT,
          new VariableDeclInfo(addPrefix(OpenGlConstants.GL_LOCAL_INVOCATION_INDEX), null, null)));
    }

    // Remove 'in', 'out' and 'layout' qualifiers from global variables.  This means that, for
    // instance, the output colour variable of a donated fragment shader will change from:
    //   layout(location = 0) out vec4 name;
    // to:
    //   vec4 name;
    for (VariablesDeclaration variablesDeclarationWithQualifiers :
        tu.getTopLevelDeclarations().stream().filter(item -> item instanceof VariablesDeclaration)
            .map(item -> (VariablesDeclaration) item)
            .filter(item -> item.getBaseType() instanceof QualifiedType)
            .collect(Collectors.toList())) {
      // Get an unqualified version of the base type.
      final Type baseTypeWithoutQualifiers =
          variablesDeclarationWithQualifiers.getBaseType().getWithoutQualifiers();
      // Get all the original qualifiers except the ones we wish to remove.
      final List<TypeQualifier> strippedQualifiers =
          ((QualifiedType) variablesDeclarationWithQualifiers.getBaseType()).getQualifiers()
              .stream()
              .filter(qualifier -> !(qualifier instanceof LayoutQualifierSequence)
                  && qualifier != TypeQualifier.SHADER_INPUT
                  && qualifier != TypeQualifier.SHADER_OUTPUT)
              .collect(Collectors.toList());
      // Set the base type to be qualified with only these qualifiers.
      variablesDeclarationWithQualifiers.setBaseType(new QualifiedType(
          baseTypeWithoutQualifiers,
          strippedQualifiers));
    }

    // Each interface block in a shader needs to be removed, and every previous member of the
    // interface block replaced with a corresponding global variable.
    //
    // Consider every interface block.
    for (InterfaceBlock interfaceBlock :
        tu.getTopLevelDeclarations().stream().filter(item -> item instanceof InterfaceBlock)
            .map(item -> (InterfaceBlock) item)
            .collect(Collectors.toList())) {
      // Consider every member of the block.
      for (String memberName : interfaceBlock.getMemberNames()) {
        // We will declare a global variable of the member's type with a prefixed version of the
        // member's name.  However, we need to take care regarding array members, in particular
        // because interface blocks can have unsized arrays.
        final Type memberType = interfaceBlock.getMemberType(memberName).get();

        // This will be the array-free base type of the new global variable.
        Type plainVariableBaseType;
        // If the member was an array, this will allow a sized array to be declared.
        ArrayInfo plainVariableArrayInfo;
        if (memberType.getWithoutQualifiers() instanceof ArrayType) {
          // The member is an array, so we need to deal with it's size.
          final ArrayType memberArrayType = ((ArrayType) memberType.getWithoutQualifiers());
          // The array variable we declare with have the array member's base type as its base type.
          plainVariableBaseType = memberArrayType.getBaseType();
          if (memberArrayType.getArrayInfo().hasSizeExpr(0)) {
            // The array was already sized; we re-use the size.
            plainVariableArrayInfo = memberArrayType.getArrayInfo();
          } else {
            // The array was unsized, so we give it a made up size.  We set its size expression to
            // be an expression that yields this size, and also make its known-constant size be the
            // same integer value.
            plainVariableArrayInfo =
                new ArrayInfo(Collections.singletonList(Optional.of(new IntConstantExpr(
                Integer.toString(Constants.DUMMY_SIZE_FOR_UNSIZED_ARRAY_DONATION)))));
            plainVariableArrayInfo.setConstantSizeExpr(0,
                Constants.DUMMY_SIZE_FOR_UNSIZED_ARRAY_DONATION);
          }
        } else {
          plainVariableBaseType = memberType;
          plainVariableArrayInfo = null;
        }
        // Add the global variable corresponding to the member right before the interface block.
        tu.addDeclarationBefore(
            new VariablesDeclaration(plainVariableBaseType,
                new VariableDeclInfo(addPrefix(memberName),
                    plainVariableArrayInfo, null)),
            interfaceBlock);
      }
      // Now that globals for all members have been added, remove the interface block.
      tu.removeTopLevelDeclaration(interfaceBlock);
    }

    // Remove all default layouts, such as the local size of workgroups in compute shaders, from
    // the donor, to avoid clashes with similar information in the reference.
    for (DefaultLayout defaultLayout : tu.getTopLevelDeclarations()
        .stream()
        .filter(item -> item instanceof DefaultLayout)
        .map(item -> (DefaultLayout) item)
        .collect(Collectors.toList())) {
      tu.removeTopLevelDeclaration(defaultLayout);
    }

    // To avoid undefined behaviours, make all array access in bounds for every donor.
    MakeArrayAccessesInBounds.makeInBounds(tu);

    // To avoid undefined behaviours, make sure all variables are initialized for every donor.
    // We do this regardless of whether we are injecting live or dead code, because global
    // variables from dead code injection may end up getting used in fuzzed expressions.  We could
    // avoid initializing local variables from dead code injection, but it seems harmless to
    // initialize everything.
    initializeAllVariables(tu, generator);

    adaptTranslationUnitForSpecificDonation(tu, generator);

    translationUnitCount++;
    return tu;
  }

  /**
   * For every array info object that contains a size expression, replace that size expression with
   * an integer constant expression reflecting the array's constant-folded size.
   */
  private void simplifyArrayInfo(TranslationUnit tu) {
    new StandardVisitor() {
      @Override
      public void visitArrayInfo(ArrayInfo arrayInfo) {
        super.visitArrayInfo(arrayInfo);
        for (int i = 0; i < arrayInfo.getDimensionality(); i++) {
          if (arrayInfo.hasSizeExpr(i)) {
            assert arrayInfo.hasConstantSize(i);
            arrayInfo.resetSizeExprToConstant(i);
          }
        }
      }
    }.visit(tu);
  }

  private void addPrefixes(TranslationUnit tu) {
    new StandardVisitor() {

      @Override
      public void visitVariableIdentifierExpr(VariableIdentifierExpr variableIdentifierExpr) {
        variableIdentifierExpr.setName(addPrefix(variableIdentifierExpr.getName()));
      }

      @Override
      public void visitParameterDecl(ParameterDecl parameterDecl) {
        super.visitParameterDecl(parameterDecl);
        if (parameterDecl.getName() != null) {
          parameterDecl.setName(addPrefix(parameterDecl.getName()));
        }
      }

      @Override
      public void visitVariableDeclInfo(VariableDeclInfo variableDeclInfo) {
        super.visitVariableDeclInfo(variableDeclInfo);
        variableDeclInfo.setName(addPrefix(variableDeclInfo.getName()));
      }

      @Override
      public void visitFunctionPrototype(FunctionPrototype functionPrototype) {
        super.visitFunctionPrototype(functionPrototype);
        functionPrototype.setName(addPrefix(functionPrototype.getName()));
      }

      @Override
      public void visitFunctionCallExpr(FunctionCallExpr functionCallExpr) {
        super.visitFunctionCallExpr(functionCallExpr);
        if (!TyperHelper.getBuiltins(tu.getShadingLanguageVersion(), tu.getShaderKind())
            .containsKey(functionCallExpr.getCallee())) {
          functionCallExpr.setCallee(addPrefix(functionCallExpr.getCallee()));
        }
      }

      @Override
      public void visitTypeConstructorExpr(TypeConstructorExpr typeConstructorExpr) {
        super.visitTypeConstructorExpr(typeConstructorExpr);
        // Get the standard names of all basic types.
        final Set<String> basicTypeNames = new HashSet<>(BasicType.allBasicTypes()
            .stream()
            .map(BasicType::toString).collect(Collectors.toSet()));
        // Add alternative names for square matrices.
        basicTypeNames.add("mat2x2");
        basicTypeNames.add("mat3x3");
        basicTypeNames.add("mat4x4");
        if (!basicTypeNames.contains(typeConstructorExpr.getTypename())) {
          // The type constructor is not one of these names, so it must be a struct; we add its
          // prefix.
          typeConstructorExpr.setTypename(addPrefix(typeConstructorExpr.getTypename()));
        }
      }

      @Override
      public void visitStructNameType(StructNameType structNameType) {
        super.visitStructNameType(structNameType);
        structNameType.setName(addPrefix(structNameType.getName()));
      }

    }.visit(tu);
  }

  abstract Stmt prepareStatementToDonate(IInjectionPoint injectionPoint,
                                         DonationContext donationContext,
                                         TransformationProbabilities probabilities,
                                         IRandom generator,
                                         ShadingLanguageVersion shadingLanguageVersion);

  private Stmt prepareStatementToDonate(IInjectionPoint injectionPoint,
                                        TransformationProbabilities probabilities,
                                        IRandom generator,
                                        ShadingLanguageVersion shadingLanguageVersion) {
    final int maxTries = 10;
    int tries = 0;
    while (true) {
      final Optional<TranslationUnit> maybeDonor = chooseDonor(generator);

      if (!maybeDonor.isPresent()) {
        // No compatible donors were found, thus we cannot do serious code donation here;
        // we return a null statement instead.
        return new NullStmt();
      } else {
        if (shadingLanguageVersion != maybeDonor.get().getShadingLanguageVersion()) {
          throw new RuntimeException("Incompatible versions: reference=" + shadingLanguageVersion
              + " donor=" + maybeDonor.get().getShadingLanguageVersion());
        }
        if (this.generationParams.getShaderKind() != maybeDonor.get().getShaderKind()) {
          throw new RuntimeException("Incompatible shader types: reference="
              + this.generationParams.getShaderKind() + " donor="
              + maybeDonor.get().getShaderKind());
        }
      }
      Optional<DonationContext> donationContext = new DonationContextFinder(maybeDonor.get(),
          generator)
          .getDonationContext();
      if (!donationContext.isPresent() || incompatible(injectionPoint, donationContext.get(),
          shadingLanguageVersion)) {
        tries++;
        if (tries == maxTries) {
          // We have tried and tried to find something compatible to inject but not managed;
          // return a null statement instead of a real piece of code to inject.
          return new NullStmt();
        }
      } else {
        return prepareStatementToDonate(injectionPoint, donationContext.get(), probabilities,
            generator,
            shadingLanguageVersion);
      }
    }
  }

  @Override
  public boolean apply(TranslationUnit tu,
                       TransformationProbabilities probabilities,
                       IRandom generator,
                       GenerationParams generationParams) {

    if (donorFiles.isEmpty()) {
      return false;
    }

    List<IInjectionPoint> injectionPoints = new InjectionPoints(tu, generator, x -> true)
        .getInjectionPoints(probabilityOfDonation);

    for (IInjectionPoint injectionPoint : injectionPoints) {
      final Stmt injectedStmt = prepareStatementToDonate(injectionPoint, probabilities,
          generator, tu.getShadingLanguageVersion());
      injectionPoint.inject(injectedStmt);
    }

    final List<Declaration> newRecipientTopLevelDeclarations = new ArrayList<>();

    for (TranslationUnit donor : donorFiles
        .stream()
        .filter(donorsToTranslationUnits::containsKey)
        .map(donorsToTranslationUnits::get)
        .collect(Collectors.toList())) {
      newRecipientTopLevelDeclarations.addAll(donor.getTopLevelDeclarations());
    }
    newRecipientTopLevelDeclarations.addAll(tu.getTopLevelDeclarations());

    tu.setTopLevelDeclarations(newRecipientTopLevelDeclarations);

    // If injectionSwitch is present, move it to the top of the shader.  This is because other
    // passes may assume that it is available globally and use it in donated functions that appear
    // earlier than where injectionSwitch originally appeared.
    final Optional<VariablesDeclaration> maybeInjectionSwitch =
        tu.getTopLevelDeclarations()
            .stream()
            .filter(item -> item instanceof VariablesDeclaration)
            .map(item -> (VariablesDeclaration) item)
            .filter(item -> item.getDeclInfos().size() == 1
                && item.getDeclInfo(0).getName().equals(Constants.INJECTION_SWITCH))
            .findFirst();
    if (maybeInjectionSwitch.isPresent()) {
      tu.removeTopLevelDeclaration(maybeInjectionSwitch.get());
      tu.addDeclaration(maybeInjectionSwitch.get());
    }

    eliminateUsedDonors();
    return !injectionPoints.isEmpty();

  }

  private void eliminateUsedDonors() {
    // Having done donation using a particular donor, we move it to the list of used donors so that
    // we only use it again in a future pass once all other donors have been tried.
    for (File donor : donorsToTranslationUnits.keySet()) {
      assert donorFiles.contains(donor);
      donorFiles.remove(donor);
      usedDonorFiles.add(donor);
    }
    // If there are no donors left -- i.e., if all have been used, then recycle the list of used
    // donors.
    if (donorFiles.isEmpty()) {
      donorFiles = usedDonorFiles;
      usedDonorFiles = new ArrayList<>();
    }
    donorsToTranslationUnits.clear();
  }

  private boolean incompatible(IInjectionPoint injectionPoint, DonationContext donationContext,
                               ShadingLanguageVersion shadingLanguageVersion) {
    if (shadingLanguageVersion.isWebGl()) {
      // TODO: revisit in case this was just a WebGL 1 restriction; maybe it is OK in WebGL 2
      if (donationContext.indexesArrayUsingFreeVariable()) {
        return true;
      }
    }
    return false;
  }

  final Initializer getInitializer(IInjectionPoint injectionPoint,
                                   DonationContext donationContext,
                                   Type type,
                                   boolean restrictToConst,
                                   IRandom generator,
                                   ShadingLanguageVersion shadingLanguageVersion) {
    final boolean isConst = type.hasQualifier(TypeQualifier.CONST);
    // We may need to generate an initializer for a free variable of struct type.  The struct
    // will be present in the donor but not yet added to the recipient.  We thus make a
    // temporary scope identical to the scope at the injection point, but with all of the
    // structs from the donation context added.
    final Scope scopeForFuzzing = injectionPoint.scopeAtInjectionPoint().shallowClone();
    for (StructDefinitionType sdt : donationContext.getAvailableStructs()) {
      scopeForFuzzing.addStructDefinition(sdt);
    }
    try {
      return new Initializer(
          new OpaqueExpressionGenerator(generator, generationParams, shadingLanguageVersion)
              .fuzzedConstructor(
                  new Fuzzer(new FuzzingContext(scopeForFuzzing),
                      shadingLanguageVersion,
                      generator,
                      generationParams)
                      .fuzzExpr(type, false, restrictToConst, 0)));
    } catch (FuzzedIntoACornerException exception) {
      if (isConst) {
        // We need to give up, since we have to initialize const variables.
        throw exception;
      }
      // We simply make do without an initializer, as we didn't manage to fuzz one.
      return null;
    }
  }

  private Optional<TranslationUnit> chooseDonor(IRandom generator) {
    // The donors that we have previously selected during this donation pass are captured via
    // 'donorsToTranslationUnits'.  Furthermore, there is a maximum number of distinct donors we
    // are allowed to use per donation pass.  So first check whether the donors we have already
    // used have hit this maximum.

    if (donorsToTranslationUnits.size() >= generationParams.getMaxDonorsPerDonationPass()) {
      // We have used the maximum number of donors we are allowed to use in this pass, so choose
      // one of them again.
      // The limit should be reached but never exceeded.
      assert donorsToTranslationUnits.size() == generationParams.getMaxDonorsPerDonationPass();
      final List<File> sortedKeys = new ArrayList<>(donorsToTranslationUnits.keySet());
      sortedKeys.sort(Comparator.naturalOrder());
      return Optional.of(getDonorTranslationUnit(sortedKeys.get(generator.nextInt(sortedKeys
              .size())),
          generator));
    }

    return Optional.of(getDonorTranslationUnit(donorFiles
            .get(generator.nextInt(donorFiles.size())), generator
        ));
  }

  private TranslationUnit getDonorTranslationUnit(File donorFile, IRandom generator) {
    if (!donorsToTranslationUnits.containsKey(donorFile)) {
      try {
        donorsToTranslationUnits.put(donorFile, prepareTranslationUnit(donorFile, generator));
      } catch (IOException | ParseTimeoutException | InterruptedException
          | GlslParserException exception) {
        throw new RuntimeException("An exception occurred during donor parsing.", exception);
      }
    }
    return donorsToTranslationUnits.get(donorFile);
  }

  Type dropQualifiersThatCannotBeUsedForLocalVariable(Type type) {
    if (!(type instanceof QualifiedType)) {
      return type;
    }
    final QualifiedType qualifiedType = (QualifiedType) type;
    final List<TypeQualifier> newQualifiers = new ArrayList<>();
    for (TypeQualifier qualifier : qualifiedType.getQualifiers()) {

      // The following qualifiers are only allowed on global variables, and global variables should
      // not occur as free variables (being global, they are always available).
      assert qualifier != TypeQualifier.UNIFORM;
      assert qualifier != TypeQualifier.SHADER_INPUT;
      assert qualifier != TypeQualifier.SHADER_OUTPUT;
      assert qualifier != TypeQualifier.SHARED;
      assert !(qualifier instanceof LayoutQualifierSequence);

      // There are probably other qualifiers that are not allowed; move them up as we discover this.
      if (Arrays.asList(
          TypeQualifier.IN_PARAM,
          TypeQualifier.INOUT_PARAM,
          TypeQualifier.OUT_PARAM).contains(qualifier)) {
        continue;
      }
      newQualifiers.add(qualifier);
    }
    return new QualifiedType(qualifiedType.getTargetType(), newQualifiers);
  }

  abstract String getPrefix();

  String addPrefix(String name) {
    return getPrefix() + translationUnitCount + name;
  }

  private void initializeAllVariables(TranslationUnit tu, IRandom generator) {
    // We consider every variable declaration in the translation unit, and give it an initializer
    // if it doesn't have one.

    new ScopeTrackingVisitor() {

      // This gives us access to the base type of the group of variable declarations we are
      // currently visiting, if any.
      private VariablesDeclaration currentVariablesDeclaration = null;

      @Override
      public void visitVariablesDeclaration(VariablesDeclaration variablesDeclaration) {
        // Skip variables for which initializers are illegal.
        if (variablesDeclaration.getBaseType().hasQualifier(TypeQualifier.UNIFORM)
            || variablesDeclaration.getBaseType().hasQualifier(TypeQualifier.SHARED)) {
          return;
        }
        // We now let the scope tracking visitor process the variables declaration, and we
        // override visitVariableDeclInfo() to ensure that each declaration is initialized.  It is
        // important to do it this way because:
        // - giving initializers to each declaration before invoking the superclass doesn't work in
        //   the case where the base type for the variables declaration introduces a struct type,
        //   as the struct type will not have been added to the current scope.
        // - giving initializers to each declaration after invoking the superclass doesn't work
        //   because a variable is then made available to the expression fuzzer at the point where
        //   we fuzz an initializer for the variable, so that a variable can appear in its own
        //   initialization expression, which is illegal.
        assert currentVariablesDeclaration == null;
        currentVariablesDeclaration = variablesDeclaration;
        super.visitVariablesDeclaration(variablesDeclaration);
        assert currentVariablesDeclaration != null;
        currentVariablesDeclaration = null;
      }

      @Override
      public void visitVariableDeclInfo(VariableDeclInfo variableDeclInfo) {
        assert currentVariablesDeclaration != null;
        if (variableDeclInfo.hasInitializer()) {
          return;
        }
        if (variableDeclInfo.hasArrayInfo()
            && !tu.getShadingLanguageVersion().supportedArrayConstructors()) {
          // We cannot initialize this variable: it is an array, and the shading language doesn't
          // support array constructors.
          return;
        }
        final Type type = Typer.combineBaseTypeAndArrayInfo(
            currentVariablesDeclaration.getBaseType(),
            variableDeclInfo.getArrayInfo());
        final boolean restrictToConst = atGlobalScope()
            || currentVariablesDeclaration.getBaseType().hasQualifier(TypeQualifier.CONST);
        variableDeclInfo.setInitializer(new Initializer(
            new OpaqueExpressionGenerator(generator, generationParams,
                tu.getShadingLanguageVersion())
                .fuzzedConstructor(
                    new Fuzzer(new FuzzingContext(getCurrentScope()),
                        tu.getShadingLanguageVersion(),
                        generator,
                        generationParams)
                        .fuzzExpr(type, false, restrictToConst, 0))));
      }
    }.visit(tu);
  }

}
