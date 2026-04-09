package frc.robot;

import edu.wpi.first.wpilibj.DataLogManager;
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;
import edu.wpi.first.wpilibj2.command.Command;
import edu.wpi.first.wpilibj2.command.InstantCommand;
import edu.wpi.first.wpilibj2.command.RunCommand;
import edu.wpi.first.wpilibj2.command.button.CommandJoystick;
import edu.wpi.first.wpilibj2.command.button.CommandXboxController;
import edu.wpi.first.wpilibj2.command.sysid.SysIdRoutine;
import frc.robot.commands.Autos;
import frc.robot.commands.DefaultDrive;
import frc.robot.commands.FireCommand;
import frc.robot.commands.UnjamIntakeCommand;
import frc.robot.constants.Constants;
import frc.robot.subsystems.CameraSubsystem;
import frc.robot.subsystems.DriveSubsystem;
import frc.robot.subsystems.FireControlSubsystem;
import frc.robot.subsystems.IntakeSubsystem;
import frc.robot.subsystems.LoaderSubsystem;
import frc.robot.subsystems.TurretSubsystem;

/**
 * The RobotContainer class is responsible for instantiating and configuring all robot subsystems,
 * setting up controller bindings, and managing the default and autonomous commands. This class
 * serves as the central hub for organizing the robot's command-based structure.
 */
public class RobotContainer {
  // The Controller (Port 0 is usually the first USB controller plugged in)
  private final CommandXboxController m_controller1 =
      new CommandXboxController(Constants.CONTROLLER_USB_INDEX);
  private final CommandJoystick m_flightstick =
      new CommandJoystick(Constants.FLIGHTSTICK_USB_INDEX);

  // Initialize subsystems
  private final DriveSubsystem m_driveSubsystem = new DriveSubsystem();
  private final CameraSubsystem m_cameraSubsystem = new CameraSubsystem(m_driveSubsystem);

  private final TurretSubsystem m_turretSubsystem =
      new TurretSubsystem(
          Constants.CURRENT_MODE == Constants.Mode.REAL
              ? new frc.robot.subsystems.TurretIOSparkMax()
              : new frc.robot.subsystems.TurretIO() {});
  private final FireControlSubsystem m_fireSubsystem =
      new FireControlSubsystem(
          Constants.CURRENT_MODE == Constants.Mode.REAL
              ? new frc.robot.subsystems.FireControlIOSparkMax()
              : new frc.robot.subsystems.FireControlIO() {});
  private final IntakeSubsystem m_intakeSubsystem =
      new IntakeSubsystem(
          Constants.CURRENT_MODE == Constants.Mode.REAL
              ? new frc.robot.subsystems.IntakeIOSparkMax()
              : new frc.robot.subsystems.IntakeIO() {});
  private final LoaderSubsystem m_loaderSubsystem =
      new LoaderSubsystem(
          Constants.CURRENT_MODE == Constants.Mode.REAL
              ? new frc.robot.subsystems.LoaderIOSparkMax()
              : new frc.robot.subsystems.LoaderIO() {});

  // Initialize Commands
  private final DefaultDrive m_defaultDrive =
      new DefaultDrive(
          m_driveSubsystem,
          this::getControllerLeftY,
          this::getControllerRightY,
          () -> m_controller1.leftBumper().getAsBoolean());

  public final boolean enableAutoProfiling = false;

  /**
   * Constructs a new RobotContainer.
   *
   * <p>This constructor initializes the robot's subsystems and configures controller bindings by
   * calling {@link #configureBindings()}. This setup ensures that the drivebase subsystem and
   * controller commands are properly initialized before the robot starts operating.
   */
  public RobotContainer() {

    // Initialize the autonomous command
    initializeAutonomous();
    // Setup on the fly path planning
    configureTeleopPaths();

    if (enableAutoProfiling) {
      // bindDriveSysIDCommands();
      bindDriveSysIDCommands();
      // bindElevatorSysIDCommands();
    } else {
      bindCommands();
    }

    frc.robot.utils.CompetitionDashboard.setup(
        Autos.getAutoChooser(), Autos.getSideChooser(), Autos.getDelayChooser(), m_driveSubsystem);
  }

