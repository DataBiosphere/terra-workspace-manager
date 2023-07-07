// package bio.terra.workspace.service.resource.controlled.cloud.gcp.dataproccluster;
//
// import static
// bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys.CREATE_DATAPROC_CLUSTER_PARAMETERS;
//
// import bio.terra.cloudres.google.storage.BucketCow;
// import bio.terra.cloudres.google.storage.StorageCow;
// import bio.terra.stairway.FlightContext;
// import bio.terra.stairway.FlightMap;
// import bio.terra.stairway.Step;
// import bio.terra.stairway.StepResult;
// import bio.terra.stairway.StepStatus;
// import bio.terra.stairway.exception.RetryException;
// import bio.terra.workspace.generated.model.ApiGcpDataprocClusterCreationParameters;
// import bio.terra.workspace.service.crl.CrlService;
// import bio.terra.workspace.service.iam.model.SamConstants.SamControlledResourceActions;
// import bio.terra.workspace.service.resource.controlled.ControlledResourceMetadataManager;
// import bio.terra.workspace.service.resource.controlled.ControlledResourceService;
// import
// bio.terra.workspace.service.resource.controlled.cloud.gcp.gcsbucket.ControlledGcsBucketResource;
// import bio.terra.workspace.service.resource.controlled.cloud.gcp.gcsbucket.GcsApiConversions;
// import bio.terra.workspace.service.resource.controlled.model.ControlledResource;
// import bio.terra.workspace.service.workspace.GcpCloudContextService;
// import bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys;
// import
// bio.terra.workspace.service.workspace.flight.WorkspaceFlightMapKeys.ControlledResourceKeys;
// import com.google.cloud.storage.BucketInfo;
// import java.util.UUID;
// import javax.ws.rs.BadRequestException;
// import org.slf4j.Logger;
// import org.slf4j.LoggerFactory;
//
// /**
//  * For dataproc cluster creation, we need to provide a staging and temp bucket. This steps
// validates that the staging and temp bucket
//  *
//  * <p>Preconditions: Staging and temp buckets are valid controlled gcs bucket resources.
//  *
//  * <p>Post-conditions: Staging and temp bucket cloud ids are stored in the flight map in
//  */
// public class RetrieveStagingAndTempBucketStep implements Step {
//   private static final Logger logger =
//       LoggerFactory.getLogger(RetrieveStagingAndTempBucketStep.class);
//   private final ControlledResourceMetadataManager controlledResourceMetadataManager;
//   private final ControlledDataprocClusterResource resource;
//
//   public RetrieveStagingAndTempBucketStep(
//       ControlledResourceMetadataManager controlledResourceMetadataManager,
//       ControlledDataprocClusterResource resource
//       ) {
//     this.controlledResourceMetadataManager = controlledResourceMetadataManager;
//     this.resource = resource;
//   }
//
//   @Override
//   public StepResult doStep(FlightContext flightContext)
//       throws InterruptedException, RetryException {
//
//     ApiGcpDataprocClusterCreationParameters creationParameters =
//         flightContext
//             .getInputParameters()
//             .get(CREATE_DATAPROC_CLUSTER_PARAMETERS,
// ApiGcpDataprocClusterCreationParameters.class);
//
//     UUID workspaceId = resource.getWorkspaceId();
//     UUID stagingBucketResourceId = creationParameters.getConfigBucket();
//     UUID tempBucketResourceId = creationParameters.getTempBucket();
//
//     ControlledGcsBucketResource stagingBucketResource =
//         controlledResourceMetadataManager
//             .validateControlledResourceAndAction(
//                 userRequest, workspaceId, stagingBucketResourceId,
// SamControlledResourceActions.READ_ACTION)
//
//     return StepResult.getStepResultSuccess();
//   }
//
//   // Nothing to undo here
//   @Override
//   public StepResult undoStep(FlightContext flightContext) throws InterruptedException {
//     return StepResult.getStepResultSuccess();
//   }
// }
