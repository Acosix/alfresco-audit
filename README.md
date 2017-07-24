[![Build Status](https://travis-ci.org/Acosix/alfresco-audit.svg?branch=master)](https://travis-ci.org/Acosix/alfresco-audit)

# About

This addon aims to provide some general use functionality and utilities relating to the Auditing feature within Alfresco Content Services.

## Compatbility

This addon has been built to be compatible with Alfresco Community and Enterprise Edition 5.1, though it technically is compatible with 5.0 as well as long as Java 8 is used to run Alfresco.

## Features

### Web Scripts to query active / inactive users
The Repository-tier web scripts at URLs _/alfresco/s/acosix/api/audit/activeUsers_ and _/alfresco/s/acosix/api/audit/inactiveUsers_ provide reports about (in)active users based on audit data (currently limited to the alfresco-access audit application). These web scripts check each user that exists as a _cm:person_ node against the audit data within a particular time frame and include them in the report when they can / cannot be associated with a single audit entry in that time frame. The web scripts utilise batch execution to avoid issues with overflowing transactional caches.

Parameters:
- lookBackMode - mode/unit for defining the time frame; default value: months, allowed values: days, months, years
- lookBackAmount - number of units for defining the time frame, default value: 1 (mode=years), 3 (mode=months), 90 (mode=days)
- workerThreads - the amount of parallel execution, default value: 4
- batchSize  - the size of individual batches, default value: 20

Reports are provided in JSON or CSV format, with JSON being the default if a specific format is not reqeusted by using the URL parameter _?format=xxx_ or adding a file extension to the URL.

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
    <groupId>de.acosix.alfresco.audit</groupId>
    <artifactId>de.acosix.alfresco.audit.repo</artifactId>
    <version>1.0.0.0-SNAPSHOT</version>
    <type>jar</type>
    <classifier>installable</classifier>
</dependency>

<!-- OR -->

<!-- AMP packaging -->
<dependency>
    <groupId>de.acosix.alfresco.audit</groupId>
    <artifactId>de.acosix.alfresco.audit.repo</artifactId>
    <version>1.0.0.0-SNAPSHOT</version>
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
        <groupId>de.acosix.alfresco.audit</groupId>
        <artifactId>de.acosix.alfresco.audit.repo</artifactId>
        <version>1.0.0.0-SNAPSHOT</version>
        <type>amp</type>
    </moduleDependency>
</platformModules>
```

# Other installation methods

Using Maven to build the Alfresco WAR is the **recommended** approach to install this module. As an alternative it can be installed manually.

## alfresco-mmt.jar / apply_amps

The default Alfresco installer creates folders *amps* and *amps_share* where you can place AMP files for modules which Alfresco will install when you use the apply_amps script. Place the AMP for the *de.acosix.alfresco.audit.repo* module in the *amps* directory and execute the script to install it. You must restart Alfresco for the installation to take effect.

Alternatively you can use the alfresco-mmt.jar to install the module as [described in the documentation](http://docs.alfresco.com/5.1/concepts/dev-extensions-modules-management-tool.html).

## Manual "installation" using JAR files

Some addons and some other sources on the net suggest that you can install **any** addon by putting their JARs in a path like &lt;tomcat&gt;/lib, &lt;tomcat&gt;/shared or &lt;tomcat&gt;/shared/lib. This is **not** correct. Only the most trivial addons / extensions can be installed that way - "trivial" in this case means that these addons have no Java class-level dependencies on any component that Alfresco ships, e.g. addons that only consist of static resources, configuration files or web scripts using pure JavaScript / Freemarker.

The only way to manually install an addon using JARs that is **guaranteed** not to cause Java classpath issues is by dropping the JAR files directly into the &lt;tomcat&gt;/webapps/alfresco/WEB-INF/lib (Repository-tier) or &lt;tomcat&gt;/webapps/share/WEB-INF/lib (Share-tier) folders.
If Alfresco has been setup by using the official installer, another, **explicitly recommended** way to install the module manually would be by dropping the JAR(s) into the &lt;alfresco&gt;/modules/platform (Repository-tier) or &lt;alfresco&gt;/modules/share (Share-tier) folders.