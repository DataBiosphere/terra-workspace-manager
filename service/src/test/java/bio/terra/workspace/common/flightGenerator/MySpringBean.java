package bio.terra.workspace.common.flightGenerator;

public interface MySpringBean {
  @NoUndo
  void doSomething();

  @UndoMethod("testUndo")
  void throwException();

  void testUndo();
}
