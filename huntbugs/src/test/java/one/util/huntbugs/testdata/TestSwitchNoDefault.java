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
package one.util.huntbugs.testdata;

import one.util.huntbugs.registry.anno.AssertWarning;

/**
 * @author isopov
 *
 */
public class TestSwitchNoDefault {


    @AssertWarning(type = "SwitchNoDefault")
    public void shouldWarn() {
        switch (1) {
        case 1:
            break;
        }
    }

    public void shouldNotWarn() {
        switch (1) {
        case 1:
            break;
        default:
            break;
        }
    }
}
