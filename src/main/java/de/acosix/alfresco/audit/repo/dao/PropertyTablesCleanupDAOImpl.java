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
package de.acosix.alfresco.audit.repo.dao;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.alfresco.repo.cache.SimpleCache;
import org.alfresco.util.ParameterCheck;
import org.alfresco.util.PropertyCheck;
import org.apache.ibatis.session.RowBounds;
import org.mybatis.spring.SqlSessionTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;

/**
 * @author Axel Faust, <a href="http://acosix.de">Acosix GmbH</a>
 *
 */
public class PropertyTablesCleanupDAOImpl implements PropertyTablesCleanupDAO, InitializingBean
{

    private static final String SELECT_MAX_PROPERTY_ROOT_ID = "propertyTableCleanup.select_maxPropertyRootId";

    private static final String SELECT_MAX_PROPERTY_VALUE_ID = "propertyTableCleanup.select_maxPropertyValueId";

    private static final String SELECT_MAX_PROPERTY_DOUBLE_VALUE_ID = "propertyTableCleanup.select_maxPropertyDoubleValueId";

    private static final String SELECT_MAX_PROPERTY_STRING_VALUE_ID = "propertyTableCleanup.select_maxPropertyStringValueId";

    private static final String SELECT_MAX_PROPERTY_SERIALIZABLE_VALUE_ID = "propertyTableCleanup.select_maxPropertySerializableValueId";

    private static final String SELECT_EXISTING_PROPERTY_ROOT_IDS = "propertyTableCleanup.select_existingPropertyRootIds";

    private static final String SELECT_EXISTING_PROPERTY_VALUE_IDS = "propertyTableCleanup.select_existingPropertyValueIds";

    private static final String SELECT_EXISTING_PROPERTY_DOUBLE_VALUE_IDS = "propertyTableCleanup.select_existingPropertyDoubleValueIds";

    private static final String SELECT_EXISTING_PROPERTY_STRING_VALUE_IDS = "propertyTableCleanup.select_existingPropertyStringValueIds";

    private static final String SELECT_EXISTING_PROPERTY_SERIALIZABLE_VALUE_IDS = "propertyTableCleanup.select_existingPropertySerializableValueIds";

    private static final String SELECT_USED_AUDIT_VALUES = "propertyTableCleanup.select_usedAuditValues";

    private static final String SELECT_USED_AUDIT_USERS = "propertyTableCleanup.select_usedAuditUsers";

    private static final String SELECT_USED_AUDIT_APP_NAMES = "propertyTableCleanup.select_usedAuditAppNames";

    private static final String SELECT_USED_AUDIT_APP_DISABLED_PATHS = "propertyTableCleanup.select_usedAuditAppDisabledPaths";

    private static final String SELECT_USED_PROP_LINK_KEYS = "propertyTableCleanup.select_usedPropLinkKeys";

    private static final String SELECT_USED_PROP_LINK_VALUES = "propertyTableCleanup.select_usedPropLinkValues";

    private static final String SELECT_USED_UNIQUE_CONTEXT_PROPS = "propertyTableCleanup.select_usedUniqueContextProps";

    private static final String SELECT_USED_UNIQUE_CONTEXT_VALUES_1 = "propertyTableCleanup.select_usedUniqueContextValues1";

    private static final String SELECT_USED_UNIQUE_CONTEXT_VALUES_2 = "propertyTableCleanup.select_usedUniqueContextValues2";

    private static final String SELECT_USED_UNIQUE_CONTEXT_VALUES_3 = "propertyTableCleanup.select_usedUniqueContextValues3";

    private static final String SELECT_USED_PROPERTY_DOUBLE_VALUE_IDS = "propertyTableCleanup.select_usedPropertyDoubleValueIds";

    private static final String SELECT_USED_PROPERTY_STRING_VALUE_IDS = "propertyTableCleanup.select_usedPropertyStringValueIds";

    private static final String SELECT_USED_PROPERTY_SERIALIZABLE_VALUE_IDS = "propertyTableCleanup.select_usedPropertySerializableValueIds";

    private static final String DELETE_UNUSED_PROPERTY_ROOTS = "propertyTableCleanup.delete_unusedPropertyRoots";

    private static final String DELETE_UNUSED_PROPERTY_VALUES = "propertyTableCleanup.delete_unusedPropertyValues";

    private static final String DELETE_UNUSED_PROPERTY_DOUBLE_VALUES = "propertyTableCleanup.delete_unusedPropertyDoubleValues";

    private static final String DELETE_UNUSED_PROPERTY_STRING_VALUES = "propertyTableCleanup.delete_unusedPropertyStringValues";

