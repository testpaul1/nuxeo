/*
 * (C) Copyright 2010-2015 Nuxeo SA (http://nuxeo.com/) and others.
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
 *     tdelprat, jcarsique
 */

package org.nuxeo.ecm.admin;

import java.io.File;
import java.io.IOException;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.common.Environment;
import org.nuxeo.launcher.config.ConfigurationConstants;
import org.nuxeo.launcher.config.ConfigurationGenerator;
import org.nuxeo.runtime.api.Framework;

/**
 * Helper class to call NuxeoCtl restart.
 *
 * @author Tiry (tdelprat@nuxeo.com)
 */
public class NuxeoCtlManager {

    private static final Logger log = LogManager.getLogger(NuxeoCtlManager.class);

    protected static final String CMD_POSIX = "nuxeoctl";

    protected static final String CMD_WIN = "nuxeoctl.bat";

    private ConfigurationGenerator cg;

    private static String winEscape(String command) {
        return command.replaceAll("([ ()<>&])", "^$1");
    }

    protected static boolean doExec(String path, String logPath) {
        try {
            String[] cmd = getCommand(path);
            log.debug("Restart command: {}", () -> StringUtils.join(cmd, " "));
            ProcessBuilder pb = new ProcessBuilder(cmd).redirectOutput(new File(logPath, "restart.log"))
                                                       .redirectError(new File(logPath, "restart-err.log"));
            pb.start();
        } catch (IOException e) {
            log.error("Unable to restart server", e);
            return false;
        }

        return true;
    }

    /**
     * Gets the OS dependent command for nuxeoctl restartbg
     *
     * @param path the Nuxeo bin path
     * @return an array of String to pass to the {@link ProcessBuilder}
     * @since 9.2
     */
    protected static String[] getCommand(String path) {
        if (SystemUtils.IS_OS_WINDOWS) {
            return new String[] { "cmd", "/C", winEscape(new File(path, CMD_WIN).getPath()), "--gui=false",
                    "restartbg" };
        }
        return new String[] { "/bin/sh", "-c", "\"" + new File(path, CMD_POSIX).getPath() + "\"" + " restartbg" };
    }

    private static boolean restartInProgress = false;

    public static synchronized boolean restart() {
        if (restartInProgress) {
            return false;
        }
        restartInProgress = true;
        String nuxeoHome = Framework.getProperty(Environment.NUXEO_HOME);
        final String binPath = new File(nuxeoHome, "bin").getPath();
        final String logDir = Framework.getProperty(Environment.NUXEO_LOG_DIR, nuxeoHome);
        new Thread("restart thread") {
            @Override
            public void run() {
                try {
                    log.info("Restarting Nuxeo server");
                    Thread.sleep(3000);
                    doExec(binPath, logDir);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.error("Restart failed", e);
                }
            }
        }.start();
        return true;
    }

    public String restartServer() {
        restart();
        return "Nuxeo server is restarting";
    }

    /**
     * @since 5.6
     * @return Configured server URL (may differ from current URL)
     */
    public String getServerURL() {
        if (cg == null) {
            cg = ConfigurationGenerator.builder().init(true).build();
        }
        return cg.getUserConfig().getProperty(ConfigurationConstants.PARAM_NUXEO_URL);
    }

}
