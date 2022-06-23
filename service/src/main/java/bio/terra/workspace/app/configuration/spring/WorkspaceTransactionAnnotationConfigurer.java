package bio.terra.workspace.app.configuration.spring;

import bio.terra.workspace.app.configuration.external.WorkspaceDatabaseConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.TransactionManagementConfigurer;

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
