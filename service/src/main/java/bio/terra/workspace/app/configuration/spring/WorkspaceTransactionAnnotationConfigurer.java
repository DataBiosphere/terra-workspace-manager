package bio.terra.workspace.app.configuration.spring;

import bio.terra.workspace.app.configuration.external.WorkspaceDatabaseConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.TransactionManagementConfigurer;

/**
 * WSM can support other components running inside it. Those components may also use databases and
 * have transaction beans. They are not able to use the cool @ReadTransaction and @WriteTransaction
 * annotations and have to specify the transaction manager in the @Transaction annotation on their
 * database operations.
 *
 * <p>This class configures the default transaction manager to be the one associated with the
 * workspace database. That allows WSM DAOs to use the cool transaction annotations.
 */
@Configuration
@EnableTransactionManagement
public class WorkspaceTransactionAnnotationConfigurer implements TransactionManagementConfigurer {
  private final WorkspaceDatabaseConfiguration workspaceDatabaseConfiguration;

  @Autowired
  public WorkspaceTransactionAnnotationConfigurer(
      WorkspaceDatabaseConfiguration workspaceDatabaseConfiguration) {
    this.workspaceDatabaseConfiguration = workspaceDatabaseConfiguration;
  }

  @Override
  public PlatformTransactionManager annotationDrivenTransactionManager() {
    return new JdbcTransactionManager(workspaceDatabaseConfiguration.getDataSource());
  }
}
