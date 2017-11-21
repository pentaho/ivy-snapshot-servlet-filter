package org.pentaho.engops.nexus.plugin.ivysnapshot;

import java.io.IOException;
import java.io.StringReader;

import javax.inject.Named;
import javax.inject.Singleton;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;

import org.apache.http.impl.client.CloseableHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletResponse;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;


/**
 * @author smaring@pentaho.com
 */
@Named
@Singleton
public class IvySnapshotServletFilter implements Filter {

  private static Logger logger = LoggerFactory.getLogger( IvySnapshotServletFilter.class );
  private static String context;

  @Override
  public void init( FilterConfig filterConfig ) throws ServletException {
    setContext( filterConfig.getServletContext().getContextPath() );
  }

  @Override
  public void doFilter( ServletRequest request, ServletResponse response, FilterChain chain ) throws IOException,
    ServletException {
    if ( request instanceof HttpServletRequest ) {
      HttpServletRequest httpRequest = (HttpServletRequest) request;
      String requestURL = httpRequest.getRequestURL().toString();
      if ( requestURL.contains( "-SNAPSHOT-" ) || requestURL.contains( "-SNAPSHOT." ) ) {
        logger.info( "    REQUESTED: {} from {}", httpRequest.getRequestURL(), httpRequest.getRemoteAddr() );
        try {
          String latestSnapshotFilePath = getLatestSnapshotFilePath( requestURL );
          logger.info( "FORWARDING TO: {}", latestSnapshotFilePath );

          /*
           * ensure you add <dispatcher>FORWARD</dispatcher> to the Nexus web.xml GuiceFilter or this won't work ...
           */
          request.getRequestDispatcher( latestSnapshotFilePath ).forward( httpRequest, (HttpServletResponse) response );
          return;
        } catch ( SnapshotNotFoundException | IOException e ) {
          logger.warn( e.getMessage() );
        }
      }
    }
    chain.doFilter( request, response );
  }

  @Override
  public void destroy() {
    logger.debug( "servlet life cycle finished" );
  }

  private String getLatestSnapshotFilePath( String requestURL ) throws SnapshotNotFoundException {
    String protocol = requestURL.substring( 0, requestURL.indexOf( ':' ) );
    logger.debug( "protocol: {}", protocol );
    String serverPort = requestURL
      .substring( requestURL.indexOf( "://" ) + 3, requestURL.indexOf( '/', requestURL.indexOf( "://" ) + 3 ) );
    logger.debug( "serverPort: {}", serverPort );
    logger.debug( "context: {}", getContext() );
    String path =
      requestURL
        .substring( ( protocol + "://" + serverPort + getContext() ).length(), requestURL.lastIndexOf( '/' ) + 1 );
    String topGroupIdLevel = path.substring( 1, path.substring( 1, path.length() - 1 ).lastIndexOf( '/' ) + 1 );
    if ( topGroupIdLevel.contains( "." ) ) {
      logger.debug( "requested path contains an Ivy group: {}", topGroupIdLevel );
      StringBuilder stringBuilder = new StringBuilder( path );
      topGroupIdLevel = topGroupIdLevel.replace( ".", "/" );
      stringBuilder.replace( 1, path.substring( 1, path.length() - 1 ).lastIndexOf( '/' ) + 1, topGroupIdLevel );
      path = stringBuilder.toString();
    }

    logger.debug( "path:    {}", path );
    String fileName = requestURL.substring( requestURL.lastIndexOf( '/' ) + 1 );
    logger.debug( "fileName: {}", fileName );

    String metadataXml = "";
    try {
      metadataXml = this.getMavenMetadataXml( protocol, serverPort, path );
    } catch ( MavenMetadataNotFoundException | IOException e ) {
      logger.warn( e.getMessage() );
      throw new SnapshotNotFoundException( e.getMessage(), e );
    }
    logger.debug( "metadata: {}", metadataXml );

    Document metadataDomDocument;
    try {
      metadataDomDocument = this.getMetadataDomDocument( metadataXml );
    } catch ( MetadataNotParsableException e ) {
      logger.warn( e.getMessage() );
      throw new SnapshotNotFoundException( e.getMessage(), e );
    }
    MavenGAV mavenGAV = this.getMavenGAV( metadataDomDocument, fileName );
    logger.debug( "groupId: " + mavenGAV.getGroupId() );
    logger.debug( "artifactId: " + mavenGAV.getArtifactId() );
    logger.debug( "version: " + mavenGAV.getVersion() );
    logger.debug( "classifier: " + mavenGAV.getClassifier() );
    logger.debug( "extension: " + mavenGAV.getExtension() );

    String latestSnapshotVersion = this.getLatestSnapshotVersion( metadataDomDocument, mavenGAV );

    if ( latestSnapshotVersion.length() == 0 ) {
      throw new SnapshotNotFoundException(
        "latest SNAPSHOT version of " + mavenGAV.getGroupId() + ":" + mavenGAV.getArtifactId() + ":" + mavenGAV
          .getClassifier() + ":" + mavenGAV.getExtension() + " not found" );
    }

    String latestSnapshotFilePath =
      path + mavenGAV.getArtifactId() + "-" + latestSnapshotVersion + "." + mavenGAV.getExtension();
    if ( mavenGAV.getClassifier().length() > 0 ) {
      latestSnapshotFilePath =
        path + mavenGAV.getArtifactId() + "-" + latestSnapshotVersion + "-" + mavenGAV.getClassifier() + "." + mavenGAV
          .getExtension();
    }

    return latestSnapshotFilePath;
  }

