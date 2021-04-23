package scripts.testscripts;

import bio.terra.testrunner.runner.config.TestUserSpecification;
import bio.terra.workspace.api.ControlledGcpResourceApi;
import bio.terra.workspace.api.WorkspaceApi;
import bio.terra.workspace.client.ApiException;
import bio.terra.workspace.model.AccessScope;
import bio.terra.workspace.model.CloningInstructionsEnum;
import bio.terra.workspace.model.ControlledResourceCommonFields;
import bio.terra.workspace.model.ControlledResourceIamRole;
import bio.terra.workspace.model.CreateControlledGcpAiNotebookInstanceRequestBody;
import bio.terra.workspace.model.CreatedControlledGcpAiNotebookInstanceResult;
import bio.terra.workspace.model.CreatedControlledGcpGcsBucket;
import bio.terra.workspace.model.GcpAiNotebookInstanceCreationParameters;
import bio.terra.workspace.model.GcpAiNotebookInstanceResource;
import bio.terra.workspace.model.GcpAiNotebookInstanceVmImage;
import bio.terra.workspace.model.GcpGcsBucketResource;
import bio.terra.workspace.model.GrantRoleRequestBody;
import bio.terra.workspace.model.IamRole;
import bio.terra.workspace.model.JobControl;
import bio.terra.workspace.model.JobReport;
import bio.terra.workspace.model.ManagedBy;
import bio.terra.workspace.model.PrivateResourceIamRoles;
import bio.terra.workspace.model.PrivateResourceUser;
import com.google.api.client.http.HttpStatusCodes;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Bucket;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageException;
import org.apache.commons.lang3.RandomStringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scripts.utils.ClientTestUtils;
import scripts.utils.CloudContextMaker;
import scripts.utils.ResourceMaker;
import scripts.utils.WorkspaceAllocateTestScriptBase;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class PrivateControlledGcsAiNotebookInstanceLifecycle extends WorkspaceAllocateTestScriptBase {
    private static final Logger logger =
            LoggerFactory.getLogger(PrivateControlledGcsAiNotebookInstanceLifecycle.class);


    private TestUserSpecification resourceUser;
    private TestUserSpecification otherWorkspaceUser;
    private String instanceId;

    @Override
    protected void doSetup(List<TestUserSpecification> testUsers, WorkspaceApi workspaceApi)
            throws Exception {
        super.doSetup(testUsers, workspaceApi);
        // Note the 0th user is the owner of the workspace, set up in the super class and passed as the 'testUser'.
        assertThat(
                "There must be at least three test users defined for this test.",
                testUsers != null && testUsers.size() > 1);
        this.resourceUser = testUsers.get(1);
        this.otherWorkspaceUser = testUsers.get(2);
        assertNotEquals(resourceUser.userEmail, otherWorkspaceUser.userEmail);
        this.instanceId = RandomStringUtils.randomAlphabetic(8).toLowerCase();
    }


    @Override
    protected void doUserJourney(TestUserSpecification testUser, WorkspaceApi workspaceApi) throws Exception {
        workspaceApi.grantRole(
                new GrantRoleRequestBody().memberEmail(resourceUser.userEmail),
                getWorkspaceId(),
                IamRole.WRITER);
        logger.info(
                "Added {} as a writer to workspace {}", resourceUser.userEmail, getWorkspaceId());
        workspaceApi.grantRole(
                new GrantRoleRequestBody().memberEmail(otherWorkspaceUser.userEmail),
                getWorkspaceId(),
                IamRole.WRITER);
        logger.info(
                "Added {} as a writer to workspace {}", otherWorkspaceUser.userEmail, getWorkspaceId());

        String projectId = CloudContextMaker.createGcpCloudContext(getWorkspaceId(), workspaceApi);
        logger.info("Created project {}", projectId);

        ControlledGcpResourceApi resourceUserApi = ClientTestUtils.getControlledGcpResourceClient(resourceUser, server);
        CreatedControlledGcpAiNotebookInstanceResult creationResult = createPrivateNotebook(resourceUser, resourceUserApi);
        logger.info("Initiated notebook instance creation. Polling...");
        creationResult = pollUntilComplete(creationResult, resourceUserApi);
        assertEquals(JobReport.StatusEnum.SUCCEEDED, creationResult.getJobReport().getStatus());
        logger.info("Creation succeeded for instanceId {}", creationResult.getAiNotebookInstance().getAttributes().getInstanceId());

        UUID resourceId = creationResult.getAiNotebookInstance().getMetadata().getResourceId();

        GcpAiNotebookInstanceResource resource = resourceUserApi.getAiNotebookInstance(getWorkspaceId(), resourceId);
        assertEquals(resource.getAttributes().getInstanceId(), creationResult.getAiNotebookInstance().getAttributes().getInstanceId());

        // TODO(PF-626): Test cloud permissions.
        // TODO(PF-???): Test notebook instance deletion. DO NOT SUBMIT file ticket.
    }

    private CreatedControlledGcpAiNotebookInstanceResult createPrivateNotebook(TestUserSpecification user, ControlledGcpResourceApi resourceApi) throws ApiException, InterruptedException {
        // Fill out the minimum required fields to arbitrary values.
        var creationParameters = new GcpAiNotebookInstanceCreationParameters()
                .instanceId("default-instance-id")
                .location("us-east1-b")
                .machineType("e2-standard-2")
                .vmImage(
                        new GcpAiNotebookInstanceVmImage()
                                .projectId("deeplearning-platform-release")
                                .imageFamily("r-latest-cpu-experimental"));

        PrivateResourceIamRoles privateIamRoles = new PrivateResourceIamRoles();
        privateIamRoles.add(ControlledResourceIamRole.EDITOR);
        privateIamRoles.add(ControlledResourceIamRole.WRITER);
        var commonParameters =
                new ControlledResourceCommonFields()
                        .name(RandomStringUtils.randomAlphabetic(6))
                        .cloningInstructions(CloningInstructionsEnum.NOTHING)
                        .accessScope(AccessScope.PRIVATE_ACCESS)
                        .managedBy(ManagedBy.USER)
                        .privateResourceUser(new PrivateResourceUser().userName(user.userEmail)
                        .privateResourceIamRoles(privateIamRoles));

        var body =
                new CreateControlledGcpAiNotebookInstanceRequestBody()
                        .aiNotebookInstance(creationParameters)
                        .common(commonParameters)
                        .jobControl(new JobControl().id(UUID.randomUUID().toString()));

        return resourceApi.createAiNotebookInstance(body, getWorkspaceId());
    }

    /** Polls job for the notebook instance creation until it finishes or we give up. */
    private CreatedControlledGcpAiNotebookInstanceResult pollUntilComplete(CreatedControlledGcpAiNotebookInstanceResult result, ControlledGcpResourceApi resourceApi) throws ApiException, InterruptedException {
        String creationJobId = result.getJobReport().getId();
        Instant deadline = Instant.now().plus(Duration.ofMinutes(12));
        while (ClientTestUtils.jobIsRunning(result.getJobReport())) {
            if (Instant.now().isAfter(deadline)) {
                throw new InterruptedException("Timed out waiting for notebok instance creation to finish.");
            }
            TimeUnit.SECONDS.sleep(20);
            result = resourceApi.getCreateAiNotebookInstanceResult(getWorkspaceId(), creationJobId);
        }
        return result;
    }
}
