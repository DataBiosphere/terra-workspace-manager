package bio.terra.workspace.app.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import bio.terra.workspace.common.BaseConnectedTest;
import bio.terra.workspace.service.iam.AuthenticatedUserRequest;
import bio.terra.workspace.service.iam.SamService;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

public class AdminApiControllerTest extends BaseConnectedTest {

  // mock out samService because the test user doesn't have admin access.
  @Mock SamService samService;

  @Autowired MockMvc mockMvc;

  @Test
  void syncIamRole() {

  }
}
