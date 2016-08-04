package org.pentaho.engops;

import javax.inject.Named;

import com.google.inject.AbstractModule;
import com.google.inject.servlet.ServletModule;

@Named
public class IvySnapshotModule extends AbstractModule {

  @Override
  protected void configure() {
    
    install( new ServletModule() {
      @Override
      protected void configureServlets() {
        filter("/*").through( IvySnapshotServletFilter.class );
      }
    });
    
  }

}
