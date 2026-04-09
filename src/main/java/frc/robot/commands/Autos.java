package frc.robot.commands;

import com.pathplanner.lib.auto.NamedCommands;
import com.pathplanner.lib.commands.PathPlannerAuto;
import edu.wpi.first.wpilibj.Alert;
import edu.wpi.first.wpilibj.Alert.AlertType;
import edu.wpi.first.wpilibj.Filesystem;
import edu.wpi.first.wpilibj.smartdashboard.SendableChooser;
import edu.wpi.first.wpilibj2.command.Command;
import edu.wpi.first.wpilibj2.command.Commands;
import frc.robot.constants.SpeedConstants;
import frc.robot.parameters.AutoSide;
import frc.robot.subsystems.CameraSubsystem;
import frc.robot.subsystems.DriveSubsystem;
import frc.robot.subsystems.FireControlSubsystem;
import frc.robot.subsystems.IntakeSubsystem;
import frc.robot.subsystems.LoaderSubsystem;
import frc.robot.subsystems.TurretSubsystem;
import java.io.File;
import java.util.Arrays;
import java.util.Set;

public final class Autos {
  private static final String AUTO_FILE_TYPE = ".auto";
  private static final String NONE_AUTO = "None";
  private static final double AUTO_INTAKE_TIME_SECONDS = 1.5;
  private static final double AUTO_TURRET_ANGLE_DEGREES = 0.0;
  private static final File DEPLOY_AUTOS_DIR =
      new File(Filesystem.getDeployDirectory(), "pathplanner/autos");
  private static final File SOURCE_AUTOS_DIR = new File("src/main/deploy/pathplanner/autos");

  private static final SendableChooser<String> autoChooser = new SendableChooser<>();
  private static final SendableChooser<AutoSide> sideChooser = new SendableChooser<>();
  private static final SendableChooser<Integer> delayChooser = new SendableChooser<>();

  private static final Alert invalidAutoAlert =
      new Alert(
          "Invalid auto/side combination selected. No autonomous command will run.",
          AlertType.kError);

  private static boolean initialized = false;

  public static void init(
      DriveSubsystem driveSubsystem,
      CameraSubsystem cameraSubsystem,
      FireControlSubsystem fireSubsystem,
      LoaderSubsystem loaderSubsystem,
      IntakeSubsystem intakeSubsystem,
      TurretSubsystem turretSubsystem) {
    if (initialized) {
      return;
    }

    registerNamedCommands(
        driveSubsystem,
        cameraSubsystem,
        fireSubsystem,
        loaderSubsystem,
        intakeSubsystem,
        turretSubsystem);
    populateAutoChooser();
    populateSideChooser();
    populateDelayChooser();
    initialized = true;
  }

  private static void registerNamedCommands(
      DriveSubsystem driveSubsystem,
      CameraSubsystem cameraSubsystem,
      FireControlSubsystem fireSubsystem,
      LoaderSubsystem loaderSubsystem,
      IntakeSubsystem intakeSubsystem,
      TurretSubsystem turretSubsystem) {
    NamedCommands.registerCommand(
        "BrakeCommand", Commands.runOnce(driveSubsystem::SetBrakemode, driveSubsystem));
    NamedCommands.registerCommand(
        "AimCommand",
        Commands.defer(
            () -> new AimCommand(driveSubsystem, cameraSubsystem),
            Set.of(driveSubsystem, cameraSubsystem)));
    NamedCommands.registerCommand(
        "ShootWithAutoRotation",
        Commands.defer(
            () -> buildShootCommand(fireSubsystem, loaderSubsystem, turretSubsystem),
            Set.of(fireSubsystem, loaderSubsystem, turretSubsystem)));
    NamedCommands.registerCommand(
        "IntakeArmBumpAngle", Commands.none().withName("IntakeArmBumpAngle"));
    NamedCommands.registerCommand(
        "ExtendAndIntake",
        Commands.defer(
            () -> buildTimedIntakeCommand(intakeSubsystem, "ExtendAndIntake"),
            Set.of(intakeSubsystem)));
    NamedCommands.registerCommand(
        "Intake",
        Commands.defer(
            () -> buildTimedIntakeCommand(intakeSubsystem, "Intake"), Set.of(intakeSubsystem)));
    NamedCommands.registerCommand("Extend", Commands.none().withName("Extend"));
    NamedCommands.registerCommand(
        "DisableIntake",
        Commands.runOnce(
                () -> {
                  intakeSubsystem.stop();
                  intakeSubsystem.stopPivot();
                },
                intakeSubsystem)
            .withName("DisableIntake"));
    NamedCommands.registerCommand(
        "RampUpShooter",
        Commands.runOnce(
                () -> fireSubsystem.setShooterRPM(SpeedConstants.FIRE_MAX_SPEED), fireSubsystem)
            .withName("RampUpShooter"));
    NamedCommands.registerCommand(
        "RampUpShooterForAutoScore",
        Commands.runOnce(
                () -> fireSubsystem.setShooterRPM(SpeedConstants.FIRE_MAX_SPEED), fireSubsystem)
            .withName("RampUpShooterForAutoScore"));
  }

