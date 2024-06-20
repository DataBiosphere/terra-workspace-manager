package bio.terra.workspace.service.iam;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.junit.jupiter.api.Assertions.*;

import bio.terra.workspace.common.BaseSpringBootUnitTest;
import bio.terra.workspace.common.exception.InternalLogicException;
import bio.terra.workspace.common.utils.GcpUtils;
import bio.terra.workspace.service.iam.model.ControlledResourceIamRole;
import bio.terra.workspace.service.resource.controlled.model.ControlledResourceCategory;
import bio.terra.workspace.service.workspace.model.WsmApplication;
import bio.terra.workspace.service.workspace.model.WsmWorkspaceApplication;
import org.broadinstitute.dsde.workbench.client.sam.model.CreateResourceRequestV2;
import org.junit.jupiter.api.Test;

class ControlledResourceSamPolicyBuilderTest extends BaseSpringBootUnitTest {

  @Test
  void addPolicies_userPrivate() {
    var policyBuilder =
        new ControlledResourceSamPolicyBuilder(
            ControlledResourceIamRole.EDITOR,
            "fake@example.com",
            ControlledResourceCategory.USER_PRIVATE,
            null);
    var request = new CreateResourceRequestV2();

    policyBuilder.addPolicies(request);

    assertThat(
        request.getPolicies().get(ControlledResourceIamRole.OWNER.toSamRole()).getMemberEmails(),
        contains(GcpUtils.getWsmSaEmail()));
    assertThat(
        request.getPolicies().get(ControlledResourceIamRole.EDITOR.toSamRole()).getMemberEmails(),
        contains("fake@example.com"));
  }

  @Test
  void addPolicies_userPrivateWihtoutPrivateEmailFails() {
    var policyBuilder =
        new ControlledResourceSamPolicyBuilder(
            ControlledResourceIamRole.DELETER, null, ControlledResourceCategory.USER_PRIVATE, null);
    var request = new CreateResourceRequestV2();

    assertThrows(InternalLogicException.class, () -> policyBuilder.addPolicies(request));
  }

  @Test
  void addPolicies_applicationShared() {
    var app =
        new WsmWorkspaceApplication()
            .application(new WsmApplication().serviceAccount("fake-svc@example.com"));
    var policyBuilder =
        new ControlledResourceSamPolicyBuilder(
            ControlledResourceIamRole.READER,
            null,
            ControlledResourceCategory.APPLICATION_SHARED,
            app);
    var request = new CreateResourceRequestV2();

    policyBuilder.addPolicies(request);

    assertThat(
        request.getPolicies().get(ControlledResourceIamRole.OWNER.toSamRole()).getMemberEmails(),
        contains(GcpUtils.getWsmSaEmail()));
    assertThat(
        request.getPolicies().get(ControlledResourceIamRole.EDITOR.toSamRole()).getMemberEmails(),
        contains("fake-svc@example.com"));
  }

  @Test
  void addPolicies_applicationSharedWithNoAppFails() {
    var policyBuilder =
        new ControlledResourceSamPolicyBuilder(
            ControlledResourceIamRole.EDITOR,
            null,
            ControlledResourceCategory.APPLICATION_SHARED,
            null);
    var request = new CreateResourceRequestV2();

    assertThrows(InternalLogicException.class, () -> policyBuilder.addPolicies(request));
  }

  @Test
  void addPolicies_applicationSharedWithUserEmailFails() {
    var app =
        new WsmWorkspaceApplication()
            .application(new WsmApplication().serviceAccount("fake-svc@example.com"));
    var policyBuilder =
        new ControlledResourceSamPolicyBuilder(
            ControlledResourceIamRole.DELETER,
            "fake@example.com",
            ControlledResourceCategory.APPLICATION_SHARED,
            app);
    var request = new CreateResourceRequestV2();

    assertThrows(InternalLogicException.class, () -> policyBuilder.addPolicies(request));
  }

  @Test
  void addPolicies_applicationPrivate() {
    var policyBuilder =
        new ControlledResourceSamPolicyBuilder(
            ControlledResourceIamRole.WRITER,
            "fake@example.com",
            ControlledResourceCategory.APPLICATION_PRIVATE,
            new WsmWorkspaceApplication()
                .application(new WsmApplication().serviceAccount("fake-svc@example.com")));
    var request = new CreateResourceRequestV2();

    policyBuilder.addPolicies(request);

    assertThat(
        request.getPolicies().get(ControlledResourceIamRole.EDITOR.toSamRole()).getMemberEmails(),
        contains("fake-svc@example.com"));
    assertThat(
        request.getPolicies().get(ControlledResourceIamRole.WRITER.toSamRole()).getMemberEmails(),
        contains("fake@example.com"));
  }

  @Test
  void addPolicies_applicationPrivateWithNoAppFails() {
    var policyBuilder =
        new ControlledResourceSamPolicyBuilder(
            ControlledResourceIamRole.EDITOR,
            null,
            ControlledResourceCategory.APPLICATION_PRIVATE,
            null);
    var request = new CreateResourceRequestV2();

    assertThrows(InternalLogicException.class, () -> policyBuilder.addPolicies(request));
  }
}
