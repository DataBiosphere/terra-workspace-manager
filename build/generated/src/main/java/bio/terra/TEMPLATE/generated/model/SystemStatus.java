package bio.terra.TEMPLATE.generated.model;

import java.util.Objects;
import bio.terra.TEMPLATE.generated.model.SystemStatusSystems;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.openapitools.jackson.nullable.JsonNullable;
import javax.validation.Valid;
import javax.validation.constraints.*;

/**
 * SystemStatus
 */
@javax.annotation.Generated(value = "org.openapitools.codegen.languages.SpringCodegen", date = "2020-02-13T13:20:17.711576-05:00[America/New_York]")

public class SystemStatus   {
  @JsonProperty("ok")
  private Boolean ok;

  @JsonProperty("systems")
  @Valid
  private Map<String, SystemStatusSystems> systems = null;

  public SystemStatus ok(Boolean ok) {
    this.ok = ok;
    return this;
  }

  /**
   * status of this service
   * @return ok
  */
  @ApiModelProperty(value = "status of this service")


  public Boolean getOk() {
    return ok;
  }

  public void setOk(Boolean ok) {
    this.ok = ok;
  }

  public SystemStatus systems(Map<String, SystemStatusSystems> systems) {
    this.systems = systems;
    return this;
  }

  public SystemStatus putSystemsItem(String key, SystemStatusSystems systemsItem) {
    if (this.systems == null) {
      this.systems = new HashMap<>();
    }
    this.systems.put(key, systemsItem);
    return this;
  }

  /**
   * Get systems
   * @return systems
  */
  @ApiModelProperty(value = "")

  @Valid

  public Map<String, SystemStatusSystems> getSystems() {
    return systems;
  }

  public void setSystems(Map<String, SystemStatusSystems> systems) {
    this.systems = systems;
  }


  @Override
  public boolean equals(java.lang.Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    SystemStatus systemStatus = (SystemStatus) o;
    return Objects.equals(this.ok, systemStatus.ok) &&
        Objects.equals(this.systems, systemStatus.systems);
  }

  @Override
  public int hashCode() {
    return Objects.hash(ok, systems);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class SystemStatus {\n");
    
    sb.append("    ok: ").append(toIndentedString(ok)).append("\n");
    sb.append("    systems: ").append(toIndentedString(systems)).append("\n");
    sb.append("}");
    return sb.toString();
  }

  /**
   * Convert the given object to string with each line indented by 4 spaces
   * (except the first line).
   */
  private String toIndentedString(java.lang.Object o) {
    if (o == null) {
      return "null";
    }
    return o.toString().replace("\n", "\n    ");
  }
}

