package org.pentaho.engops.nexus.plugin.ivysnapshot;

import java.io.IOException;
import java.io.OutputStream;
import java.io.StringReader;
import java.util.Enumeration;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.http.HttpStatus;
import org.apache.http.impl.client.CloseableHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.http.Header;
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
 * @author steve.maring -at- hitachivantara.com
 */

public class IvySnapshotServletFilter extends HttpServlet {

  private static final long serialVersionUID = 1L;

  private static Logger logger = LoggerFactory.getLogger( IvySnapshotServletFilter.class );
  
  public static String proxiedURL;
  public static String redirectURL;
  
  
  static {
    proxiedURL = System.getProperty( "proxiedURL" );
    redirectURL = System.getProperty( "redirectURL" );
    // required property
    if (proxiedURL == null) {
      System.out.println("specify the proxied repo to pull artifacts from as (e.g.) -DproxiedURL=http://localhost:8081");
      System.exit( 1 );
    }
    // required property
    if (redirectURL == null) {
      System.out.println("specify the URL to redirect the client to as (e.g.) -DredirectURL=http://nexus.pentaho.org");
      System.exit( 1 );
    }
    
    logger.debug( "proxiedURL: " + proxiedURL );
    logger.debug( "redirectURL: " + redirectURL );
  }
  
  


