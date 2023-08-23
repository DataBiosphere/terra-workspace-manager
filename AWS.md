# AWS Workspace Controlled Resources
Managing Workspace Controlled Resources in Amazon Web Services (AWS).

Table of Contents
=================
* [Support Resource Discovery](#support-resource-discovery)
* [Runtime Configuration](#runtime-configuration)
  * [Required Configuration Parameters](#required-configuration-parameters)
  * [Optional Configuration Parameters](#optional-configuration-parameters)
* [Credentials](#credentials)
  * [IAM Roles](#iam-roles)
    * [Terra Discovery IAM Role](#terra-discovery-iam-role) 
    * [Terra Workspace Manager IAM Role](#terra-workspace-manager-iam-role)
    * [Terra User IAM Role](#terra-user-iam-role)
  * [Credential Providers](#credential-providers)

# Support Resource Discovery
In order to manage and provide access to Controlled Resources in an AWS environment, Terra Services
(including the Workspace Manager) depend on pre-existing resources in the target AWS account.
These resources are called **Support Resources**.  Details on Support Resources and their
organization can be found in the
[`terra-aws-resource-discovery` README file](https://github.com/DataBiosphere/terra-aws-resource-discovery/blob/main/README.md#terra-aws-resource-discovery).

In order to discover the Support Resources in a given
[Terra AWS Environment](https://github.com/DataBiosphere/terra-aws-resource-discovery/blob/main/README.md#environments),
Terra Services will make use of the [`terra-aws-resource-discovery`](https://github.com/DataBiosphere/terra-aws-resource-discovery) library.
It will consume its discovery data from an S3 bucket, making use of class 
[`S3EnvironmentDiscovery`](https://github.com/DataBiosphere/terra-aws-resource-discovery#configuration-storage-layout)
(optionally in conjunction with class 
[`CachedEnvironmentDiscovery`](https://github.com/DataBiosphere/terra-aws-resource-discovery#configuration-storage-layout)).

# Runtime Configuration
To enable Support Resource Discovery, and ultimately creation and management of Controlled 
Resources in AWS, there are several configuration parameters defined in class 
[`AwsConfiguration`](service/src/main/java/bio/terra/workspace/app/configuration/external/AwsConfiguration.java).
## Required Configuration Parameters
The following configuration parameters are *required* to interact with AWS:
* `workspaces.aws.discovery.roleArn` must be set to an AWS IAM Role ARN that:
  * Has read access to the Discovery AWS Bucket
  * Has a [Trust Policy](https://docs.aws.amazon.com/IAM/latest/UserGuide/id_roles_terms-and-concepts.html#term_trust-policy)
which allows the Workspace Manager Service Account (SA) to assume the role
via [Web Identity Federation](https://docs.aws.amazon.com/IAM/latest/UserGuide/id_roles_providers_oidc.html).
* `workspaces.aws.discovery.bucket.name` must be set to the name of the S3 bucket containing 
Discovery data.
* `workspaces.aws.discovery.bucket.region` must be set to the region of the S3 bucket containing
Discovery data.
* `workspaces.aws.authentication.googleJwtAudience` the OAuth2 `aud` claim expected by AWS IAM when
assuming roles using Web Identity Federation.  This is passed to Google auth API's when requesting 
JWT credential representing the trusted WSM SA. 
## Optional Configuration Parameters
The following configuration parameters are optional:
* `workspaces.aws.discovery.caching.enabled` enables caching of Discovery data retrieved from the
Discovery bucket, to reduce calls to the infrequently updated bucket (default: `true`)
* `workspaces.aws.discovery.caching.expirationTimeSeconds` expiration time for cached Discovery
data (default: `600` seconds)
* `workspaces.aws.authentication.credentialLifetimeSeconds` lifetime to specify when requesting
a WSM credential via [`AssumeRoleWithWebIdentity`](https://docs.aws.amazon.com/STS/latest/APIReference/API_AssumeRoleWithWebIdentity.html) 
AWS STS API call (default: `900` seconds, which is minimum supported by STS)
* `workspaces.aws.authentication.credentialStaleTimeSeconds` is the amount of time (in seconds)
before the expiration time of a WSM credential held in a [`AwsCredentialsProvider`](https://sdk.amazonaws.com/java/api/2.0.0/software/amazon/awssdk/auth/credentials/AwsCredentialsProvider.html) 
instance will be considered stale and refreshed (default: `300` seconds)
# Credentials
## IAM Roles
### Terra Discovery IAM Role
The Terra Discovery IAM Role provides read-only access to the Terra Discovery S3 bucket in order to
enable the Terra WSM service to discover Support Resources in the AWS Environment.  This role is 
assumed via Web Identity Federation, with a trust policy that allows the WSM GCP Service Account 
to assume this role by passing an Identity JWT to the 
[`AssumeRoleWithWebIdentity`](https://docs.aws.amazon.com/STS/latest/APIReference/API_AssumeRoleWithWebIdentity.html)
AWS Secure Token Service (STS) API.
### Terra Workspace Manager IAM Role
The Terra Workspace Manager IAM Role provides extensive permissions in the AWS Environment that allow
the Terra WSM service to manage Controlled Resources in the AWS Environment.  

The ARN (Amazon Resource Name) for this IAM Role is discovered as part of Environment Discovery.

This role is assumed via Web Identity Federation, with a trust policy that allows the WSM GCP 
Service Account to assume this role by passing an Identity JWT to the
[`AssumeRoleWithWebIdentity`](https://docs.aws.amazon.com/STS/latest/APIReference/API_AssumeRoleWithWebIdentity.html)
AWS Secure Token Service (STS) API.
### Terra User IAM Role
The Workspace Manager obtains temporary credentials on behalf of Terra end users to provide 
[Attribute Based Access Control (ABAC)](https://docs.aws.amazon.com/IAM/latest/UserGuide/introduction_attribute-based-access-control.html)
to Controlled Resources that they are allowed to access.

The ARN (Amazon Resource Name) for this IAM Role is discovered as part of Environment Discovery.

Assuming the Terra User role requires the Workspace Manager service, operating as the Workspace 
Manager IAM Role, to call the STS
[`AssumeRole`](https://docs.aws.amazon.com/STS/latest/APIReference/API_AssumeRole.html) API with
an appropriate set of [session tags](https://docs.aws.amazon.com/IAM/latest/UserGuide/id_session-tags.html)
that allow access to a given resource.

The trust policy of the Terra User IAM Role only allows the Terra Workspace Manager IAM role to 
assume the Terra User IAM Role, thus the WSM is the only entity that may provide credentials to
end users, and has full control over the tags passed to STS when assuming the role.  Thus the tags
associated with a Terra User temporary credential session can be treated as authoritative.

Stated another way, if a user has a credential for Terra User with tag "`user = alice@foo.com`",
an IAM Policy can safely assume that (1) the credential was issued by the Terra Workspace Manager,
and (2) the Workspace Manager authenticated the requesting user as `alice@foo.com` (in the absence
of compromised WSM SA credential or a bug in WSM authentication).
## Credential Providers
The following methods of the [`AwsUtils` class](service/src/main/java/bio/terra/workspace/common/utils/AwsUtils.java)
can be used to obtain credentials for use in the AWS Environment:
* `createDiscoveryCredentialsProvider()` creates an
[`AwsCredentialsProvider`](https://sdk.amazonaws.com/java/api/2.0.0/software/amazon/awssdk/auth/credentials/AwsCredentialsProvider.html)
instance that obtains credentials for the Terra Discovery role in an AWS Environment, and
can be used to read from the Discovery bucket.  This `AwsCredentialsProvider` will refresh 
credentials under the hood as necessary, and is meant to live for the entire lifetime of the WSM
service process, as referenced by an equally long-lived `S3EnvironmentDiscovery` singleton.
* `createWsmCredentialProvider()` creates an 
[`AwsCredentialsProvider`](https://sdk.amazonaws.com/java/api/2.0.0/software/amazon/awssdk/auth/credentials/AwsCredentialsProvider.html)
instance that obtains credentials for the Terra Workspace Manager role in an AWS Environment, and 
can be used in AWS SDK calls.  This `AwsCredentialsProvider` will refresh credentials under the 
hood as necessary, but should not be as long-lived as the Discovery Credentials Provider.
Generally it should have the same lifecycle as the `Environment` object passed to this method: one
operation (API call, Stairway Flight).