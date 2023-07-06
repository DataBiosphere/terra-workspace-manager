package bio.terra.workspace.azureDatabaseUtils;

import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ActiveProfiles("test")
@SpringBootTest
@ContextConfiguration(classes = AzureDatabaseUtilsApplication.class)
@ExtendWith(SpringExtension.class)
public abstract class BaseUnitTest {}
