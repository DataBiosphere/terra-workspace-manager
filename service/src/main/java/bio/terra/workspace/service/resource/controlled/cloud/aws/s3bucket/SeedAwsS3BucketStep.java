package bio.terra.workspace.service.resource.controlled.cloud.aws.s3bucket;

import static bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys.AWS_CLOUD_CONTEXT;

import bio.terra.common.iam.SamUser;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.app.configuration.external.AwsConfiguration;
import bio.terra.workspace.common.utils.AwsUtils;
import bio.terra.workspace.common.utils.MultiCloudUtils;
import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
import bio.terra.workspace.service.workspace.model.AwsCloudContext;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sts.model.Credentials;

public class SeedAwsS3BucketStep implements Step {

  private final List<AwsConfiguration.AwsS3BucketSeedFile> seedFiles;
  private final ControlledAwsS3BucketResource resource;

  public SeedAwsS3BucketStep(
      List<AwsConfiguration.AwsS3BucketSeedFile> seedFiles,
      ControlledAwsS3BucketResource resource) {
    this.seedFiles = seedFiles;
    this.resource = resource;
  }

  private String getKey(String path) {
    return String.format("%s/%s", resource.getPrefix(), path);
  }

  @Override
  public StepResult doStep(FlightContext flightContext)
      throws InterruptedException, RetryException {
    FlightMap inputParameters = flightContext.getInputParameters();

    AwsCloudContext awsCloudContext =
        flightContext.getWorkingMap().get(AWS_CLOUD_CONTEXT, AwsCloudContext.class);
    Credentials awsCredentials = MultiCloudUtils.assumeAwsServiceRoleFromGcp(awsCloudContext);
    SamUser samUser = inputParameters.get(WorkspaceFlightMapKeys.SAM_USER, SamUser.class);

    for (AwsConfiguration.AwsS3BucketSeedFile seedFile : seedFiles) {
      String content =
          new String(
              Base64.getDecoder().decode(seedFile.getContent().getBytes(StandardCharsets.UTF_8)));
      String key = getKey(seedFile.getPath());
      AwsUtils.putObject(
          awsCredentials,
          resource.getWorkspaceId(),
          samUser,
          Region.of(resource.getRegion()),
          resource.getS3BucketName(),
          key,
          content);
    }
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
    AwsCloudContext awsCloudContext =
        flightContext.getWorkingMap().get(AWS_CLOUD_CONTEXT, AwsCloudContext.class);
    Credentials awsCredentials = MultiCloudUtils.assumeAwsServiceRoleFromGcp(awsCloudContext);

    List<String> seedFileKeys =
        seedFiles.stream().map(seedFile -> getKey(seedFile.getPath())).toList();

    AwsUtils.deleteObjects(
        awsCredentials, Region.of(resource.getRegion()), resource.getS3BucketName(), seedFileKeys);
    return StepResult.getStepResultSuccess();
  }
}
