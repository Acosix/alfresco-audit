[![Build Status](https://travis-ci.org/Acosix/alfresco-audit.svg?branch=master)](https://travis-ci.org/Acosix/alfresco-audit)

# About

This addon aims to provide some general use functionality and utilities relating to the Auditing feature within Alfresco Content Services.

## Compatbility

This addon has been built to be compatible with Alfresco Community and Enterprise Edition 5.1, though it technically is compatible with 5.0 as well as long as Java 8 is used to run Alfresco.

## Features

### User Login auditing (aka active user audit log)
In some use cases (e.g. Alfresco Enterprise license management) it is important to know which users actually use an Alfresco system. While auditing actions or changes to nodes (default _alfresco-access_ audit application) can provide that information, it has potential gaps: it may be filtered based on user names and logins only show password-based login without supporting SSO.

This addon provides a purely authentication-focused audit application (_acosix-audit-activeUsersLogin_) that records each instance of a successful password-based authentication, as well as any authentication that properly informs [authentication listeners](http://dev.alfresco.com/resource/docs/java/org/alfresco/repo/web/auth/AuthenticationListener.html) (web script authenticators and NTLM / Kerberos SSO for WebDAV / Alfresco Office Services). Only the user name and type of web credentials (if any) are recorded.

Support for SSO and web script authenticators is dependent on custom authentication listeners that plug into pre-defined beans. This requires that the module _de.acosix.alfresco.utility.repo_ has been installed and configured to augment the default no-op listeners with a multiple listeners-aware facade (active by default, configured via _acosix-utility.web.auth.multipleAuthenticationListeners.enabled_). The listeners of this addon must also be enabled (active by default, configured via _acosix-audit..auth.listener.enabled_).

Depending on the authentication method and frequency of HTTP calls to the Repository, quite a lot of audit entries may be created in a short duration. Entries are only kept for a limited time (14 days, defined as ISO 8601 period via _acosix-audit.job.activeUserLoginCleanup.cutOffPeriod_) and cleared at specific intervals (1 AM every day, defined as CRON via _acosix-utility.job.activeUserLoginCleanup.cron_). For long-term use the audit data is regularly consolidated (5 minutes, defined as CRON via _acosix-audit.job.consolidateActiveUsersAudit.cron_) into a separate audit application (_acosix-audit-activeUsers_). This stores the user name and the start/end of a reporting time frame (1 hour, defined as number of hours (divisors of 24) via _acosix-utility.job.consolidateActiveUsersAudit.timeframeHours_) in which the user has logged into Alfresco at least once.

### Incremental cleanup of alf\_prop\_\* tables
When Auditing entries or AttributeService entries are being deleted, Alfresco does not actually delete all of the associated data. The structures of the alf\_prop\_\* tables are designed to heavily reuse individual textual or numerical data elements, much to the point that cascade deletion upon removal of audit entries or attributes is no longer possible as the same values could be referenced in other elements.

Since Alfresco 4.1.9, 4.2.2 (Enterprise) and 5.0 (Community), Alfresco includes a default job to clean up dangling data in alf\_prop\_* tables. This job is disabled via a CRON expression that is guaranteed to never run and must be re-configured to be able to run. This job is a brute-force approach to deleting dangling data - it tries to clear all entries in one single transaction. This may be inappropriate for constellations where data has accumulated over many years or the system cannot be bogged down by expensive database operations.

The incremental cleanup provided by this addon is composed of multiple jobs that iteratively check sub-sets of data entries for being actively referenced. By default they are configured to run between 9 PM and 5 AM in a staggered pattern. The following jobs are part of this feature:

- propertyRootsCleanup
- propertyValuesCleanup
- propertyStringValuesCleanup
- propertySerializableValuesCleanup
- propertyDoubleValuesCleanup

Each job can be configured via alfresco-global.properties using the key pattern _acosix-audit.&lt;jobName&gt;.&gt;setting&lt;. The following settings are supported:

- _cron_ - the CRON expression determining the time to run
- _batchSize_ - the amount of sub-sets of entries to process in a batch
- _workerCount_ - the number of parallel threads to process the job
- _idsPerWorkItem_ - the size of entry sub-sets to process as an individual work item
- _checkItemsLimit_ - the number of entries to check in one run of the job to limit the execution time / time of load on the database

### Web Scripts to query active / inactive users
The Repository-tier web scripts at URLs _/alfresco/s/acosix/api/audit/activeUsers_ and _/alfresco/s/acosix/api/audit/inactiveUsers_ provide reports about (in)active users based on audit data. These web scripts check each user that exists as a _cm:person_ node against the audit data within a particular time frame and include them in the report when they can / cannot be associated with a single audit entry in that time frame. The web scripts utilise batch execution to avoid issues with overflowing transactional caches.

Parameters:
- lookBackMode - mode/unit for defining the time frame; default value: months, allowed values: days, months, years
- lookBackAmount - number of units for defining the time frame, default value: 1 (mode=years), 3 (mode=months), 90 (mode=days)
- workerThreads - the amount of parallel execution, default value: 4
- batchSize  - the size of individual batches, default value: 20

By default the web scripts will use the _acosix-audit-activeUsers_ audit application as the source of data. This can be reconfigured to use any audit application, e.g. the default _alfresco-access_. All configuration properties share the same prefix of _acosix-audit.web.script.activeUser._. The following properties are supported:

- _auditApplicationName_ - the name of the audit application to use (default: _acosix-audit-activeUsers_)
- _userAuditPath_ - the path to the user name within the audit data to filter queries against; if empty, the user name associated with the audit entry will be used to query
- _dateFromAuditPath_ - the path to a date or ISO 8601 string value within the audit data that denotes the start of a timeframe in which the user was active; must be set together with _dateToAuditPath_ (default: _/acosix-audit-activeUsers/timeframeStart_)
- _dateToAuditPath_ - the path to a date or ISO 8601 string value within the audit data that denotes the end of a timeframe in which the user was active; must be set together with _dateFromAuditPath_ (default: _/acosix-audit-activeUsers/timeframeEnd_)
- _dateAuditPath_ - the path to a date or ISO 8601 string value within the audit data that denotes an effective date at which the user was active
- _defaultLookBackMode_ - the default lookBackMode if no parameter is provided in the web script call (default: months)
- _defaultLookBackDays_ - the amount of days to look back if lookBackMode is "days" and no parameter is provided in the web script call (default: 90)
- _defaultLookBackMonths_ - the amount of months to look back if lookBackMode is "months" and no parameter is provided in the web script call (default: 3)
- _defaultLookBackYears_ - the amount of years to look back if lookBackMode is "years" and no parameter is provided in the web script call (default: 1)
- _defaultBatchSize_ - the size of an atomic batch of users to process if no parameter is provided in the web script call (default: 10)
- _defaultWorkerThreads_ - the number of parallel worker threads to use if no parameter is provided in the web script call (default: 4)
- _defaultLoggingInterval_ - the number of processed users after which to log process information (default: 50)

If none of the date-related configuration properties are set to a valid constellation, the date of the audit entries will be used as input to the report of the web scripts.

Reports are provided in JSON or CSV format, with JSON being the default if a specific format is not reqeusted by using the URL parameter _?format=xxx_ or adding a file extension to the URL. The report of active users will include the earliest and latest date within the reporting time frame at which the user was active - this may be the abstract boundaries of "user interaction time frames" if defined and extracted from the underlying audit application. 

# Maven usage

This addon is being built using the [Acosix Alfresco Maven framework](https://github.com/Acosix/alfresco-maven) and produces both AMP and installable JAR artifacts. Depending on the setup of a project that wants to include the addon, different approaches can be used to include it in the build.

## Build

This project can be build simply by executing the standard Maven build lifecycles for package, install or deploy depending on the intent for further processing. A Java Development Kit (JDK) version 8 or higher is required for the build.

## Dependency in Alfresco SDK

The simplest option to include the addon in an All-in-One project is by declaring a dependency to the installable JAR artifact. Alternatively, the AMP package may be included which typically requires additional configuration in addition to the dependency.

### Using SNAPSHOT builds

In order to use a pre-built SNAPSHOT artifact published to the Open Source Sonatype Repository Hosting site, the artifact repository may need to be added to the POM, global settings.xml or an artifact repository proxy server. The following is the XML snippet for inclusion in a POM file.

```xml
<repositories>
    <repository>
        <id>ossrh</id>
        <url>https://oss.sonatype.org/content/repositories/snapshots</url>
        <snapshots>
            <enabled>true</enabled>
        </snapshots>
    </repository>
</repositories>
```

### Repository

```xml
<dependency>
    <groupId>de.acosix.alfresco.utility</groupId>
    <artifactId>de.acosix.alfresco.utility.common</artifactId>
    <version>1.0.2.0</version>
    <type>jar</type>
</dependency>

<dependency>
    <groupId>de.acosix.alfresco.utility</groupId>
    <artifactId>de.acosix.alfresco.utility.repo</artifactId>
    <version>1.0.2.0</version>
    <type>jar</type>
    <classifier>installable</classifier>
</dependency>

<dependency>
    <groupId>de.acosix.alfresco.audit</groupId>
    <artifactId>de.acosix.alfresco.audit.repo</artifactId>
    <version>1.0.0.0</version>
    <type>jar</type>
    <classifier>installable</classifier>
</dependency>

<!-- OR -->

<!-- AMP packaging -->
<dependency>
    <groupId>de.acosix.alfresco.utility</groupId>
    <artifactId>de.acosix.alfresco.utility.repo</artifactId>
    <version>1.0.2.0</version>
    <type>amp</type>
</dependency>

<dependency>
    <groupId>de.acosix.alfresco.audit</groupId>
    <artifactId>de.acosix.alfresco.audit.repo</artifactId>
    <version>1.0.0.0</version>
    <type>amp</type>
</dependency>

<plugin>
    <artifactId>maven-war-plugin</artifactId>
    <configuration>
        <overlays>
            <overlay />
            <overlay>
                <groupId>${alfresco.groupId}</groupId>
                <artifactId>${alfresco.repo.artifactId}</artifactId>
                <type>war</type>
                <excludes />
            </overlay>
            <!-- other AMPs -->
            <overlay>
                <groupId>de.acosix.alfresco.utility</groupId>
                <artifactId>de.acosix.alfresco.utility.repo</artifactId>
                <type>amp</type>
            </overlay>
            <overlay>
                <groupId>de.acosix.alfresco.audit</groupId>
                <artifactId>de.acosix.alfresco.audit.repo</artifactId>
                <type>amp</type>
            </overlay>
        </overlays>
    </configuration>
</plugin>
```

For Alfresco SDK 3 beta users:

```xml
<platformModules>
     <moduleDependency>
        <groupId>de.acosix.alfresco.utility</groupId>
        <artifactId>de.acosix.alfresco.utility.repo</artifactId>
        <version>1.0.2.0</version>
        <type>amp</type>
    </moduleDependency>
    <moduleDependency>
        <groupId>de.acosix.alfresco.audit</groupId>
        <artifactId>de.acosix.alfresco.audit.repo</artifactId>
        <version>1.0.0.0</version>
        <type>amp</type>
    </moduleDependency>
</platformModules>
```

# Other installation methods

Using Maven to build the Alfresco WAR is the **recommended** approach to install this module. As an alternative it can be installed manually.

## alfresco-mmt.jar / apply_amps

The default Alfresco installer creates folders *amps* and *amps_share* where you can place AMP files for modules which Alfresco will install when you use the apply_amps script. Place the AMPs for the *de.acosix.alfresco.utility.repo* and *de.acosix.alfresco.audit.repo* modules in the *amps* directory and execute the script to install it. You must restart Alfresco for the installation to take effect.

Alternatively you can use the alfresco-mmt.jar to install the module as [described in the documentation](http://docs.alfresco.com/5.1/concepts/dev-extensions-modules-management-tool.html).

## Manual "installation" using JAR files

Some addons and some other sources on the net suggest that you can install **any** addon by putting their JARs in a path like &lt;tomcat&gt;/lib, &lt;tomcat&gt;/shared or &lt;tomcat&gt;/shared/lib. This is **not** correct. Only the most trivial addons / extensions can be installed that way - "trivial" in this case means that these addons have no Java class-level dependencies on any component that Alfresco ships, e.g. addons that only consist of static resources, configuration files or web scripts using pure JavaScript / Freemarker.

The only way to manually install an addon using JARs that is **guaranteed** not to cause Java classpath issues is by dropping the JAR files directly into the &lt;tomcat&gt;/webapps/alfresco/WEB-INF/lib (Repository-tier) or &lt;tomcat&gt;/webapps/share/WEB-INF/lib (Share-tier) folders.

For this addon the following JARs need to be dropped into &lt;tomcat&gt;/webapps/alfresco/WEB-INF/lib:

 - de.acosix.alfresco.utility.common-&lt;version&gt;.jar
 - de.acosix.alfresco.utility.repo-&lt;version&gt;-installable.jar
 - de.acosix.alfresco.audit.repo-&lt;version&gt;-installable.jar

If Alfresco has been setup by using the official installer, another, **explicitly recommended** way to install the module manually would be by dropping the JAR(s) into the &lt;alfresco&gt;/modules/platform (Repository-tier) or &lt;alfresco&gt;/modules/share (Share-tier) folders.