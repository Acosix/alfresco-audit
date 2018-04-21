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
package de.acosix.alfresco.audit.repo.dao;

import java.util.List;

import org.alfresco.repo.audit.AuditComponent;
import org.alfresco.service.cmr.attributes.AttributeService;

/**
 * This data access object interface deals with resolutions and checks related
 * to the cleanup of the alf_prop_* tables which are used for
 * {@link AuditComponent} and {@link AttributeService}. In contrast to the
 * {@link PropertyTablesCleanupDAO} of Alfresco, this DAO supports more granular
 * operations without running a massive all-or-nothing job. This may allow
 * cleanup jobs to use parallel execution to speed up overall cleanup while the
 * individual operation may be less efficient.
 *
 * @author Axel Faust, <a href="http://acosix.de">Acosix GmbH</a>
 *
 */
public interface PropertyTablesCleanupDAO
{

    /**
     * Retrieves the highest alf_prop_root ID currently in the database.
     *
     * @return the highest alf_prop_root ID
     */
    Long getMaxPropertyRootId();

    /**
     * Retrieves the highest alf_prop_value ID currently in the database.
     *
     * @return the highest alf_prop_value ID
     */
    Long getMaxPropertyValueId();

    /**
     * Retrieves the highest alf_prop_*_value ID currently in the database.
     *
     * @param valueTableType
     *            the type of the value table for which to lookup the highest ID
     * @return the highest alf_prop_*_value ID
     */
    Long getMaxPropertyValueInstanceId(PropertyValueTableType valueTableType);

    /**
     * Retrieves a specific subset of existing alf_prop_root IDs higher than the
     * provided offset.
     *
     * @param maxItems
     *            the number of IDs to retrieve
     * @param fromIdExclusive
     *            the ID to use as the offset (results must be higher than this)
     * @return the retrieved IDs
     */
    List<Long> listPropertyRootIds(int maxItems, Long fromIdExclusive);

    /**
     * Retrieves a specific subset of existing alf_prop_value IDs higher than
     * the provided offset.
     *
     * @param maxItems
     *            the number of IDs to retrieve
     * @param fromIdExclusive
     *            the ID to use as the offset (results must be higher than this)
     * @return the retrieved IDs
     */
    List<Long> listPropertyValueIds(int maxItems, Long fromIdExclusive);

    /**
     * Retrieves a specific subset of existing alf_prop_*_value IDs higher than
     * the provided offset.
     *
     * @param valueTableType
     *            the type of the value table for which to lookup existing IDs
     * @param maxItems
     *            the number of IDs to retrieve
     * @param fromIdExclusive
     *            the ID to use as the offset (results must be higher than this)
     * @return the retrieved IDs
     */
    List<Long> listPropertyValueInstanceIds(PropertyValueTableType valueTableType, int maxItems, Long fromIdExclusive);

    /**
     * List all alf_prop_root IDs that are still being referenced from any
     * alf_audit_* or alf_prop_* table.
     *
     * @param fromIdInclusive
     *            the first ID to include in checks
     * @param toIdInclusive
     *            the last ID to include in checks
     * @return the list of used IDs
     */
    List<Long> listUsedPropertyRootIds(Long fromIdInclusive, Long toIdInclusive);

    /**
     * List all alf_prop_value IDs that are still being referenced from any
     * alf_audit_* or alf_prop_* table.
     *
     * @param fromIdInclusive
     *            the first ID to include in checks
     * @param toIdInclusive
     *            the last ID to include in checks
     * @return the list of used IDs
     */
    List<Long> listUsedPropertyValueIds(Long fromIdInclusive, Long toIdInclusive);

    /**
     * List all alf_prop_*_value IDs that are still being referenced from the
     * alf_prop_value table via its long_value column.
     *
     * @param valueTableType
     *            the type of the value table for which to lookup used reference
     *            IDs
     * @param fromIdInclusive
     *            the first ID to include in checks
     * @param toIdInclusive
     *            the last ID to include in checks
     * @return the list of used instance IDs
     */
    List<Long> listUsedPropertyValueInstanceIds(PropertyValueTableType valueTableType, Long fromIdInclusive,
            Long toIdInclusive);

    /**
     * Deletes a set of alf_prop_root entries that have been determined to be
     * unused.
     *
     * @param ids
     *            the list of IDs for which to delete entries
     */
    void deletePropertyRoots(List<Long> ids);

    /**
     * Deletes a set of alf_prop_value entries that have been determined to be
     * unused.
     *
     * @param ids
     *            the list of IDs for which to delete entries
     */
    void deletePropertyValues(List<Long> ids);

    /**
     * Deletes a set of alf_prop_*_value entries that have been determined to be
     * unused.
     *
     * @param valueTableType
     *            type of entries to delete.
     * @param ids
     *            the list of IDs for which to delete entries
     */
    void deletePropertyValueInstances(PropertyValueTableType valueTableType, List<Long> ids);

    /**
     * Defines the types of alf_prop_*_value tables supported by this DAO.
     *
     * @author Axel Faust
     *
     */
    public static enum PropertyValueTableType
    {
        /** alf_prop_double_value */
        DOUBLE,
        /** alf_prop_serializable_value */
        SERIALIZABLE,
        /** alf_prop_string_value */
        STRING;
    }
}
