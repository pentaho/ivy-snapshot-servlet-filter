package org.pentaho.engops;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HttpGetUtilFactory {
  private static Logger logger = LoggerFactory.getLogger( HttpGetUtil.class );
  
  public static HttpGetUtil getInstance() {

    HttpGetUtil httpGetUtil = null;
    
    try {
      Class apacheHttpGetUtil = Class.forName("org.pentaho.engops.ApacheHttpGetUtil");
      httpGetUtil = (HttpGetUtil) apacheHttpGetUtil.newInstance();
    } catch ( ClassNotFoundException e ) {
      logger.debug( "org.pentaho.engops.ApacheHttpGetUtil class not found" );
    } catch ( InstantiationException e ) {
      logger.debug( "unable to instantiate org.pentaho.engops.ApacheHttpGetUtil", e );
    } catch ( IllegalAccessException e ) {
      logger.debug( "illegal access on org.pentaho.engops.ApacheHttpGetUtil", e );
    }
    
    try {
      Class commonsHttpGetUtil = Class.forName("org.pentaho.engops.CommonsHttpGetUtil");
      httpGetUtil = (HttpGetUtil) commonsHttpGetUtil.newInstance();
    } catch ( ClassNotFoundException e ) {
      logger.debug( "org.pentaho.engops.CommonsHttpGetUtil class not found" );
    } catch ( InstantiationException e ) {
      logger.debug( "unable to instantiate org.pentaho.engops.CommonsHttpGetUtil", e );
    } catch ( IllegalAccessException e ) {
      logger.debug( "illegal access on org.pentaho.engops.CommonsHttpGetUtil", e );
    }
    
    if (httpGetUtil == null) {
      logger.error("no HttpGetUtil impl found");
      throw new RuntimeException("no HttpGetUtil impl found");
    }
    
    return httpGetUtil;
  }

}
