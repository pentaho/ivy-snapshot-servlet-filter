
  <filter>
    <filter-name>ivySnapshotFilter</filter-name>
    <filter-class>org.pentaho.engops.IvySnapshotServletFilter</filter-class>
  </filter>

  <filter-mapping>
    <filter-name>ivySnapshotFilter</filter-name>
    <url-pattern>/*</url-pattern>
    <dispatcher>REQUEST</dispatcher>
  </filter-mapping>