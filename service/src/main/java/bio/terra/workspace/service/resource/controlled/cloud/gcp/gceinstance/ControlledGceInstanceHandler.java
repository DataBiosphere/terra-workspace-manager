package bio.terra.workspace.service.resource.controlled.cloud.gcp.gceinstance;

import bio.terra.workspace.db.DbSerDes;
import bio.terra.workspace.db.model.DbResource;
import bio.terra.workspace.service.resource.controlled.model.ControlledResourceFields;
import bio.terra.workspace.service.resource.model.WsmResource;
import bio.terra.workspace.service.resource.model.WsmResourceHandler;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.CharMatcher;
import java.util.UUID;
import javax.annotation.PostConstruct;
import javax.ws.rs.BadRequestException;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Component;

@Component
public class ControlledGceInstanceHandler implements WsmResourceHandler {

  @VisibleForTesting public static final int MAX_INSTANCE_NAME_LENGTH = 63;
  private static ControlledGceInstanceHandler theHandler;

  public static ControlledGceInstanceHandler getHandler() {
    return theHandler;
  }

  @PostConstruct
  public void init() {
    theHandler = this;
  }

  /** {@inheritDoc} */
  @Override
  public WsmResource makeResourceFromDb(DbResource dbResource) {
    ControlledGceInstanceAttributes attributes =
        DbSerDes.fromJson(dbResource.getAttributes(), ControlledGceInstanceAttributes.class);

    return ControlledGceInstanceResource.builder()
        .common(new ControlledResourceFields(dbResource))
        .instanceId(attributes.getInstanceId())
        .zone(attributes.getZone())
        .projectId(attributes.getProjectId())
        .build();
  }

  /**
   * Generate GCE instance name that meets the requirements for a valid instance.
   *
   * <p>The resource name must be 1-63 characters long, and comply with RFC1035. Specifically, the
   * name must be 1-63 characters long and match the regular expression [a-z]([-a-z0-9]*[a-z0-9])?
   * which means the first character must be a lowercase letter, and all following characters must
   * be a dash, lowercase letter, or digit, except the last character, which cannot be a dash.
   * https://cloud.google.com/compute/docs/reference/rest/v1/instances/insert
   */
  @Override
  public String generateCloudName(@Nullable UUID workspaceUuid, String instanceName) {
    // GCE instance name only allows numbers, dash("-"), and lower case letters.
    String generatedName =
        CharMatcher.inRange('0', '9')
            .or(CharMatcher.inRange('a', 'z'))
            .or(CharMatcher.is('-'))
            .retainFrom(instanceName.toLowerCase());
    // The name must start with a letter.
    generatedName =
        CharMatcher.inRange('0', '9').or(CharMatcher.is('-')).trimLeadingFrom(generatedName);
    // Truncate before trimming characters to ensure the name does not end with dash("-").
    generatedName = StringUtils.truncate(generatedName, MAX_INSTANCE_NAME_LENGTH);
    // The name cannot end with dash("-").
    generatedName = CharMatcher.is('-').trimTrailingFrom(generatedName);

    if (generatedName.length() == 0) {
      throw new BadRequestException(
          String.format(
              "Cannot generate a valid GCE instance name from %s, it must contain"
                  + " alphanumerical characters.",
              instanceName));
    }
    return generatedName;
  }
}
