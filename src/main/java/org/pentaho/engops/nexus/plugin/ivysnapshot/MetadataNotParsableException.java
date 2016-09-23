package org.pentaho.engops.nexus.plugin.ivysnapshot;

@SuppressWarnings( "serial" )
public class MetadataNotParsableException extends Exception {

  public MetadataNotParsableException ( String message ) {
    super( message );
  }
  
  public MetadataNotParsableException ( String message, Throwable cause ) {
    super( message, cause );
  }
  
}
