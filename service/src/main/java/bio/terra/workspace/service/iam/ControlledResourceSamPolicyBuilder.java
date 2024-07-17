package bio.terra.workspace.service.iam;

import bio.terra.common.exception.InternalServerErrorException;
import bio.terra.workspace.common.exception.InternalLogicException;
import bio.terra.workspace.common.utils.GcpUtils;
import bio.terra.workspace.service.iam.model.ControlledResourceIamRole;
import bio.terra.workspace.service.resource.controlled.model.ControlledResourceCategory;
import bio.terra.workspace.service.workspace.model.WsmWorkspaceApplication;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;
import org.broadinstitute.dsde.workbench.client.sam.model.AccessPolicyMembershipRequest;
import org.broadinstitute.dsde.workbench.client.sam.model.CreateResourceRequestV2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class is used to construct Sam policies for controlled resources
 *
 * <p>Add the WSM SA as the owner of all controlled resources.
 *
 * <p>Add uninherited policies for controlled resources. Note that in Sam we fill out all of the
 * roles, even if there are no users assigned. That is purely future proofing.
 *
 * <p>For user shared:
 *
 * <ul>
 *   <li>OWNER = WSM SA
 *   <li>EDITOR = inherited from workspace owner and writer
 *   <li>WRITER = inherited from workspace owner and writer
 *   <li>READER = inherited from workspace reader
 * </ul>
 *
 * <p>For user-private the user is always the creating user. The role is determined by the type of
 * resource. As of this writing, all resources give EDITOR role for the creating user.
 *
 * <ul>
 *   <li>OWNER = WSM SA
 *   <li>EDITOR = creating user (userRequest)
 *   <li>WRITER = currently unused
 *   <li>READER = currently unused
 *   <li>DELETER = inherited from workspace owner
 * </ul>
 *
 * <p>For application shared:
 *
 * <ul>
 *   <li>OWNER = WSM SA
 *   <li>EDITOR = app (userRequest)
 *   <li>WRITER = inherited from workspace owner and writer
 *   <li>READER = inherited from workspace reader
 * </ul>
 *
 * <p>For application private, there are no inherited roles:
 *
 * <ul>
 *   <li>OWNER = WSM SA
 *   <li>EDITOR = app (userRequest)
 *   <li>WRITER = if assigned user present with role WRITER
 *   <li>READER = if assigned user present with role READER
 * </ul>
 */
public class ControlledResourceSamPolicyBuilder {
  private final Logger logger = LoggerFactory.getLogger(ControlledResourceSamPolicyBuilder.class);

  private final ControlledResourceIamRole privateIamRole;
  private final String privateUserEmail;
  private final ControlledResourceCategory category;
  @Nullable private final WsmWorkspaceApplication application;

  public ControlledResourceSamPolicyBuilder(
      ControlledResourceIamRole privateIamRole,
      @Nullable String privateUserEmail,
      ControlledResourceCategory category,
      @Nullable WsmWorkspaceApplication application) {
    this.privateIamRole = privateIamRole;
    this.privateUserEmail = privateUserEmail;
    this.category = category;
    this.application = application;
  }

