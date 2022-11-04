package bio.terra.workspace.service.workspace.model;

import bio.terra.workspace.service.folder.model.Folder;
import bio.terra.workspace.service.resource.model.WsmResource;

import javax.annotation.Nullable;
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
    private List<WsmResource> referencedResources;
    private List<WsmResource> controlledResources;

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

    public List<WsmResource> getReferencedResources() {
        return referencedResources;
    }

    public void setReferencedResources(List<WsmResource> referencedResources) {
        this.referencedResources = referencedResources;
    }

    public List<WsmResource> getControlledResources() {
        return controlledResources;
    }

    public void setControlledResources(List<WsmResource> controlledResources) {
        this.controlledResources = controlledResources;
    }

    public Workspace getWorkspace() {
        return workspace;
    }

    public void setWorkspace(Workspace workspace) {
        this.workspace = workspace;
    }
}
