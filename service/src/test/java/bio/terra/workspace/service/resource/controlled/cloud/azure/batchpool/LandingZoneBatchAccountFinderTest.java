package bio.terra.workspace.service.resource.controlled.cloud.azure.batchpool;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import bio.terra.common.iam.BearerToken;
import bio.terra.workspace.amalgam.landingzone.azure.LandingZoneApiDispatch;
import bio.terra.workspace.amalgam.landingzone.azure.LandingZoneNotFoundException;
import bio.terra.workspace.common.BaseAzureConnectedTest;
import bio.terra.workspace.common.fixtures.ControlledResourceFixtures;
import bio.terra.workspace.common.fixtures.WorkspaceFixtures;
import bio.terra.workspace.generated.model.ApiAzureLandingZoneDeployedResource;
import bio.terra.workspace.service.workspace.WorkspaceService;
import com.azure.resourcemanager.batch.models.DeploymentConfiguration;
import com.azure.resourcemanager.batch.models.ImageReference;
import com.azure.resourcemanager.batch.models.VirtualMachineConfiguration;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;

public class LandingZoneBatchAccountFinderTest extends BaseAzureConnectedTest {
  private static final UUID LANDING_ZONE_ID = UUID.randomUUID();
  private static final BearerToken TOKEN = new BearerToken("fakeToken");
  private static final DeploymentConfiguration DEPLOYMENT_CONFIGURATION =
      new DeploymentConfiguration()
          .withVirtualMachineConfiguration(
              new VirtualMachineConfiguration()
                  .withImageReference(
                      new ImageReference()
                          .withOffer("ubuntuserver")
                          .withSku("18.04-lts")
                          .withPublisher("canonical")));

  @Mock LandingZoneApiDispatch mockLandingZoneApiDispatch;
  @Autowired WorkspaceService workspaceService;

  LandingZoneBatchAccountFinder landingZoneBatchAccountFinder;

  @BeforeEach
  public void setup() {
    landingZoneBatchAccountFinder =
        new LandingZoneBatchAccountFinder(mockLandingZoneApiDispatch, workspaceService);
  }

  @Test
  public void sharedLzBatchAccountFound() {
    ControlledAzureBatchPoolResource resource = buildDefaultResource();

    when(mockLandingZoneApiDispatch.getLandingZoneId(
            any(), argThat(a -> a.getWorkspaceId().equals(resource.getWorkspaceId()))))
        .thenReturn(LANDING_ZONE_ID);
    var expectedResource =
        Optional.of(
            buildLandingZoneDeployedResource(
                "sharedBatchAccountId", "resourceName", "resourceType", "region"));
    when(mockLandingZoneApiDispatch.getSharedBatchAccount(any(), eq(LANDING_ZONE_ID)))
        .thenReturn(expectedResource);

    Optional<ApiAzureLandingZoneDeployedResource> lzResource =
        landingZoneBatchAccountFinder.find("fakeToken", resource);

    assertTrue(lzResource.isPresent());
    assertThat(lzResource, equalTo(expectedResource));
    verify(mockLandingZoneApiDispatch, times(1))
        .getLandingZoneId(
            eq(TOKEN), argThat(a -> a.getWorkspaceId().equals(resource.getWorkspaceId())));
    verify(mockLandingZoneApiDispatch, times(1))
        .getSharedBatchAccount(eq(TOKEN), eq(LANDING_ZONE_ID));
  }

  @Test
  public void sharedLzBatchAccountNotFound() {
    ControlledAzureBatchPoolResource resource = buildDefaultResource();

    when(mockLandingZoneApiDispatch.getLandingZoneId(
            any(), argThat(a -> a.getWorkspaceId().equals(resource.getWorkspaceId()))))
        .thenReturn(LANDING_ZONE_ID);
    when(mockLandingZoneApiDispatch.getSharedBatchAccount(any(), eq(LANDING_ZONE_ID)))
        .thenReturn(Optional.empty());

    Optional<ApiAzureLandingZoneDeployedResource> lzResource =
        landingZoneBatchAccountFinder.find("fakeToken", resource);

    assertTrue(lzResource.isEmpty());
    verify(mockLandingZoneApiDispatch, times(1))
        .getLandingZoneId(
            eq(TOKEN), argThat(a -> a.getWorkspaceId().equals(resource.getWorkspaceId())));
    verify(mockLandingZoneApiDispatch, times(1))
        .getSharedBatchAccount(eq(TOKEN), eq(LANDING_ZONE_ID));
  }

  @ParameterizedTest
  @MethodSource("exceptionSupplier")
  public void sharedLzBatchAccountNotFoundFailure(Class<Exception> exceptionClass) {
    ControlledAzureBatchPoolResource resource = buildDefaultResource();

    when(mockLandingZoneApiDispatch.getLandingZoneId(any(), any())).thenThrow(exceptionClass);

    var batchAccount = landingZoneBatchAccountFinder.find("fakeToken", resource);
    assertTrue(batchAccount.isEmpty());
  }

  private ApiAzureLandingZoneDeployedResource buildLandingZoneDeployedResource(
      String resourceId, String resourceName, String resourceType, String region) {
    return new ApiAzureLandingZoneDeployedResource()
        .resourceId(resourceId)
        .resourceName(resourceName)
        .resourceType(resourceType)
        .region(region);
  }

  private ControlledAzureBatchPoolResource buildDefaultResource() {
    return ControlledResourceFixtures.getAzureBatchPoolResourceBuilder(
            UUID.randomUUID(),
            "displayName",
            "Standard_D2s_v3",
            DEPLOYMENT_CONFIGURATION,
            "description")
        .build();
  }

  static Stream<Arguments> exceptionSupplier() {
    return Stream.of(
        Arguments.of(LandingZoneNotFoundException.class));
  }
}
