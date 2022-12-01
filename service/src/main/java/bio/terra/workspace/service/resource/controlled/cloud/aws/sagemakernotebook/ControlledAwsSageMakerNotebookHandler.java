package bio.terra.workspace.service.resource.controlled.cloud.aws.sagemakernotebook;

import bio.terra.workspace.common.exception.FeatureNotSupportedException;
import bio.terra.workspace.db.DbSerDes;
import bio.terra.workspace.db.model.DbResource;
import bio.terra.workspace.service.resource.controlled.model.ControlledResourceFields;
import bio.terra.workspace.service.resource.model.WsmResource;
import bio.terra.workspace.service.resource.model.WsmResourceHandler;
import java.util.UUID;
import javax.annotation.Nullable;

public class ControlledAwsSageMakerNotebookHandler implements WsmResourceHandler {

  private static final int MAX_INSTANCE_NAME_LENGTH = 63;
  private static ControlledAwsSageMakerNotebookHandler theHandler;

  public static ControlledAwsSageMakerNotebookHandler getHandler() {
    if (theHandler == null) {
      theHandler = new ControlledAwsSageMakerNotebookHandler();
    }
    return theHandler;
  }

  /** {@inheritDoc} */
  @Override
  public WsmResource makeResourceFromDb(DbResource dbResource) {
    ControlledAwsSageMakerNotebookAttributes attributes =
        DbSerDes.fromJson(
            dbResource.getAttributes(), ControlledAwsSageMakerNotebookAttributes.class);

    return ControlledAwsSageMakerNotebookResource.builder()
        .common(new ControlledResourceFields(dbResource))
        .instanceId(attributes.getInstanceId())
        .region(attributes.getRegion())
        .instanceType(attributes.getInstanceId())
        .defaultBucket(attributes.getDefaultBucket())
        .build();
  }

  // Naming rules:
  // https://docs.aws.amazon.com/sagemaker/latest/APIReference/API_CreateNotebookInstance.html#sagemaker-CreateNotebookInstance-request-NotebookInstanceName
  public String generateCloudName(@Nullable UUID workspaceUuid, String resourceName) {
    throw new FeatureNotSupportedException("This generate cloud name feature is not implement yet");
  }
}
