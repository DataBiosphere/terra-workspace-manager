package bio.terra.workspace.common.utils;

import bio.terra.common.iam.SamUser;
import bio.terra.workspace.service.workspace.model.AwsCloudContext;
import com.amazonaws.services.securitytoken.model.Credentials;
import com.amazonaws.services.securitytoken.model.Tag;
import java.util.Collection;

public class MultiCloudUtils {
  public static Credentials assumeAwsServiceRoleFromGcp(
      AwsCloudContext awsCloudContext, Integer duration) {
    return AwsUtils.assumeServiceRole(
        awsCloudContext,
        GcpUtils.getWsmSaJwt(awsCloudContext.getServiceRoleAudience()),
        GcpUtils.getWsmSaEmail(),
        duration);
  }

  public static Credentials assumeAwsServiceRoleFromGcp(AwsCloudContext awsCloudContext) {
    return assumeAwsServiceRoleFromGcp(awsCloudContext, AwsUtils.MIN_TOKEN_DURATION_SECONDS);
  }

  public static Credentials assumeAwsUserRoleFromGcp(
      AwsCloudContext awsCloudContext, SamUser user, Collection<Tag> tags, Integer duration) {
    return AwsUtils.assumeUserRole(
        awsCloudContext,
        GcpUtils.getWsmSaJwt(awsCloudContext.getServiceRoleAudience()),
        GcpUtils.getWsmSaEmail(),
        user,
        tags,
        duration);
  }

  public static Credentials assumeAwsUserRoleFromGcp(
      AwsCloudContext awsCloudContext, SamUser user, Collection<Tag> tags) {
    return assumeAwsUserRoleFromGcp(
        awsCloudContext, user, tags, AwsUtils.MIN_TOKEN_DURATION_SECONDS);
  }
}