  public void addPolicies(CreateResourceRequestV2 request) {
    Map<ControlledResourceIamRole, AccessPolicyMembershipRequest> policyMap;

    // Owner is always WSM SA
    addWsmResourceOwnerPolicy(request);

    switch (category) {
      case USER_SHARED:
        if (privateIamRole != null || privateUserEmail != null) {
          logger.warn("User shared resources may not be assigned to a private user email");
        }
        // All other policies are inherited - nothing more to do
        break;

      case USER_PRIVATE:
        // Double check - this is validated earlier and should never happen.
        if (privateUserEmail == null || privateIamRole == null) {
          throw new InternalLogicException(
              "Flight should never see user-private without a user email and iam role");
        }

        policyMap = makeInitialPolicyMap();
        policyMap.get(privateIamRole).addMemberEmailsItem(privateUserEmail);
        applyPolicyMap(request, policyMap);
        break;

      case APPLICATION_SHARED:
        // Double check - this is validated earlier and should never happen
        if (privateUserEmail != null) {
          throw new InternalLogicException(
              "Flight should never see application-shared with a user email");
        }

        if (privateIamRole != null) {
          throw new InternalLogicException(
              "Attempting to create an application controlled resource with a private IAM role");
        }

        if (application == null) {
          throw new InternalLogicException(
              "Attempting to create an application controlled resource with no application");
        }
        // Application is always editor on its resources; other policies are inherited
        AccessPolicyMembershipRequest editorPolicy =
            new AccessPolicyMembershipRequest()
                .addRolesItem(ControlledResourceIamRole.EDITOR.toSamRole());
        addApplicationResourceEditorPolicy(editorPolicy, application);
        request.putPoliciesItem(ControlledResourceIamRole.EDITOR.toSamRole(), editorPolicy);
        break;

      case APPLICATION_PRIVATE:
        if (application == null) {
          throw new InternalLogicException(
              "Attempting to create an application controlled resource with no application");
        }

        policyMap = makeInitialPolicyMap();
        // Application is always editor
        addApplicationResourceEditorPolicy(
            policyMap.get(ControlledResourceIamRole.EDITOR), application);
        // if we have an assigned user, set up their permission
        if (privateUserEmail != null) {
          policyMap.get(privateIamRole).addMemberEmailsItem(privateUserEmail);
        }
        applyPolicyMap(request, policyMap);
        break;
    }
  }

  /**
   * For the private resources we always fill in READER, WRITER, and EDITOR even if empty. This
   * generates a map by role of the policy.
   */
  private Map<ControlledResourceIamRole, AccessPolicyMembershipRequest> makeInitialPolicyMap() {
    Map<ControlledResourceIamRole, AccessPolicyMembershipRequest> policyMap = new HashMap<>();
    policyMap.put(
        ControlledResourceIamRole.READER,
        new AccessPolicyMembershipRequest()
            .addRolesItem(ControlledResourceIamRole.READER.toSamRole()));
    policyMap.put(
        ControlledResourceIamRole.WRITER,
        new AccessPolicyMembershipRequest()
            .addRolesItem(ControlledResourceIamRole.WRITER.toSamRole()));
    policyMap.put(
        ControlledResourceIamRole.EDITOR,
        new AccessPolicyMembershipRequest()
            .addRolesItem(ControlledResourceIamRole.EDITOR.toSamRole()));
    return policyMap;
  }

  /**
   * Given the map created in @{@link #makeInitialPolicyMap()}, populate the resource request with
   * the policies.
   *
   * @param request resource request for setting the roles on the Sam resource creation
   * @param policyMap previously created policy map
   */
  private void applyPolicyMap(
      CreateResourceRequestV2 request,
      Map<ControlledResourceIamRole, AccessPolicyMembershipRequest> policyMap) {
    policyMap.forEach((key, value) -> request.putPoliciesItem(key.toSamRole(), value));
  }

  /**
   * Add WSM's service account as the owner of a controlled resource in Sam. Used for admin
   * reassignment of resources. This assumes samService.initialize() has already been called, which
   * should happen on start.
   */
  private void addWsmResourceOwnerPolicy(CreateResourceRequestV2 request) {
    try {
      AccessPolicyMembershipRequest ownerPolicy =
          new AccessPolicyMembershipRequest()
              .addRolesItem(ControlledResourceIamRole.OWNER.toSamRole())
              .addMemberEmailsItem(GcpUtils.getWsmSaEmail());
      request.putPoliciesItem(ControlledResourceIamRole.OWNER.toSamRole(), ownerPolicy);
    } catch (InternalServerErrorException e) {
      // In cases where WSM is not running as a service account (e.g. unit tests), the above call to
      // get application default credentials will fail. This is fine, as those cases don't create
      // real resources.
      logger.warn(
          "Failed to add WSM service account as resource owner Sam. This is expected for tests.",
          e);
    }
  }

  /**
   * Find the email of the application and add it to the incoming policy.
   *
   * @param editorPolicy editor policy
   * @param application Application being added to the policy
   */
  private void addApplicationResourceEditorPolicy(
      AccessPolicyMembershipRequest editorPolicy, WsmWorkspaceApplication application) {
    editorPolicy.addMemberEmailsItem(application.getApplication().getServiceAccount());
  }
}
