/*
 * Copyright 2017 Acosix GmbH
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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.alfresco.repo.batch.BatchProcessWorkProvider;
import org.alfresco.repo.batch.BatchProcessor;
import org.alfresco.repo.batch.BatchProcessor.BatchProcessWorkerAdaptor;
import org.alfresco.repo.lock.LockAcquisitionException;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.repo.transaction.RetryingTransactionHelper;
import org.alfresco.service.cmr.attributes.AttributeService;
import org.alfresco.service.cmr.repository.datatype.DefaultTypeConverter;
import org.alfresco.service.namespace.QName;
import org.alfresco.service.transaction.TransactionService;
import org.alfresco.util.EqualsHelper;
import org.apache.commons.logging.LogFactory;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;

import de.acosix.alfresco.audit.repo.AuditModuleConstants;
import de.acosix.alfresco.audit.repo.dao.PropertyTablesCleanupDAO;
import de.acosix.alfresco.utility.repo.job.JobUtilities;
import de.acosix.alfresco.utility.repo.job.JobUtilities.LockReleasedCheck;

/**
 * Instances of this class perform incremental cleanup of unused alf_prop_* table entries (e.g. as a result of cleared
 * audit entries). They use batch processing to retrieve and check manageable chunks of entries in parallel instead of
 * doing one single, massive cleanup operation on the database. This class provides the base framework for specialised sub-classes to only
 * implement specific detail callbacks.
 *
 * @author Axel Faust, <a href="http://acosix.de">Acosix GmbH</a>
 *
 */
public abstract class IncrementalPropertyTableCleanupJob implements Job
{

    private static final String ATTR_LAST_ID = "lastId";

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public void execute(final JobExecutionContext context) throws JobExecutionException
    {
        final Logger logger = this.getLogger();
        final QName lockQName = QName.createQName(AuditModuleConstants.SERVICE_NAMESPACE, this.getClass().getSimpleName());
        try
        {
            logger.debug("Running incremental cleanup");
            AuthenticationUtil.runAsSystem(() -> {
                JobUtilities.runWithJobLock(context, lockQName, (lockReleaseCheck) -> {
                    final TransactionService transactionService = JobUtilities.getJobDataValue(context, "transactionService",
                            TransactionService.class);
                    final RetryingTransactionHelper retryingTransactionHelper = transactionService.getRetryingTransactionHelper();
                    retryingTransactionHelper.doInTransaction(() -> {
                        this.doCleanup(context, retryingTransactionHelper, lockReleaseCheck);
                        return null;
                    });
                });
                return null;
            });
        }
        catch (final RuntimeException e)
        {
            if (!(e instanceof LockAcquisitionException))
            {
                logger.warn("Incremental cleanup failed", e);
            }
        }
        catch (final Exception e)
        {
            logger.error("Incremental cleanup failed", e);
        }
    }

    /**
     * Retrieves the logger to be used for jobs of this class.
     *
     * @return the logger
     */
    abstract protected Logger getLogger();

    /**
     * Determines the highest ID of entries in the database
     *
     * @param cleanupDAO
     *            the cleanup DAO
     * @return the highest ID
     */
    abstract protected Long getMaxId(PropertyTablesCleanupDAO cleanupDAO);

    /**
     * Retrieves a single batch of entry IDs to process
     *
     * @param cleanupDAO
     *            the cleanup DAO
     * @param maxItems
     *            the number of IDs to retrieve
     * @param startId
     *            the offset
     * @return the batch of IDs
     */
    abstract protected List<Long> getIdBatch(PropertyTablesCleanupDAO cleanupDAO, int maxItems, Long startId);

    /**
     * Retrieves entry IDs that are actively referenced
     *
     * @param cleanupDAO
     *            the cleanup DAO
     * @param fromIdInclusive
     *            the first ID to include in checks
     * @param toIdInclusive
     *            the last ID to include in checks
     * @return the used IDs
     */
    abstract protected List<Long> getUsedEntries(PropertyTablesCleanupDAO cleanupDAO, Long fromIdInclusive, Long toIdInclusive);

    /**
     * Deletes a set of entries
     *
     * @param cleanupDAO
     *            the cleanup DAO
     * @param batchIds
     *            the batch of IDs of entries to delete
     */
    abstract protected void deleteEntries(PropertyTablesCleanupDAO cleanupDAO, List<Long> batchIds);

