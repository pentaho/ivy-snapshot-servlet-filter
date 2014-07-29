package org.pentaho.engops;

import java.io.IOException;

import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ApacheHttpGetUtil implements HttpGetUtil {
  private static Logger logger = LoggerFactory.getLogger( ApacheHttpGetUtil.class );
  
  @Override
  public String get(String url) {
    
    String metadataXml = "";
    CloseableHttpClient httpClient = HttpClients.createDefault();
    HttpGet httpGet = new HttpGet(url);
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
    
    return metadataXml;
  }
  
}
