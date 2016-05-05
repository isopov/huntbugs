/*
 * Copyright 2015, 2016 Tagir Valeev
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

import com.strobel.decompiler.ast.CaseBlock;
import com.strobel.decompiler.ast.Node;
import com.strobel.decompiler.ast.Switch;

import one.util.huntbugs.registry.MethodContext;
import one.util.huntbugs.registry.anno.AstVisitor;
import one.util.huntbugs.registry.anno.WarningDefinition;

/**
 * @author isopov
 */
@WarningDefinition(category = "BadPractice", name = "SwitchNoDefault", maxScore = 50)
public class SwitchNoDefault {
    @AstVisitor
    public void visit(Node node, MethodContext mc) {
        if (node instanceof Switch) {
            Switch sw = (Switch) node;

            if (!sw.getCaseBlocks().stream().anyMatch(CaseBlock::isDefault)) {
                mc.report("SwitchNoDefault", 0, node);
            }
        }
    }
}
