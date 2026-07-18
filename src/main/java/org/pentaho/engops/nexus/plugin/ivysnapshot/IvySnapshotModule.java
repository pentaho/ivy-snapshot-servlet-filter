/*! ******************************************************************************
 *
 * Pentaho
 *
 * Copyright (C) 2026 by Pentaho Canada Inc. : http://www.pentaho.com
 *
 * Use of this software is governed by the Business Source License included
 * in the LICENSE.TXT file.
 *
 * Change Date: 2030-06-15
 ******************************************************************************/
package org.pentaho.engops.nexus.plugin.ivysnapshot;

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
