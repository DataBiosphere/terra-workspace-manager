package bio.terra.workspace.app;

import bio.terra.common.db.BaseDatabaseProperties;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import javax.annotation.PreDestroy;
import javax.sql.DataSource;
import org.apache.commons.dbcp2.ConnectionFactory;
import org.apache.commons.dbcp2.DriverManagerConnectionFactory;
import org.apache.commons.dbcp2.PoolableConnection;
import org.apache.commons.dbcp2.PoolableConnectionFactory;
import org.apache.commons.dbcp2.PoolingDataSource;
import org.apache.commons.pool2.ObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * During JUnit testing, when the application context gets refreshed the connection pools are
 * abandoned. As a result, we leak database connections. This class is a replacement for
 * DataSourceInitializer This class tracks the object pools holding the connections and is able to
 * close them. It is a bean so can receive the PreDestroy call and do close at that time.
 *
 * <p>TODO: If we like this solution, I will implement this in the common library
 */
@Component
public class DataSourceManager {
  private static final Logger logger = LoggerFactory.getLogger(DataSourceManager.class);
  private List<ObjectPool<PoolableConnection>> connectionPools = new ArrayList<>();

  @PreDestroy
  public void destroy() {
    logger.warn("<<< Closing all connection pools >>>");
    for (var pool : connectionPools) {
      logger.info("Pool active {} idle {}", pool.getNumActive(), pool.getNumIdle());
      pool.close();
    }
    connectionPools = new ArrayList<>();
  }

  public DataSource initializeDataSource(BaseDatabaseProperties baseDatabaseProperties) {
    Properties props = new Properties();
    props.setProperty("user", baseDatabaseProperties.getUsername());
    props.setProperty("password", baseDatabaseProperties.getPassword());

    ConnectionFactory connectionFactory =
        new DriverManagerConnectionFactory(baseDatabaseProperties.getUri(), props);

    PoolableConnectionFactory poolableConnectionFactory =
        new PoolableConnectionFactory(connectionFactory, null);

    GenericObjectPoolConfig<PoolableConnection> config = new GenericObjectPoolConfig<>();
    config.setJmxEnabled(baseDatabaseProperties.isJmxEnabled());
    config.setMaxTotal(baseDatabaseProperties.getPoolMaxTotal());
    config.setMaxIdle(baseDatabaseProperties.getPoolMaxIdle());
    ObjectPool<PoolableConnection> connectionPool =
        new GenericObjectPool<>(poolableConnectionFactory, config);
    connectionPools.add(connectionPool);

    poolableConnectionFactory.setPool(connectionPool);

    return new PoolingDataSource<>(connectionPool);
  }
}
