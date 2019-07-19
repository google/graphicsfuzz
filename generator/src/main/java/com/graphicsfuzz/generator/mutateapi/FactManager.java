package com.graphicsfuzz.generator.mutateapi;

import com.graphicsfuzz.common.ast.TranslationUnit;
import com.graphicsfuzz.common.ast.decl.Declaration;
import com.graphicsfuzz.common.ast.decl.FunctionDefinition;
import com.graphicsfuzz.common.ast.decl.FunctionPrototype;
import com.graphicsfuzz.common.ast.decl.Initializer;
import com.graphicsfuzz.common.ast.decl.VariableDeclInfo;
import com.graphicsfuzz.common.ast.decl.VariablesDeclaration;
import com.graphicsfuzz.common.ast.expr.Expr;
import com.graphicsfuzz.common.ast.expr.FloatConstantExpr;
import com.graphicsfuzz.common.ast.expr.FunctionCallExpr;
import com.graphicsfuzz.common.ast.expr.VariableIdentifierExpr;
import com.graphicsfuzz.common.ast.stmt.BlockStmt;
import com.graphicsfuzz.common.ast.stmt.DeclarationStmt;
import com.graphicsfuzz.common.ast.stmt.ExprStmt;
import com.graphicsfuzz.common.ast.stmt.ReturnStmt;
import com.graphicsfuzz.common.ast.stmt.Stmt;
import com.graphicsfuzz.common.ast.type.BasicType;
import com.graphicsfuzz.common.ast.type.QualifiedType;
import com.graphicsfuzz.common.ast.visitors.StandardVisitor;
import com.graphicsfuzz.common.util.IRandom;
import com.graphicsfuzz.common.util.PipelineInfo;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FactManager {

  private final FactManager prototype;

  private final Map<VariableDeclInfo, VariableFact> variableFacts;
  private final Map<FunctionPrototype, FunctionFact> functionFacts;

  public FactManager(FactManager prototype) {
    this.prototype = prototype;
    variableFacts = new HashMap<>();
    functionFacts = new HashMap<>();
  }

  public static Expr generateExpr(TranslationUnit tu,
                                  PipelineInfo pipelineInfo,
                                  FactManager factManager,
                                  FunctionDefinition functionDefinition,
                                  Stmt stmt,
                                  Value value,
                                  IRandom generator) {

    String primitiveValueString = value.getData().get(0).get().toString();
    
    switch (generator.nextInt(2)) {
      // variableFact
      case 0:
        String variableName =
            "_GLF_PRIMITIVE_VAR_" + value.getType().toString() + "_" + primitiveValueString.replace("."
                , "_");
        VariableDeclInfo declInfo =
            new VariableDeclInfo(variableName,
                null,
                new Initializer(new FloatConstantExpr(primitiveValueString)));
        VariablesDeclaration variablesDecl = new VariablesDeclaration(value.getType(), declInfo);
        functionDefinition.getBody().addStmt(new DeclarationStmt(variablesDecl));

        // Store variable fact in FactManager
        factManager.getVariableFacts().put(declInfo, new VariableFact(variablesDecl, declInfo,
            value));
        return new VariableIdentifierExpr(variableName);


      case 1:
        // functionFact

        String functionName =
            "_GLF_COMPUTE_"
                + value.getType().toString()
                + "_" + primitiveValueString.replace(".", "_");
        List<Declaration> declarations = new ArrayList<>(tu.clone().getTopLevelDeclarations());
        FunctionPrototype functionPrototype = new FunctionPrototype(functionName,
            new QualifiedType(value.getType(), Arrays.asList()));

        declarations.add(0, new FunctionDefinition(functionPrototype,
            new BlockStmt(
                Arrays.asList(
                    new ReturnStmt(
                        new FloatConstantExpr(primitiveValueString))), false)
        ));

        tu.setTopLevelDeclarations(declarations);
        // Store function fact in FactManager
        factManager.getFunctionFacts().put(functionPrototype,
            new FunctionFact(functionPrototype,
                new ArrayList<>(),
                value
            ));

        return new FunctionCallExpr(functionName, Arrays.asList());
    }


    return null;
  }


  public Map<VariableDeclInfo, VariableFact> getVariableFacts() {
    return variableFacts;
  }

  public Map<FunctionPrototype, FunctionFact> getFunctionFacts() {
    return functionFacts;
  }



}