  private void bindCommands() {
    // Controller Bindings
    m_controller1
        .rightBumper()
        .onTrue(new InstantCommand(() -> m_driveSubsystem.SwitchBrakemode()));

    // Intake
    m_controller1
        .a()
        .and(() -> !m_flightstick.button(Constants.JOYSTICK_DEFAULT_BUTTON).getAsBoolean())
        .toggleOnTrue(runIntake(1.0));
    m_controller1
        .leftTrigger()
        .and(() -> !m_flightstick.button(Constants.JOYSTICK_DEFAULT_BUTTON).getAsBoolean())
        .whileTrue(runIntake(-1.0));

    // Fire Override
    m_controller1
        .rightTrigger()
        .and(() -> !m_turretSubsystem.isUnwinding())
        .whileTrue(
            new FireCommand(
                m_fireSubsystem,
                m_loaderSubsystem,
                () -> 1.0,
                m_controller1.rightTrigger().and(() -> !m_turretSubsystem.isUnwinding())));

    // Default Drive
    m_driveSubsystem.setDefaultCommand(m_defaultDrive);
    // Joystick Bindings
    // (Removed queued shooter mode override)

    // Turret Default Command (Bind to X-axis of flight stick, capped at 80% speed)
    m_turretSubsystem.setDefaultCommand(
        new RunCommand(
            () -> m_turretSubsystem.setTurretSpeed(m_flightstick.getX() * 0.8), m_turretSubsystem));

    // Intake Default Command
    m_intakeSubsystem.setDefaultCommand(
        new RunCommand(
            () -> {
              m_intakeSubsystem.setRunSpeed(0.0);
              m_intakeSubsystem.setPivotSpeed(0.0);
            },
            m_intakeSubsystem));

    // Loader Default Command
    m_loaderSubsystem.setDefaultCommand(
        new RunCommand(() -> m_loaderSubsystem.stop(), m_loaderSubsystem));

    // Fire Control Command (Bind to Trigger / Button 1 of flight stick)
    // Run at Y-axis speed or SmartDashboard override while trigger is held. Loader feeds at 100%.
    SmartDashboard.putNumber("Regression Test Firing Speed Override", -1.0);
    m_flightstick
        .button(Constants.JOYSTICK_DEFAULT_BUTTON)
        .and(() -> !m_turretSubsystem.isUnwinding())
        .and(() -> Math.abs(m_driveSubsystem.getSpeeds().vxMetersPerSecond) < 0.1)
        .whileTrue(
            new FireCommand(
                m_fireSubsystem,
                m_loaderSubsystem,
                () -> {
                  double override =
                      SmartDashboard.getNumber("Regression Test Firing Speed Override", -1.0);
                  return override != -1.0 ? override : 1.0;
                },
                m_flightstick.button(Constants.JOYSTICK_DEFAULT_BUTTON)));

    // Intake on Flight Stick (Button 6) - Toggle
    m_flightstick.button(6).toggleOnTrue(runIntake(1.0));

    // Loader 1 & 2 on Flight Stick (Button 7) - Toggle
    m_flightstick.button(7).toggleOnTrue(runLoader(1.0));

    // Intake Pivot Manual Control (Buttons 9 and 10)
    m_flightstick
        .button(9)
        .whileTrue(
            new RunCommand(() -> m_intakeSubsystem.setPivotSpeed(-1.0), m_intakeSubsystem)
                .finallyDo(m_intakeSubsystem::stopPivot));
    m_flightstick
        .button(10)
        .whileTrue(
            new RunCommand(() -> m_intakeSubsystem.setPivotSpeed(1.0), m_intakeSubsystem)
                .finallyDo(m_intakeSubsystem::stopPivot));

    // Intake system operates on buttons now; the slider command is not bound.

    // Emergency Unjam (Button 12)
    m_flightstick.button(12).onTrue(new UnjamIntakeCommand(m_intakeSubsystem));
  }

  private Command runIntake(double speed) {
    return new RunCommand(() -> m_intakeSubsystem.setRunSpeed(speed), m_intakeSubsystem)
        .finallyDo(m_intakeSubsystem::stop);
  }

  private Command runLoader(double speed) {
    return new RunCommand(() -> m_loaderSubsystem.setLoaderSpeed(speed), m_loaderSubsystem)
        .finallyDo(m_loaderSubsystem::stop);
  }

  public void disabledInit() {
    stopAll();
  }

  public void stopAll() {
    m_driveSubsystem.stop();
    m_intakeSubsystem.stop();
    m_intakeSubsystem.stopPivot();
    m_fireSubsystem.stop();
    m_loaderSubsystem.stop();
    m_turretSubsystem.stop();
  }

  public edu.wpi.first.wpilibj2.command.Command getPitHealthCheckCommand() {
    return new frc.robot.commands.PitHealthCheckCommand(
        m_driveSubsystem, m_intakeSubsystem, m_fireSubsystem, m_loaderSubsystem, m_turretSubsystem);
  }

  private void initializeAutonomous() {
    Autos.init(
        m_driveSubsystem,
        m_cameraSubsystem,
        m_fireSubsystem,
        m_loaderSubsystem,
        m_intakeSubsystem,
        m_turretSubsystem);
  }

  private void bindDriveSysIDCommands() {
    m_controller1.a().whileTrue(m_driveSubsystem.sysIdQuasistatic(SysIdRoutine.Direction.kForward));
    m_controller1.b().whileTrue(m_driveSubsystem.sysIdQuasistatic(SysIdRoutine.Direction.kReverse));
    m_controller1.x().whileTrue(m_driveSubsystem.sysIdDynamic(SysIdRoutine.Direction.kForward));
    m_controller1.y().whileTrue(m_driveSubsystem.sysIdDynamic(SysIdRoutine.Direction.kReverse));
    m_controller1.leftTrigger().whileTrue(new InstantCommand(() -> DataLogManager.stop()));
  }

  private void configureTeleopPaths() {
    // TODO: Write new paths
    // EX
    // PathPlannerPath ampPath = PathPlannerPath.fromPathFile("TeleopAmpPath");

    // m_driveToAmp = AutoBuilder.pathfindThenFollowPath(ampPath, constraints);
  }

  public double getControllerRightY() {
    double y = -m_controller1.getRightY();
    return y;
  }

  public double getControllerLeftY() {
    double y = -m_controller1.getLeftY();
    return y;
  }

  public double GetFlightStickY() {
    return m_flightstick.getY();
  }

  // for autonomous
  public DefaultDrive getM_defaultDrive() {
    return m_defaultDrive;
  }

  // for future SmartDashboard uses.
  public CommandXboxController getM_controller1() {
    return this.m_controller1;
  }

  // for smart dashboard.
  public CommandJoystick getFlightStick() {
    return this.m_flightstick;
  }

  /**
   * Returns the command to run during the autonomous period.
   *
   * @return the autonomous command to execute
   */
  public Command getAutonomousCommand() {
    return Autos.getAutonomousCommand();
  }

  public void periodic() {
    // This method will be called once per scheduler run (Only for inter subsystem state updating)
  }
}
