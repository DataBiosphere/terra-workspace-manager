package bio.terra.workspace.service.resource;

import bio.terra.workspace.app.controller.ResourceController;
import bio.terra.workspace.common.BaseUnitTest;
import bio.terra.workspace.generated.model.ApiControlledResourceMetadata;
import bio.terra.workspace.generated.model.ApiDataRepoSnapshotAttributes;
import bio.terra.workspace.generated.model.ApiGcpBigQueryDatasetAttributes;
import bio.terra.workspace.generated.model.ApiGcpGcsBucketAttributes;
import bio.terra.workspace.generated.model.ApiPrivateResourceUser;
import bio.terra.workspace.generated.model.ApiResourceAttributesUnion;
import bio.terra.workspace.generated.model.ApiResourceDescription;
import bio.terra.workspace.generated.model.ApiResourceMetadata;
import bio.terra.workspace.service.resource.controlled.AccessScopeType;
import bio.terra.workspace.service.resource.controlled.ControlledGcsBucketResource;
import bio.terra.workspace.service.resource.controlled.ManagedByType;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.resource.referenced.ReferencedBigQueryDatasetResource;
import bio.terra.workspace.service.resource.referenced.ReferencedDataRepoSnapshotResource;
import bio.terra.workspace.service.resource.referenced.ReferencedGcsBucketResource;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

public class MakeApiResourceDescriptionTest  extends BaseUnitTest {
    @Autowired
    ResourceController resourceController;

    private UUID workspaceId;
    private UUID resourceId;
    private String resourceName;
    private String description;
    private CloningInstructions cloning;

    @BeforeEach
    public void setup() {
        workspaceId = UUID.randomUUID();
        resourceId = UUID.randomUUID();
        resourceName = RandomStringUtils.randomAlphabetic(6);
        description = "Description of " + resourceName;
        cloning = CloningInstructions.COPY_DEFINITION;
    }

    @Test
    public void mapReferencedBigQueryTest() throws Exception {
        String projectId = RandomStringUtils.randomAlphabetic(12);
        String datasetName = RandomStringUtils.randomAlphabetic(12);

        var resource = new ReferencedBigQueryDatasetResource(
                workspaceId,
                resourceId,
                resourceName,
                description,
                cloning,
                projectId,
                datasetName);

        ApiResourceDescription resourceDescription = resourceController.makeApiResourceDescription((WsmResource) resource);
        validateWsmResource(resourceDescription);
        ApiResourceAttributesUnion union = resourceDescription.getResourceAttributes();
        ApiGcpBigQueryDatasetAttributes attributes = union.getGcpBigQuery();
        assertThat(attributes, is(notNullValue()));
        assertThat(attributes.getDatasetId(), equalTo(datasetName));
        assertThat(attributes.getProjectId(), equalTo(projectId));
    }

    @Test
    public void mapReferencedDataRepoSnapshotTest() throws Exception {
        String snapshotId = UUID.randomUUID().toString();
        String instanceName = RandomStringUtils.randomAlphabetic(5);

        var resource = new ReferencedDataRepoSnapshotResource(
                workspaceId,
                resourceId,
                resourceName,
                description,
                cloning,
                instanceName,
                snapshotId);

        ApiResourceDescription resourceDescription = resourceController.makeApiResourceDescription((WsmResource) resource);
        validateWsmResource(resourceDescription);
        ApiResourceAttributesUnion union = resourceDescription.getResourceAttributes();
        ApiDataRepoSnapshotAttributes attributes = union.getGcpDataRepoSnapshot();
        assertThat(attributes, is(notNullValue()));
        assertThat(attributes.getInstanceName(), equalTo(instanceName));
        assertThat(attributes.getSnapshot(), equalTo(snapshotId));
    }

    @Test
    public void mapReferencedGcsBucketTest() throws Exception {
        String bucketName = RandomStringUtils.randomAlphabetic(5).toLowerCase();

        var resource = new ReferencedGcsBucketResource(
                workspaceId,
                resourceId,
                resourceName,
                description,
                cloning,
                bucketName);

        ApiResourceDescription resourceDescription = resourceController.makeApiResourceDescription((WsmResource) resource);
        validateWsmResource(resourceDescription);
        ApiResourceAttributesUnion union = resourceDescription.getResourceAttributes();
        ApiGcpGcsBucketAttributes attributes = union.getGcpGcsBucket();
        assertThat(attributes, is(notNullValue()));
        assertThat(attributes.getBucketName(), equalTo(bucketName));
    }

    public void validateWsmResource(ApiResourceDescription resourceDescription) {
        ApiResourceMetadata metadata = resourceDescription.getMetadata();
        assertThat(metadata.getWorkspaceId(), equalTo(workspaceId));
        assertThat(metadata.getResourceId(), equalTo(resourceId));
        assertThat(metadata.getName(), equalTo(resourceName));
        assertThat(metadata.getDescription(), equalTo(description));
        assertThat(metadata.getCloningInstructions(), equalTo(cloning.toApiModel()));
    }

    @Nested
    class ControlledResource {
        private String assignedUser;
        private AccessScopeType accessScopeType;
        private ManagedByType managedByType;

        @BeforeEach
        public void controlledSetup() {
            assignedUser = RandomStringUtils.random(20, true, false);
            accessScopeType = AccessScopeType.ACCESS_SCOPE_PRIVATE;
            managedByType = ManagedByType.MANAGED_BY_USER;
        }

        @Test
        public void mapControlledGcsBucketTest() throws Exception {
            String bucketName = RandomStringUtils.randomAlphabetic(5).toLowerCase();

            var resource = new ControlledGcsBucketResource(
                workspaceId,
                resourceId,
                resourceName,
                description,
                cloning,
                assignedUser,
                accessScopeType,
                managedByType,
                bucketName);

            ApiResourceDescription resourceDescription = resourceController.makeApiResourceDescription(resource);
            validateControlledResource(resourceDescription);
            ApiResourceAttributesUnion union = resourceDescription.getResourceAttributes();
            ApiGcpGcsBucketAttributes attributes = union.getGcpGcsBucket();
            assertThat(attributes, is(notNullValue()));
            assertThat(attributes.getBucketName(), equalTo(bucketName));
        }

        public void validateControlledResource(ApiResourceDescription resourceDescription) {
            validateWsmResource(resourceDescription);
            ApiResourceMetadata metadata = resourceDescription.getMetadata();
            ApiControlledResourceMetadata common = metadata.getControlledResourceMetadata();
            assertThat(common, is(notNullValue()));
            assertThat(common.getAccessScope(), equalTo(accessScopeType.toApiModel()));
            assertThat(common.getManagedBy(), equalTo(managedByType.toApiModel()));
            ApiPrivateResourceUser user = common.getPrivateResourceUser();
            assertThat(user, is(notNullValue()));
            assertThat(user.getUserName(), equalTo(assignedUser));
        }
    }
}
