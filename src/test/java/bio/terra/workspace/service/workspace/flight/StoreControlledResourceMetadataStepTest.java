package bio.terra.workspace.service.workspace.flight;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
import bio.terra.workspace.service.job.JobMapKeys;
import bio.terra.workspace.service.resource.controlled.ControlledResourceDbModel;
import bio.terra.workspace.service.resource.controlled.gcp.ControlledGcsBucketResource;
import com.google.common.collect.ImmutableList;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;

public class StoreControlledResourceMetadataStepTest extends BaseUnitTest {

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
  private static final ControlledGcsBucketResource BUCKET_RESOURCE =
      new ControlledGcsBucketResource(
          "The Red Bucket",
          "A bucket that's red.",
          RESOURCE_ID,
          WORKSPACE_ID,
          true,
          null,
          OWNER_EMAIL,
          GOOGLE_BUCKET_CREATION_PARAMETERS);

  @Mock private ControlledResourceDao mockControlledResourceDao;
  @Mock private FlightContext mockFlightContext;
  @Captor private ArgumentCaptor<ControlledResourceDbModel> dbModelArgumentCaptor;
  private StoreControlledResourceMetadataStep storeGoogleBucketMetadataStep;

  @BeforeEach
  public void setup() {
    storeGoogleBucketMetadataStep =
        new StoreControlledResourceMetadataStep(mockControlledResourceDao);

    final FlightMap inputFlightMap = new FlightMap();
    inputFlightMap.put(JobMapKeys.REQUEST.getKeyName(), BUCKET_RESOURCE);
    inputFlightMap.put(WorkspaceFlightMapKeys.WORKSPACE_ID, WORKSPACE_ID);
    inputFlightMap.put(WorkspaceFlightMapKeys.CONTROLLED_RESOURCE_ID, RESOURCE_ID);
    inputFlightMap.put(WorkspaceFlightMapKeys.CONTROLLED_RESOURCE_OWNER_EMAIL, OWNER_EMAIL);
    inputFlightMap.makeImmutable();

    doReturn(inputFlightMap).when(mockFlightContext).getInputParameters();
  }

  @Test
  public void testEntersInfo() throws InterruptedException, RetryException {
    final StepResult result = storeGoogleBucketMetadataStep.doStep(mockFlightContext);
    assertThat(result, equalTo(StepResult.getStepResultSuccess()));
    verify(mockControlledResourceDao).createControlledResource(dbModelArgumentCaptor.capture());

    final ControlledResourceDbModel metadata = dbModelArgumentCaptor.getValue();
    assertThat(metadata.getWorkspaceId(), equalTo(WORKSPACE_ID));
    assertThat(metadata.getResourceId(), equalTo(RESOURCE_ID));
    assertThat(metadata.getOwner().get(), equalTo(OWNER_EMAIL));
    assertTrue(metadata.isVisible());
    assertTrue(metadata.getAssociatedApp().isEmpty());
    assertThat(metadata.getAttributes(), equalTo("{\"bucketName\":\"my-bucket\"}"));
  }
}
