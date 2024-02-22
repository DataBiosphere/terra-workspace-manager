package bio.terra.workspace.common;

import bio.terra.workspace.common.annotations.Unit;

/**
 * Base class for Spring Boot unit tests: not connected to cloud providers. Includes GCP unit tests
 * & others cloud-agnostic WSM component unit tests
 */
@Unit
public class BaseSpringBootUnitTest extends BaseSpringBootUnitTestMocks {}
