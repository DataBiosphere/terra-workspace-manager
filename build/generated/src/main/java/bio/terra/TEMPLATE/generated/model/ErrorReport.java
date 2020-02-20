package bio.terra.TEMPLATE.generated.model;

import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import java.util.ArrayList;
import java.util.List;
import org.openapitools.jackson.nullable.JsonNullable;
import javax.validation.Valid;
import javax.validation.constraints.*;

/**
 * ErrorReport
 */
@javax.annotation.Generated(value = "org.openapitools.codegen.languages.SpringCodegen", date = "2020-02-13T13:20:17.711576-05:00[America/New_York]")

public class ErrorReport   {
  @JsonProperty("message")
  private String message;

  @JsonProperty("statusCode")
  private Integer statusCode;

  @JsonProperty("causes")
  @Valid
  private List<String> causes = null;

  public ErrorReport message(String message) {
    this.message = message;
    return this;
  }

  /**
   * Get message
   * @return message
  */
  @ApiModelProperty(value = "")


  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }

  public ErrorReport statusCode(Integer statusCode) {
    this.statusCode = statusCode;
    return this;
  }

  /**
   * Get statusCode
   * @return statusCode
  */
  @ApiModelProperty(value = "")


  public Integer getStatusCode() {
    return statusCode;
  }

  public void setStatusCode(Integer statusCode) {
    this.statusCode = statusCode;
  }

  public ErrorReport causes(List<String> causes) {
    this.causes = causes;
    return this;
  }

  public ErrorReport addCausesItem(String causesItem) {
    if (this.causes == null) {
      this.causes = new ArrayList<>();
    }
    this.causes.add(causesItem);
    return this;
  }

  /**
   * Get causes
   * @return causes
  */
  @ApiModelProperty(value = "")


  public List<String> getCauses() {
    return causes;
  }

  public void setCauses(List<String> causes) {
    this.causes = causes;
  }


  @Override
  public boolean equals(java.lang.Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ErrorReport errorReport = (ErrorReport) o;
    return Objects.equals(this.message, errorReport.message) &&
        Objects.equals(this.statusCode, errorReport.statusCode) &&
        Objects.equals(this.causes, errorReport.causes);
  }

  @Override
  public int hashCode() {
    return Objects.hash(message, statusCode, causes);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class ErrorReport {\n");
    
    sb.append("    message: ").append(toIndentedString(message)).append("\n");
    sb.append("    statusCode: ").append(toIndentedString(statusCode)).append("\n");
    sb.append("    causes: ").append(toIndentedString(causes)).append("\n");
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

