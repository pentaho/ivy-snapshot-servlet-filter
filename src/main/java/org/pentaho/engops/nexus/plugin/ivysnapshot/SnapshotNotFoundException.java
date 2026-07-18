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

@SuppressWarnings( "serial" )
public class SnapshotNotFoundException extends Exception {
  
  public SnapshotNotFoundException ( String message ) {
    super( message );
  }
  
  public SnapshotNotFoundException ( String message, Throwable cause ) {
    super( message, cause );
  }

}