  @Override
  public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    
    if ( request instanceof HttpServletRequest ) {
      HttpServletRequest httpRequest = (HttpServletRequest) request;
      String requestURL = httpRequest.getRequestURL().toString();
      String requestFileName = requestURL.substring( requestURL.lastIndexOf( '/' ) + 1 );
      String requestPath = this.getRequestPath(requestURL);
      if ( requestURL.contains( "-SNAPSHOT-" ) || requestURL.contains( "-SNAPSHOT." ) ) {
        logger.info( "REQUESTED: {} from {}", httpRequest.getRequestURL(), httpRequest.getRemoteAddr() );
        try {
          String latestSnapshotFilePath = getLatestSnapshotFilePath( requestPath, requestFileName );
          logger.info( "   PROXYING {}", latestSnapshotFilePath );

          String proxyURL = proxiedURL + latestSnapshotFilePath;
          logger.debug( "returning {}", proxyURL );
          this.proxyDownload( proxyURL, httpRequest, response );
          
        } catch ( SnapshotNotFoundException | IOException e ) {
          logger.warn( e.getMessage() );
        }
      } else {
        logger.debug( "redirecting to {}", redirectURL + requestPath + requestFileName );
        response.sendRedirect( redirectURL + requestPath + requestFileName );
      }
    }
    
  }
  
  
  private String getRequestPath( String requestURL ) {
    String requestProtocol = requestURL.substring( 0, requestURL.indexOf( ':' ) );
    String requestServerPort = requestURL
      .substring( requestURL.indexOf( "://" ) + 3, requestURL.indexOf( '/', requestURL.indexOf( "://" ) + 3 ) );
    String requestPath =
      requestURL.substring( ( requestProtocol + "://" + requestServerPort ).length(), requestURL.lastIndexOf( '/' ) + 1 );
    /***** nexus3 seems to handle the dotted groupId in a single folder thing OK, this was creating problems with it activated ******/
    /*
    String topGroupIdLevel = requestPath.substring( 1, 
        requestPath.substring( 1, requestPath.length() - 1 ).lastIndexOf( '/' ) + 1 );
    if ( topGroupIdLevel.contains( "." ) ) {
      logger.info( "requested path contains an Ivy group: {}", topGroupIdLevel );
      StringBuilder stringBuilder = new StringBuilder( requestPath );
      topGroupIdLevel = topGroupIdLevel.replace( ".", "/" );
      stringBuilder.replace( 1, 
          requestPath.substring( 1, requestPath.length() - 1 ).lastIndexOf( '/' ) + 1, topGroupIdLevel );
      requestPath = stringBuilder.toString();
    }
    */
    return requestPath;
  }

  
  
  private void proxyDownload(String proxyURL, HttpServletRequest request, HttpServletResponse response ) throws IOException {
    OutputStream out = response.getOutputStream();
    CloseableHttpClient httpClient = HttpClients.createDefault();
    HttpGet httpGet = new HttpGet(proxyURL);
    CloseableHttpResponse httpResponse = null;
    try {
      
      Enumeration<String> headerNames = request.getHeaderNames();
      while ( headerNames.hasMoreElements() ) {
        String headerName = headerNames.nextElement();
        if ( !(headerName.equals("If-Match") || headerName.equals("If-Modified-Since") ) ) {
          String headerValue = request.getHeader( headerName );
          httpGet.setHeader( headerName, headerValue );
        }
      }

      httpResponse = httpClient.execute(httpGet);
      
      if ( httpResponse.getStatusLine().getStatusCode() != HttpStatus.SC_OK ) {
        String message = httpResponse.getStatusLine().getStatusCode() + " : " + httpResponse.getStatusLine().getReasonPhrase() + " downloading " + request.getRequestURL().toString();
        logger.warn( message );
      }
      
      HttpEntity entity = httpResponse.getEntity();
      
      for (Header header : httpResponse.getAllHeaders()) {
        if ( header.getName().equals( "Set-Cookie" ) ) {
          String cookieValue = header.getValue();
          if ( cookieValue.contains( "Path" ) ) {
            String existingPath = cookieValue.substring( cookieValue.indexOf( "Path=" ) + 5, cookieValue.indexOf( ";", cookieValue.indexOf( "Path=" ) + 6 ) );
            cookieValue = cookieValue.replace( existingPath, "/" );
          }
          response.setHeader( "Set-Cookie", cookieValue );
        } else {
          response.setHeader( header.getName(), header.getValue() );
        }
      }
      
      response.setStatus( httpResponse.getStatusLine().getStatusCode() );
      
      if (entity != null) {
         entity.writeTo( out );
      } else {
        logger.warn( "error loading " + proxyURL );
      }
    } catch (Exception e) {
      logger.warn( "error loading " + proxyURL, e );
    } finally {
      try {
        httpResponse.close();
      } catch ( IOException e ) {
        logger.warn( "can't close connection", e );
      }
    }
    out.close();
  }
  
  

  private String getLatestSnapshotFilePath( String requestPath, String requestFileName ) throws SnapshotNotFoundException {

    String metadataXml = "";
    try {
      metadataXml = this.getMavenMetadataXml(requestPath);
    } catch ( MavenMetadataNotFoundException | IOException e ) {
      throw new SnapshotNotFoundException( e.getMessage(), e );
    }
    logger.debug( "metadata: {}", metadataXml );

    Document metadataDomDocument;
    try {
      metadataDomDocument = this.getMetadataDomDocument( metadataXml );
    } catch ( MetadataNotParsableException e ) {
      throw new SnapshotNotFoundException( e.getMessage(), e );
    }
    MavenGAV mavenGAV = this.getMavenGAV( metadataDomDocument, requestFileName );
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
        requestPath + mavenGAV.getArtifactId() + "-" + latestSnapshotVersion + "." + mavenGAV.getExtension();
    if ( mavenGAV.getClassifier().length() > 0 ) {
      latestSnapshotFilePath =
          requestPath + mavenGAV.getArtifactId() + "-" + latestSnapshotVersion + "-" + mavenGAV.getClassifier() + "." + mavenGAV
          .getExtension();
    }

    return latestSnapshotFilePath;
  }

  protected CloseableHttpClient getHttpClient() {
    return HttpClients.createDefault();
  }

  private String getMavenMetadataXml( String requestPath )
    throws MavenMetadataNotFoundException, IOException {

    String mavenMetadataURL =  proxiedURL + requestPath + "maven-metadata.xml";
    logger.debug( "getting metadata file: {}", mavenMetadataURL );

    String metadataXml = "";
    CloseableHttpClient httpClient = getHttpClient();
    try {
      HttpGet httpGet = new HttpGet( mavenMetadataURL );
      CloseableHttpResponse httpResponse = httpClient.execute( httpGet );
      try {
        if ( httpResponse.getStatusLine().getStatusCode() != HttpStatus.SC_OK ) {
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


  private MavenGAV getMavenGAV( Document metadataDomDocument, String requestFileName ) {

    NodeList groupIdNodeList = metadataDomDocument.getElementsByTagName( "groupId" );
    Element groupIdElement = (Element) groupIdNodeList.item( 0 );
    String groupId = groupIdElement.getFirstChild().getNodeValue().trim();

    NodeList artifactIdNodeList = metadataDomDocument.getElementsByTagName( "artifactId" );
    Element artifactIdElement = (Element) artifactIdNodeList.item( 0 );
    String artifactId = artifactIdElement.getFirstChild().getNodeValue().trim();

    NodeList versionNodeList = metadataDomDocument.getElementsByTagName( "version" );
    Element versionElement = (Element) versionNodeList.item( 0 );
    String version = versionElement.getFirstChild().getNodeValue().trim();

    int endOfVersionInFileName = requestFileName.indexOf( version ) + version.length();
    String classifier = "";
    if ( requestFileName.substring( endOfVersionInFileName ).contains( "-" ) ) { // if there seems to be a classifier
      // from the next character after "-" following the version to the first occurance of "." after the "-"
      classifier = requestFileName.substring( requestFileName.indexOf( '-', endOfVersionInFileName ) + 1,
          requestFileName.indexOf( '.', endOfVersionInFileName + 1 ) );
    }
    // from the next character after the first occurance of "." after the version to the end
    String extension = requestFileName.substring( requestFileName.indexOf( '.', endOfVersionInFileName ) + 1 );

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

}
