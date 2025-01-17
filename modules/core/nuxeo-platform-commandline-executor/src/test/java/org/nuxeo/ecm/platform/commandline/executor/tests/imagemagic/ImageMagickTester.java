/*
 * (C) Copyright 2006-2022 Nuxeo SA (http://nuxeo.com/) and others.
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
 *
 * Contributors:
 *     Nuxeo - initial API and implementation
 */
package org.nuxeo.ecm.platform.commandline.executor.tests.imagemagic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.List;

import javax.inject.Inject;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.common.utils.FileUtils;
import org.nuxeo.ecm.platform.commandline.executor.api.CmdParameters;
import org.nuxeo.ecm.platform.commandline.executor.api.CommandLineExecutorService;
import org.nuxeo.ecm.platform.commandline.executor.api.CommandNotAvailable;
import org.nuxeo.ecm.platform.commandline.executor.api.ExecResult;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.RuntimeFeature;

/**
 * Test identify imagemagick commandline.
 */
@RunWith(FeaturesRunner.class)
@Features(RuntimeFeature.class)
@Deploy("org.nuxeo.ecm.platform.commandline.executor")
@Deploy("org.nuxeo.ecm.platform.commandline.executor:OSGI-INF/commandline-imagemagic-test-contrib.xml")
public class ImageMagickTester {

    @Inject
    protected CommandLineExecutorService cles;

    @Test
    public void testIdentifyExec() throws CommandNotAvailable {
        File img = FileUtils.getResourceFileFromContext("test.png");

        CmdParameters params = cles.getDefaultCmdParameters();
        params.addNamedParameter("filePath", img);

        ExecResult result = cles.execCommand("identify", params);

        assertTrue(result.isSuccessful());
        assertSame(0, result.getReturnCode());

        List<String> lines = result.getOutput();

        assertEquals("PNG 48 48", lines.get(0));
    }

    /**
     * NXP-31458 - Validate that ImageMagick is able to identify/convert a raw image format such as .crw without the
     * deprecated ufraw utility installed.
     *
     * @since 2023
     */
    @Test
    public void testRawImageFormat() throws CommandNotAvailable {
        File img = FileUtils.getResourceFileFromContext("Canon.crw");

        // identify
        CmdParameters params = cles.getDefaultCmdParameters();
        params.addNamedParameter("filePath", img);

        ExecResult result = cles.execCommand("identify", params);

        assertTrue(result.isSuccessful());
        assertEquals(0, result.getReturnCode());

        // convert
        params = cles.getDefaultCmdParameters();
        params.addNamedParameter("inputFilePath", img);
        File output = new File(img.getPath().replace("crw", "png"));
        params.addNamedParameter("outputFilePath", output);

        result = cles.execCommand("converter", params);

        assertTrue(result.isSuccessful());
        assertEquals(0, result.getReturnCode());

        // check converted file is PNG
        params = cles.getDefaultCmdParameters();
        params.addNamedParameter("filePath", output);

        result = cles.execCommand("identify", params);

        assertTrue(result.isSuccessful());
        assertEquals(0, result.getReturnCode());
        List<String> lines = result.getOutput();
        assertEquals("PNG 1552 1024", lines.get(0));
    }

}
