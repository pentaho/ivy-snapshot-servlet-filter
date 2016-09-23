package org.pentaho.engops.nexus.plugin.ivysnapshot;

@SuppressWarnings( "serial" )
public class MavenMetadataNotFoundException extends Exception {
  
  public MavenMetadataNotFoundException ( String message ) {
    super( message );
  }
  
  public MavenMetadataNotFoundException ( String message, Throwable cause ) {
    super( message, cause );
  }
  
}
