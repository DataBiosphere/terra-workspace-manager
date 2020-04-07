package bio.terra.workspace.model;

import java.util.UUID;

public class DataReference {

  private UUID workspaceId;
  private UUID referenceId;
  private String name;
  private String resourceId;
  private String credentialId;
  private String cloningInstructions; // todo: enum
  private String referenceType; // todo: enum
  private DataRepoSnapshot reference; // todo: use a generic parent

  public DataReference(
      UUID workspaceId,
      UUID referenceId,
      String name,
      String resourceId,
      String credentialId,
      String cloningInstructions,
      String referenceType,
      DataRepoSnapshot reference) {
    this.workspaceId = workspaceId;
    this.referenceId = referenceId;
    this.name = name;
    this.resourceId = resourceId;
    this.credentialId = credentialId;
    this.cloningInstructions = cloningInstructions;
    this.referenceType = referenceType;
    this.reference = reference;
  }

  public DataReference() {
    super();
  }

  public UUID getWorkspaceId() {
    return workspaceId;
  }

  public void setWorkspaceId(UUID workspaceId) {
    this.workspaceId = workspaceId;
  }

  public UUID getReferenceId() {
    return referenceId;
  }

  public void setReferenceId(UUID referenceId) {
    this.referenceId = referenceId;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getResourceId() {
    return resourceId;
  }

  public void setResourceId(String resourceId) {
    this.resourceId = resourceId;
  }

  public String getCredentialId() {
    return credentialId;
  }

  public void setCredentialId(String credentialId) {
    this.credentialId = credentialId;
  }

  public String getCloningInstructions() {
    return cloningInstructions;
  }

  public void setCloningInstructions(String cloningInstructions) {
    this.cloningInstructions = cloningInstructions;
  }

  public String getReferenceType() {
    return referenceType;
  }

  public void setReferenceType(String referenceType) {
    this.referenceType = referenceType;
  }

  public DataRepoSnapshot getReference() {
    return reference;
  }

  public void setReference(DataRepoSnapshot reference) {
    this.reference = reference;
  }
}