    protected void doCleanup(final JobExecutionContext context, final RetryingTransactionHelper retryingTransactionHelper,
            final LockReleasedCheck lockReleaseCheck)
    {
        final AttributeService attributeService = JobUtilities.getJobDataValue(context, "attributeService", AttributeService.class);
        final PropertyTablesCleanupDAO propertyTablesCleanupDAO = JobUtilities.getJobDataValue(context, "propertyTablesCleanupDAO",
                PropertyTablesCleanupDAO.class);

        final String batchSizeStr = JobUtilities.getJobDataValue(context, "batchSize", String.class);
        final String idsPerWorkItemStr = JobUtilities.getJobDataValue(context, "idsPerWorkItem", String.class);
        final String workerCountStr = JobUtilities.getJobDataValue(context, "workerCount", String.class);
        final String checkItemsLimitStr = JobUtilities.getJobDataValue(context, "checkItemsLimit", String.class);

        final int batchSize = Integer.parseInt(batchSizeStr, 10);
        final int workerCount = Integer.parseInt(workerCountStr, 10);
        final int idsPerWorkItem = Integer.parseInt(idsPerWorkItemStr, 10);
        final int checkItemsLimit = Integer.parseInt(checkItemsLimitStr, 10);

        final String simpleJobClassName = this.getClass().getSimpleName();
        final Serializable attribute = attributeService.getAttribute(AuditModuleConstants.SERVICE_NAMESPACE, simpleJobClassName,
                ATTR_LAST_ID);
        final Long lastId = DefaultTypeConverter.INSTANCE.convert(Long.class, attribute);

        this.getLogger().info(
                "Running incremental cleanup from last ID {} with batchSize {}, workerCount {}, idsPerWorkrItem {} and checkItemsLimit {}",
                lastId, batchSizeStr, workerCountStr, idsPerWorkItemStr, checkItemsLimitStr);

        final EntryIdsWorkProvider workProvider = new EntryIdsWorkProvider(this, propertyTablesCleanupDAO, workerCount, batchSize,
                idsPerWorkItem, checkItemsLimit, lastId);
        final EntryIdsBatchWorker batchWorker = new EntryIdsBatchWorker(this, propertyTablesCleanupDAO);
        final BatchProcessor<List<Long>> batchProcessor = new BatchProcessor<>(simpleJobClassName, retryingTransactionHelper, workProvider,
                workerCount, batchSize, null, LogFactory.getLog(this.getClass().getName() + ".batchProcessor"),
                Math.max(25, batchSize * workerCount * 2));
        batchProcessor.process(batchWorker, true);

        final Long newLastId = workProvider.getLastId();
        final Long maxId = workProvider.getMaxId();

        if (EqualsHelper.nullSafeEquals(lastId, newLastId) || EqualsHelper.nullSafeEquals(newLastId, maxId))
        {
            // just delete the attribute so next time we start from the beginning
            attributeService.removeAttribute(AuditModuleConstants.SERVICE_NAMESPACE, simpleJobClassName, ATTR_LAST_ID);
        }
        else
        {
            // store the last ID so next time we start from there
            attributeService.setAttribute(newLastId, AuditModuleConstants.SERVICE_NAMESPACE, simpleJobClassName, ATTR_LAST_ID);
        }

        this.getLogger().info("Completed incremental cleanup with last processed ID {} and deleted {} unused entries", newLastId,
                batchWorker.getDeletedEntries());
    }

    protected static class EntryIdsWorkProvider implements BatchProcessWorkProvider<List<Long>>
    {

        protected final IncrementalPropertyTableCleanupJob job;

        protected final PropertyTablesCleanupDAO cleanupDAO;

        protected final int parallelFactor;

        protected final int batchSize;

        protected final int idsPerWorkItem;

        protected final int checkItemsLimit;

        protected final Long startId;

        protected final Long maxId;

        protected volatile Long lastId;

        protected volatile int estimated = -1;

        protected final AtomicInteger loadedIds = new AtomicInteger(0);

