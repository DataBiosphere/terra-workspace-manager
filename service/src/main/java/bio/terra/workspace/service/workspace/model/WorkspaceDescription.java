package bio.terra.workspace.service.workspace.model;

import bio.terra.policy.model.TpsPaoGetResult;
import bio.terra.workspace.service.iam.model.WsmIamRole;
import java.time.OffsetDateTime;
import java.util.List;
import javax.annotation.Nullable;

/**
 * The WorkspaceDescription contains the full set of information about a workspace that WSM holds in
 * its metadata. It is used for retrieving a single workspace or getting the list of all workspaces.
 *
 * @param workspace
 * @param highestRole
 * @param missingAuthDomains
 * @param lastUpdatedByEmail
 * @param lastUpdatedByDate
 * @param awsCloudContext
 * @param azureCloudContext
 * @param gcpCloudContext
 * @param workspacePolicies
 */
public record WorkspaceDescription(
    Workspace workspace,
    WsmIamRole highestRole,
    @Nullable List<String> missingAuthDomains,
    String lastUpdatedByEmail,
    OffsetDateTime lastUpdatedByDate,
    @Nullable AwsCloudContext awsCloudContext,
    @Nullable AzureCloudContext azureCloudContext,
    @Nullable GcpCloudContext gcpCloudContext,
    @Nullable TpsPaoGetResult workspacePolicies) {}
