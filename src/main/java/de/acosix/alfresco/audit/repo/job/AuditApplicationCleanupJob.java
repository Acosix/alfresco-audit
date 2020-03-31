/*
 * Copyright 2017 - 2020 Acosix GmbH
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

import java.time.LocalDateTime;
import java.time.Period;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.alfresco.repo.lock.LockAcquisitionException;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.repo.transaction.RetryingTransactionHelper;
import org.alfresco.service.cmr.audit.AuditService;
import org.alfresco.service.namespace.QName;
import org.alfresco.service.transaction.TransactionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.acosix.alfresco.audit.repo.AuditModuleConstants;
import de.acosix.alfresco.utility.repo.job.GenericJob;
import de.acosix.alfresco.utility.repo.job.JobUtilities;

/**
 * Instances of this job cleanup data from an audit application that is older than a configured cut-off period.
 *
 * @author Axel Faust, <a href="http://acosix.de">Acosix GmbH</a>
 */
public class AuditApplicationCleanupJob implements GenericJob
{

    private static final Logger LOGGER = LoggerFactory.getLogger(AuditApplicationCleanupJob.class);

    private static final QName LOCK_QNAME = QName.createQName(AuditModuleConstants.SERVICE_NAMESPACE,
            AuditApplicationCleanupJob.class.getSimpleName());

    /**
     *
     * {@inheritDoc}
     */
    @Override
    public void execute(final Object context)
    {
        try
        {
            final String auditApplicationName = JobUtilities.getJobDataValue(context, "auditApplicationName", String.class);
            LOGGER.debug("Running cleanup of outdated data in audit application {}", auditApplicationName);
            AuthenticationUtil.runAsSystem(() -> {
                JobUtilities.runWithJobLock(context, LOCK_QNAME, (lockReleaseCheck) -> {
                    final TransactionService transactionService = JobUtilities.getJobDataValue(context, "transactionService",
                            TransactionService.class);
                    final RetryingTransactionHelper retryingTransactionHelper = transactionService.getRetryingTransactionHelper();
                    retryingTransactionHelper.doInTransaction(() -> {
                        this.cleanupAuditData(auditApplicationName, context);
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
                LOGGER.warn("Cleanup of audit data failed", e);
            }
        }
        catch (final Exception e)
        {
            LOGGER.error("Cleanup of audit data failed", e);
        }
    }

    protected void cleanupAuditData(final String auditApplicationName, final Object context)
    {
        final AuditService auditService = JobUtilities.getJobDataValue(context, "auditService", AuditService.class);

        final String cutOffPeriodStr = JobUtilities.getJobDataValue(context, "cutOffPeriod", String.class);
        final String timezoneStr = JobUtilities.getJobDataValue(context, "timezone", String.class, false);

        final Period cutOffPeriod = Period.parse(cutOffPeriodStr);
        final ZoneId zone = ZoneId.of(timezoneStr != null ? timezoneStr : "Z");
        final ZonedDateTime now = LocalDateTime.now(ZoneId.of("Z")).atZone(zone);
        final ZonedDateTime cutOffDate = now.minus(cutOffPeriod);
        final long epochSecond = cutOffDate.toEpochSecond();

        LOGGER.debug("Clearing all audit entries of application {} until {}", auditApplicationName, cutOffDate);
        auditService.clearAudit(auditApplicationName, null, Long.valueOf(epochSecond));
    }
}
