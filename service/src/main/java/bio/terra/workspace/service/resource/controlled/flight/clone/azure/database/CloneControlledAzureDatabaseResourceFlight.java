package bio.terra.workspace.service.resource.controlled.flight.clone.azure.database;

import bio.terra.stairway.Flight;
import bio.terra.stairway.FlightMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CloneControlledAzureDatabaseResourceFlight extends Flight {
  private static final Logger logger =
      LoggerFactory.getLogger(CloneControlledAzureDatabaseResourceFlight.class);

  public CloneControlledAzureDatabaseResourceFlight(
      FlightMap inputParameters, Object applicationContext) {
    super(inputParameters, applicationContext);

    logger.info(
        "(sanity check) CloneControlledAzureDatabaseResourceFlight constructor has been called");

    // Flight plan
    // 1. Check user has read access to source database
    // 2. Check user has write access to destination database server(?) (are we in the right
    // abstraction layer to be checking the db server? or should I be checking the workspace?)
    // 2. Gather controlled resource metadata for source object
    // 3. Check if the database is already present
    // 4. ...? Copy container definition to new container resource
    // 5. ...? If referenced, bail out as unsupported.
    // 6. pg_dump source database to destination workspace blob storage
  }
}
