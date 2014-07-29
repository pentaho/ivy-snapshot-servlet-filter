package org.pentaho.engops;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.URI;
import org.apache.commons.httpclient.methods.GetMethod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CommonsHttpGetUtil implements HttpGetUtil {
  private static Logger logger = LoggerFactory.getLogger( CommonsHttpGetUtil.class );

  @Override
  public String get( String url ) {
    
    String metadataXml = "";
    HttpClient client = new HttpClient();
    GetMethod method  = new GetMethod();
    
    try {
      method.setURI(new URI(url, true));
      int returnCode = client.executeMethod(method);
      if(returnCode != HttpStatus.SC_OK) {
        logger.warn( "http error code: " + returnCode );
      }
      metadataXml = method.getResponseBodyAsString();
    } catch ( Exception e ) {
      logger.warn( "failed to download " + url, e );
    }

    return metadataXml;
  }

}
