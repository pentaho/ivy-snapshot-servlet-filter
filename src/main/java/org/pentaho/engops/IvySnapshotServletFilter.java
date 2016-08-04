package org.pentaho.engops;

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
 * 
 * 
 * 
 * @author smaring@pentaho.com
 *
 */
@Named
@Singleton
public class IvySnapshotServletFilter implements Filter {

  private static Logger logger = LoggerFactory.getLogger(IvySnapshotServletFilter.class);
  private static String context;
      
  @Override
  public void init( FilterConfig filterConfig ) throws ServletException {
    context = filterConfig.getServletContext().getContextPath();
  }
  
  @Override
  public void doFilter( ServletRequest request, ServletResponse response, FilterChain chain ) throws IOException,
    ServletException {
    if ( request instanceof HttpServletRequest ) {
      HttpServletRequest httpRequest = (HttpServletRequest) request;
      String requestURL = httpRequest.getRequestURL().toString();
      if ( requestURL.contains( "-SNAPSHOT-" ) || requestURL.contains( "-SNAPSHOT." ) ) {
        logger.info( "    REQUESTED: " + httpRequest.getRequestURL().toString() );
        String latestSnapshotFilePath = getLatestSnapshotFilePath( requestURL );
        logger.info( "FORWARDING TO: " + latestSnapshotFilePath);
        
        /*
         * ensure you add <dispatcher>FORWARD</dispatcher> to the Nexus web.xml GuiceFilter or this won't work ...
         */
        request.getRequestDispatcher( latestSnapshotFilePath ).forward( httpRequest, (HttpServletResponse) response );
        return;
      }
    }
    chain.doFilter( request, response );
  }

  @Override
  public void destroy() {}
  
  
  
  private String getLatestSnapshotFilePath ( String requestURL ) {
    String protocol = requestURL.substring( 0, requestURL.indexOf( ":" ) );
    logger.debug( "protocol: " + protocol );
    String serverPort = requestURL.substring( requestURL.indexOf( "://" ) + 3, requestURL.indexOf( "/", requestURL.indexOf( "://" ) +3 ) );
    logger.debug( "serverPort: " + serverPort );
    logger.debug( "context: " + context );
    String path = requestURL.substring( (protocol + "://" + serverPort + context ).length(), requestURL.lastIndexOf( "/" ) + 1 );
    logger.debug( "path:    " + path );
    String fileName = requestURL.substring( requestURL.lastIndexOf( "/" ) + 1 );
    logger.debug( "fileName: " + fileName );

    String metadataXml = this.getMavenMetadataXml( protocol, serverPort, context, path );
    logger.debug( "metadata: " + metadataXml );

    Document metadataDomDocument = this.getMetadataDomDocument( metadataXml );
    MavenGAV mavenGAV = this.getMavenGAV( metadataDomDocument, fileName );
    logger.debug( "groupId: " + mavenGAV.getGroupId() );
    logger.debug( "artifactId: " + mavenGAV.getArtifactId() );
    logger.debug( "version: " + mavenGAV.getVersion() );
    logger.debug( "classifier: " + mavenGAV.getClassifier() );
    logger.debug( "extension: " + mavenGAV.getExtension() );
    
    String latestSnapshotVersion = this.getLatestSnapshotVersion( metadataDomDocument, mavenGAV );
    String latestSnapshotFilePath = path + mavenGAV.getArtifactId() + "-" + latestSnapshotVersion + "." + mavenGAV.getExtension();
    if ( mavenGAV.getClassifier().length() > 0 ) {
      latestSnapshotFilePath = path + mavenGAV.getArtifactId() + "-" + latestSnapshotVersion + "-" + mavenGAV.getClassifier() + "." + mavenGAV.getExtension();
    }

    return latestSnapshotFilePath;
  }
  
  
  
  private String getMavenMetadataXml( String protocol, String serverPort, String context, String path) {
    
    String mavenMetadataURL = protocol + "://" + serverPort + context + path + "maven-metadata.xml";
    logger.info( "getting metadata file: " + mavenMetadataURL );
    
    String metadataXml = "";
    CloseableHttpClient httpClient = HttpClients.createDefault();
    HttpGet httpGet = new HttpGet(mavenMetadataURL);
    CloseableHttpResponse httpResponse = null;
    try {
      httpResponse = httpClient.execute(httpGet);
      HttpEntity entity = httpResponse.getEntity();
      if (entity != null) {
         metadataXml = EntityUtils.toString(entity);
      } else {
        logger.warn( "error loading " + mavenMetadataURL );
      }
    } catch (Exception e) {
      logger.warn( "error loading " + mavenMetadataURL, e );
    } finally {
      try {
        httpResponse.close();
      } catch ( IOException e ) {
        logger.warn( "can't close connection", e );
      }
    }
    return metadataXml;
  }
  
  
  
  private Document getMetadataDomDocument( String metadataXml ) {
    Document metadataDocument = null;
    try {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      DocumentBuilder builder = factory.newDocumentBuilder();
      InputSource metadataSource = new InputSource(new StringReader(metadataXml));
      metadataDocument = builder.parse(metadataSource);
    } catch (Exception e) {
      logger.error("error while parsing XML", e);
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
      classifier = fileName.substring( fileName.indexOf( "-", endOfVersionInFileName ) + 1, fileName.indexOf( ".", endOfVersionInFileName + 1 ) );
    }
    // from the next character after the first occurance of "." after the version to the end
    String extension = fileName.substring( fileName.indexOf( ".", endOfVersionInFileName ) + 1 );
    
    MavenGAV mavenGAV = new MavenGAV( groupId, artifactId, version, classifier, extension );
    return mavenGAV;
  }
  
  
  
