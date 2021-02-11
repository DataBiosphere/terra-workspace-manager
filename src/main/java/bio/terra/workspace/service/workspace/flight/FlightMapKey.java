package bio.terra.workspace.service.workspace.flight;

public interface FlightMapKey {
  String getKey();

  Class<?> getKlass();

  //  default <T> T getFromMap(FlightMap flightMap) {
  //    return flightMap.get(getKey(), getKlass());
  //  }
}
