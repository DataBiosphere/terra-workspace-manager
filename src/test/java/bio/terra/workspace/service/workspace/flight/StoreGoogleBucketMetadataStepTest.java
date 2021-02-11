package bio.terra.workspace.service.workspace.flight;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.exception.RetryException;
import bio.terra.workspace.common.BaseUnitTest;
import bio.terra.workspace.db.ControlledResourceDao;
import bio.terra.workspace.generated.model.GoogleBucketCreationParameters;
import bio.terra.workspace.generated.model.GoogleBucketDefaultStorageClass;
import bio.terra.workspace.generated.model.GoogleBucketLifecycle;
import bio.terra.workspace.generated.model.GoogleBucketLifecycleRule;
import bio.terra.workspace.generated.model.GoogleBucketLifecycleRuleAction;
import bio.terra.workspace.generated.model.GoogleBucketLifecycleRuleActionType;
import bio.terra.workspace.generated.model.GoogleBucketLifecycleRuleCondition;
import bio.terra.workspace.service.controlledresource.model.ControlledResourceMetadata;
import com.google.common.collect.ImmutableList;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;

public class StoreGoogleBucketMetadataStepTest extends BaseUnitTest {

  private static final UUID WORKSPACE_ID = UUID.fromString("00000000-fcf0-4981-bb96-6b8dd634e7c0");
  private static final UUID RESOURCE_ID = UUID.fromString("11111111-fcf0-4981-bb96-6b8dd634e7c0");
  private static final GoogleBucketCreationParameters GOOGLE_BUCKET_CREATION_PARAMETERS =
      new GoogleBucketCreationParameters()
          .name("my-bucket")
          .location("US-CENTRAL1")
          .defaultStorageClass(GoogleBucketDefaultStorageClass.STANDARD)
          .lifecycle(
              new GoogleBucketLifecycle()
                  .rules(
                      ImmutableList.of(
                          new GoogleBucketLifecycleRule()
                              .action(
                                  new GoogleBucketLifecycleRuleAction()
                                      .storageClass(GoogleBucketDefaultStorageClass.COLDLINE)
                                      .type(GoogleBucketLifecycleRuleActionType.DELETE))
                              .condition(
                                  new GoogleBucketLifecycleRuleCondition()
                                      .age(64)
                                      .isLive(true)
                                      .matchesStorageClass(
                                          ImmutableList.of(
                                              GoogleBucketDefaultStorageClass.ARCHIVE))))));
  public static final String OWNER_EMAIL = "jay@all-the-bits-thats-fit-to-blit.dev";
  @Mock private ControlledResourceDao mockControlledResourceDao;
  @Mock private FlightContext mockFlightContext;
  @Captor private ArgumentCaptor<ControlledResourceMetadata> controlledResourceMetadataCaptor;
  private StoreGoogleBucketMetadataStep storeGoogleBucketMetadataStep;

  @BeforeEach
  public void setup() {
    storeGoogleBucketMetadataStep = new StoreGoogleBucketMetadataStep(mockControlledResourceDao);

    final FlightMap inputFlightMap = new FlightMap();
    inputFlightMap.put(WorkspaceFlightMapKeys.WORKSPACE_ID, WORKSPACE_ID);
    inputFlightMap.put(WorkspaceFlightMapKeys.CONTROLLED_RESOURCE_ID, RESOURCE_ID);
    inputFlightMap.put(WorkspaceFlightMapKeys.CONTROLLED_RESOURCE_OWNER_EMAIL, OWNER_EMAIL);
    inputFlightMap.put(
        GoogleBucketFlightMapKeys.BUCKET_CREATION_PARAMS.getKey(),
        GOOGLE_BUCKET_CREATION_PARAMETERS);
    inputFlightMap.makeImmutable();

    doReturn(inputFlightMap).when(mockFlightContext).getInputParameters();
  }

  @Test
  public void testEntersInfo() throws InterruptedException, RetryException {
    final StepResult result = storeGoogleBucketMetadataStep.doStep(mockFlightContext);
    assertThat(result, equalTo(StepResult.getStepResultSuccess()));
    verify(mockControlledResourceDao)
        .createControlledResource(controlledResourceMetadataCaptor.capture());

    final ControlledResourceMetadata metadata = controlledResourceMetadataCaptor.getValue();
    assertThat(metadata.workspaceId(), equalTo(WORKSPACE_ID));
    assertThat(metadata.resourceId(), equalTo(RESOURCE_ID));
    assertThat(metadata.owner(), equalTo(OWNER_EMAIL));
    assertThat(metadata.associatedApp(), equalTo(null));

  }
}
