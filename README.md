This is a Sonatype Nexus 2.x plugin meant to allow Ivy to resolve SNAPSHOT(s) from remote SNAPSHOT repositories using the Ivy
UrlResolver, a requirement of ivy.xml processing.

The goal of this servlet filter is to overcome a feature lacking in Ivy resolution.  Specifically, only the IbiblioResolver in
Ivy properly understands Maven SNAPSHOT(s) in remote repositories.  Therefore, requests handled by the UrlResolver, like the
ivy.xml and the dependencies and transitives resolved therein, will not properly retrieve the most recent timestamped SNAPSHOT from a
remote Maven repo.  It will look only for the SNAPSHOT explicitly by name, without consideration for the maven-metadata.xml.  This will
resolve SNAPSHOT(s) installed locally, but SNAPSHOT(s) deployed into maven repositories are all timestamped.  This
filter will examine requests with SNAPSHOT in the filename and attempt to dispatch them to the proper file.  This should
not affect Maven resolves as they are smart enough to ask for the maven-metadata.xml first and request the proper
timestamped file.

This project creates a plugin bundle zip that must be unpacked in the Nexus 2.x directory nexus/WEB-INF/plugin-repository

Also, because the operation of this filter relies upon dispatcher forwarding, the NexusGuiceFilter mapped in /nexus/WEB-INF/web.xml
needs to be able to process FORWARD(s).  Therefore, it is necessary to add a FORWARD dispatcher to the filter-mapping like so:

<pre>
  &lt;filter-mapping&gt;
    &lt;filter-name&gt;guiceFilter&lt;/filter-name&gt;
    &lt;url-pattern&gt;/*&lt;/url-pattern&gt;
    &lt;dispatcher&gt;REQUEST&lt;/dispatcher&gt;
    &lt;dispatcher&gt;FORWARD&lt;/dispatcher&gt;
    &lt;dispatcher&gt;ERROR&lt;/dispatcher&gt;
  &lt;/filter-mapping&gt;
</pre>

The default INFO level debugging should generate log output like:

<pre>
INFO  ...  IvySnapshotServletFilter - REQUESTED: http:// ... /report-designer-ext-toc-6.1-SNAPSHOT.ivy.xml
INFO  ...  IvySnapshotServletFilter - FORWARDING TO: ...     /report-designer-ext-toc-6.1-20160803.134911-964.ivy.xml
</pre>

For DEBUG oriented logging, a logger of "org.pentaho.engops" can be added via the logging manager in the gui and set to the
DEBUG level.