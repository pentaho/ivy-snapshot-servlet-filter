package org.pentaho.engops;

import java.io.IOException;
import java.io.OutputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.lang.reflect.Array;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

public class IvySnapshotServletFilter extends HttpServlet {

  private static final long serialVersionUID = 1L;

  private static Logger logger = LoggerFactory.getLogger( IvySnapshotServletFilter.class );

  public static String proxiedServerContext;
  public static String contextName = "";
  public static String redirectURL = "";
  public static String urlSearchString = "";
  public static String urlReplaceString = "";
  public static boolean performReplacement = false;
  public static HashMap<String,String> replacementMap = new HashMap<>();
  
  static {
    proxiedServerContext = System.getProperty( "proxiedURL" );
    redirectURL = System.getProperty( "redirectURL" );
    // required property
    if (proxiedServerContext == null) {
      System.out.println("specify the proxied repo to pull artifacts from as (e.g.) -DproxiedURL=http://localhost:8081");
      System.exit( 1 );
    }
    // required property
    if (redirectURL == null) {
      System.out.println("specify the URL to redirect the client to as (e.g.) -DredirectURL=http://nexus.pentaho.org");
      System.exit( 1 );
    }
    
    logger.debug( "proxiedURL: " + proxiedServerContext );
    logger.debug( "redirectURL: " + redirectURL );
    
    if ( proxiedServerContext.indexOf("/", proxiedServerContext.indexOf( "://"  ) + 3 ) > 0 ) {
      contextName = proxiedServerContext.substring( proxiedServerContext.indexOf("/", proxiedServerContext.indexOf( "://"  ) + 3 ), proxiedServerContext.length() );
      logger.debug("proxying to context name: " + contextName);
    }
    
    // optional properties
    urlSearchString = System.getProperty( "urlSearchString" );
    urlReplaceString = System.getProperty( "urlReplaceString" );
    if (urlSearchString != null && urlReplaceString != null) {
      performReplacement = true;
      String[] searchTokens = new String[1];
      String[] replaceTokens = new String[1];;
      if (urlSearchString.contains( "," )) {
        searchTokens = urlSearchString.split( "," );
      } else {
        searchTokens[0] = urlSearchString;
      }
      if (urlReplaceString.contains( "," )) {
        replaceTokens = urlReplaceString.split( "," );
      } else {
        replaceTokens[0] = urlReplaceString;
      }
      for (int i=0; i < Array.getLength( searchTokens ); i++) {
        replacementMap.put( searchTokens[i], replaceTokens[i] );
      }
      
    }
  }
  


  @Override
  public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
      
   
      String url = request.getRequestURL().toString();
      logger.debug( "requested url: " + url );
      
      String path = url.substring( url.indexOf( "/", url.indexOf( "://" ) +3 ), url.lastIndexOf( "/" ) + 1 );
      logger.debug( "path: " + path );
      
      // JavaScript server callbacks to load content will often have the target context in the request
      while ( path.startsWith( contextName  ) && contextName.length() > 2 ) {
        path = path.substring( contextName.length(), path.length() );
      }
      
      if (performReplacement) {
        Set<String> searchStrings = replacementMap.keySet();
        Iterator<String> searchStringsIterator = searchStrings.iterator();
        while (searchStringsIterator.hasNext()) {
          String searchString = searchStringsIterator.next();
          path = path.replace(searchString, replacementMap.get( searchString ) );
        }
        logger.debug( "new path: " + path );
      }
      

      String filename = url.substring(url.lastIndexOf( "/" ) + 1,url.length());
      //logger.debug( "filename: " + filename);
      
