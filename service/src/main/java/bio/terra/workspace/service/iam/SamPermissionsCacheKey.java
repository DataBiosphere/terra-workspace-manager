package bio.terra.workspace.service.iam;

public record SamPermissionsCacheKey(
    String samResourceType, String samResourceName, String samResourceAction, String userEmail) {}
