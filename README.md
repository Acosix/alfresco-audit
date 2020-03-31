[![Build Status](https://travis-ci.org/Acosix/alfresco-audit.svg?branch=master)](https://travis-ci.org/Acosix/alfresco-audit)

# About

This addon aims to provide some general use functionality and utilities relating to the Auditing feature within Alfresco Content Services.

## Compatbility

This module is built to be compatible with Alfresco 5.0 and above. It may be used on either Community or Enterprise Edition.

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

# Build

This project uses a Maven build using templates from the [Acosix Alfresco Maven](https://github.com/Acosix/alfresco-maven) project and produces module AMPs, regular Java *classes* JARs, JavaDoc and source attachment JARs, as well as installable (Simple Alfresco Module) JAR artifacts for the Alfresco Content Services and Share extensions. If the installable JAR artifacts are used for installing this module, developers / users are advised to consult the 'Dependencies' section of this README.

## Maven toolchains

By inheritance from the Acosix Alfresco Maven framework, this project uses the [Maven Toolchains plugin](http://maven.apache.org/plugins/maven-toolchains-plugin/) to allow potential cross-compilation against different Java versions. This plugin is used to avoid potentially inconsistent compiler and library versions compared to when only the source/target compiler options of the Maven compiler plugin are set, which (as an example) has caused issues with some Alfresco releases in the past where Alfresco compiled for Java 7 using the Java 8 libraries.
In order to build the project it is necessary to provide a basic toolchain configuration via the user specific Maven configuration home (usually ~/.m2/). That file (toolchains.xml) only needs to list the path to a compatible JDK for the Java version required by this project. The following is a sample file defining a Java 7 and 8 development kit.

```xml
<?xml version='1.0' encoding='UTF-8'?>
<toolchains xmlns="http://maven.apache.org/TOOLCHAINS/1.1.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://maven.apache.org/TOOLCHAINS/1.1.0 http://maven.apache.org/xsd/toolchains-1.1.0.xsd">
  <toolchain>
    <type>jdk</type>
    <provides>
      <version>1.8</version>
      <vendor>oracle</vendor>
    </provides>
    <configuration>
      <jdkHome>C:\Program Files\Java\jdk1.8.0_112</jdkHome>
    </configuration>
  </toolchain>
  <toolchain>
    <type>jdk</type>
    <provides>
      <version>1.7</version>
      <vendor>oracle</vendor>
    </provides>
    <configuration>
      <jdkHome>C:\Program Files\Java\jdk1.7.0_80</jdkHome>
    </configuration>
  </toolchain>
</toolchains>
```

The master branch requires Java 8.

## Docker-based integration tests

In a default build using ```mvn clean install```, this project will build the extension for Alfresco Content Services, executing regular unit-tests without running integration tests. The integration tests of this project are based on Docker and require a Docker engine to run the necessary components (PostgreSQL database as well as Alfresco Content Services). Since a Docker engine may not be available in all environments of interested community members / collaborators, the integration tests have been made optional. A full build, including integration tests, can be run by executing

```
mvn clean install -Ddocker.tests.enabled=true
```

This project currently does not contain any integration tests, but may do so in the future.

## Dependencies

This module depends on the following projects / libraries:

- Acosix Alfresco Utility (Apache License, Version 2.0) - core extension

When the installable JAR produced by the build of this project is used for installation, the developer / user is responsible to either manually install all the required components / libraries provided by the listed projects, or use a build system to collect all relevant direct / transitive dependencies.

**Note**: The Acosix Alfresco Utility project is also built using templates from the Acosix Alfresco Maven project, and as such produces similar artifacts. Automatic resolution and collection of (transitive) dependencies using Maven / Gradle will resolve the Java *classes* JAR as a dependency, and **not** the installable (Simple Alfresco Module) variant. It is recommended to exclude Acosix Alfresco Utility from transitive resolution and instead include it directly / explicitly.

**Note**: The feature to audit user login events requires the full extension of Acosix Alfresco Utility, which adds a patch to support more than one authentication listener to Alfresco.