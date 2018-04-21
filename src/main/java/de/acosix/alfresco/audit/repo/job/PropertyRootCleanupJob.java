/*
 * Copyright 2017, 2018 Acosix GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.acosix.alfresco.audit.repo.job;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.acosix.alfresco.audit.repo.dao.PropertyTablesCleanupDAO;

/**
 * This job performs a cleanup of unused alf_prop_root entries (e.g. as a result of cleared audit entries). It uses
 * batch processing to retrieve and check manageable chunks of entries in parallel instead of doing one single, massive
 * cleanup operation on the database.
 *
 * @author Axel Faust, <a href="http://acosix.de">Acosix GmbH</a>
 *
 */
public class PropertyRootCleanupJob extends IncrementalPropertyTableCleanupJob
{

    private static final Logger LOGGER = LoggerFactory.getLogger(PropertyRootCleanupJob.class);

    /**
     * {@inheritDoc}
     */
    @Override
    protected Logger getLogger()
    {
        return LOGGER;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Long getMaxId(final PropertyTablesCleanupDAO cleanupDAO)
    {
        return cleanupDAO.getMaxPropertyRootId();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected List<Long> getIdBatch(final PropertyTablesCleanupDAO cleanupDAO, final int maxItems, final Long startId)
    {
        return cleanupDAO.listPropertyRootIds(maxItems, startId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected List<Long> getUsedEntries(final PropertyTablesCleanupDAO cleanupDAO, final Long fromIdInclusive, final Long toIdInclusive)
    {
        return cleanupDAO.listUsedPropertyRootIds(fromIdInclusive, toIdInclusive);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void deleteEntries(final PropertyTablesCleanupDAO cleanupDAO, final List<Long> batchIds)
    {
        cleanupDAO.deletePropertyRoots(batchIds);
    }
}
