package bio.terra.workspace.service.resource.model;

import bio.terra.workspace.generated.model.ApiCloningInstructionsEnum;
import bio.terra.workspace.service.resource.ResourceValidationUtils;
import com.fasterxml.jackson.annotation.JsonIgnore;
import javax.annotation.Nullable;

/**
 * Class for assembling common update parameters from an update request. The setters perform
 * validation. The class stores the cloning instruction as the DB string so it can be null. Various
 * getters do the conversions to CloningInstructions
 */
public class CommonUpdateParameters {
  private @Nullable String name;
  private @Nullable String description;
  private @Nullable String dbCloningInstructions;

  public @Nullable String getName() {
    return name;
  }

  public CommonUpdateParameters setName(@Nullable String name) {
    ResourceValidationUtils.validateOptionalResourceName(name);
    this.name = name;
    return this;
  }

  public @Nullable String getDescription() {
    return description;
  }

  public CommonUpdateParameters setDescription(@Nullable String description) {
    ResourceValidationUtils.validateResourceDescriptionName(description);
    this.description = description;
    return this;
  }

  @JsonIgnore
  public @Nullable CloningInstructions getCloningInstructions() {
    if (dbCloningInstructions == null) {
      return null;
    }
    return CloningInstructions.fromSql(dbCloningInstructions);
  }

  @JsonIgnore
  public CommonUpdateParameters setCloningInstructions(
      StewardshipType stewardshipType,
      @Nullable ApiCloningInstructionsEnum apiCloningInstructions) {
    return setCloningInstructions(
        stewardshipType, CloningInstructions.fromApiModel(apiCloningInstructions));
  }

  @JsonIgnore
  public CommonUpdateParameters setCloningInstructions(
      StewardshipType stewardshipType, @Nullable CloningInstructions cloningInstructions) {
    if (cloningInstructions == null) {
      this.dbCloningInstructions = null;
    } else {
      ResourceValidationUtils.validateCloningInstructions(stewardshipType, cloningInstructions);
      this.dbCloningInstructions = cloningInstructions.toSql();
    }
    return this;
  }

  // Get/set for serdes
  public @Nullable String getDbCloningInstructions() {
    return dbCloningInstructions;
  }

  public CommonUpdateParameters setDbCloningInstructions(@Nullable String dbCloningInstructions) {
    this.dbCloningInstructions = dbCloningInstructions;
    return this;
  }
}
