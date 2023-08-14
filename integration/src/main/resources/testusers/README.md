# WSM Integration Test Users

Workspace Manager currently uses 3 test users for running integration tests with varying levels of access to external resources: 
- `bella.redwalker@test.firecloud.org`
- `elijah.thunderlord@test.firecloud.org`
- `liam.dragonmaw@test.firecloud.org`

Tests generate credentials for these users via domain-wide delegation. More information about test user configuration (including information for setting up additional test users) is available [on Confluence](https://broadworkbench.atlassian.net/wiki/spaces/QA/pages/2730524689/The+Testerson+Family+and+the+Order+of+the+QA). See the linked "Test Horde" sheet when setting up additional test users.

Workspace Manager integration tests also talk to a number of external resources.
These include:
- Read access to TDR snapshots specified per-environment by UUID values in test configs
    - Owned by Jade team in all environments. Test user `Albus Dumbledore` can also grant other test users access.
- Usage of the `wm-default-spend-profile` spend profile in Sam
    - Owned by `developer-admins` and Platform Foundations team
    - Read access to the `terra_wsm_test_dataset` BigQuery dataset with tables `terra wsm test data table` and `terra wsm test data table 2`, the `terra_wsm_test_dataset_2` BigQuery dataset with table `terra wsm test data table 2`, the `terra_wsm_test_resource` 
      GCS bucket with uniform access, and the `terra_wsm_fine_grained_test_bucket` GCS bucket with fine-grained access, both in the `terra-kernel-k8s` GCP project.
        - Owned by Platform Foundation and App Services teams.
  
Users have different access to the above resources. Some tests also rely on specific
users not having resource access, e.g. testing that a user cannot use a spend profile
without having access. Changing user access on these external resources may break
existing tests. If you need a user with a combination of resource accesses that
doesn't currently exist, consider minting a new test user.

Currently, users have the following access permissions, replicated across environments:

|                                           | TDR Snapshot       | `wm-default-spend-profile` | BQ Dataset `terra_wsm_test_dataset` | BQ Dataset `terra_wsm_test_dataset_2` | BQ Data table `terra_wsm_test_dataset/terra wsm test data table` | BQ Data table `terra_wsm_test_dataset/terra wsm test data table 2` | BQ Data table `terra_wsm_test_dataset_2/terra wsm test data table 2` | Bucket `terra_wsm_test_resource` | Bucket `terra_wsm_fine_grained_test_bucket` | Bucket object `terra_wsm_fine_grained_test_bucket/foo/` | Bucket object `terra_wsm_fine_grained_test_bucket/foo/monkey_sees_monkey_dos.txt` |
| ----------------------------------------- | ------------------ | -------------------------- | ----------------------------------- | ------------------------------------  | ---------------------------------------------------------------- | ------------------------------------------------------------------ | -------------------------------------------------------------------- | -------------------------------- | ------------------------------------------- | ------------------------------------------------------- | --------------------------------------------------------------------------------- |
| **bella.redwalker**                       | :white_check_mark: | :white_check_mark:         | :white_check_mark:                  | :white_check_mark:                    | :white_check_mark:                                               | :white_check_mark:                                                 | :white_check_mark:                                                   | :white_check_mark:               | :white_check_mark:                          | :white_check_mark:                                      | :white_check_mark:                                                                |
| **elijah.thunderlord**                    | :x:                | :white_check_mark:         | :x:                                 | :white_check_mark:                    | :x:                                                              | :x:                                                                | :white_check_mark:                                                   | :white_check_mark:               | :x:                                         | :x:                                                     | :white_check_mark:                                                                |
| **liam.dragonmaw**                        | :x:                | :x:                        | :x:                                 | :x:                                   | :x:                                                              | :x:                                                                | :x:                                                                  | :x:                              | :x:                                         | :x:                                                     | :x:                                                                               |