/*
 * (C) Copyright 2006-2017 Nuxeo (http://nuxeo.com/) and others.
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
package org.nuxeo.ecm.core.storage.sql;

import java.io.Serializable;
import java.util.Calendar;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

import org.nuxeo.ecm.core.api.IterableQueryResult;
import org.nuxeo.ecm.core.api.PartialList;
import org.nuxeo.ecm.core.api.ScrollResult;
import org.nuxeo.ecm.core.query.QueryFilter;

/**
 * A {@link Mapper} maps {@link Row}s to and from the database.
 */
public interface Mapper extends RowMapper {

    /**
     * Executes the given query and returns the first batch of results containing id of documents, next batch must be
     * requested within the {@code keepAliveSeconds} delay.
     *
     * @since 8.4
     */
    ScrollResult<String> scroll(String query, int batchSize, int keepAliveSeconds);

    /**
     * Executes the given query and returns the first batch of results containing id of documents, next batch must be
     * requested within the {@code keepAliveSeconds} delay.
     *
     * @since 10.3
     */
    ScrollResult<String> scroll(String query, QueryFilter queryFilter, int batchSize, int keepAliveSeconds);

    /**
     * Get the next batch of results containing id of documents, the {@code scrollId} is part of the previous
     * {@link ScrollResult} response.
     *
     * @since 8.4
     */
    ScrollResult<String> scroll(String scrollId);

    /**
     * Identifiers assigned by a server to identify a client mapper and its repository.
     */
    final class Identification implements Serializable {

        private static final long serialVersionUID = 1L;

        public final String repositoryId;

        public final String mapperId;

        public Identification(String repositoryId, String mapperId) {
            this.repositoryId = repositoryId;
            this.mapperId = mapperId;
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + '(' + repositoryId + ',' + mapperId + ')';
        }

    }

    /**
     * Returns the repository id and mapper id assigned.
     * <p>
     * This is used in remote stateless mode to be able to identify to which mapper an incoming connection is targeted,
     * and from which repository instance.
     *
     * @return the repository and mapper identification
     */
    Identification getIdentification();

    // used for reflection
    String GET_IDENTIFICATION = "getIdentification";

    void close();

    // used for reflection
    String CLOSE = "close";

    // TODO
    int getTableSize(String tableName);

    /*
     * ========== Methods returning non-Rows ==========
     */

    /*
     * ----- Root -----
     */

    /**
     * Gets the root id for a given repository, if registered.
     *
     * @param repositoryId the repository id
     * @return the root id, or null if not found
     */
    Serializable getRootId(String repositoryId);

    /**
     * Records the newly generated root id for a given repository.
     *
     * @param repositoryId the repository id, usually 0
     * @param id the root id
     */
    void setRootId(Serializable repositoryId, Serializable id);

    /*
     * ----- Query -----
     */

    /**
     * Makes a NXQL query to the database.
     *
     * @param query the query
     * @param queryType the query type
     * @param queryFilter the query filter
     * @param countTotal if {@code true}, count the total size without limit/offset
     * @return the list of matching document ids
     */
    PartialList<Serializable> query(String query, String queryType, QueryFilter queryFilter, boolean countTotal);

    /**
     * Makes a NXQL query to the database.
     *
     * @param query the query
     * @param queryType the query type
     * @param queryFilter the query filter
     * @param countUpTo if {@code -1}, count the total size without offset/limit.<br>
     *            If {@code 0}, don't count the total size.<br>
     *            If {@code n}, count the total number if there are less than n documents otherwise set the size to
     *            {@code -1}.
     * @return the list of matching document ids
     * @since 5.6
     */
    PartialList<Serializable> query(String query, String queryType, QueryFilter queryFilter, long countUpTo);

    /**
     * Makes a query to the database and returns an iterable (which must be closed when done).
     *
     * @param query the query
     * @param queryType the query type
     * @param queryFilter the query filter
     * @param distinctDocuments if {@code true} then a maximum of one row per document will be returned
     * @param params optional query-type-dependent parameters
     * @return an iterable, which <b>must</b> be closed when done
     */
    // queryFilter used for principals and permissions
    IterableQueryResult queryAndFetch(String query, String queryType, QueryFilter queryFilter,
            boolean distinctDocuments, Object... params);

    /**
     * Makes a query to the database.
     *
     * @param query the query
     * @param queryType the query type
     * @param queryFilter the query filter
     * @param distinctDocuments if {@code true} then a maximum of one row per document will be returned
     * @param countUpTo if {@code -1}, also count the total size without offset/limit.<br>
     *            If {@code 0}, don't count the total size.<br>
     *            If {@code n}, count the total number if there are less than n documents otherwise set the size to
     *            {@code -1}.
     * @param params optional query-type-dependent parameters
     * @return a projection
     * @since 7.10-HF-25, 8.10-HF06, 9.2
     */
    PartialList<Map<String,Serializable>> queryProjection(String query, String queryType, QueryFilter queryFilter, boolean distinctDocuments,
            long countUpTo, Object... params);

    /**
     * Gets the ids for all the ancestors of the given row ids.
     *
     * @param ids the ids
     * @return the set of ancestor ids
     */
    Set<Serializable> getAncestorsIds(Collection<Serializable> ids);

    /*
     * ----- ACLs -----
     */

    void updateReadAcls();

    void rebuildReadAcls();

    /*
     * ----- Clustering -----
     */

    int getClusterNodeIdType();

    /**
     * Informs the cluster that this node exists.
     */
    void createClusterNode(Serializable nodeId);

    /**
     * Removes this node from the cluster.
     */
    void removeClusterNode(Serializable nodeId);

    /**
     * Inserts the invalidation rows for the other cluster nodes.
     */
    void insertClusterInvalidations(Serializable nodeId, VCSInvalidations invalidations);

    /**
     * Gets the invalidations from other cluster nodes.
     */
    VCSInvalidations getClusterInvalidations(Serializable nodeId);

    /**
     * Marks the blobs in use by passing them to the provided callback (taking the blob key and the repository name).
     *
     * @since 2021.8
     */
    void markReferencedBlobs(BiConsumer<String, String> markerCallback);

    /**
     * Cleans up (hard-delete) any rows that have been soft-deleted in the database.
     *
     * @param max the maximum number of rows to delete at a time
     * @param beforeTime the maximum deletion time of the rows to delete
     * @return the number of rows deleted
     */
    int cleanupDeletedRows(int max, Calendar beforeTime);

}