  private static CloseableHttpClient getHttpClient() {
    return HttpClients.createDefault();
  }

  private String getMavenMetadataXml( String protocol, String serverPort, String path )
    throws MavenMetadataNotFoundException, IOException {

    String mavenMetadataURL = protocol + "://" + serverPort + getContext() + path + "maven-metadata.xml";
    logger.debug( "getting metadata file: {}", mavenMetadataURL );

    String metadataXml = "";
    CloseableHttpClient httpClient = getHttpClient();
    try {
      HttpGet httpGet = new HttpGet( mavenMetadataURL );
      CloseableHttpResponse httpResponse = httpClient.execute( httpGet );
      try {
        if ( httpResponse.getStatusLine().getStatusCode() != 200 ) {
          throw new MavenMetadataNotFoundException( httpResponse.getStatusLine().getReasonPhrase() );
        }
        HttpEntity entity = httpResponse.getEntity();
        if ( entity != null ) {
          metadataXml = EntityUtils.toString( entity );
        } else {
          logger.warn( "error loading {}", mavenMetadataURL );
        }
      } finally {
        httpResponse.close();
      }
    } finally {
      httpClient.close();
    }
    return metadataXml;
  }


  private Document getMetadataDomDocument( String metadataXml ) throws MetadataNotParsableException {
    Document metadataDocument;
    try {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      DocumentBuilder builder = factory.newDocumentBuilder();
      InputSource metadataSource = new InputSource( new StringReader( metadataXml ) );
      metadataDocument = builder.parse( metadataSource );
    } catch ( Exception e ) {
      throw new MetadataNotParsableException( "error while parsing XML", e );
    }
    return metadataDocument;
  }


  private MavenGAV getMavenGAV( Document metadataDomDocument, String fileName ) {

    NodeList groupIdNodeList = metadataDomDocument.getElementsByTagName( "groupId" );
    Element groupIdElement = (Element) groupIdNodeList.item( 0 );
    String groupId = groupIdElement.getFirstChild().getNodeValue().trim();

    NodeList artifactIdNodeList = metadataDomDocument.getElementsByTagName( "artifactId" );
    Element artifactIdElement = (Element) artifactIdNodeList.item( 0 );
    String artifactId = artifactIdElement.getFirstChild().getNodeValue().trim();

    NodeList versionNodeList = metadataDomDocument.getElementsByTagName( "version" );
    Element versionElement = (Element) versionNodeList.item( 0 );
    String version = versionElement.getFirstChild().getNodeValue().trim();

    int endOfVersionInFileName = fileName.indexOf( version ) + version.length();
    String classifier = "";
    if ( fileName.substring( endOfVersionInFileName ).contains( "-" ) ) { // if there seems to be a classifier
      // from the next character after "-" following the version to the first occurance of "." after the "-"
      classifier = fileName.substring( fileName.indexOf( '-', endOfVersionInFileName ) + 1,
        fileName.indexOf( '.', endOfVersionInFileName + 1 ) );
    }
    // from the next character after the first occurance of "." after the version to the end
    String extension = fileName.substring( fileName.indexOf( '.', endOfVersionInFileName ) + 1 );

    return new MavenGAV( groupId, artifactId, version, classifier, extension );
  }

