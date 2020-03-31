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
package de.acosix.alfresco.audit.repo;

import org.alfresco.repo.audit.model.AuditApplication;

/**
 * @author Axel Faust, <a href="http://acosix.de">Acosix GmbH</a>
 */
public interface AuditModuleConstants
{

    String SERVICE_NAMESPACE = "http://acosix.org/alfresco-audit/service";

    String AUDIT_PRODUCER_ROOT_PATH = "/acosix-audit";

    String AUDIT_ACTIVE_USER_LOGIN_APP_NAME = "acosix-audit-activeUserLogin";

    String AUDIT_ACTIVE_USER_LOGIN_ROOT_PATH = "/" + AUDIT_ACTIVE_USER_LOGIN_APP_NAME;

    String AUDIT_ACTIVE_USERS_APP_NAME = "acosix-audit-activeUsers";

    String AUDIT_ACTIVE_USERS_ROOT_PATH = "/" + AUDIT_ACTIVE_USERS_APP_NAME;

    String AUDIT_ACTIVE_USER_LOGIN_USER_KEY = AuditApplication.buildPath(AUDIT_ACTIVE_USER_LOGIN_ROOT_PATH, "userName");

    String AUDIT_ACTIVE_USERS_TIMEFRAME_START_KEY = AuditApplication.buildPath(AUDIT_ACTIVE_USERS_ROOT_PATH, "timeframeStart");

    String AUDIT_ACTIVE_USERS_TIMEFRAME_END_KEY = AuditApplication.buildPath(AUDIT_ACTIVE_USERS_ROOT_PATH, "timeframeEnd");
}
