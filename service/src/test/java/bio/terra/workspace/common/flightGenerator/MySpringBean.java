package bio.terra.workspace.common.flightGenerator;

public interface MySpringBean {
  @NoUndo @NoRetry
  void doSomething();

  @UndoMethod("testUndo") @NoRetry
  void throwException();

  void testUndo();
}