      if ( filename.contains( "SNAPSHOT" ) 
            && !filename.contains( "maven-metadata.xml") 
            && (  filename.endsWith( ".xml" )
                  || filename.endsWith( ".js" )
                  || filename.endsWith( ".jar" )
                  || filename.endsWith( ".zip" )
                  || filename.endsWith( ".gz" )
                  || filename.endsWith( ".pom" )
                  || filename.endsWith( ".sha1" )
                  || filename.endsWith( ".md5" )) ) {
        
        int lastDirSeparator = url.lastIndexOf( "/" );
        int secondToLastDirSeparator = url.substring( 0,lastDirSeparator ).lastIndexOf( "/" );
        int thirdToLastDirSeparator = url.substring( 0,secondToLastDirSeparator ).lastIndexOf( "/" );
        
        String artifactId = url.substring( thirdToLastDirSeparator + 1, secondToLastDirSeparator );
        logger.debug( "artifactId: " + artifactId);
        
        String snapshotLabel = url.substring( secondToLastDirSeparator + 1, lastDirSeparator );
        logger.debug( "snapshotLabel: " + snapshotLabel );
        
        String classifierExtension = filename.substring(artifactId.length() + 1 + snapshotLabel.length(), filename.length());
        logger.debug( "classifierExtension: " + classifierExtension );
        
        String artifactClassifier = "";
        String artifactExtension = "";
        if (classifierExtension.startsWith("-")) {
          artifactClassifier = classifierExtension.substring( 1, classifierExtension.indexOf( "." ) );
          artifactExtension = classifierExtension.substring( classifierExtension.indexOf( "." ) + 1, classifierExtension.length() );
        } else {
          artifactExtension = classifierExtension.substring(1,classifierExtension.length());
        }
        logger.debug( "classifer: " + artifactClassifier);
        
        String hashType = "";
        if (artifactExtension.endsWith(".sha1")) {
          artifactExtension = artifactExtension.substring( 0, artifactExtension.indexOf( ".sha1" ) );
          hashType = ".sha1";
        } else if (artifactExtension.endsWith(".md5")) {
          artifactExtension = artifactExtension.substring( 0, artifactExtension.indexOf( ".md5" ) );
          hashType = ".md5";
        }
        logger.debug( "extension: " + artifactExtension );
        logger.debug( "hash type: " + hashType );
        

        String mavenMetadataURL = proxiedServerContext + path + "maven-metadata.xml";
        logger.debug( "metadata file: " + mavenMetadataURL );
        
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
            logger.warn( "error loading " + url );
          }
        } catch (Exception e) {
          logger.warn( "error loading " + url, e );
        } finally {
          try {
            httpResponse.close();
          } catch ( IOException e ) {
            logger.warn( "can't close connection", e );
          }
        }

        try {
          DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
          DocumentBuilder builder = factory.newDocumentBuilder();
          InputSource metadataSource = new InputSource(new StringReader(metadataXml));
          Document metadataDocument = builder.parse(metadataSource);
          
          if (logger.isDebugEnabled()) {
            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            StreamResult streamResult = new StreamResult(new StringWriter());
            DOMSource domSource = new DOMSource(metadataDocument);
            transformer.transform(domSource, streamResult);
            logger.debug(streamResult.getWriter().toString());
          }
          
          String snapshotVersion = snapshotLabel;
          
          boolean hasSnapshotVersionElements = false;
          NodeList snapshotVersionNodeList = metadataDocument.getElementsByTagName( "snapshotVersion" );
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
              if ( extension.equals(artifactExtension) ) {
                logger.debug( extension + " matches artifact extension of " + artifactExtension );
                
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
                
                if (artifactClassifier.length() > 0) {
                  /***** requested artifact does have a classifier *****/
                  boolean classifierFound = false;
                  
                  if ( artifactClassifier.equals(classifier) ) {
                    logger.debug( classifier + " matches artifact classifier of " + artifactClassifier );
                    snapshotVersion = value;
                    classifierFound = true;
                    break;
                  }
                  
                  if (!classifierFound) {
                    logger.debug( "classifier " + artifactClassifier + " not found on this node, continuing node search .." );
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
            
            NodeList snapshotNodeList = metadataDocument.getElementsByTagName( "snapshot" );
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
            
                snapshotVersion = snapshotLabel.substring( 0, snapshotLabel.indexOf( "-SNAPSHOT" ) ) + "-" + timestamp + "-" + buildNumber;
                break;
              }
            }
            
          }
          
          String snapshotFile = filename;
          if (artifactClassifier.length() > 0) {
            snapshotFile = artifactId + "-" + snapshotVersion + "-" + artifactClassifier + "." + artifactExtension + hashType;
          } else {
            snapshotFile = artifactId + "-" + snapshotVersion + "." + artifactExtension + hashType;
          }

          logger.info( "##################################################################" );
          logger.info( "proxying the request for:" );
          logger.info( "\t" + path + filename );
          logger.info( "from:" );
          logger.info( "\t" + proxiedServerContext + path + snapshotFile );
          logger.info( "##################################################################" );
          
          if (  filename.endsWith( ".xml" ) || filename.endsWith( ".pom" ) ) {
            response.setContentType( "application/xml" );
          } else if (  filename.endsWith( ".js" ) ) {
            response.setContentType( "application/javascript" );
          } else if (  filename.endsWith( ".jar" ) ) {
            response.setContentType( "application/java-archive" );
          } else if (  filename.endsWith( ".zip" ) ) {
            response.setContentType( "application/zip" );
          } else if (  filename.endsWith( ".sha1" ) || filename.endsWith( ".md5" ) ) {
            response.setContentType( "text/plain" );
          } else {
            response.setContentType( "application/octet-stream" );
          }
                    
          url = proxiedServerContext + path + snapshotFile;
          this.proxyDownload( url, request, response );               
                  
        } catch (Exception e) {
          logger.error("error while parsing XML", e);
          if ( redirectURL.length() > 1 ) {
            logger.info( "redirecting to " + redirectURL + path + filename );
            response.sendRedirect( redirectURL + path + filename );
          } else {
            logger.info( "proxying " + proxiedServerContext + path + filename );
            this.proxyDownload( proxiedServerContext + path + filename, request, response );
          }
          return;
        }
        
      } else {
        
        if ( redirectURL.length() > 1 ) {
          logger.info( "redirecting to " + redirectURL + path + filename );
          response.sendRedirect( redirectURL + path + filename );
        } else {
          logger.info( "proxying " + proxiedServerContext + path + filename );
          this.proxyDownload( proxiedServerContext + path + filename, request, response );
        }
        
      }

    
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
  
  
}