  private static Command buildShootCommand(
      FireControlSubsystem fireSubsystem,
      LoaderSubsystem loaderSubsystem,
      TurretSubsystem turretSubsystem) {
    Command holdTurretAtGoal =
        Commands.sequence(
            new SetTurretPositionCommand(turretSubsystem, AUTO_TURRET_ANGLE_DEGREES),
            Commands.idle(turretSubsystem));

    return Commands.parallel(
            holdTurretAtGoal,
            new FireCommand(fireSubsystem, loaderSubsystem, () -> 1.0, () -> true))
        .withName("ShootWithAutoRotation");
  }

  private static Command buildTimedIntakeCommand(
      IntakeSubsystem intakeSubsystem, String commandName) {
    return Commands.startEnd(
            () -> intakeSubsystem.setRunSpeed(1.0),
            () -> {
              intakeSubsystem.stop();
              intakeSubsystem.stopPivot();
            },
            intakeSubsystem)
        .withTimeout(AUTO_INTAKE_TIME_SECONDS)
        .withName(commandName);
  }

  private static void populateAutoChooser() {
    autoChooser.setDefaultOption(NONE_AUTO, NONE_AUTO);

    String[] autoFiles = getAutosDirectory().list((dir, name) -> name.endsWith(AUTO_FILE_TYPE));
    if (autoFiles == null) {
      return;
    }

    Arrays.stream(autoFiles)
        .map(name -> name.substring(0, name.length() - AUTO_FILE_TYPE.length()))
        .sorted()
        .forEach(name -> autoChooser.addOption(name, name));
  }

  private static void populateSideChooser() {
    sideChooser.setDefaultOption(AutoSide.RIGHT.toString(), AutoSide.RIGHT);
    sideChooser.addOption(AutoSide.LEFT.toString(), AutoSide.LEFT);
  }

  private static void populateDelayChooser() {
    delayChooser.setDefaultOption("No Delay", 0);
    for (int seconds = 1; seconds <= 7; seconds++) {
      delayChooser.addOption(String.format("%d Second Delay", seconds), seconds);
    }
  }

  private static File getAutosDirectory() {
    return DEPLOY_AUTOS_DIR.exists() ? DEPLOY_AUTOS_DIR : SOURCE_AUTOS_DIR;
  }

  public static Command getAutonomousCommand() {
    String autoName = autoChooser.getSelected();
    if (autoName == null || NONE_AUTO.equals(autoName)) {
      invalidAutoAlert.set(false);
      return Commands.none().withName(NONE_AUTO);
    }

    AutoSide side = getSelectedSide();
    if (!isValidAuto(autoName, side)) {
      invalidAutoAlert.set(true);
      return Commands.none().withName(autoName + " (Invalid)");
    }

    invalidAutoAlert.set(false);
    return Commands.sequence(
            Commands.waitSeconds(getSelectedDelaySeconds()),
            new PathPlannerAuto(autoName, shouldMirror(autoName, side)))
        .withName(autoName);
  }

  private static AutoSide getSelectedSide() {
    AutoSide selectedSide = sideChooser.getSelected();
    return selectedSide != null ? selectedSide : AutoSide.RIGHT;
  }

  private static int getSelectedDelaySeconds() {
    Integer selectedDelay = delayChooser.getSelected();
    return selectedDelay != null ? selectedDelay : 0;
  }

  private static boolean isValidAuto(String autoName, AutoSide side) {
    if (autoName.contains("Outpost")) {
      return side == AutoSide.RIGHT;
    }

    if (autoName.contains("Depot")) {
      return side == AutoSide.LEFT;
    }

    return true;
  }

  private static boolean shouldMirror(String autoName, AutoSide side) {
    return side == AutoSide.LEFT && !autoName.contains("Depot");
  }

  public static SendableChooser<String> getAutoChooser() {
    return autoChooser;
  }

  public static SendableChooser<AutoSide> getSideChooser() {
    return sideChooser;
  }

  public static SendableChooser<Integer> getDelayChooser() {
    return delayChooser;
  }

  public static Alert getInvalidAutoAlert() {
    return invalidAutoAlert;
  }

  private Autos() {
    throw new UnsupportedOperationException("This is a utility class!");
  }
}