    private static final String DELETE_UNUSED_PROPERTY_SERIALIZABLE_VALUES = "propertyTableCleanup.delete_unusedPropertySerializableValues";

    private static final Logger LOGGER = LoggerFactory.getLogger(PropertyTablesCleanupDAOImpl.class);

    protected SqlSessionTemplate sqlSessionTemplate;

    protected SimpleCache<Serializable, Object> propertyRootCache;

    protected SimpleCache<Serializable, Object> propertyValueCache;

    protected SimpleCache<Serializable, Object> propertySerializableCache;

    protected SimpleCache<Serializable, Object> propertyDoubleCache;

    protected SimpleCache<Serializable, Object> propertyStringCache;

    /**
     * {@inheritDoc}
     */
    @Override
    public void afterPropertiesSet()
    {
        PropertyCheck.mandatory(this, "sqlSessionTemplate", this.sqlSessionTemplate);
    }

    /**
     * @param sqlSessionTemplate
     *            The SQL session template to set
     */
    public void setSqlSessionTemplate(final SqlSessionTemplate sqlSessionTemplate)
    {
        this.sqlSessionTemplate = sqlSessionTemplate;
    }

    /**
     * @param propertyRootCache
     *            the cache for alf_prop_root entries to set
     */
    public void setPropertyRootCache(final SimpleCache<Serializable, Object> propertyRootCache)
    {
        this.propertyRootCache = propertyRootCache;
    }

    /**
     * @param propertyValueCache
     *            the the cache for alf_prop_value entries to set
     */
    public void setPropertyValueCache(final SimpleCache<Serializable, Object> propertyValueCache)
    {
        this.propertyValueCache = propertyValueCache;
    }

    /**
     * @param propertySerializableCache
     *            the cache for alf_prop_serializable_value entries to set
     */
    public void setPropertySerializableCache(final SimpleCache<Serializable, Object> propertySerializableCache)
    {
        this.propertySerializableCache = propertySerializableCache;
    }

    /**
     * @param propertyDoubleCache
     *            the cache for alf_prop_double_value entries to set
     */
    public void setPropertyDoubleCache(final SimpleCache<Serializable, Object> propertyDoubleCache)
    {
        this.propertyDoubleCache = propertyDoubleCache;
    }

