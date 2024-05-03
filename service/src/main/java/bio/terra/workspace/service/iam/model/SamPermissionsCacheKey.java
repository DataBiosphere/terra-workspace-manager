package bio.terra.workspace.service.iam.model;

public record SamPermissionsCacheKey(
    String samResourceType, String samResourceName, String samResourceAction, String userEmail) {}