  private String getLatestSnapshotVersion( Document metadataDomDocument, MavenGAV mavenGAV ) {
    
    String snapshotVersion = "";
        
    boolean hasSnapshotVersionElements = false;
    NodeList snapshotVersionNodeList = metadataDomDocument.getElementsByTagName( "snapshotVersion" );
    /***** iterate all the snapshotVersion nodes looking for the matching extension and optional classifier *****/
    for ( int indexA=0 ; indexA < snapshotVersionNodeList.getLength() ; indexA++ ) {
      Node snapshotVersionNode = snapshotVersionNodeList.item( indexA );
      if (snapshotVersionNode.getNodeType() == Node.ELEMENT_NODE) {
        Element snapshotVersionElement = (Element) snapshotVersionNode;
        hasSnapshotVersionElements = true;
        NodeList extensionNodeList = snapshotVersionElement.getElementsByTagName( "extension" );
        String extension = "";
        for ( int indexB=0 ; indexB < extensionNodeList.getLength() ; indexB++ ) {
          Node extensionNode = extensionNodeList.item( indexB );
          if (extensionNode.getNodeType() == Node.ELEMENT_NODE) {
            Element extensionElement = (Element) extensionNode;
            extension = extensionElement.getFirstChild().getNodeValue().trim();
            break;
          }
        }
        logger.debug( "examining node with extension " + extension );
        if ( mavenGAV.getExtension().startsWith(extension) ) {  // we use startsWith to match requests for hashes (.md5, .sha, etc)
          logger.debug( extension + " matches artifact extension of " + mavenGAV.getExtension() );
          
          String value = "";
          /***** get the value of the snapshotVersion for this node ******/
          NodeList valueNodeList = snapshotVersionElement.getElementsByTagName( "value" );
          for ( int indexC=0 ; indexC < valueNodeList.getLength() ; indexC++ ) {
            Node valueNode = valueNodeList.item( indexC );
            if ( valueNode.getNodeType() == Node.ELEMENT_NODE ) {
              Element valueElement = (Element) valueNode;
              value = valueElement.getFirstChild().getNodeValue().trim();
              logger.debug( "value of this node is " + value );
            }
            break;
          }
          
          String classifier = "";
          /***** let's see if this node has a classifier ******/
          NodeList classifierNodeList = snapshotVersionElement.getElementsByTagName("classifier");
          for (int indexD = 0; indexD < classifierNodeList.getLength(); indexD++) {
            Node classifierNode = classifierNodeList.item(indexD);
            if (classifierNode.getNodeType() == Node.ELEMENT_NODE) {
              Element classifierElement = (Element) classifierNode;
              classifier = classifierElement.getFirstChild().getNodeValue().trim();
              logger.debug( "classifer " + classifier + " found" );
            }
            break;
          }
          
          if (mavenGAV.getClassifier().length() > 0) {
            /***** requested artifact does have a classifier *****/
            boolean classifierFound = false;
            
            if ( mavenGAV.getClassifier().equals(classifier) ) {
              logger.debug( classifier + " matches artifact classifier of " + mavenGAV.getClassifier() );
              snapshotVersion = value;
              classifierFound = true;
              break;
            }
            
            if (!classifierFound) {
              logger.debug( "classifier " + mavenGAV.getClassifier() + " not found on this node, continuing node search .." );
              continue;
            }

          } else {
            /***** requested artifact does not have a classifier *****/
            if (classifier.length() > 0) {
              logger.debug ("this node has a classifier and we don't want that, continuing node search ...");
              continue;
            } else {
              logger.debug ( "found the right node without a classifier" );
              snapshotVersion = value;
              break;
            }
          }

        }
      
      }
      
    }
    
    if (!hasSnapshotVersionElements) {
      // there may be only one type of artifact here, so let's try using the versioning/snapshot element
      
      NodeList snapshotNodeList = metadataDomDocument.getElementsByTagName( "snapshot" );
      for ( int indexA=0 ; indexA < snapshotNodeList.getLength() ; indexA++ ) {
        Node snapshotNode = snapshotNodeList.item( indexA );
        if (snapshotNode.getNodeType() == Node.ELEMENT_NODE) {
          Element snapshotElement = (Element) snapshotNode;
          
          String timestamp = "";
          NodeList timestampNodeList = snapshotElement.getElementsByTagName( "timestamp" );
          for ( int indexB=0 ; indexB < timestampNodeList.getLength() ; indexB++ ) {
            Node timestampNode = timestampNodeList.item( indexB );
            if (timestampNode.getNodeType() == Node.ELEMENT_NODE){
              Element timestampElement = (Element) timestampNode;
              timestamp = timestampElement.getFirstChild().getNodeValue().trim();
            }
          }
          
          String buildNumber = "";
          NodeList buildNumberNodeList = snapshotElement.getElementsByTagName( "buildNumber" );
          for ( int indexB=0 ; indexB < buildNumberNodeList.getLength() ; indexB++ ) {
            Node buildNumberNode = buildNumberNodeList.item( indexB );
            if (buildNumberNode.getNodeType() == Node.ELEMENT_NODE){
              Element buildNumberElement = (Element) buildNumberNode;
              buildNumber = buildNumberElement.getFirstChild().getNodeValue().trim();
            }
          }
      
          snapshotVersion = mavenGAV.getVersion().substring( 0, mavenGAV.getVersion().indexOf( "-SNAPSHOT" ) ) + "-" + timestamp + "-" + buildNumber;
          break;
        }
      }
      
    }
    
    return snapshotVersion;
  }

}