        public EntryIdsWorkProvider(final IncrementalPropertyTableCleanupJob job, final PropertyTablesCleanupDAO cleanupDAO,
                final int parallelFactor, final int batchSize, final int idsPerWorkItem, final int checkItemsLimit, final Long startId)
        {
            this.job = job;
            this.cleanupDAO = cleanupDAO;
            this.parallelFactor = parallelFactor;
            this.batchSize = batchSize;
            this.idsPerWorkItem = idsPerWorkItem;
            this.checkItemsLimit = checkItemsLimit;
            this.startId = startId;

            this.maxId = this.job.getMaxId(cleanupDAO);
        }

        public Long getLastId()
        {
            return this.lastId;
        }

        public Long getMaxId()
        {
            return this.maxId;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int getTotalEstimatedWorkSize()
        {
            if (this.estimated == -1)
            {
                if (this.maxId == null)
                {
                    this.estimated = 0;
                }
                else
                {
                    long estimated = this.maxId.longValue();

                    if (this.lastId != null)
                    {
                        estimated -= this.lastId.longValue();
                        estimated += this.loadedIds.get();
                    }
                    else if (this.startId != null)
                    {
                        estimated -= this.startId.longValue();
                    }

                    estimated = this.checkItemsLimit > 0 ? Math.min(estimated, this.checkItemsLimit) : estimated;
                    if (estimated % this.idsPerWorkItem != 0)
                    {
                        estimated /= this.idsPerWorkItem;
                        estimated += 1;
                    }
                    else
                    {
                        estimated /= this.idsPerWorkItem;
                    }
                    this.estimated = (int) estimated;
                }
            }

            return this.estimated;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Collection<List<Long>> getNextWork()
        {
            final List<List<Long>> workItems = new ArrayList<>();

            if (this.maxId != null && this.loadedIds.get() < this.checkItemsLimit)
            {
                final int baseMaxItems = this.parallelFactor * this.batchSize * this.idsPerWorkItem;
                final int maxItems = this.checkItemsLimit > 0 ? Math.min(baseMaxItems, this.checkItemsLimit - this.loadedIds.get())
                        : baseMaxItems;

                final List<Long> ids = maxItems > 0
                        ? this.job.getIdBatch(this.cleanupDAO, maxItems, this.lastId != null ? this.lastId : this.startId)
                        : Collections.<Long> emptyList();

                final int loaded = ids.size();

                while (!ids.isEmpty())
                {
                    final List<Long> subList = ids.subList(0, Math.min(this.idsPerWorkItem, ids.size()));
                    workItems.add(new ArrayList<>(subList));
                    subList.clear();
                }

                if (!workItems.isEmpty())
                {
                    final List<Long> lastBatch = workItems.get(workItems.size() - 1);
                    this.lastId = lastBatch.get(lastBatch.size() - 1);
                }
                else if (this.loadedIds.get() == 0)
                {
                    this.lastId = this.job.getMaxId(this.cleanupDAO);
                }

                this.loadedIds.addAndGet(loaded);
            }

            return workItems;
        }

    }

    protected static class EntryIdsBatchWorker extends BatchProcessWorkerAdaptor<List<Long>>
    {

        protected final IncrementalPropertyTableCleanupJob job;

        protected final PropertyTablesCleanupDAO cleanupDAO;

        protected final AtomicInteger deletedEntries = new AtomicInteger();

        public EntryIdsBatchWorker(final IncrementalPropertyTableCleanupJob job, final PropertyTablesCleanupDAO cleanupDAO)
        {
            this.job = job;
            this.cleanupDAO = cleanupDAO;
        }

        public int getDeletedEntries()
        {
            return this.deletedEntries.get();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void process(final List<Long> ids)
        {
            // ensure they are sorted
            Collections.sort(ids);

            final Long fromId = ids.get(0);
            final Long toId = ids.get(ids.size() - 1);

            final List<Long> usedIds = this.job.getUsedEntries(this.cleanupDAO, fromId, toId);
            ids.removeAll(usedIds);

            this.job.getLogger().debug("Found {} unused entries between {} and {}", ids.size(), fromId, toId);
            if (!ids.isEmpty())
            {
                this.job.getLogger().trace("Unused entries: {}", ids);
                this.job.deleteEntries(this.cleanupDAO, ids);
            }
            this.deletedEntries.addAndGet(ids.size());
        }

    }
}
