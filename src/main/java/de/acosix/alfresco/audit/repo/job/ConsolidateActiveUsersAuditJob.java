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
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicBoolean;

import org.alfresco.model.ContentModel;
import org.alfresco.repo.audit.AuditComponent;
import org.alfresco.repo.audit.model.AuditApplication;
import org.alfresco.repo.batch.BatchProcessor;
import org.alfresco.repo.batch.BatchProcessor.BatchProcessWorkerAdaptor;
import org.alfresco.repo.lock.LockAcquisitionException;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.repo.transaction.RetryingTransactionHelper;
import org.alfresco.service.cmr.audit.AuditQueryParameters;
import org.alfresco.service.cmr.audit.AuditService;
import org.alfresco.service.cmr.audit.AuditService.AuditQueryCallback;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.repository.datatype.DefaultTypeConverter;
import org.alfresco.service.cmr.search.SearchService;
import org.alfresco.service.cmr.security.PersonService;
import org.alfresco.service.namespace.NamespaceService;
import org.alfresco.service.namespace.QName;
import org.alfresco.service.transaction.TransactionService;
import org.alfresco.util.EqualsHelper;
import org.alfresco.util.ISO8601DateFormat;
import org.alfresco.util.Pair;
import org.apache.commons.logging.LogFactory;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.acosix.alfresco.audit.repo.AuditModuleConstants;
import de.acosix.alfresco.utility.repo.batch.PersonBatchWorkProvider;
import de.acosix.alfresco.utility.repo.job.JobUtilities;

/**
 * This job is responsible for regularly consolidating the data from the audit application {@code acosix-audit-activeUserLogin} to keep a
 * record of dates when users have connected to the Alfresco Repository, e.g. can be considered {@code activeUsers}. The size of the time
 * frame in which data will be consolidated to a single audit entry can be configured between one hour and a day, with values in between the
 * limits required to be proper divisors of 24 (hours).
 *
 * @author Axel Faust, <a href="http://acosix.de">Acosix GmbH</a>
 */
public class ConsolidateActiveUsersAuditJob implements Job
{

    private static final Logger LOGGER = LoggerFactory.getLogger(ConsolidateActiveUsersAuditJob.class);

