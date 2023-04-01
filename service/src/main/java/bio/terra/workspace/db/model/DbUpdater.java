package bio.terra.workspace.db.model;

import bio.terra.workspace.db.DbSerDes;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.resource.model.CommonUpdateParameters;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import javax.annotation.Nullable;
import org.apache.commons.lang3.StringUtils;

public class DbUpdater {
  public static class UpdateData {
    private String name;
    private String description;
    private String dbCloningInstructions;
    private String jsonAttributes;

    public @Nullable String getName() {
      return name;
    }

    public UpdateData setName(@Nullable String name) {
      this.name = name;
      return this;
    }

    public @Nullable String getDescription() {
      return description;
    }

    public UpdateData setDescription(@Nullable String description) {
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
    public UpdateData setCloningInstructions(@Nullable CloningInstructions cloningInstructions) {
      if (cloningInstructions == null) {
        this.dbCloningInstructions = null;
      } else {
        this.dbCloningInstructions = cloningInstructions.toSql();
      }
      return this;
    }

    // Get/set for serdes
    public String getDbCloningInstructions() {
      return dbCloningInstructions;
    }

    public UpdateData setDbCloningInstructions(String dbCloningInstructions) {
      this.dbCloningInstructions = dbCloningInstructions;
      return this;
    }

    public @Nullable String getJsonAttributes() {
      return jsonAttributes;
    }

    public UpdateData setJsonAttributes(@Nullable String jsonAttributes) {
      this.jsonAttributes = jsonAttributes;
      return this;
    }
  }

  // We maintain the original data and any updated data
  private final UpdateData originalData;
  private final UpdateData updatedData;

  /**
   * This constructor and the two getters are only intended for use by Jackson JSON serdes.
   *
   * @param originalData original data
   * @param updatedData any updated to original data
   */
  @JsonCreator
  public DbUpdater(
      @JsonProperty("originalData") UpdateData originalData,
      @JsonProperty("updatedData") UpdateData updatedData) {
    this.originalData = originalData;
    this.updatedData = updatedData;
  }

  public UpdateData getOriginalData() {
    return originalData;
  }

  public UpdateData getUpdatedData() {
    return updatedData;
  }

  /**
   * Used by ResourceDao to construct a DbUpdater
   *
   * @param name resource name
   * @param description resource description
   * @param cloningInstructions cloning instructions
   * @param jsonAttributes JSON string form of the resource attributes
   */
  public DbUpdater(
      String name,
      @Nullable String description,
      @Nullable CloningInstructions cloningInstructions,
      @Nullable String jsonAttributes) {
    this.originalData =
        new UpdateData()
            .setName(name)
            .setDescription(description)
            .setCloningInstructions(cloningInstructions)
            .setJsonAttributes(jsonAttributes);
    this.updatedData =
        new UpdateData()
            .setName(null)
            .setDescription(null)
            .setCloningInstructions(null)
            .setJsonAttributes(null);
  }

  /**
   * Populate the updated data using the CommonUpdateParameters gather from the API input of an
   * update endpoint.
   *
   * @param commonUpdateParameters class containing the updated parameters
   */
  public void updateFromCommonParameters(CommonUpdateParameters commonUpdateParameters) {
    if (commonUpdateParameters.getName() != null) {
      updatedData.setName(commonUpdateParameters.getName());
    }
    if (commonUpdateParameters.getDescription() != null) {
      updatedData.setDescription(commonUpdateParameters.getDescription());
    }
    if (commonUpdateParameters.getCloningInstructions() != null) {
      updatedData.setCloningInstructions(commonUpdateParameters.getCloningInstructions());
    }
  }

  @JsonIgnore
  public <T> T getOriginalAttributes(Class<T> clazz) {
    if (originalData.getJsonAttributes() == null) {
      return null;
    }
    return DbSerDes.fromJson(originalData.getJsonAttributes(), clazz);
  }

  @JsonIgnore
  public <T> void updateAttributes(T jsonAttributes) {
    if (jsonAttributes != null) {
      updatedData.setJsonAttributes(DbSerDes.toJson(jsonAttributes));
    }
  }

  @JsonIgnore
  public boolean isNameUpdated() {
    return (updatedData.getName() != null
        && !StringUtils.equals(updatedData.getName(), originalData.getName()));
  }

  @JsonIgnore
  public boolean isDescriptionUpdated() {
    return (updatedData.getDescription() != null
        && !StringUtils.equals(updatedData.getDescription(), originalData.getDescription()));
  }

  @JsonIgnore
  public boolean isCloningInstructionsUpdated() {
    return (updatedData.getCloningInstructions() != null
        && updatedData.getCloningInstructions() != originalData.getCloningInstructions());
  }

  @JsonIgnore
  public boolean isJsonAttributesUpdated() {
    return (updatedData.getJsonAttributes() != null
        && !StringUtils.equals(updatedData.getJsonAttributes(), originalData.getJsonAttributes()));
  }

  @JsonIgnore
  public String getUpdatedName() {
    return updatedData.getName();
  }

  @JsonIgnore
  public String getUpdatedDescription() {
    return updatedData.getDescription();
  }

  @JsonIgnore
  public CloningInstructions getUpdatedCloningInstructions() {
    return updatedData.getCloningInstructions();
  }

  @JsonIgnore
  public String getUpdatedJsonAttributes() {
    return updatedData.getJsonAttributes();
  }
}
