package bio.terra.TEMPLATE.app.configuration;

import bio.terra.TEMPLATE.app.StartupInitializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import org.apache.commons.dbcp2.ConnectionFactory;
import org.apache.commons.dbcp2.DriverManagerConnectionFactory;
import org.apache.commons.dbcp2.PoolableConnection;
import org.apache.commons.dbcp2.PoolableConnectionFactory;
import org.apache.commons.dbcp2.PoolingDataSource;
import org.apache.commons.pool2.ObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import java.util.Properties;

@Configuration
@EnableConfigurationProperties
@EnableTransactionManagement
@ConfigurationProperties(prefix = "template")
public class ApplicationConfiguration {

    // Configurable properties
    private String dbUsername;
    private String dbPassword;
    private String dbUri;
    // These properties control code in the StartupInitializer. We would not use these in production, but they
    // are handy to set for development and testing. There are only three interesting states:
    // 1. initialize is true; upgrade is irrelevant - initialize and recreate an empty database
    // 2. initialize is false; upgrade is true - apply changesets to an existing database
    // 3. initialize is false; upgrade is false - do nothing to the database
    private boolean dbInitializeOnStart;
    private boolean dbUpgradeOnStart;

    // Not a property
    private PoolingDataSource<PoolableConnection> dataSource;

    public String getDbUsername() {
        return dbUsername;
    }

    public void setDbUsername(String dbUsername) {
        this.dbUsername = dbUsername;
    }

    public String getDbPassword() {
        return dbPassword;
    }

    public void setDbPassword(String dbPassword) {
        this.dbPassword = dbPassword;
    }

    public String getDbUri() {
        return dbUri;
    }

    public void setDbUri(String dbUri) {
        this.dbUri = dbUri;
    }

    public boolean isDbInitializeOnStart() {
        return dbInitializeOnStart;
    }

    public void setDbInitializeOnStart(boolean dbInitializeOnStart) {
        this.dbInitializeOnStart = dbInitializeOnStart;
    }

    public boolean isDbUpgradeOnStart() {
        return dbUpgradeOnStart;
    }

    public void setDbUpgradeOnStart(boolean dbUpgradeOnStart) {
        this.dbUpgradeOnStart = dbUpgradeOnStart;
    }

    public PoolingDataSource<PoolableConnection> getDataSource() {
        // Lazy allocation of the data source
        if (dataSource == null) {
            Properties props = new Properties();
            props.setProperty("user", getDbUsername());
            props.setProperty("password", getDbPassword());

            ConnectionFactory connectionFactory = new DriverManagerConnectionFactory(getDbUri(), props);

            PoolableConnectionFactory poolableConnectionFactory =
                    new PoolableConnectionFactory(connectionFactory, null);

            ObjectPool<PoolableConnection> connectionPool =
                    new GenericObjectPool<>(poolableConnectionFactory);

            poolableConnectionFactory.setPool(connectionPool);

            dataSource = new PoolingDataSource<>(connectionPool);
        }
        return dataSource;
    }

    // This bean plus the @EnableTransactionManagement annotation above enables the use of the
    // @Transaction annotation to control the transaction properties of the data source.
    @Bean("transactionManager")
    public PlatformTransactionManager getTransactionManager() {
        return new DataSourceTransactionManager(getDataSource());
    }

    @Bean("jdbcTemplate")
    public NamedParameterJdbcTemplate getNamedParameterJdbcTemplate() {
        return new NamedParameterJdbcTemplate(getDataSource());
    }

    @Bean("objectMapper")
    public ObjectMapper objectMapper() {
        return new ObjectMapper()
                .registerModule(new ParameterNamesModule())
                .registerModule(new Jdk8Module())
                .registerModule(new JavaTimeModule());
    }

    // This is a "magic bean": It supplies a method that Spring calls after the application is setup,
    // but before the port is opened for business. That lets us do database migration and stairway
    // initialization on a system that is otherwise fully configured. The rule of thumb is that all
    // bean initialization should avoid database access. If there is additional database work to be
    // done, it should happen inside this method.
    @Bean
    public SmartInitializingSingleton postSetupInitialization(ApplicationContext applicationContext) {
        return () -> {
            StartupInitializer.initialize(applicationContext);
        };
    }

}
