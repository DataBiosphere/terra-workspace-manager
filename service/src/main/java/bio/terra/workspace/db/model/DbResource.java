package bio.terra.workspace.db.model;

import bio.terra.workspace.service.resource.controlled.model.AccessScopeType;
import bio.terra.workspace.service.resource.controlled.model.ManagedByType;
import bio.terra.workspace.service.resource.controlled.model.PrivateResourceState;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.resource.model.ResourceLineageEntry;
import bio.terra.workspace.service.resource.model.StewardshipType;
import bio.terra.workspace.service.resource.model.WsmResourceFamily;
import bio.terra.workspace.service.resource.model.WsmResourceType;
import bio.terra.workspace.service.workspace.exceptions.MissingRequiredFieldsException;
import bio.terra.workspace.service.workspace.model.CloudPlatform;
import com.google.common.collect.ImmutableMap;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import javax.annotation.Nullable;

/**
 * This class is used to have a common structure to hold the database view of a resource. It
 * includes all possible fields for a reference or controlled resource and (currently) maps
 * one-to-one with the resource table.
 */
public class DbResource {
  private UUID workspaceUuid;
  private CloudPlatform cloudPlatform;
  private UUID resourceId;
  private String name;
  private String description;
  private StewardshipType stewardshipType;
  private WsmResourceFamily cloudResourceType;
  private WsmResourceType resourceType;
  private CloningInstructions cloningInstructions;
  private String attributes;
  @Nullable List<ResourceLineageEntry> resourceLineage;
  // controlled resource fields
  @Nullable private AccessScopeType accessScope;
  @Nullable private ManagedByType managedBy;
  @Nullable private String applicationId;
  @Nullable private String assignedUser;
  @Nullable private PrivateResourceState privateResourceState;
  @Nullable private ImmutableMap<String, String> properties;
  private String createdByEmail;
  @Nullable private OffsetDateTime createdDate;

  private static final Supplier<RuntimeException> MISSING_REQUIRED_FIELD =
      () -> new MissingRequiredFieldsException("Missing required field");

  public UUID getWorkspaceId() {
    return workspaceUuid;
  }

  public DbResource workspaceUuid(UUID workspaceUuid) {
    this.workspaceUuid = workspaceUuid;
    return this;
  }

  public CloudPlatform getCloudPlatform() {
    return cloudPlatform;
  }

  public DbResource cloudPlatform(CloudPlatform cloudPlatform) {
    this.cloudPlatform = cloudPlatform;
    return this;
  }

  public UUID getResourceId() {
    return resourceId;
  }

  public DbResource resourceId(UUID resourceId) {
    this.resourceId = resourceId;
    return this;
  }

  public String getName() {
    return name;
  }

  public DbResource name(String name) {
    this.name = name;
    return this;
  }

  public String getDescription() {
    return description;
  }

  public DbResource description(String description) {
    this.description = description;
    return this;
  }

  public StewardshipType getStewardshipType() {
    return stewardshipType;
  }

  public DbResource stewardshipType(StewardshipType stewardshipType) {
    this.stewardshipType = stewardshipType;
    return this;
  }

  public DbResource cloudResourceType(WsmResourceFamily cloudResourceType) {
    this.cloudResourceType = cloudResourceType;
    return this;
  }

  public WsmResourceFamily getCloudResourceType() {
    return cloudResourceType;
  }

  public WsmResourceType getResourceType() {
    return resourceType;
  }

  public DbResource resourceType(WsmResourceType resourceType) {
    this.resourceType = resourceType;
    return this;
  }

  public CloningInstructions getCloningInstructions() {
    return cloningInstructions;
  }

  public DbResource cloningInstructions(CloningInstructions cloningInstructions) {
    this.cloningInstructions = cloningInstructions;
    return this;
  }

  public String getAttributes() {
    return attributes;
  }

  public DbResource attributes(String attributes) {
    this.attributes = attributes;
    return this;
  }

  public AccessScopeType getAccessScope() {
    return Optional.ofNullable(accessScope).orElseThrow(MISSING_REQUIRED_FIELD);
  }

  public DbResource accessScope(@Nullable AccessScopeType accessScope) {
    this.accessScope = accessScope;
    return this;
  }

  public ManagedByType getManagedBy() {
    return Optional.ofNullable(managedBy).orElseThrow(MISSING_REQUIRED_FIELD);
  }

  public DbResource managedBy(@Nullable ManagedByType managedBy) {
    this.managedBy = managedBy;
    return this;
  }

  public Optional<String> getApplicationId() {
    return Optional.ofNullable(applicationId);
  }

  public DbResource applicationId(@Nullable String applicationId) {
    this.applicationId = applicationId;
    return this;
  }

  public Optional<String> getAssignedUser() {
    return Optional.ofNullable(assignedUser);
  }

  public DbResource assignedUser(String assignedUser) {
    this.assignedUser = assignedUser;
    return this;
  }

  public Optional<PrivateResourceState> getPrivateResourceState() {
    return Optional.ofNullable(privateResourceState);
  }

  public DbResource privateResourceState(PrivateResourceState privateResourceState) {
    this.privateResourceState = privateResourceState;
    return this;
  }

  public Optional<List<ResourceLineageEntry>> getResourceLineage() {
    return Optional.ofNullable(resourceLineage);
  }

  public DbResource resourceLineage(@Nullable List<ResourceLineageEntry> resourceLineage) {
    this.resourceLineage = resourceLineage;
    return this;
  }

  public ImmutableMap<String, String> getProperties() {
    return Optional.ofNullable(properties).orElseThrow(MISSING_REQUIRED_FIELD);
  }

  public DbResource properties(Map<String, String> properties) {
    this.properties = ImmutableMap.copyOf(properties);
    return this;
  }

  public DbResource createdByEmail(String createdByEmail) {
    this.createdByEmail = createdByEmail;
    return this;
  }

  public String getCreatedByEmail() {
    return createdByEmail;
  }

  public DbResource createdDate(OffsetDateTime createdDate) {
    this.createdDate = createdDate;
    return this;
  }

  public OffsetDateTime getCreatedDate() {
    return createdDate;
  }
}
