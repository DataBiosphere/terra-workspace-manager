package bio.terra.workspace.common.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import bio.terra.aws.resource.discovery.Metadata;
import bio.terra.workspace.service.resource.model.WsmResourceState;
import bio.terra.workspace.service.spendprofile.SpendProfileId;
import bio.terra.workspace.service.workspace.model.AwsCloudContextFields;
import bio.terra.workspace.service.workspace.model.CloudContextCommonFields;
import org.springframework.stereotype.Component;

// Test Utils for AWS (unit & connected) tests
@Component
public class AwsTestUtils {

  public static void assertAwsCloudContextFields(
      Metadata envMetadata, AwsCloudContextFields contextFields) {
    assertNotNull(contextFields);
    assertEquals(envMetadata.getMajorVersion(), contextFields.getMajorVersion());
    assertEquals(envMetadata.getOrganizationId(), contextFields.getOrganizationId());
    assertEquals(envMetadata.getAccountId(), contextFields.getAccountId());
    assertEquals(envMetadata.getTenantAlias(), contextFields.getTenantAlias());
    assertEquals(envMetadata.getEnvironmentAlias(), contextFields.getEnvironmentAlias());
  }

  public static void assertCloudContextCommonFields(
      CloudContextCommonFields commonFields,
      SpendProfileId spendProfileId,
      WsmResourceState state,
      String flightId) {
    assertNotNull(commonFields);
    assertEquals(spendProfileId, commonFields.spendProfileId());
    assertEquals(state, commonFields.state());
    assertEquals(flightId, commonFields.flightId());
    assertNull(commonFields.error());
  }
}
