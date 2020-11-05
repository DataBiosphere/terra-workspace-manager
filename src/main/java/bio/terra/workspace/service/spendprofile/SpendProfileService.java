package bio.terra.workspace.service.spendprofile;

import bio.terra.workspace.service.iam.SamService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class SpendProfileService {
  private final SamService samService;

  @Autowired
  public SpendProfileService(SamService samService) {
    this.samService = samService;
  }
}
