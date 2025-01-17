/*
 * (C) Copyright 2006-2019 Nuxeo (http://nuxeo.com/) and others.
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
 *     Florent Guillaume
 */
package org.nuxeo.ecm.core.storage.sql.jdbc;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nuxeo.ecm.core.api.ConcurrentUpdateException;
import org.nuxeo.ecm.core.api.NuxeoException;
import org.nuxeo.ecm.core.storage.sql.Mapper.Identification;
import org.nuxeo.ecm.core.storage.sql.Model;
import org.nuxeo.ecm.core.storage.sql.jdbc.dialect.Dialect;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.datasource.ConnectionHelper;

/**
 * Holds a connection to a JDBC database.
 */
public class JDBCConnection {

    private static final Logger log = LogManager.getLogger(JDBCConnection.class);

    /** JDBC application name parameter for setClientInfo. */
    private static final String APPLICATION_NAME = "ApplicationName";

    private static final String SET_CLIENT_INFO_PROP = "org.nuxeo.vcs.setClientInfo";

    private static final String SET_CLIENT_INFO_DEFAULT = "false";

    /** The model used to do the mapping. */
    protected final Model model;

    /** The SQL information. */
    protected final SQLInfo sqlInfo;

    /** The dialect. */
    protected final Dialect dialect;

    /** The actual connection. */
    public Connection connection;

    protected boolean supportsBatchUpdates;

    // for tests
    public boolean countExecutes;

    // for tests
    public int executeCount;

    // for debug
    private static final AtomicLong instanceCounter = new AtomicLong(0);

    // for debug
    private final long instanceNumber = instanceCounter.incrementAndGet();

    // for debug
    public final JDBCLogger logger = new JDBCLogger(String.valueOf(instanceNumber));

    protected boolean setClientInfo;

    /**
     * Creates a new Mapper.
     *
     * @param model the model
     * @param sqlInfo the sql info
     */
    public JDBCConnection(Model model, SQLInfo sqlInfo) {
        this.model = model;
        this.sqlInfo = sqlInfo;
        dialect = sqlInfo.dialect;
        setClientInfo = Boolean.parseBoolean(Framework.getProperty(SET_CLIENT_INFO_PROP, SET_CLIENT_INFO_DEFAULT));
        connect();
    }

    /**
     * for tests only
     *
     * @since 5.9.3
     */
    public JDBCConnection() {
        sqlInfo = null;
        model = null;
        dialect = null;
    }

    public String getRepositoryName() {
        return model.getRepositoryDescriptor().name;
    }

    public Identification getIdentification() {
        return new Identification(null, "" + instanceNumber);
    }

    protected void countExecute() {
        if (countExecutes) {
            executeCount++;
        }
    }

    /**
     * Gets the datasource to use for the given repository.
     *
     * @since 8.4
     */
    public static String getDataSourceName(String repositoryName) {
        return "repository_" + repositoryName;
    }

    public void connect() {
        try {
            String dataSourceName = getDataSourceName(getRepositoryName());
            connection = ConnectionHelper.getConnection(dataSourceName);
            if (setClientInfo) {
                // log the mapper number (m=123)
                connection.setClientInfo(APPLICATION_NAME, "nuxeo m=" + instanceNumber);
            }
            supportsBatchUpdates = connection.getMetaData().supportsBatchUpdates();
            dialect.performPostOpenStatements(connection);
        } catch (SQLException cause) {
            throw new NuxeoException("Cannot connect to database: " + getRepositoryName(), cause);
        }
    }

    public void closeConnection() {
        if (connection != null) {
            try {
                try {
                    if (setClientInfo) {
                        // connection will become idle in the pool
                        connection.setClientInfo(APPLICATION_NAME, "nuxeo");
                    }
                } finally {
                    connection.close();
                }
            } catch (SQLException e) {
                log.error(e, e);
            } finally {
                connection = null;
            }
        }
    }

    /**
     * Checks the SQL error we got and determine if a concurrent update happened. Throws if that's the case.
     *
     * @param e the exception
     * @since 5.8
     */
    protected void checkConcurrentUpdate(Throwable e) throws ConcurrentUpdateException {
        if (dialect.isConcurrentUpdateException(e)) {
            log.debug(e, e);
            // don't keep the original message, as it may reveal database-level info
            throw new ConcurrentUpdateException("Concurrent update", e);
        }
    }

}
