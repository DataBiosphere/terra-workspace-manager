package bio.terra.workspace.service.spendprofile.model;

import bio.terra.profile.model.Organization;

public record SpendProfileOrganization(Boolean enterprise) {
  public SpendProfileOrganization(Organization organization) {
    this(organization.isEnterprise());
  }
}
