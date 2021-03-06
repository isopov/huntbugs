/*
 * Copyright 2016 HuntBugs contributors
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package one.util.huntbugs.detect;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import com.strobel.assembler.metadata.MethodDefinition;
import com.strobel.assembler.metadata.TypeDefinition;
import com.strobel.assembler.metadata.TypeReference;
import com.strobel.decompiler.ast.AstCode;
import com.strobel.decompiler.ast.Block;
import com.strobel.decompiler.ast.CatchBlock;
import com.strobel.decompiler.ast.Expression;
import com.strobel.decompiler.ast.Variable;

import one.util.huntbugs.flow.Inf;
import one.util.huntbugs.flow.ValuesFlow;
import one.util.huntbugs.registry.MethodContext;
import one.util.huntbugs.registry.anno.AstNodes;
import one.util.huntbugs.registry.anno.AstVisitor;
import one.util.huntbugs.registry.anno.WarningDefinition;
import one.util.huntbugs.util.NodeChain;
import one.util.huntbugs.util.Nodes;
import one.util.huntbugs.util.Types;
import one.util.huntbugs.warning.Roles;

/**
 * @author Tagir Valeev
 *
 */
@WarningDefinition(category="RedundantCode", name="DeadStoreInReturn", maxScore=50)
@WarningDefinition(category="RedundantCode", name="DeadIncrementInReturn", maxScore=60)
@WarningDefinition(category="RedundantCode", name="DeadIncrementInAssignment", maxScore=60)
@WarningDefinition(category="RedundantCode", name="DeadParameterStore", maxScore=60)
@WarningDefinition(category="RedundantCode", name="DeadLocalStore", maxScore=50)
@WarningDefinition(category="RedundantCode", name="UnusedLocalVariable", maxScore=35)
public class DeadLocalStore {
    @AstVisitor(nodes=AstNodes.EXPRESSIONS)
    public void visit(Expression expr, NodeChain nc, MethodContext mc, MethodDefinition md, TypeDefinition td) {
        if(expr.getCode() == AstCode.Return && expr.getArguments().size() == 1) {
            Expression arg = expr.getArguments().get(0);
            if(arg.getCode() == AstCode.Store) {
                mc.report("DeadStoreInReturn", 0, arg);
            } else if(arg.getCode() == AstCode.PostIncrement) {
                Expression var = arg.getArguments().get(0);
                if(var.getOperand() instanceof Variable)
                    mc.report("DeadIncrementInReturn", 0, var);
            }
        }
        if(expr.getCode() == AstCode.Store) {
            Variable var = (Variable) expr.getOperand();
            if(var.isGenerated())
                return;
            Expression child = expr.getArguments().get(0);
            if(child.getCode() == AstCode.PostIncrement) {
                Expression load = child.getArguments().get(0);
                if(load.getCode() == AstCode.Load && var.equals(load.getOperand()) && Integer.valueOf(1).equals(child.getOperand())) {
                    mc.report("DeadIncrementInAssignment", 0, expr, Roles.EXPRESSION.create(var.getName() + " = " + var
                            .getName() + "++"));
                }
            }
            List<Expression> args = child.getCode() == AstCode.TernaryOp ? child.getArguments().subList(1, 3)
                    : Collections.singletonList(child);
            for(Expression arg : args) {
                // exclude AstCode.Store (chaining assignment) as procyon sometimes reorders locals assignments
                if (mc.isAnnotated() && ValuesFlow.getSource(arg) == arg && arg.getCode() != AstCode.Store
                    && arg.getCode() != AstCode.PutField && arg.getCode() != AstCode.PutStatic) {
                    Set<Expression> usages = Inf.BACKLINK.findUsages(arg);
                    if(usages.size() == 1 && usages.iterator().next() == expr) {
                        Set<Expression> storeUsages = Inf.BACKLINK.findUsages(expr);
                        if(storeUsages.isEmpty()) {
                            if(nc.getNode() instanceof CatchBlock && nc.getNode().getChildren().get(0) == expr
                                    && ((CatchBlock)nc.getNode()).getCaughtTypes().size() > 1) {
                                // Exception variable in multi-catch block
                                return;
                            }
                            // autogenerated by javacc
                            if (md.getName().equals("adjustBeginLineColumn") && td.getSimpleName().equals(
                                "SimpleCharStream") || md.getName().startsWith("jj") && td.getSimpleName().endsWith(
                                    "TokenManager")) {
                                return;
                            }
                            // autogenerated by antlr
                            if(Types.is(td.getBaseType(), "org/antlr/runtime/Lexer")) {
                                return;
                            }
                            // autogenerated by protocol buffers
                            if(Types.is(td.getBaseType(), "com/google/protobuf/GeneratedMessage$Builder")) {
                                return;
                            }
                            // autogenerated by jflex
                            if (var.getName().equals("offset") && md.getName().startsWith("zz") && td.getSimpleName()
                                    .endsWith("Lexer")) {
                                return;
                            }
                            int priority = 0;
                            Block root = nc.getRoot();
                            if(arg.getCode() == AstCode.LdC && !var.isParameter()) {
                                Object val = arg.getOperand();
                                TypeReference tr = arg.getInferredType();
                                if(tr != null && (tr.isPrimitive() || Types.isString(tr))) {
                                    // probably final constant var which is inlined when used
                                    if (Nodes.find(root, n -> n != expr && n instanceof Expression
                                            && ((Expression) n).getOperand() == var) == null) {
                                        if(Nodes.find(root, n -> n != arg && n instanceof Expression
                                            && val.equals(((Expression) n).getOperand())) != null)
                                            return;
                                        // If constant value is not used explicitly, it's still possible that it's inlined
                                        // inside other constant expression, thus lower the priority
                                        priority += 20;
                                    }
                                }
                            }
                            if(var.isParameter()) {
                                mc.report("DeadParameterStore", priority, expr);
                            } else {
                                boolean unusedLocal = Nodes.find(root, n -> n instanceof Expression
                                    && ((Expression) n).getOperand() == var && ((Expression) n).getCode() != AstCode.Store) == null;
                                String type;
                                if (!unusedLocal) {
                                    type = "DeadLocalStore";
                                    if(arg.getCode() == AstCode.AConstNull)
                                        priority += 20;
                                    else if(arg.getCode() == AstCode.LdC) {
                                        if (arg.getOperand() instanceof Number
                                            && ((Number) arg.getOperand()).doubleValue() == 0.0)
                                            priority += 20;
                                        else if ("".equals(arg.getOperand()) || Integer.valueOf(1).equals(arg.getOperand())
                                                || Integer.valueOf(-1).equals(arg.getOperand()))
                                            priority += 10;
                                    }
                                } else {
                                    type = "UnusedLocalVariable";
                                }
                                mc.report(type, priority, expr);
                            }
                        }
                    }
                }
            }
        }
    }
}
