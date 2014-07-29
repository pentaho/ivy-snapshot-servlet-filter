package org.pentaho.engops;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.URL;
import java.util.Enumeration;
import java.util.HashMap;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

public class IvySnapshotServletFilter implements Filter {
  private static Logger logger = LoggerFactory.getLogger( IvySnapshotServletFilter.class );

  @Override
  public void destroy() {
    // TODO Auto-generated method stub

  }

  @Override
  public void doFilter( ServletRequest request, ServletResponse response, FilterChain chain ) throws IOException,
    ServletException {

    if ( request instanceof HttpServletRequest ) {
      HttpServletRequest httpRequest = (HttpServletRequest) request;
      String url = httpRequest.getRequestURL().toString();
      logger.debug( "requested url: " + url );

      String filename = url.substring(url.lastIndexOf( "/" ) + 1,url.length());
      logger.debug( "filename: " + filename);
      
      if ( filename.contains( "SNAPSHOT" ) && !url.endsWith( "maven-metadata.xml" )) {
        
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
        logger.debug( "extension: " + artifactExtension );
        
        String mavenMetadataURL = url.substring( 0, url.lastIndexOf( "/" ) ) + "/maven-metadata.xml";
        logger.debug( "metadata file: " + mavenMetadataURL );
        
        
        String metadataXml = "";
        CloseableHttpClient httpClient = HttpClients.createDefault();
        HttpGet httpGet = new HttpGet(mavenMetadataURL);
        CloseableHttpResponse httpResponse = httpClient.execute(httpGet);
        try {
          HttpEntity entity = httpResponse.getEntity();
          if (entity != null) {
             metadataXml = EntityUtils.toString(entity);
          } else {
            logger.warn( "error loading " + mavenMetadataURL );
            chain.doFilter( request, response );
            return;
          }
        } catch (Exception e) {
          logger.warn( "error loading " + mavenMetadataURL, e );
          chain.doFilter( request, response );
          return;
        } finally {
          httpResponse.close();
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
                NodeList valueNodeList = snapshotVersionElement.getElementsByTagName( "value" );
                for ( int indexC=0 ; indexC < valueNodeList.getLength() ; indexC++ ) {
                  Node valueNode = valueNodeList.item( indexC );
                  if ( valueNode.getNodeType() == Node.ELEMENT_NODE ) {
                    Element valueElement = (Element) valueNode;
                    String value = valueElement.getFirstChild().getNodeValue().trim();
                    logger.debug( "value of this node is " + value );
                    if (artifactClassifier.length() > 0) {
                      NodeList classifierNodeList = snapshotVersionElement.getElementsByTagName("classifier");
                      for (int indexD = 0; indexD < classifierNodeList.getLength(); indexD++) {
                        Node classifierNode = classifierNodeList.item(indexD);
                        if (classifierNode.getNodeType() == Node.ELEMENT_NODE) {
                          Element classifierElement = (Element) classifierNode;
                          String classifier = classifierElement.getFirstChild().getNodeValue().trim();
                          logger.debug( "classifer " + classifier + " found" );
                          if ( artifactClassifier.equals(classifier) ) {
                            logger.debug( classifier + " matches artifact classifier of " + artifactClassifier );
                            snapshotVersion = value;
                            break;
                          }
                        }
                      }
                      
                    } else {
                      snapshotVersion = value;
                    }
                  }
                }
                break;
              }
            }
            
          }
          
          String snapshotFile = filename;
          if (artifactClassifier.length() > 0) {
            snapshotFile = artifactId + "-" + snapshotVersion + "-" + artifactClassifier + "." + artifactExtension;
          } else {
            snapshotFile = artifactId + "-" + snapshotVersion + "." + artifactExtension;
          }
          
          logger.info( "replacing " + filename + " request with " + snapshotFile );
          request.getRequestDispatcher( snapshotFile ).forward( request, response );
          
        
        } catch (Exception e) {
          logger.error("error while parsing XML", e);
          chain.doFilter( request, response );
          return;
        }
        
      } else {
        logger.debug( "not the droids you are looking for" );
        chain.doFilter( request, response );
      }
    } else {
      logger.debug( "not an http servlet request" );
      chain.doFilter( request, response );
    }

    
  }

  @Override
  public void init( FilterConfig arg0 ) throws ServletException {
    // TODO Auto-generated method stub

  }

}
