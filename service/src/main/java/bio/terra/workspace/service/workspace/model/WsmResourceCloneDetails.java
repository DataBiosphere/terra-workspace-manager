package bio.terra.workspace.service.workspace.model;

import bio.terra.workspace.generated.model.ApiResourceCloneDetails;
import bio.terra.workspace.service.resource.WsmResourceType;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.resource.model.StewardshipType;
import java.util.UUID;

/** Internal wrapper type for {@link ApiResourceCloneDetails} */
public class WsmResourceCloneDetails {
  private CloningInstructions cloningInstructions;
  private WsmResourceType resourceType;
  private StewardshipType stewardshipType;
  private UUID sourceResourceId;
  private UUID destinationResourceId;
  private WsmCloneResourceResult result;

  public WsmResourceCloneDetails() {}

  public CloningInstructions getCloningInstructions() {
    return cloningInstructions;
  }

  public void setCloningInstructions(CloningInstructions cloningInstructions) {
    this.cloningInstructions = cloningInstructions;
  }

  public WsmResourceType getResourceType() {
    return resourceType;
  }

  public void setResourceType(WsmResourceType resourceType) {
    this.resourceType = resourceType;
  }

  public StewardshipType getStewardshipType() {
    return stewardshipType;
  }

  public void setStewardshipType(StewardshipType stewardshipType) {
    this.stewardshipType = stewardshipType;
  }

  public UUID getSourceResourceId() {
    return sourceResourceId;
  }

  public void setSourceResourceId(UUID sourceResourceId) {
    this.sourceResourceId = sourceResourceId;
  }

  public UUID getDestinationResourceId() {
    return destinationResourceId;
  }

  public void setDestinationResourceId(UUID destinationResourceId) {
    this.destinationResourceId = destinationResourceId;
  }

  public WsmCloneResourceResult getResult() {
    return result;
  }

  public void setResult(WsmCloneResourceResult result) {
    this.result = result;
  }

  public ApiResourceCloneDetails toApiModel() {
    return new ApiResourceCloneDetails()
        .effectiveCloningInstructions(cloningInstructions.toApiModel())
        .resourceType(resourceType.toApiModel())
        .stewardshipType(stewardshipType.toApiModel())
        .sourceResourceId(sourceResourceId)
        .destinationResourceId(destinationResourceId)
        .result(result.toApiModel());
  }
}
