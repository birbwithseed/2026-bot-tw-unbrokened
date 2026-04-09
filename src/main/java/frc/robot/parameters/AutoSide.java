package frc.robot.parameters;

public enum AutoSide {
  LEFT("Left"),
  RIGHT("Right");

  private final String displayName;

  AutoSide(String displayName) {
    this.displayName = displayName;
  }

  @Override
  public String toString() {
    return displayName;
  }
}
