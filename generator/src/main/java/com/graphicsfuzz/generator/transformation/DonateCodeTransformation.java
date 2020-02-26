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

import com.graphicsfuzz.common.ast.AstUtil;
import com.graphicsfuzz.common.ast.IAstNode;
import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.ast.decl.Declaration;
import com.graphicsfuzz.common.ast.decl.FunctionDefinition;
import com.graphicsfuzz.common.ast.decl.FunctionPrototype;
import com.graphicsfuzz.common.ast.decl.Initializer;
import com.graphicsfuzz.common.ast.decl.ParameterDecl;
import com.graphicsfuzz.common.ast.decl.PrecisionDeclaration;
import com.graphicsfuzz.common.ast.decl.VariableDeclInfo;
import com.graphicsfuzz.common.ast.decl.VariablesDeclaration;
import com.graphicsfuzz.common.ast.expr.FunctionCallExpr;
import com.graphicsfuzz.common.ast.expr.VariableIdentifierExpr;
import com.graphicsfuzz.common.ast.stmt.NullStmt;
import com.graphicsfuzz.common.ast.stmt.Stmt;
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
import com.graphicsfuzz.common.util.GlslParserException;
import com.graphicsfuzz.common.util.IRandom;
import com.graphicsfuzz.common.util.ListConcat;
import com.graphicsfuzz.common.util.MakeArrayAccessesInBounds;
import com.graphicsfuzz.common.util.OpenGlConstants;
import com.graphicsfuzz.common.util.ParseHelper;
import com.graphicsfuzz.common.util.ParseTimeoutException;
import com.graphicsfuzz.common.util.StructUtils;
import com.graphicsfuzz.generator.fuzzer.FuzzedIntoACornerException;
import com.graphicsfuzz.generator.fuzzer.Fuzzer;
import com.graphicsfuzz.generator.fuzzer.FuzzingContext;
import com.graphicsfuzz.generator.fuzzer.OpaqueExpressionGenerator;
import com.graphicsfuzz.generator.transformation.donation.DonationContext;
import com.graphicsfuzz.generator.transformation.donation.DonationContextFinder;
import com.graphicsfuzz.generator.transformation.donation.IncompatibleDonorException;
import com.graphicsfuzz.generator.transformation.injection.IInjectionPoint;
import com.graphicsfuzz.generator.transformation.injection.InjectionPoints;
import com.graphicsfuzz.generator.util.GenerationParams;
import com.graphicsfuzz.generator.util.TransformationProbabilities;
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
  private Map<File, TranslationUnit> donorsToTranslationUnits;

  private final List<FunctionPrototype> functionPrototypes;
  private final Map<String, Type> globalVariables;
  private final Set<String> structNames;

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
    this.functionPrototypes = new ArrayList<>();
    this.globalVariables = new HashMap<>();
    this.structNames = new HashSet<>();
    assert donorsDirectory.exists();
    this.donorFiles = new ArrayList<>();
    this.donorFiles.addAll(Arrays.asList(donorsDirectory.listFiles(
        pathname -> pathname.getName().endsWith(".frag"))));
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
    // To avoid undefined behaviours, make all array access in bounds for every donor.
    MakeArrayAccessesInBounds.makeInBounds(tu);
    addPrefixes(tu, getDeclaredFunctionNames(tu));
    // Add prefixed versions of these builtins, in case they are used.
    // Use explicit precision qualifier to avoid introducing errors if there are no float precision
    // qualifiers.
    tu.addDeclaration(new VariablesDeclaration(
        new QualifiedType(BasicType.VEC4, Collections.singletonList(TypeQualifier.MEDIUMP)),
        new VariableDeclInfo(addPrefix(OpenGlConstants.GL_FRAG_COORD), null, null)));
    if (tu.getShadingLanguageVersion().supportedGlFragColor()) {
      tu.addDeclaration(new VariablesDeclaration(
          new QualifiedType(BasicType.VEC4, Collections.singletonList(TypeQualifier.MEDIUMP)),
          new VariableDeclInfo(addPrefix(OpenGlConstants.GL_FRAG_COLOR), null, null)));
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
    adaptTranslationUnitForSpecificDonation(tu, generator);

    translationUnitCount++;
    return tu;
  }

  private void addPrefixes(TranslationUnit tu, final Set<String> declaredFunctionNames) {
    new StandardVisitor() {

      @Override
      public void visitVariableIdentifierExpr(VariableIdentifierExpr variableIdentifierExpr) {
        variableIdentifierExpr.setName(addPrefix(variableIdentifierExpr.getName()));
      }

      @Override
      public void visitParameterDecl(ParameterDecl parameterDecl) {
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
        if (!functionPrototype.getName().equals("main")) {
          functionPrototype.setName(addPrefix(functionPrototype.getName()));
        }
      }

      @Override
      public void visitFunctionCallExpr(FunctionCallExpr functionCallExpr) {
        super.visitFunctionCallExpr(functionCallExpr);
        if (declaredFunctionNames.contains(functionCallExpr.getCallee())) {
          functionCallExpr.setCallee(addPrefix(functionCallExpr.getCallee()));
        }
      }

    }.visit(tu);
  }

  private Set<String> getDeclaredFunctionNames(TranslationUnit tu) {
    return new StandardVisitor() {

      private Set<String> names = new HashSet<>();

      @Override
      public void visitFunctionPrototype(FunctionPrototype functionPrototype) {
        super.visitFunctionPrototype(functionPrototype);
        names.add(functionPrototype.getName());
      }

      Set<String> getDeclaredFunctionNames(TranslationUnit tu) {
        visit(tu);
        return names;
      }

    }.getDeclaredFunctionNames(tu);
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
      final Optional<TranslationUnit> maybeDonor = chooseDonor(generator, shadingLanguageVersion);

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
      DonationContext donationContext = new DonationContextFinder(maybeDonor.get(), generator)
          .getDonationContext();
      if (incompatible(injectionPoint, donationContext, shadingLanguageVersion)) {
        tries++;
        if (tries == maxTries) {
          // We have tried and tried to find something compatible to inject but not managed;
          // return a null statement instead of a real piece of code to inject.
          return new NullStmt();
        }
      } else {
        return prepareStatementToDonate(injectionPoint, donationContext, probabilities,
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

    functionPrototypes.addAll(AstUtil.getFunctionPrototypesFromShader(tu));
    globalVariables.putAll(getGlobalVariablesFromShader(tu));
    structNames.addAll(getStructNamesFromShader(tu));

    List<IInjectionPoint> injectionPoints = new InjectionPoints(tu, generator, x -> true)
        .getInjectionPoints(probabilityOfDonation);

    final List<Stmt> injectedStmts = new ArrayList<>();

    for (IInjectionPoint injectionPoint : injectionPoints) {
      final Stmt injectedStmt = prepareStatementToDonate(injectionPoint, probabilities,
          generator, tu.getShadingLanguageVersion());
      injectionPoint.inject(injectedStmt);
      injectedStmts.add(injectedStmt);
    }
    donateFunctionsAndGlobals(tu);
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
    donorsToTranslationUnits = new HashMap<>();
  }

  private boolean incompatible(IInjectionPoint injectionPoint, DonationContext donationContext,
                               ShadingLanguageVersion shadingLanguageVersion) {
    // It is a problem if the injection point has an available variable that has the same name
    // as a function called from the donation context
    if (injectionPoint.scopeAtInjectionPoint().namesOfAllVariablesInScope().stream().anyMatch(
        item -> getCalledFunctions(donationContext.getDonorFragment()).contains(item))) {
      return true;
    }
    if (shadingLanguageVersion.isWebGl()) {
      // TODO: revisit in case this was just a WebGL 1 restriction; maybe it is OK in WebGL 2
      if (donationContext.indexesArrayUsingFreeVariable()) {
        return true;
      }
    }
    return false;
  }

  private Map<String, Type> getGlobalVariablesFromShader(TranslationUnit shader) {
    return new ScopeTrackingVisitor() {
      Map<String, Type> getGlobalsFromShader(TranslationUnit shader) {
        visit(shader);
        Map<String, Type> result = new HashMap<>();
        for (String globalName : getCurrentScope().keys()) {
          result.put(globalName, getCurrentScope().lookupType(globalName));
        }
        return result;
      }
    }.getGlobalsFromShader(shader);
  }

  final Initializer getInitializer(IInjectionPoint injectionPoint,
                                   DonationContext donationContext,
                                   Type type,
                                   boolean restrictToConst,
                                   IRandom generator,
                                   ShadingLanguageVersion shadingLanguageVersion) {
    final boolean isConst = type.hasQualifier(TypeQualifier.CONST);
    try {

      // We may need to generate an initializer for a free variable of struct type.  The struct
      // will be present in the donor but not yet added to the recipient.  We thus make a
      // temporary scope identical to the scope at the injection point, but with all of the
      // structs from the donation context added.
      final Scope scopeForFuzzing = injectionPoint.scopeAtInjectionPoint().shallowClone();
      for (StructDefinitionType sdt : donationContext.getAvailableStructs()) {
        scopeForFuzzing.addStructDefinition(sdt);
      }

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

  private List<Declaration> getNecessaryFunctionsAndGlobalsFromDonor(
      TranslationUnit donor,
      List<FunctionPrototype> recipientFunctionPrototypes,
      Set<String> recipientGlobalNames) {

    List<Declaration> declarationsToAdd = new ArrayList<>();

    for (Declaration d : donor.getTopLevelDeclarations()) {
      if (d instanceof FunctionPrototype) {
        FunctionPrototype functionPrototype = (FunctionPrototype) d;
        if (!needToAddDonorFunction(functionPrototype, recipientFunctionPrototypes)) {
          continue;
        }
        declarationsToAdd.add(functionPrototype);
      }
      if (d instanceof FunctionDefinition) {
        FunctionDefinition fd = (FunctionDefinition) d;
        FunctionPrototype functionPrototype = fd.getPrototype();
        if (!needToAddDonorFunction(functionPrototype, recipientFunctionPrototypes)) {
          continue;
        }
        declarationsToAdd.add(fd);
      }
      if (d instanceof VariablesDeclaration) {
        List<VariableDeclInfo> declInfo = ((VariablesDeclaration) d).getDeclInfos().stream()
            .filter(vd -> !recipientGlobalNames.contains(vd.getName()))
            .collect(Collectors.toList());
        // It either contains a name not already used in the reference, or declares structs
        // which the donated code may need.
        if (declInfo.size() > 0 || !StructUtils.getStructDefinitions(d).isEmpty()) {
          declarationsToAdd
              .add(new VariablesDeclaration(((VariablesDeclaration) d).getBaseType(), declInfo));
        }
      }
    }
    return declarationsToAdd;
  }

  private void donateFunctionsAndGlobals(TranslationUnit recipient) {

    // We record the number of precision qualifiers in the recipient so that we can check that it
    // is not changed by donation.
    final long numPrecisionDeclarationsBeforeDonation = recipient.getTopLevelDeclarations()
        .stream()
        .filter(item -> item instanceof PrecisionDeclaration)
        .count();

    List<Declaration> newRecipientTopLevelDeclarations = recipient.getTopLevelDeclarations()
        .stream()
        .filter(d -> !(d instanceof FunctionDefinition)).collect(Collectors.toList());

    List<FunctionPrototype> recipientFunctionPrototypes = AstUtil
        .getFunctionPrototypesFromShader(recipient);
    Set<String> recipientGlobalNames = getVariableNames(recipient.getTopLevelDeclarations());

    for (TranslationUnit donor : donorFiles
        .stream()
        .filter(item -> donorsToTranslationUnits.containsKey(item))
        .map(item -> donorsToTranslationUnits.get(item))
        .collect(Collectors.toList())) {
      final List<Declaration> newDeclarations = getNecessaryFunctionsAndGlobalsFromDonor(donor,
          recipientFunctionPrototypes,
          recipientGlobalNames);
      checkNoDuplicateStructNames(newDeclarations, newRecipientTopLevelDeclarations);
      newRecipientTopLevelDeclarations.addAll(newDeclarations);
      recipientFunctionPrototypes.addAll(AstUtil.getPrototypesForAllFunctions(newDeclarations));
      recipientGlobalNames.addAll(getVariableNames(newDeclarations));
    }

    newRecipientTopLevelDeclarations.addAll(recipient.getTopLevelDeclarations().stream()
        .filter(d -> d instanceof FunctionDefinition).collect(Collectors.toList()));

    newRecipientTopLevelDeclarations =
        addNecessaryForwardDeclarations(newRecipientTopLevelDeclarations);

    recipient.setTopLevelDeclarations(newRecipientTopLevelDeclarations);

    // Check that the number of precision qualifiers present in the recipient has not changed due
    // to donation.
    final long numPrecisionDeclarationsAfterDonation =
        recipient.getTopLevelDeclarations()
            .stream()
            .filter(item -> item instanceof PrecisionDeclaration)
            .count();
    assert numPrecisionDeclarationsBeforeDonation == numPrecisionDeclarationsAfterDonation
        : "Donation should not affect top-level precision qualifiers.";

  }

  private void checkNoDuplicateStructNames(List<Declaration> toBeAdded,
                                           List<Declaration> existing) {
    List<String> existingStructNames = getStructNames(existing);
    for (String name : getStructNames(toBeAdded)) {
      assert !existingStructNames.contains(name);
    }
  }

  private List<String> getStructNames(List<Declaration> toBeAdded) {
    return toBeAdded
        .stream()
        .map(StructUtils::getStructDefinitions)
        .reduce(new ArrayList<>(), ListConcat::concatenate)
        .stream()
        .filter(StructDefinitionType::hasStructNameType)
        .map(item -> item.getStructNameType().getName())
        .collect(Collectors.toList());
  }

  private List<Declaration> addNecessaryForwardDeclarations(List<Declaration> decls) {

    // TODO: this does not take full account of overloading: it assumes that if any function called
    // foo has been defined then all overloads of foo have also been defined
    List<Declaration> result = new ArrayList<>();
    Set<FunctionPrototype> declared = new HashSet<>();

    List<Set<FunctionPrototype>> functionsDefinedAfterDecl = new ArrayList<>();
    for (int i = 0; i < decls.size(); i++) {
      functionsDefinedAfterDecl.add(new HashSet<>());
    }
    for (int i = decls.size() - 2; i >= 0; i--) {
      functionsDefinedAfterDecl.get(i).addAll(functionsDefinedAfterDecl.get(i + 1));
      if (decls.get(i + 1) instanceof FunctionDefinition) {
        functionsDefinedAfterDecl.get(i)
            .add(((FunctionDefinition) decls.get(i + 1)).getPrototype());
      }
    }

    for (int i = 0; i < decls.size(); i++) {
      if (decls.get(i) instanceof FunctionDefinition) {

        Set<String> calledFunctionNames =
            getCalledFunctions(decls.get(i));

        List<FunctionPrototype> toDeclare =
            functionsDefinedAfterDecl.get(i).stream().filter(item ->
                calledFunctionNames.contains(item.getName())).filter(item ->
                !declared.stream().anyMatch(alreadyDeclared -> alreadyDeclared.matches(item)))
                .collect(Collectors.toList());

        // Make sure we clone the FunctionPrototypes that are added, otherwise each will exist
        // twice in the AST: first as a top-level declaration, second as part of the
        // FunctionDefinition.
        result.addAll(toDeclare.stream().map(Declaration::clone).collect(Collectors.toList()));
        declared.addAll(toDeclare);
      }
      if (decls.get(i) instanceof FunctionDefinition) {
        declared.add(((FunctionDefinition) decls.get(i)).getPrototype());
      }
      if (decls.get(i) instanceof FunctionPrototype) {
        declared.add((FunctionPrototype) decls.get(i));
      }

      result.add(decls.get(i));

    }
    return removeRepeatedFunctionPrototypes(result);
  }

  Set<String> getCalledFunctions(final IAstNode node) {
    return new StandardVisitor() {

      private Set<String> calledFunctions = new HashSet<String>();

      @Override
      public void visitFunctionCallExpr(FunctionCallExpr functionCallExpr) {
        super.visitFunctionCallExpr(functionCallExpr);
        calledFunctions.add(functionCallExpr.getCallee());
      }

      public Set<String> calledFunctions() {
        visit(node);
        return calledFunctions;
      }

    }.calledFunctions();
  }

  private List<Declaration> removeRepeatedFunctionPrototypes(List<Declaration> decls) {
    List<Declaration> result = new ArrayList<>();
    Set<FunctionPrototype> declared = new HashSet<>();
    for (Declaration d : decls) {
      if (d instanceof FunctionPrototype) {
        if (declared.stream().anyMatch(item -> item.matches((FunctionPrototype) d))) {
          continue;
        }
        declared.add((FunctionPrototype) d);
      }
      result.add(d);
    }
    return result;
  }

  private Set<String> getStructNamesFromShader(TranslationUnit tu) {
    return tu.getStructDefinitions()
        .stream()
        .filter(StructDefinitionType::hasStructNameType)
        .map(item -> item.getStructNameType())
        .filter(item -> item instanceof StructNameType)
        .map(item -> ((StructNameType) item).getName())
        .collect(Collectors.toSet());
  }

  /**
   * Gets the names of all variables declared in the given list of declarations.
   *
   * @param declarations A list of declarations
   * @return The names of all variables declared in the list of declarations
   */
  private Set<String> getVariableNames(List<Declaration> declarations) {
    Set<VariablesDeclaration> variablesDeclarations = declarations.stream()
        .filter(x -> x instanceof VariablesDeclaration)
        .map(x -> (VariablesDeclaration) x).collect(Collectors.toSet());
    Set<String> names = new HashSet<>();
    for (VariablesDeclaration decls : variablesDeclarations) {
      names.addAll(decls.getDeclInfos().stream().map(x -> x.getName()).collect(Collectors.toSet()));
    }
    return names;
  }

  private boolean needToAddDonorFunction(FunctionPrototype fromDonor,
                                         List<FunctionPrototype> recipientFunctionPrototypes) {
    if (fromDonor.getName().equals("main") || prototypeMatches(fromDonor,
        recipientFunctionPrototypes)) {
      return false;
    }
    if (prototypeClashes(fromDonor, recipientFunctionPrototypes)) {
      throw new RuntimeException(
          "Donor and recipient are incompatible as they declare clashing functions named "
              + fromDonor.getName());
    }
    return true;
  }

  private boolean prototypeClashes(FunctionPrototype fp, List<FunctionPrototype> fs) {
    return !fs.stream().filter(item -> fp.clashes(item)).collect(Collectors.toList()).isEmpty();
  }

  private boolean prototypeMatches(FunctionPrototype fp, List<FunctionPrototype> fs) {
    return !fs.stream().filter(item -> fp.matches(item)).collect(Collectors.toList()).isEmpty();
  }

  Optional<TranslationUnit> chooseDonor(IRandom generator,
                                        ShadingLanguageVersion shadingLanguageVersion) {
    // The donors that we have previously selected during this donation pass are captured via
    // 'donorsToTranslationUnits'.  Furthermore, there is a maximum number of distinct donors we
    // are allowed to use per donation pass.  So first check whether the donors we have already
    // used have hit this maximum.

    final String previouslyUsedDonorFoundToBeIncompatibleMessage = "A donor that was previously "
        + "used has now been found to be incompatible.  This should not occur.";

    if (donorsToTranslationUnits.size() >= generationParams.getMaxDonorsPerDonationPass()) {
      // We have used the maximum number of donors we are allowed to use in this pass, so choose
      // one of them again.
      // The limit should be reached but never exceeded.
      assert donorsToTranslationUnits.size() == generationParams.getMaxDonorsPerDonationPass();
      final List<File> sortedKeys = new ArrayList<>(donorsToTranslationUnits.keySet());
      sortedKeys.sort(Comparator.naturalOrder());
      try {
        return Optional.of(getDonorTranslationUnit(sortedKeys.get(generator.nextInt(sortedKeys
                .size())),
            generator, shadingLanguageVersion));
      } catch (IncompatibleDonorException exception) {
        throw new RuntimeException(previouslyUsedDonorFoundToBeIncompatibleMessage);
      }
    }

    // We have not reached the limit.  So we can choose any one of the available donor files.  It
    // might turn out to be a donor we tried before (in which case it will already be a key to
    // the 'donorsToTranslationUnits' map), or it may be new.
    while (!donorFiles.isEmpty()) {
      final File candidateDonorFile = donorFiles
          .get(generator.nextInt(donorFiles.size()));
      try {
        return Optional.of(getDonorTranslationUnit(candidateDonorFile, generator,
            shadingLanguageVersion));
      } catch (IncompatibleDonorException exception) {
        if (donorsToTranslationUnits.containsKey(candidateDonorFile)) {
          throw new RuntimeException(previouslyUsedDonorFoundToBeIncompatibleMessage);
        }
        donorFiles.remove(candidateDonorFile);
      }
    }
    // We did not manage to find any suitable donor.  This happens if all donors prove to be
    // incompatible.
    return Optional.empty();
  }

  private TranslationUnit getDonorTranslationUnit(File donorFile, IRandom generator,
                                                  ShadingLanguageVersion shadingLanguageVersion)
      throws IncompatibleDonorException {
    if (!donorsToTranslationUnits.containsKey(donorFile)) {
      try {
        TranslationUnit donor = prepareTranslationUnit(donorFile, generator);
        if (!compatibleDonor(donor, shadingLanguageVersion)) {
          throw new IncompatibleDonorException();
        }
        functionPrototypes.addAll(AstUtil.getFunctionPrototypesFromShader(donor));
        globalVariables.putAll(getGlobalVariablesFromShader(donor));
        structNames.addAll(getStructNamesFromShader(donor));
        donorsToTranslationUnits.put(donorFile, donor);
      } catch (IOException | ParseTimeoutException | InterruptedException
          | GlslParserException exception) {
        throw new RuntimeException("An exception occurred during donor parsing.", exception);
      }
    }
    return donorsToTranslationUnits.get(donorFile);
  }

  private boolean compatibleDonor(TranslationUnit donor,
                                  ShadingLanguageVersion shadingLanguageVersion) {
    final List<String> usedFunctionNames = functionPrototypes.stream()
        .map(FunctionPrototype::getName)
        .collect(Collectors.toList());
    final Set<String> usedGlobalVariableNames = globalVariables.keySet();
    final Set<String> usedStructNames = structNames;
    for (FunctionPrototype donorPrototype : AstUtil.getFunctionPrototypesFromShader(donor)) {
      if (usedGlobalVariableNames.contains(donorPrototype.getName())
          || usedStructNames.contains(donorPrototype.getName())) {
        return false;
      }
      if (functionPrototypes.stream().anyMatch(item -> item.clashes(donorPrototype))) {
        return false;
      }
    }
    for (Map.Entry<String, Type> global : getGlobalVariablesFromShader(donor).entrySet()) {
      final String name = global.getKey();
      final Type type = global.getValue();
      if (usedFunctionNames.contains(name) || usedStructNames.contains(name)) {
        return false;
      }
      if (globalVariables.containsKey(name) && !globalVariables.get(name).equals(type)) {
        // We have a global with the same name but a different type, so the shaders are not
        // compatible
        return false;
      }
    }
    for (String name : getStructNamesFromShader(donor)) {
      if (usedFunctionNames.contains(name) || usedGlobalVariableNames.contains(name)
          || usedStructNames.contains(name)) {
        return false;
      }
    }
    return true;
  }

  Type dropQualifiersThatCannotBeUsedForLocalVariable(Type type) {
    if (!(type instanceof QualifiedType)) {
      return type;
    }
    final QualifiedType qualifiedType = (QualifiedType) type;
    final List<TypeQualifier> newQualifiers = new ArrayList<>();
    for (TypeQualifier qualifier : qualifiedType.getQualifiers()) {
      // There are probably other qualifiers that are not allowed; move them up as we discover this.
      if (Arrays.asList(
          TypeQualifier.IN_PARAM,
          TypeQualifier.INOUT_PARAM,
          TypeQualifier.OUT_PARAM,
          TypeQualifier.UNIFORM,
          TypeQualifier.SHADER_INPUT,
          TypeQualifier.SHADER_OUTPUT,
          TypeQualifier.SHARED).contains(qualifier)) {
        continue;
      }
      if (qualifier instanceof LayoutQualifierSequence) {
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

}
