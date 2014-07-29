This project creates two jars with a servlet filter.  One for Nexus and one for Artifactory.

The goal of this filter is to overcome a feature lacking in Ivy resolution.  Specifically, only the IbiblioResolver in
Ivy understands the notion of Maven SNAPSHOTs in repositories.  Therefore, requests handled by the UrlResolver, like the
ivy.xml and the dependencies resolved therein, will not properly retrieve the most recent timestamped SNAPSHOT from a
Maven repo.  It will just look only for the SNAPSHOT by name, without consideration for the maven-metadata.xml.  This
filter will examine requests with SNAPSHOT in the filename and attempt to dispatch them to the proper file.  This should
not affect Maven resolves as they are smart enough to ask for the maven-metadata.xml first and request the proper
timestamped file.

The appropriate nexus and artifactory jar should be placed in the WEB-INF/lib directory and the WEB-INF/web.xml modified
to include an entry for the following filter:

```
  <filter>
    <filter-name>ivySnapshotFilter</filter-name>
    <filter-class>org.pentaho.engops.IvySnapshotServletFilter</filter-class>
  </filter>

  <filter-mapping>
    <filter-name>ivySnapshotFilter</filter-name>
    <url-pattern>/*</url-pattern>
    <dispatcher>REQUEST</dispatcher>
  </filter-mapping>
```