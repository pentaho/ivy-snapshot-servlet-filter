package org.pentaho.engops.nexus.plugin.ivysnapshot;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.mock.web.MockFilterConfig;
import org.springframework.mock.web.MockServletContext;

import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 *
 */
@RunWith(MockitoJUnitRunner.class)
public class IvySnapshotServletFilterTest {

  private static IvySnapshotServletFilter filter;

  private static final String CONTEXT = "/nexus";

  private static final String REQUESTED = "http://localhost:8081/nexus/content/repositories/omni/pentaho/pentaho-bi-platform-data-access/8.0-SNAPSHOT/pentaho-bi-platform-data-access-8.0-SNAPSHOT.jar";
  private static final String REQUESTED_IVY = "http://localhost:8081/nexus/content.repositories.omni.pentaho.pentaho-bi-platform-data-access/8.0-SNAPSHOT/pentaho-bi-platform-data-access-8.0-SNAPSHOT.jar";
  private static final String REQUESTED_IVY_PART = "http://localhost:8081/nexus/content/repositories/omni/pentaho.pentaho-bi-platform-data-access/8.0-SNAPSHOT/pentaho-bi-platform-data-access-8.0-SNAPSHOT.jar";
  private static final String REQUESTED_CLASSIFIER = "http://localhost:8081/nexus/content/repositories/omni/pentaho/pentaho-bi-platform-data-access/8.0-SNAPSHOT/pentaho-bi-platform-data-access-8.0-SNAPSHOT-sources.jar";
  private static final String REQUESTED_EXCEPTION = "http://localhost:8081/nexus/content/repositories/omni/pentah/pentaho-bi-platform-data-access/8.0-SNAPSHOT/pentaho-bi-platform-data-access-8.0-SNAPSHOT.jar";
  private static final String REQUESTED_NO_SNAPSHOT = "http://ivy-nexus.pentaho.org/content/groups/omni/log4j/log4j/1.2.15/log4j-1.2.15-SNAPSHOT.jar";

  private static final String FORWARDING = "/content/repositories/omni/pentaho/pentaho-bi-platform-data-access/8.0-SNAPSHOT/pentaho-bi-platform-data-access-8.0-20170803.131735-124.jar";
  private static final String FORWARDING_CLASSIFIER = "/content/repositories/omni/pentaho/pentaho-bi-platform-data-access/8.0-SNAPSHOT/pentaho-bi-platform-data-access-8.0-20170803.131735-124-sources.jar";

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

  @Before
  public void initialize() throws ServletException {
    FilterConfig config = new MockFilterConfig( servletContext );
    when( config.getServletContext().getContextPath() ).thenReturn( CONTEXT );

    filter = new IvySnapshotServletFilter();
    filter.init( config );
    when( request.getRequestDispatcher( any() ) ).thenReturn( dispacher );
  }

  @Test
  public void filterTest() throws ServletException, IOException {

    when( request.getRequestURL() ).thenReturn( new StringBuffer( REQUESTED ) );

    filter.doFilter( request, response, chain );

    verify( request ).getRequestDispatcher( FORWARDING );
    verify( chain, times( 0 ) ).doFilter(request,response);
  }

  @Test
  public void filterIvyGroupTest() throws ServletException, IOException {

    when( request.getRequestURL() ).thenReturn( new StringBuffer( REQUESTED_IVY ) );

    filter.doFilter( request, response, chain );

    verify( request ).getRequestDispatcher( FORWARDING );
    verify( chain, times( 0 ) ).doFilter(request,response);
  }

  @Test
  public void filterIvyPartGroupTest() throws ServletException, IOException {

    when( request.getRequestURL() ).thenReturn( new StringBuffer( REQUESTED_IVY_PART ) );

    filter.doFilter( request, response, chain );

    verify( request ).getRequestDispatcher( FORWARDING );
    verify( chain, times( 0 ) ).doFilter(request,response);
  }

  @Test
  public void filterWithClassifierTest() throws ServletException, IOException {

    when( request.getRequestURL() ).thenReturn( new StringBuffer( REQUESTED_CLASSIFIER ) );

    filter.doFilter( request, response, chain );

    verify( request ).getRequestDispatcher( FORWARDING_CLASSIFIER );
    verify( chain, times( 0 ) ).doFilter(request,response);
  }

  @Test
  public void filterNoSnapshotTest() throws ServletException, IOException {

    when( request.getRequestURL() ).thenReturn( new StringBuffer( REQUESTED_NO_SNAPSHOT ) );

    filter.doFilter( request, response, chain );

    verify( request ).getRequestDispatcher( FORWARDING );
    verify( chain, times( 0 ) ).doFilter(request,response);
  }

  @Test
  public void metadataNotParsableExceptionTest() throws ServletException, IOException {

    when( request.getRequestURL() ).thenReturn( new StringBuffer( REQUESTED_EXCEPTION ) );

    filter.doFilter( request, response, chain );

    verify( chain ).doFilter(request,response);
  }

}
