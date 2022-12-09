package bio.terra.workspace.service.resource.controlled.cloud.aws.storagebucket;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.app.configuration.external.AwsConfiguration;
import bio.terra.workspace.common.utils.AwsUtils;
import bio.terra.workspace.common.utils.MultiCloudUtils;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import bio.terra.workspace.service.workspace.model.AwsCloudContext;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.securitytoken.model.Credentials;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

public class SeedAwsBucketStep implements Step {

  private final List<AwsConfiguration.AwsBucketSeedFile> seedFiles;
  private final ControlledAwsBucketResource resource;

  public SeedAwsBucketStep(
      List<AwsConfiguration.AwsBucketSeedFile> seedFiles, ControlledAwsBucketResource resource) {
    this.seedFiles = seedFiles;
    this.resource = resource;
  }

  private String getKey(String path) {
    return String.format("%s/%s", resource.getPrefix(), path);
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    final String awsCloudContextString =
        flightContext
            .getWorkingMap()
            .get(WorkspaceFlightMapKeys.ControlledResourceKeys.AWS_CLOUD_CONTEXT, String.class);

    final AwsCloudContext awsCloudContext = AwsCloudContext.deserialize(awsCloudContextString);
    final Credentials awsCredentials = MultiCloudUtils.assumeAwsServiceRoleFromGcp(awsCloudContext);

    for (AwsConfiguration.AwsBucketSeedFile seedFile : seedFiles) {
      String content =
          new String(
              Base64.getDecoder().decode(seedFile.getContent().getBytes(StandardCharsets.UTF_8)));
      String key = getKey(seedFile.getPath());
      AwsUtils.putObject(
          awsCredentials,
          Regions.fromName(resource.getRegion()),
          resource.getS3BucketName(),
          key,
          content);
    }

    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    final String awsCloudContextString =
        flightContext
            .getWorkingMap()
            .get(WorkspaceFlightMapKeys.ControlledResourceKeys.AWS_CLOUD_CONTEXT, String.class);

    final AwsCloudContext awsCloudContext = AwsCloudContext.deserialize(awsCloudContextString);
    final Credentials awsCredentials = MultiCloudUtils.assumeAwsServiceRoleFromGcp(awsCloudContext);

    for (AwsConfiguration.AwsBucketSeedFile seedFile : seedFiles) {
      String key = getKey(seedFile.getPath());
      AwsUtils.deleteObject(
          awsCredentials, Regions.fromName(resource.getRegion()), resource.getS3BucketName(), key);
    }
    return StepResult.getStepResultSuccess();
  }
}
