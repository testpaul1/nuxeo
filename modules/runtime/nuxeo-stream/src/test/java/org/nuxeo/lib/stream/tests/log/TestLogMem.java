/*
 * (C) Copyright 2022 Nuxeo.
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
package org.nuxeo.lib.stream.tests.log;

import org.junit.After;
import org.nuxeo.lib.stream.log.LogManager;
import org.nuxeo.lib.stream.log.mem.MemLogManager;

public class TestLogMem extends TestLog {

    @After
    public void clear() {
        MemLogManager.clear();
    }

    @Override
    public LogManager createManager() throws Exception {
        return new MemLogManager();
    }

}
