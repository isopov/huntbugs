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
package one.util.huntbugs.flow;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Predicate;

import one.util.huntbugs.analysis.Context;
import one.util.huntbugs.flow.SourceAnnotator.Frame;
import one.util.huntbugs.util.Types;
import com.strobel.assembler.metadata.BuiltinTypes;
import com.strobel.assembler.metadata.MethodDefinition;
import com.strobel.assembler.metadata.TypeReference;
import com.strobel.decompiler.ast.AstCode;
import com.strobel.decompiler.ast.Block;
import com.strobel.decompiler.ast.Expression;

/**
 * @author Tagir Valeev
 *
 */
public class ValuesFlow {
    public static List<Expression> annotate(Context ctx, MethodDefinition md, ClassFields cf, Block method) {
        ctx.incStat("ValuesFlow");
        CFG cfg = CFG.build(md, method);
        Frame origFrame = Inf.SOURCE.build(cf, cfg);
        if(origFrame == null) {
            ctx.incStat("Inf.SOURCE2.Incomplete/ValuesFlow");
        }
        if(!Inf.CONST.build(cfg)) {
            ctx.incStat("Inf.CONST.Incomplete/ValuesFlow");
        }
        cfg.forBodies((smd, smethod) -> Inf.PURITY.annotate(smethod, new FrameContext(smd, cf)));
        cfg.forBodies((smd, smethod) -> Inf.BACKLINK.annotate(smethod));
        return origFrame == null ? null : new ArrayList<>(origFrame.initial.values());
    }

    public static <T> T reduce(Expression input, Function<Expression, T> mapper, BinaryOperator<T> reducer,
            Predicate<T> pred) {
        Expression source = getSource(input);
        if (source.getCode() == AstCode.TernaryOp) {
            T left = reduce(source.getArguments().get(1), mapper, reducer, pred);
            if(pred.test(left))
                return left;
            T right = reduce(source.getArguments().get(2), mapper, reducer, pred);
            return reducer.apply(left, right);
        }
        if (source.getCode() != SourceAnnotator.PHI_TYPE)
            return mapper.apply(source);
        boolean first = true;
        T result = null;
        for (Expression child : source.getArguments()) {
            if (first) {
                result = reduce(child, mapper, reducer, pred);
                first = false;
            } else {
                result = reducer.apply(result, reduce(child, mapper, reducer, pred));
            }
            if(pred.test(result))
                return result;
        }
        return result;
    }
    
    private static TypeReference mergeTypes(TypeReference t1, TypeReference t2) {
        if (t1 == null || t2 == null)
            return null;
        if (t1 == BuiltinTypes.Null)
            return t2;
        if (t2 == BuiltinTypes.Null)
            return t1;
        if (t1.isEquivalentTo(t2))
            return t1;
        if(t1.isArray() ^ t2.isArray())
            return null;
        if(t1.isArray()) {
            TypeReference merged = mergeTypes(t1.getElementType(), t2.getElementType());
            return merged == null ? null : merged.makeArrayType();
        }
        List<TypeReference> chain1 = Types.getBaseTypes(t1);
        List<TypeReference> chain2 = Types.getBaseTypes(t2);
        for (int i = Math.min(chain1.size(), chain2.size()) - 1; i >= 0; i--) {
            if (chain1.get(i).equals(chain2.get(i)))
                return chain1.get(i);
        }
        return null;
    }

    public static TypeReference reduceType(Expression input) {
        return reduce(input, e -> e.getCode() == AstCode.AConstNull ?
                BuiltinTypes.Null : Types.getExpressionType(e), ValuesFlow::mergeTypes, Objects::isNull);
    }

    public static Expression getSource(Expression input) {
        Expression source = Inf.SOURCE.get(input);
        return source == null ? input : source;
    }
    
    public static boolean allMatch(Expression src, Predicate<Expression> pred) {
        if(src.getCode() == SourceAnnotator.PHI_TYPE)
            return src.getArguments().stream().allMatch(pred);
        if(src.getCode() == AstCode.TernaryOp)
            return allMatch(getSource(src.getArguments().get(1)), pred) &&
                    allMatch(getSource(src.getArguments().get(2)), pred);
        return pred.test(src);
    }

    public static boolean anyMatch(Expression src, Predicate<Expression> pred) {
        if(src.getCode() == SourceAnnotator.PHI_TYPE)
            return src.getArguments().stream().anyMatch(pred);
        if(src.getCode() == AstCode.TernaryOp)
            return anyMatch(getSource(src.getArguments().get(1)), pred) ||
                    anyMatch(getSource(src.getArguments().get(2)), pred);
        return pred.test(src);
    }
    
    public static Expression findFirst(Expression src, Predicate<Expression> pred) {
        if(src.getCode() == SourceAnnotator.PHI_TYPE)
            return src.getArguments().stream().filter(pred).findFirst().orElse(null);
        if(src.getCode() == AstCode.TernaryOp) {
            Expression result = findFirst(getSource(src.getArguments().get(1)), pred);
            return result == null ? findFirst(getSource(src.getArguments().get(2)), pred) : result;
        }
        return pred.test(src) ? src : null;
    }

    public static boolean hasPhiSource(Expression input) {
        Expression source = Inf.SOURCE.get(input);
        return source != null && source.getCode() == SourceAnnotator.PHI_TYPE;
    }

    public static boolean isSpecial(Expression expr) {
        return expr.getCode() == SourceAnnotator.PHI_TYPE || expr.getCode() == SourceAnnotator.UPDATE_TYPE;
    }

    public static boolean hasUpdatedSource(Expression e) {
        return ValuesFlow.getSource(e).getCode() == SourceAnnotator.UPDATE_TYPE;
    }
}
