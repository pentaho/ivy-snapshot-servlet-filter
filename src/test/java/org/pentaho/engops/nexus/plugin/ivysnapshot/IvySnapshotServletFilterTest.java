package org.pentaho.engops.nexus.plugin.ivysnapshot;

import org.apache.http.HttpEntity;
import org.apache.http.HttpStatus;
import org.apache.http.HttpVersion;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.message.BasicStatusLine;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.mock.web.MockFilterConfig;

import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 *
 */
@RunWith( MockitoJUnitRunner.class )
public class IvySnapshotServletFilterTest {

  private static final String CONTEXT = "/nexus";

  private static final String REQUESTED = "http://localhost:8081/nexus/content/repositories/omni/pentaho/pentaho-bi-platform-data-access/8.0-SNAPSHOT/pentaho-bi-platform-data-access-8.0-SNAPSHOT.jar";
  private static final String REQUESTED_IVY = "http://localhost:8081/nexus/content.repositories.omni.pentaho.pentaho-bi-platform-data-access/8.0-SNAPSHOT/pentaho-bi-platform-data-access-8.0-SNAPSHOT.jar";
  private static final String REQUESTED_IVY_PART = "http://localhost:8081/nexus/content/repositories/omni/pentaho.pentaho-bi-platform-data-access/8.0-SNAPSHOT/pentaho-bi-platform-data-access-8.0-SNAPSHOT.jar";
  private static final String REQUESTED_CLASSIFIER = "http://localhost:8081/nexus/content/repositories/omni/pentaho/pentaho-bi-platform-data-access/8.0-SNAPSHOT/pentaho-bi-platform-data-access-8.0-SNAPSHOT-sources.jar";
  private static final String REQUESTED_EXCEPTION = "http://localhost:8081/nexus/content/repositories/omni/pentah/pentaho-bi-platform-data-access/8.0-SNAPSHOT/pentaho-bi-platform-data-access-8.0-SNAPSHOT.jar";
  private static final String REQUESTED_NO_SNAPSHOT = "http://localhost:8081/nexus/content/repositories/omni/pentaho/pentaho-bi-platform-data-access/8.0-SNAPSHOT/pentaho-bi-platform-data-access-8.0-SNAPSHOT.jar";

  private static final String FORWARDING = "/content/repositories/omni/pentaho/pentaho-bi-platform-data-access/8.0-SNAPSHOT/pentaho-bi-platform-data-access-8.0-20170803.131735-124.jar";
  private static final String FORWARDING_CLASSIFIER = "/content/repositories/omni/pentaho/pentaho-bi-platform-data-access/8.0-SNAPSHOT/pentaho-bi-platform-data-access-8.0-20170803.131735-124-sources.jar";

  private static final String METADATA = "/maven-metadata.xml";
  private static final String METADATA_NO_SNAPSHOT = "/no-snapshot-metadata.xml";

  @Spy
  IvySnapshotServletFilter filter = new IvySnapshotServletFilter();

  @Mock
  HttpServletRequest request;
  @Mock
  HttpServletResponse response;
  @Mock
  FilterChain chain;
  @Mock
  RequestDispatcher dispacher;
  @Mock
  ServletContext servletContext;
  @Mock
  CloseableHttpClient httpClient;
  @Mock
  CloseableHttpResponse httpResponse;
  @Mock
  HttpEntity entity;

  @Before
  public void initialize() throws ServletException, IOException {
    FilterConfig config = new MockFilterConfig( servletContext );
    when( config.getServletContext().getContextPath() ).thenReturn( CONTEXT );

    filter.init( config );

    when( request.getRequestDispatcher( any() ) ).thenReturn( dispacher );
    doReturn( httpClient ).when( filter ).getHttpClient();
    when( httpClient.execute( any() ) ).thenReturn( httpResponse );
    when( httpResponse.getStatusLine() ).thenReturn( new BasicStatusLine( HttpVersion.HTTP_1_1, HttpStatus.SC_OK, "FINE!" ) );
    when( httpResponse.getEntity() ).thenReturn( entity );
  }

  @Test
  public void filterSimpleTest() throws ServletException, IOException {

    when( request.getRequestURL() ).thenReturn( new StringBuffer( REQUESTED ) );
    setMetadata( METADATA );

    filter.doFilter( request, response, chain );

    verify( request ).getRequestDispatcher( FORWARDING );
    verify( chain, times( 0 ) ).doFilter( request, response );
  }

  @Test
  public void filterIvyGroupTest() throws ServletException, IOException {

    when( request.getRequestURL() ).thenReturn( new StringBuffer( REQUESTED_IVY ) );
    setMetadata( METADATA );

    filter.doFilter( request, response, chain );

    verify( request ).getRequestDispatcher( FORWARDING );
    verify( chain, times( 0 ) ).doFilter( request, response );
  }

  @Test
  public void filterIvyPartGroupTest() throws ServletException, IOException {

    when( request.getRequestURL() ).thenReturn( new StringBuffer( REQUESTED_IVY_PART ) );
    setMetadata( METADATA );

    filter.doFilter( request, response, chain );

    verify( request ).getRequestDispatcher( FORWARDING );
    verify( chain, times( 0 ) ).doFilter( request, response );
  }

  @Test
  public void filterWithClassifierTest() throws ServletException, IOException {

    when( request.getRequestURL() ).thenReturn( new StringBuffer( REQUESTED_CLASSIFIER ) );
    setMetadata( METADATA );

    filter.doFilter( request, response, chain );

    verify( request ).getRequestDispatcher( FORWARDING_CLASSIFIER );
    verify( chain, times( 0 ) ).doFilter( request, response );
  }

  @Test
  public void filterNoSnapshotTest() throws ServletException, IOException {

    when( request.getRequestURL() ).thenReturn( new StringBuffer( REQUESTED_NO_SNAPSHOT ) );
    setMetadata( METADATA_NO_SNAPSHOT );
    filter.doFilter( request, response, chain );

    verify( request ).getRequestDispatcher( FORWARDING );
    verify( chain, times( 0 ) ).doFilter( request, response );
  }

  @Test
  public void metadataNotParsableExceptionTest() throws ServletException, IOException {

    when( request.getRequestURL() ).thenReturn( new StringBuffer( REQUESTED_EXCEPTION ) );

    filter.doFilter( request, response, chain );

    verify( chain ).doFilter( request, response );
  }

  private void setMetadata( String file ) throws IOException {

    when( entity.getContent() ).thenReturn( this.getClass().getResourceAsStream( file ) );
  }
}
