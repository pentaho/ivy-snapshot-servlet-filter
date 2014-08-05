package org.pentaho.engops;

import java.io.IOException;
import java.io.OutputStream;
import java.io.StringReader;
import java.io.StringWriter;

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
  
  static {
    proxiedServerContext = System.getProperty( "proxiedServerContext" );
    if (proxiedServerContext == null) {
      System.out.println("specify the proxied repo as (e.g.) -DproxiedServerContext=http://nexus.pentaho.org:8080/nexus");
      System.exit( 1 );
    }
  }
  

  @Override
  public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
      
   
      String url = request.getRequestURL().toString();
      logger.debug( "requested url: " + url );
      
      String path = url.substring( url.indexOf( "/", url.indexOf( "://" ) +3 ), url.lastIndexOf( "/" ) + 1 );
      //logger.debug( "path: " + path );

      String filename = url.substring(url.lastIndexOf( "/" ) + 1,url.length());
      //logger.debug( "filename: " + filename);
      
      if ( filename.contains( "SNAPSHOT" ) 
            && !filename.contains( "maven-metadata.xml") 
            && (  filename.endsWith( ".xml" )
                  || filename.endsWith( ".js" )
                  || filename.endsWith( ".jar" )
                  || filename.endsWith( ".zip" )
                  || filename.endsWith( ".gz" )
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
          
          NodeList snapshotVersionNodeList = metadataDocument.getElementsByTagName( "snapshotVersion" );
          /***** iterate all the snapshotVersion nodes looking for the matching extension and optional classifier *****/
          for ( int indexA=0 ; indexA < snapshotVersionNodeList.getLength() ; indexA++ ) {
            Node snapshotVersionNode = snapshotVersionNodeList.item( indexA );
            if (snapshotVersionNode.getNodeType() == Node.ELEMENT_NODE) {
              Element snapshotVersionElement = (Element) snapshotVersionNode;
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
          
          if (  filename.endsWith( ".xml" ) ) {
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
          OutputStream out = response.getOutputStream();
          
          httpClient = HttpClients.createDefault();
          httpGet = new HttpGet(url);
          httpResponse = null;
          try {
            httpResponse = httpClient.execute(httpGet);
            HttpEntity entity = httpResponse.getEntity();
            
            // transfer headers over to the new stream, "Last-Modified" is particularly important
            for (Header header : httpResponse.getAllHeaders()) {
              response.setHeader( header.getName(), header.getValue() );
            }
            
            if (entity != null) {
               entity.writeTo( out );
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
          
          out.close();                 
                  
        } catch (Exception e) {
          logger.error("error while parsing XML", e);
          response.sendRedirect( proxiedServerContext + path + filename );
          return;
        }
        
      } else {
        logger.debug( "redirecting request to: " + proxiedServerContext + path + filename );
        response.sendRedirect( proxiedServerContext + path + filename );
      }

    
  }

}
