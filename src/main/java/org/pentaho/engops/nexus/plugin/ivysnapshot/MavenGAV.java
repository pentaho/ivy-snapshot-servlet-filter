package org.pentaho.engops.nexus.plugin.ivysnapshot;

public class MavenGAV {
  
  private String groupId;
  private String artifactId;
  private String classifier;
  private String extension;
  private String version;
  
  public MavenGAV ( String groupId, String artifactId, String version, String classifier, String extension ) {
    this.groupId = groupId;
    this.artifactId = artifactId;
    this.classifier = classifier;
    this.extension = extension;
    this.version = version;
  }
  
  public String getGroupId() {
    return groupId;
  }
  public void setGroupId( String groupId ) {
    this.groupId = groupId;
  }
  public String getArtifactId() {
    return artifactId;
  }
  public void setArtifactId( String artifactId ) {
    this.artifactId = artifactId;
  }
  public String getClassifier() {
    return classifier;
  }
  public void setClassifier( String classifier ) {
    this.classifier = classifier;
  }
  public String getExtension() {
    return extension;
  }
  public void setExtension( String extension ) {
    this.extension = extension;
  }
  public String getVersion() {
    return version;
  }
  public void setVersion( String version ) {
    this.version = version;
  }
}
