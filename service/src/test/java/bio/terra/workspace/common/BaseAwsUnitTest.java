package bio.terra.workspace.common;

import bio.terra.workspace.app.configuration.external.AwsConfiguration;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;

/** Base class for AWS unit tests: not connected to AWS */
@Tag("aws-unit")
@ActiveProfiles({"aws-unit-test", "unit-test"})
public class BaseAwsUnitTest extends BaseUnitTestMocks {

  @Autowired protected AwsConfiguration awsConfiguration;
}