    /**
     * @param propertyStringCache
     *            the cache for alf_prop_string_value entries to set
     */
    public void setPropertyStringCache(final SimpleCache<Serializable, Object> propertyStringCache)
    {
        this.propertyStringCache = propertyStringCache;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Long getMaxPropertyRootId()
    {
        final Long maxPropertyRootId = this.sqlSessionTemplate.selectOne(SELECT_MAX_PROPERTY_ROOT_ID);
        return maxPropertyRootId;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Long getMaxPropertyValueId()
    {
        final Long maxPropertyValueId = this.sqlSessionTemplate.selectOne(SELECT_MAX_PROPERTY_VALUE_ID);
        LOGGER.debug("Selected max ID {} for alf_prop_value table", maxPropertyValueId);
        return maxPropertyValueId;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Long getMaxPropertyValueInstanceId(final PropertyValueTableType valueTableType)
    {
        ParameterCheck.mandatory("valueTableType", valueTableType);
        final String query;
        switch (valueTableType)
        {
            case DOUBLE:
                query = SELECT_MAX_PROPERTY_DOUBLE_VALUE_ID;
                break;
            case SERIALIZABLE:
                query = SELECT_MAX_PROPERTY_SERIALIZABLE_VALUE_ID;
                break;
            case STRING:
                query = SELECT_MAX_PROPERTY_STRING_VALUE_ID;
                break;
            default:
                throw new IllegalArgumentException("Unsupported value table type: " + valueTableType);
        }

        final Long maxPropertyValueInstanceId = this.sqlSessionTemplate.selectOne(query);
        LOGGER.debug("Selected max ID {} for alf_prop_*_value type {}", maxPropertyValueInstanceId, valueTableType);
        return maxPropertyValueInstanceId;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Long> listPropertyRootIds(final int maxItems, final Long fromIdExclusive)
    {
        final RowBounds rowBounds = new RowBounds(0, maxItems);
        final CleanupQueryBoundsParam queryBoundsParam = new CleanupQueryBoundsParam();
        queryBoundsParam.setFromId(fromIdExclusive);
        queryBoundsParam.setMaxItems(Integer.valueOf(maxItems));

        final List<Long> ids = this.sqlSessionTemplate.selectList(SELECT_EXISTING_PROPERTY_ROOT_IDS, queryBoundsParam, rowBounds);
        LOGGER.debug("Selected {} alf_prop_root IDs starting with exclusive from ID {} and {} max items: {}", ids.size(), fromIdExclusive,
                maxItems);
        LOGGER.trace("Retrieved alf_prop_root entries : {}", ids);
        return ids;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Long> listPropertyValueIds(final int maxItems, final Long fromIdExclusive)
    {
        final RowBounds rowBounds = new RowBounds(0, maxItems);
        final CleanupQueryBoundsParam queryBoundsParam = new CleanupQueryBoundsParam();
        queryBoundsParam.setFromId(fromIdExclusive);
        queryBoundsParam.setMaxItems(Integer.valueOf(maxItems));

        final List<Long> ids = this.sqlSessionTemplate.selectList(SELECT_EXISTING_PROPERTY_VALUE_IDS, queryBoundsParam, rowBounds);
        LOGGER.debug("Selected {} alf_prop_value IDs starting with exclusive from ID {} and {} max items: {}", ids.size(), fromIdExclusive,
                maxItems);
        LOGGER.trace("Retrieved alf_prop_value entries:", ids);
        return ids;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Long> listPropertyValueInstanceIds(final PropertyValueTableType valueTableType, final int maxItems,
            final Long fromIdExclusive)
    {
        ParameterCheck.mandatory("valueTableType", valueTableType);

        final RowBounds rowBounds = new RowBounds(0, maxItems);
        final CleanupQueryBoundsParam queryBoundsParam = new CleanupQueryBoundsParam();
        queryBoundsParam.setFromId(fromIdExclusive);
        queryBoundsParam.setMaxItems(Integer.valueOf(maxItems));

        final String query;
        switch (valueTableType)
        {
            case DOUBLE:
                query = SELECT_EXISTING_PROPERTY_DOUBLE_VALUE_IDS;
                break;
            case SERIALIZABLE:
                query = SELECT_EXISTING_PROPERTY_SERIALIZABLE_VALUE_IDS;
                break;
            case STRING:
                query = SELECT_EXISTING_PROPERTY_STRING_VALUE_IDS;
                break;
            default:
                throw new IllegalArgumentException("Unsupported value table type: " + valueTableType);
        }

        final List<Long> ids = this.sqlSessionTemplate.selectList(query, queryBoundsParam, rowBounds);
        LOGGER.debug("Selected {} alf_prop_*_value IDs for value type {} starting with exclusive from ID {} and {} max items", ids.size(),
                valueTableType, fromIdExclusive, maxItems);
        LOGGER.trace("Retrieved alf_prop_*_value entries:", ids);
        return ids;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Long> listUsedPropertyRootIds(final Long fromIdInclusive, final Long toIdInclusive)
    {
        LOGGER.debug("Querying actively referenced alf_prop_root entries from {} to {}", fromIdInclusive, toIdInclusive);
        final List<Long> usedIds = this.collectUsedIds(fromIdInclusive, toIdInclusive, SELECT_USED_AUDIT_APP_DISABLED_PATHS,
                SELECT_USED_AUDIT_VALUES, SELECT_USED_UNIQUE_CONTEXT_PROPS);
        LOGGER.debug("Found {} referenced alf_prop_root entries", usedIds.size());
        LOGGER.trace("Referenced alf_prop_root entries: {}", usedIds);
        return usedIds;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Long> listUsedPropertyValueIds(final Long fromIdInclusive, final Long toIdInclusive)
    {
        LOGGER.debug("Querying actively referenced alf_prop_value entries from {} to {}", fromIdInclusive, toIdInclusive);
        final List<Long> usedIds = this.collectUsedIds(fromIdInclusive, toIdInclusive, SELECT_USED_AUDIT_APP_NAMES, SELECT_USED_AUDIT_USERS,
                SELECT_USED_PROP_LINK_KEYS, SELECT_USED_PROP_LINK_VALUES, SELECT_USED_UNIQUE_CONTEXT_VALUES_1,
                SELECT_USED_UNIQUE_CONTEXT_VALUES_2, SELECT_USED_UNIQUE_CONTEXT_VALUES_3);
        LOGGER.debug("Found {} referenced alf_prop_value entries", usedIds.size());
        LOGGER.trace("Referenced alf_prop_value entries: {}", usedIds);
        return usedIds;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Long> listUsedPropertyValueInstanceIds(final PropertyValueTableType valueTableType, final Long fromIdInclusive,
            final Long toIdInclusive)
    {
        ParameterCheck.mandatory("valueTableType", valueTableType);

        LOGGER.debug("Querying actively referenced alf_prop_*_value entries from {} to {} for {}", fromIdInclusive, toIdInclusive,
                valueTableType);

        final String query;
        switch (valueTableType)
        {
            case DOUBLE:
                query = SELECT_USED_PROPERTY_DOUBLE_VALUE_IDS;
                break;
            case SERIALIZABLE:
                query = SELECT_USED_PROPERTY_SERIALIZABLE_VALUE_IDS;
                break;
            case STRING:
                query = SELECT_USED_PROPERTY_STRING_VALUE_IDS;
                break;
            default:
                throw new IllegalArgumentException("Unsupported value table type: " + valueTableType);
        }

        final List<Long> usedIds = this.collectUsedIds(fromIdInclusive, toIdInclusive, query);
        LOGGER.debug("Found {} referenced alf_prop_*_value entries for {}", usedIds.size(), valueTableType);
        LOGGER.trace("Referenced alf_prop_*_value entries for {}: {}", valueTableType, usedIds);
        return usedIds;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deletePropertyRoots(final List<Long> ids)
    {
        ParameterCheck.mandatoryCollection("ids", ids);

        LOGGER.debug("Deleting {} alf_prop_root entries", ids.size());
        LOGGER.trace("Deleting alf_prop_root entries for IDs {}", ids);

        this.sqlSessionTemplate.delete(DELETE_UNUSED_PROPERTY_ROOTS, ids);

        if (this.propertyRootCache != null)
        {
            // due to complex key->entry + valueKey->entry mappings, it is easier + more efficient to just clear the cache
            // (blame Alfresco's cache design)
            this.propertyRootCache.clear();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deletePropertyValues(final List<Long> ids)
    {
        ParameterCheck.mandatoryCollection("ids", ids);

        LOGGER.debug("Deleting {} alf_prop_value entries", ids.size());
        LOGGER.trace("Deleting alf_prop_value entries: {}", ids);

        this.sqlSessionTemplate.delete(DELETE_UNUSED_PROPERTY_VALUES, ids);

        if (this.propertyValueCache != null)
        {
            // due to complex key->entry + valueKey->entry mappings, it is easier + more efficient to just clear the cache
            // there is also no way to limit the clearing to a particular cache region in case of cache re-use (default)
            // (blame Alfresco's cache design)
            this.propertyValueCache.clear();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deletePropertyValueInstances(final PropertyValueTableType valueTableType, final List<Long> ids)
    {
        ParameterCheck.mandatory("valueTableType", valueTableType);
        ParameterCheck.mandatoryCollection("ids", ids);

        LOGGER.debug("Deleting {} alf_prop_*_value entries of type {}", ids.size(), valueTableType);
        LOGGER.trace("Deleting alf_prop_*_value entries: {}", ids);

        final String query;
        final SimpleCache<Serializable, Object> cache;
        switch (valueTableType)
        {
            case DOUBLE:
                query = DELETE_UNUSED_PROPERTY_DOUBLE_VALUES;
                cache = this.propertyDoubleCache;
                break;
            case SERIALIZABLE:
                query = DELETE_UNUSED_PROPERTY_SERIALIZABLE_VALUES;
                cache = this.propertySerializableCache;
                break;
            case STRING:
                query = DELETE_UNUSED_PROPERTY_STRING_VALUES;
                cache = this.propertyStringCache;
                break;
            default:
                throw new IllegalArgumentException("Unsupported value table type: " + valueTableType);
        }

        this.sqlSessionTemplate.delete(query, ids);
        if (cache != null)
        {
            // due to complex key->entry + valueKey->entry mappings, it is easier + more efficient to just clear the cache
            // there is also no way to limit the clearing to a particular cache region in case of cache re-use (default)
            // (blame Alfresco's cache design)
            cache.clear();
        }
    }

    /**
     * Searches for all used entry IDs from foreign key a specific alf_prop_* tables
     *
     * @param fromIdInclusive
     *            the inclusive from ID of the range to check
     * @param toIdInclusive
     *            the inclusive to ID of the range to check
     * @param queries
     *            the queries to use to retrieve foreign keys in other table
     * @return a list of used IDs
     */
    protected List<Long> collectUsedIds(final Long fromIdInclusive, final Long toIdInclusive, final String... queries)
    {
        final Set<Long> usedIds = new HashSet<>();

        final CleanupQueryBoundsParam queryBoundsParam = new CleanupQueryBoundsParam();
        queryBoundsParam.setFromId(fromIdInclusive);
        queryBoundsParam.setToId(toIdInclusive);

        for (final String query : queries)
        {
            final List<Long> ids = this.sqlSessionTemplate.selectList(query, queryBoundsParam);
            usedIds.addAll(ids);
        }

        final List<Long> result = new ArrayList<>(usedIds);
        Collections.sort(result);
        return result;
    }
}