    private static final QName LOCK_QNAME = QName.createQName(AuditModuleConstants.SERVICE_NAMESPACE,
            ConsolidateActiveUsersAuditJob.class.getSimpleName());

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public void execute(final JobExecutionContext context) throws JobExecutionException
    {
        try
        {
            LOGGER.debug("Running consolidation on active users audit data");
            AuthenticationUtil.runAsSystem(() -> {
                JobUtilities.runWithJobLock(context, LOCK_QNAME, (lockReleaseCheck) -> {
                    final TransactionService transactionService = JobUtilities.getJobDataValue(context, "transactionService",
                            TransactionService.class);
                    final RetryingTransactionHelper retryingTransactionHelper = transactionService.getRetryingTransactionHelper();
                    retryingTransactionHelper.doInTransaction(() -> {
                        this.consolidateActiveUsersAudit(context, retryingTransactionHelper);
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
                LOGGER.warn("Consolidation of active users audit data failed", e);
            }
        }
        catch (final Exception e)
        {
            LOGGER.error("Consolidation of active users audit data failed", e);
        }
    }

    protected void consolidateActiveUsersAudit(final JobExecutionContext context, final RetryingTransactionHelper retryingTransactionHelper)
    {
        final AuditService auditService = JobUtilities.getJobDataValue(context, "auditService", AuditService.class);
        final AuditComponent auditComponent = JobUtilities.getJobDataValue(context, "auditComponent", AuditComponent.class);
        final NamespaceService namespaceService = JobUtilities.getJobDataValue(context, "namespaceService", NamespaceService.class);
        final NodeService nodeService = JobUtilities.getJobDataValue(context, "nodeService", NodeService.class);
        final PersonService personService = JobUtilities.getJobDataValue(context, "personService", PersonService.class);
        final SearchService searchService = JobUtilities.getJobDataValue(context, "searchService", SearchService.class);

        final String workerThreadsParam = JobUtilities.getJobDataValue(context, "workerThreads", String.class, true);
        final String batchSizeParam = JobUtilities.getJobDataValue(context, "batchSize", String.class, true);

        final String timeframeHoursParam = JobUtilities.getJobDataValue(context, "timeframeHours", String.class, true);

        final int workerThreads = workerThreadsParam != null ? Math.min(1, Integer.parseInt(workerThreadsParam, 10)) : 4;
        final int batchSize = batchSizeParam != null ? Math.min(1, Integer.parseInt(batchSizeParam, 10)) : 10;

        final int timeframeHours = timeframeHoursParam != null ? Integer.parseInt(timeframeHoursParam, 10) : 1;
        if (timeframeHours <= 0)
        {
            throw new IllegalArgumentException("timeframeHours must be a positive integer");
        }
        if (timeframeHours > 24)
        {
            throw new IllegalArgumentException("timeframeHours cannot be greater than a day of 24 hours");
        }

        if (24 % timeframeHours != 0)
        {
            throw new IllegalArgumentException("Number of hours in a day must be divisible by timeframeHours");
        }

        final BatchProcessor<NodeRef> processor = new BatchProcessor<>(ConsolidateActiveUsersAuditJob.class.getName(),
                retryingTransactionHelper, new PersonBatchWorkProvider(namespaceService, nodeService, personService, searchService),
                workerThreads, batchSize, null, LogFactory.getLog(ConsolidateActiveUsersAuditJob.class.getName() + ".batchProcessor"),
                Math.max(25, workerThreads * batchSize * 2));

        final PersonConsolidationAuditWorker worker = new PersonConsolidationAuditWorker(nodeService, auditService, auditComponent,
                timeframeHours);
        processor.process(worker, true);
    }

    protected static class PersonConsolidationAuditWorker extends BatchProcessWorkerAdaptor<NodeRef>
    {

        private final NodeService nodeService;

        private final AuditService auditService;

        private final AuditComponent auditComponent;

        private final int timeframeHours;

        protected PersonConsolidationAuditWorker(final NodeService nodeService, final AuditService auditService,
                final AuditComponent auditComponent, final int timeframeHours)
        {
            this.nodeService = nodeService;
            this.auditService = auditService;
            this.auditComponent = auditComponent;
            this.timeframeHours = timeframeHours;
        }

        /**
         *
         * {@inheritDoc}
         */
        @Override
        public void process(final NodeRef personRef) throws Throwable
        {
            AuthenticationUtil.setRunAsUserSystem();
            final Map<QName, Serializable> personProperties = this.nodeService.getProperties(personRef);
            final String userName = DefaultTypeConverter.INSTANCE.convert(String.class, personProperties.get(ContentModel.PROP_USERNAME));
            LOGGER.debug("Processing user {} (node {})", userName, personRef);

            final Set<Pair<String, String>> timeframes = new HashSet<>();
            final List<Long> auditEntryIds = new ArrayList<>();

            this.queryUserLogins(userName, timeframes, auditEntryIds);

            LOGGER.debug("Clearing active user login entries {}", auditEntryIds);
            this.auditService.clearAudit(auditEntryIds);

            LOGGER.debug("Recoding active user time frames {}", timeframes);
            this.recordTimeframes(userName, timeframes);
        }

        protected void recordTimeframes(final String userName, final Set<Pair<String, String>> timeframes)
        {
            for (final Pair<String, String> timeframe : timeframes)
            {
                final String timeframeStart = timeframe.getFirst();
                final String timeframeEnd = timeframe.getSecond();

                final boolean exists = this.checkEntryExists(userName, timeframeStart, timeframeEnd);

                if (!exists)
                {
                    // recording should be done using the proper user name
                    AuthenticationUtil.clearCurrentSecurityContext();
                    AuthenticationUtil.setRunAsUser(userName);

                    final String rootPath = AuditApplication.buildPath(AuditModuleConstants.AUDIT_PRODUCER_ROOT_PATH,
                            ConsolidateActiveUsersAuditJob.class.getSimpleName());
                    final Map<String, Serializable> auditMap = new HashMap<>();
                    auditMap.put("userName", userName);
                    auditMap.put("timeframeStart", timeframeStart);
                    auditMap.put("timeframeEnd", timeframeEnd);

                    LOGGER.debug("Recording 'new' active user time frame {} to {}", timeframeStart, timeframeEnd);
                    this.auditComponent.recordAuditValuesWithUserFilter(rootPath, auditMap, false);

                    // reset for next iteration
                    AuthenticationUtil.clearCurrentSecurityContext();
                    AuthenticationUtil.setRunAsUserSystem();
                }
            }
        }

        private boolean checkEntryExists(final String userName, final String timeframeStart, final String timeframeEnd)
        {
            final AuditQueryParameters aqp = new AuditQueryParameters();
            aqp.setApplicationName(AuditModuleConstants.AUDIT_ACTIVE_USERS_APP_NAME);
            aqp.setForward(true);
            aqp.setUser(userName);
            aqp.addSearchKey(AuditModuleConstants.AUDIT_ACTIVE_USERS_TIMEFRAME_START_KEY, timeframeStart);

            final AtomicBoolean exists = new AtomicBoolean();
            this.auditService.auditQuery(new AuditQueryCallback()
            {

                /**
                 *
                 * {@inheritDoc}
                 */
                @Override
                public boolean valuesRequired()
                {
                    return true;
                }

                /**
                 *
                 * {@inheritDoc}
                 */
                @Override
                public boolean handleAuditEntry(final Long entryId, final String applicationName, final String user, final long time,
                        final Map<String, Serializable> values)
                {
                    final Serializable timeframeEndCandidate = values.get(AuditModuleConstants.AUDIT_ACTIVE_USERS_TIMEFRAME_END_KEY);
                    if (EqualsHelper.nullSafeEquals(timeframeEndCandidate, timeframeEnd))
                    {
                        exists.set(true);
                    }
                    return !exists.get();
                }

                /**
                 *
                 * {@inheritDoc}
                 */
                @Override
                public boolean handleAuditEntryError(final Long entryId, final String errorMsg, final Throwable error)
                {
                    return true;
                }
            }, aqp, 7); // there are only 7 possible divisors of 24

            return exists.get();
        }

        protected void queryUserLogins(final String userName, final Set<Pair<String, String>> timeframes, final List<Long> auditEntryIds)
        {
            final AuditQueryParameters aqp = new AuditQueryParameters();
            aqp.setApplicationName(AuditModuleConstants.AUDIT_ACTIVE_USER_LOGIN_APP_NAME);
            aqp.setForward(true);
            aqp.addSearchKey(AuditModuleConstants.AUDIT_ACTIVE_USER_LOGIN_USER_KEY, userName);

            final Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.ENGLISH);
            this.auditService.auditQuery(new AuditQueryCallback()
            {

                /**
                 *
                 * {@inheritDoc}
                 */
                @Override
                public boolean valuesRequired()
                {
                    return false;
                }

                /**
                 *
                 * {@inheritDoc}
                 */
                @Override
                public boolean handleAuditEntry(final Long entryId, final String applicationName, final String user, final long time,
                        final Map<String, Serializable> values)
                {
                    cal.setTimeInMillis(time);
                    final int hourOfDay = cal.get(Calendar.HOUR_OF_DAY);
                    final int startHour = hourOfDay - (hourOfDay % PersonConsolidationAuditWorker.this.timeframeHours);
                    cal.set(Calendar.HOUR_OF_DAY, startHour);
                    cal.set(Calendar.MINUTE, 0);
                    cal.set(Calendar.SECOND, 0);
                    cal.set(Calendar.MILLISECOND, 0);

                    final String timeframeStart = ISO8601DateFormat.format(cal.getTime());
                    cal.add(Calendar.HOUR_OF_DAY, PersonConsolidationAuditWorker.this.timeframeHours);
                    final String timeframeEnd = ISO8601DateFormat.format(cal.getTime());
                    timeframes.add(new Pair<>(timeframeStart, timeframeEnd));
                    auditEntryIds.add(entryId);

                    return true;
                }

                /**
                 *
                 * {@inheritDoc}
                 */
                @Override
                public boolean handleAuditEntryError(final Long entryId, final String errorMsg, final Throwable error)
                {
                    return true;
                }

            }, aqp, Integer.MAX_VALUE);
        }
    }
}