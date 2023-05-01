package bio.terra.workspace.service.workspace.model;

import bio.terra.common.exception.BadRequestException;
import bio.terra.workspace.common.exception.InternalLogicException;
import bio.terra.workspace.generated.model.ApiCloudPlatform;
import bio.terra.workspace.service.resource.model.WsmResourceHandler;
import bio.terra.workspace.service.workspace.AwsCloudContextService;
import bio.terra.workspace.service.workspace.AzureCloudContextService;
import bio.terra.workspace.service.workspace.CloudContextService;
import bio.terra.workspace.service.workspace.GcpCloudContextService;
import org.apache.commons.lang3.SerializationException;
import org.apache.commons.lang3.StringUtils;

import java.util.function.Supplier;

public enum CloudPlatform {
  GCP("GCP", ApiCloudPlatform.GCP, "gcp", GcpCloudContextService::getTheService),
  AZURE("AZURE", ApiCloudPlatform.AZURE, "azure", AzureCloudContextService::getTheService),
  AWS("AWS", ApiCloudPlatform.AWS, "aws", AwsCloudContextService::getTheService),
  /** The resource does not have to be strongly associated with one cloud platform. */
  ANY("ANY", null, null, null);

  private final String dbString;
  private final ApiCloudPlatform apiCloudPlatform;
  private final String tpsString;
  private final Supplier<CloudContextService> cloudContextServiceSupplier;

  CloudPlatform(
    String dbString,
    ApiCloudPlatform apiCloudPlatform,
    String tpsString,
    Supplier<CloudContextService> cloudContextServiceSupplier) {
    this.dbString = dbString;
    this.apiCloudPlatform = apiCloudPlatform;
    this.tpsString = tpsString;
    this.cloudContextServiceSupplier = cloudContextServiceSupplier;
  }

  public String toSql() {
    return dbString;
  }

  public String toTps() {
    if (tpsString == null) {
      throw new InternalLogicException(
          String.format("Cloud platform %s does not have a TPS equivalent", dbString));
    }
    return tpsString;
  }

  public ApiCloudPlatform toApiModel() {
    return apiCloudPlatform;
  }

  public static CloudPlatform fromApiCloudPlatform(ApiCloudPlatform apiCloudPlatform) {
    for (CloudPlatform value : values()) {
      if (StringUtils.equals(value.dbString, apiCloudPlatform.name())) {
        return value;
      }
    }
    throw new BadRequestException(
        String.format("Unknown Api cloud platform %s", apiCloudPlatform.name()));
  }

  public static CloudPlatform fromSql(String dbString) {
    for (CloudPlatform value : values()) {
      if (StringUtils.equals(value.dbString, dbString)) {
        return value;
      }
    }
    throw new SerializationException(
        "Deserialization failed: no matching cloud platform for " + dbString);
  }

  public CloudContextService getCloudContextService() {
    return cloudContextServiceSupplier.get();
  }
}
