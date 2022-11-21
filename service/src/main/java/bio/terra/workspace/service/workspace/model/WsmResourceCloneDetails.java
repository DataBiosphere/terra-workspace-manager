package bio.terra.workspace.service.workspace.model;

import bio.terra.workspace.generated.model.ApiResourceCloneDetails;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.resource.model.StewardshipType;
import bio.terra.workspace.service.resource.model.WsmResourceType;
import java.util.UUID;

/** Internal wrapper type for {@link ApiResourceCloneDetails} */
public class WsmResourceCloneDetails {
  private CloningInstructions cloningInstructions;
  private WsmResourceType resourceType;
  private StewardshipType stewardshipType;
  private UUID sourceResourceId;
  private UUID destinationResourceId;
  private WsmCloneResourceResult result;
  private String errorMessage;
  private String name;
  private String description;

  public WsmResourceCloneDetails() {}

  public CloningInstructions getCloningInstructions() {
    return cloningInstructions;
  }

  public WsmResourceCloneDetails setCloningInstructions(CloningInstructions cloningInstructions) {
    this.cloningInstructions = cloningInstructions;
    return this;
  }

  public WsmResourceType getResourceType() {
    return resourceType;
  }

  public WsmResourceCloneDetails setResourceType(WsmResourceType resourceType) {
    this.resourceType = resourceType;
    return this;
  }

  public StewardshipType getStewardshipType() {
    return stewardshipType;
  }

  public WsmResourceCloneDetails setStewardshipType(StewardshipType stewardshipType) {
    this.stewardshipType = stewardshipType;
    return this;
  }

  public UUID getSourceResourceId() {
    return sourceResourceId;
  }

  public WsmResourceCloneDetails setSourceResourceId(UUID sourceResourceId) {
    this.sourceResourceId = sourceResourceId;
    return this;
  }

  public UUID getDestinationResourceId() {
    return destinationResourceId;
  }

  public WsmResourceCloneDetails setDestinationResourceId(UUID destinationResourceId) {
    this.destinationResourceId = destinationResourceId;
    return this;
  }

  public WsmCloneResourceResult getResult() {
    return result;
  }

  public WsmResourceCloneDetails setResult(WsmCloneResourceResult result) {
    this.result = result;
    return this;
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  public WsmResourceCloneDetails setErrorMessage(String errorMessage) {
    this.errorMessage = errorMessage;
    return this;
  }

  public String getName() {
    return name;
  }

  public WsmResourceCloneDetails setName(String name) {
    this.name = name;
    return this;
  }

  public String getDescription() {
    return description;
  }

  public WsmResourceCloneDetails setDescription(String description) {
    this.description = description;
    return this;
  }

  public ApiResourceCloneDetails toApiModel() {
    return new ApiResourceCloneDetails()
        .cloningInstructions(cloningInstructions.toApiModel())
        .resourceType(resourceType.toApiModel())
        .stewardshipType(stewardshipType.toApiModel())
        .sourceResourceId(sourceResourceId)
        .destinationResourceId(destinationResourceId)
        .result(result.toApiModel())
        .errorMessage(errorMessage)
        .name(name)
        .description(description);
  }
}
