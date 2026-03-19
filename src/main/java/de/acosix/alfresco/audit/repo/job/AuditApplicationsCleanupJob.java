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
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.alfresco.repo.lock.LockAcquisitionException;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.repo.transaction.RetryingTransactionHelper;
import org.alfresco.service.cmr.audit.AuditService;
import org.alfresco.service.namespace.QName;
import org.alfresco.service.transaction.TransactionService;
import org.quartz.JobExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.acosix.alfresco.audit.repo.AuditModuleConstants;
import de.acosix.alfresco.utility.repo.job.GenericJob;
import de.acosix.alfresco.utility.repo.job.JobUtilities;

/**
 * Instances of this job cleanup data from audit applications that
 * are older than a configured cut-off period.
 *
 * @author Piergiorgio Lucidi, <a href="https://www.ziaconsulting.com/">Zia
 *         Consulting</a>
 * @author Axel Faust, <a href="http://acosix.de">Acosix GmbH</a>
 */
public class AuditApplicationsCleanupJob implements GenericJob {

	private static final Logger LOGGER = LoggerFactory.getLogger(AuditApplicationsCleanupJob.class);

	private static final QName LOCK_QNAME = QName.createQName(AuditModuleConstants.SERVICE_NAMESPACE,
			AuditApplicationsCleanupJob.class.getSimpleName());

	private static final String ALL_APPS_PROP_VALUE = "ALL";
	private static final String AUDIT_SERVICE_ENTRY = "auditService";
	private static final String CUTOFF_ENTRY = "cutOffPeriod";
	private static final String TIMEZONE_ENTRY = "timezone";
	private static final String TARGET_APPS_ENTRY = "targetApplications";
	private static final String DEFAULT_TIMEZONE = "Z";
	private static final String ENABLED_ENTRY = "enabled";
	
	/**
	 *
	 * {@inheritDoc}
	 */
	@Override
	public void execute(final Object context) {
		try {
			AuthenticationUtil.runAsSystem(() -> {
				JobUtilities.runWithJobLock(context, LOCK_QNAME, (lockReleaseCheck) -> {
					final TransactionService transactionService = JobUtilities.getJobDataValue(context,
							"transactionService", TransactionService.class);
					final RetryingTransactionHelper retryingTransactionHelper = transactionService
							.getRetryingTransactionHelper();
					retryingTransactionHelper.doInTransaction(() -> {
						this.cleanupAuditData(context);
						return null;
					});
				});
				return null;
			});
		} catch (final RuntimeException e) {
			if (!(e instanceof LockAcquisitionException)) {
				LOGGER.warn("Cleanup of audit data failed", e);
			}
		} catch (final Exception e) {
			LOGGER.error("Cleanup of audit data failed", e);
		}
	}

	protected void cleanupAuditData(final Object context) {
		//Check the enable flag first
	    boolean enabled = Boolean.parseBoolean(JobUtilities.getJobDataValue(context, ENABLED_ENTRY, String.class));
	    if (!enabled) {
	        LOGGER.info("Audit Applications Cleanup Job is disabled via configuration. Skipping.");
	        return;
	    }
	    
		final AuditService auditService = JobUtilities.getJobDataValue(context, AUDIT_SERVICE_ENTRY, AuditService.class);
		final String cutOffPeriodStr = JobUtilities.getJobDataValue(context, CUTOFF_ENTRY, String.class);
		final String timezoneStr = JobUtilities.getJobDataValue(context, TIMEZONE_ENTRY, String.class, false);
		List<String> targetApplications = getJobList(context, TARGET_APPS_ENTRY, String.class);

		if (!targetApplications.isEmpty() && targetApplications.contains(ALL_APPS_PROP_VALUE)) {
			Set<String> auditApplications = auditService.getAuditApplications().keySet();
			targetApplications = auditApplications.stream().sorted().toList();
		}

		for (String targetApplication : targetApplications) {
			LOGGER.debug("Audit Applications Cleanup Job - Running cleanup of outdated data in audit application {}", targetApplication);

			final Period cutOffPeriod = Period.parse(cutOffPeriodStr);
			final ZoneId zone = ZoneId.of(timezoneStr != null ? timezoneStr : DEFAULT_TIMEZONE);
			final ZonedDateTime now = LocalDateTime.now(ZoneId.of(DEFAULT_TIMEZONE)).atZone(zone);
			final ZonedDateTime cutOffDate = now.minus(cutOffPeriod);
			final long epochMilli = cutOffDate.toInstant().toEpochMilli();

			LOGGER.debug("Audit Applications Cleanup Job - Clearing all audit entries of application {} until {}", targetApplication, cutOffDate);
			auditService.clearAudit(targetApplication, null, Long.valueOf(epochMilli));
		}
	}

	/**
	 * Safely retrieves a List from the JobDataMap and validates element types.
	 */
	public static <T> List<T> getJobList(Object context, String key, Class<T> elementType) {
		if (!(context instanceof JobExecutionContext jobContext)) {
			return Collections.emptyList();
		}

		Object value = jobContext.getMergedJobDataMap().get(key);

		if (value instanceof List<?> rawList) {
			return rawList.stream().filter(elementType::isInstance).map(elementType::cast).collect(Collectors.toList());
		}

		LOGGER.warn("JobData value for key '{}' is not a List. Actual type: {}", key, value.getClass().getName());
		return Collections.emptyList();
	}

}
