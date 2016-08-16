package org.pentaho.engops.nexus.plugin.ivysnapshot;

@SuppressWarnings( "serial" )
public class SnapshotNotFoundException extends Exception {
  
  public SnapshotNotFoundException ( String message ) {
    super( message );
  }

}