  private String getLatestSnapshotVersion( Document metadataDomDocument, MavenGAV mavenGAV ) {

    String snapshotVersion = "";

    NodeList snapshotVersionNodeList = metadataDomDocument.getElementsByTagName( "snapshotVersion" );
    if ( snapshotVersionNodeList.getLength() > 0 ) {
      /***** iterate all the snapshotVersion nodes looking for the matching extension and optional classifier *****/
      for ( int indexA = 0; indexA < snapshotVersionNodeList.getLength(); indexA++ ) {
        Node snapshotVersionNode = snapshotVersionNodeList.item( indexA );
        if ( snapshotVersionNode.getNodeType() == Node.ELEMENT_NODE ) {
          Element snapshotVersionElement = (Element) snapshotVersionNode;
          String extension = getNodeValue( snapshotVersionElement, "extension" );
          logger.debug( "examining node with extension {}", extension );
          // we use startsWith to match requests for hashes (.md5, .sha, etc)
          if ( mavenGAV.getExtension().startsWith( extension ) ) {
            logger.debug( "{} matches artifact extension of {}", extension, mavenGAV.getExtension() );
            /***** get the value of the snapshotVersion for this node ******/
            String value = getNodeValue( snapshotVersionElement, "value" );
            /***** let's see if this node has a classifier ******/
            String classifier = getNodeValue( snapshotVersionElement, "classifier" );

            if ( classifier.equals( mavenGAV.getClassifier() ) ) {
              logger.debug( "[{}] matches artifact classifier of [{}]", classifier, mavenGAV.getClassifier() );
              snapshotVersion = value;
              break;
            } else {
              logger.debug(
                "classifier [" + mavenGAV.getClassifier() + "] not found on this node, continuing node search .." );
            }
          }
        }
      }

    } else {
      // there may be only one type of artifact here, so let's try using the versioning/snapshot element
      NodeList snapshotNodeList = metadataDomDocument.getElementsByTagName( "snapshot" );
      for ( int indexA = 0; indexA < snapshotNodeList.getLength(); indexA++ ) {
        Node snapshotNode = snapshotNodeList.item( indexA );
        if ( snapshotNode.getNodeType() == Node.ELEMENT_NODE ) {
          Element snapshotElement = (Element) snapshotNode;
          String timestamp = getNodeValue( snapshotElement, "timestamp" );
          String buildNumber = getNodeValue( snapshotElement, "buildNumber" );
          snapshotVersion = mavenGAV.getVersion().substring( 0, mavenGAV.getVersion().indexOf( "-SNAPSHOT" ) )
            + "-" + timestamp + "-" + buildNumber;
          break;
        }
      }
    }
    return snapshotVersion;
  }

  private String getNodeValue( Element snapshotVersionElement, String tagName ) {
    String value = "";
    NodeList nodeList = snapshotVersionElement.getElementsByTagName( tagName );
    if ( nodeList != null && nodeList.getLength() > 0 ) {
      Node extensionNode = nodeList.item( 0 );
      if ( extensionNode != null && extensionNode.getNodeType() == Node.ELEMENT_NODE ) {
        Element extensionElement = (Element) extensionNode;
        value = extensionElement.getFirstChild().getNodeValue().trim();
        logger.debug( "{} {} found", tagName, value );
      }
    }
    return value;
  }

  private static void setContext( String context ) {
    IvySnapshotServletFilter.context = context;
  }

  private static String getContext() {
    return IvySnapshotServletFilter.context;
  }

}
