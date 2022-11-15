package bio.terra.workspace.service.workspace.model;

import bio.terra.workspace.service.folder.model.Folder;
import bio.terra.workspace.service.resource.model.CloningInstructions;
import bio.terra.workspace.service.resource.model.WsmResource;
import com.fasterxml.jackson.annotation.JsonIgnore;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * During workspace clone, we gather all of the metadata inputs needed for doing
 * a workspace clone in this data structure.
 */
public class CloneSourceMetadata {
    private Workspace workspace;
    @Nullable private GcpCloudContext gcpCloudContext;
    @Nullable private AzureCloudContext azureCloudContext;
    private List<String> applications; // list of application ids
    private Map<UUID, Folder> folders;
    private Map<CloningInstructions, List<WsmResource>> resourcesByInstruction;

    @Nullable
    public GcpCloudContext getGcpCloudContext() {
        return gcpCloudContext;
    }

    public void setGcpCloudContext(@Nullable GcpCloudContext gcpCloudContext) {
        this.gcpCloudContext = gcpCloudContext;
    }

    @Nullable
    public AzureCloudContext getAzureCloudContext() {
        return azureCloudContext;
    }

    public void setAzureCloudContext(@Nullable AzureCloudContext azureCloudContext) {
        this.azureCloudContext = azureCloudContext;
    }

    public List<String> getApplications() {
        return applications;
    }

    public void setApplications(List<String> applications) {
        this.applications = applications;
    }

    public Map<UUID, Folder> getFolders() {
        return folders;
    }

    public void setFolders(Map<UUID, Folder> folders) {
        this.folders = folders;
    }

    @JsonIgnore
    public List<WsmResource> getReferencedResources() {
        return resourcesByInstruction.get(CloningInstructions.COPY_REFERENCE);
    }
    @JsonIgnore
    public List<WsmResource> getControlledResources() {
        List<WsmResource> controlledResources = new ArrayList<>();
        controlledResources.addAll(resourcesByInstruction.get(CloningInstructions.COPY_DEFINITION));
        controlledResources.addAll(resourcesByInstruction.get(CloningInstructions.COPY_RESOURCE));
        return controlledResources;
    }

    public Map<CloningInstructions, List<WsmResource>> getResourcesByInstruction() {
        return resourcesByInstruction;
    }

    public void setResourcesByInstruction(Map<CloningInstructions, List<WsmResource>> resourcesByInstruction) {
        this.resourcesByInstruction = resourcesByInstruction;
    }

    public Workspace getWorkspace() {
        return workspace;
    }

    public void setWorkspace(Workspace workspace) {
        this.workspace = workspace;
    }
}
