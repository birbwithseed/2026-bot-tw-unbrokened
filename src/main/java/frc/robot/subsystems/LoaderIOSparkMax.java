package frc.robot.subsystems;

import com.revrobotics.spark.SparkBase.PersistMode;
import com.revrobotics.spark.SparkBase.ResetMode;
import com.revrobotics.spark.SparkLowLevel.MotorType;
import com.revrobotics.spark.SparkMax;
import com.revrobotics.spark.config.SparkMaxConfig;
import frc.robot.constants.Constants.CANConstants;

public class LoaderIOSparkMax implements LoaderIO {
  private final SparkMax m_loaderMotor1;
  private final SparkMax m_loaderMotor2;
  private final SparkMax m_loaderMotor3;
  private static final int CURRENT_LIMIT_AMPS = 30;

  @SuppressWarnings("removal")
  public LoaderIOSparkMax() {
    m_loaderMotor1 = new SparkMax(CANConstants.MOTOR_TURRET_CHANNEL_ID, MotorType.kBrushless);
    m_loaderMotor2 = new SparkMax(CANConstants.MOTOR_SPINDEXER_ID, MotorType.kBrushless);
    m_loaderMotor3 = new SparkMax(CANConstants.MOTOR_LOADER_ID, MotorType.kBrushless);

    SparkMaxConfig config = new SparkMaxConfig();
    config.idleMode(SparkMaxConfig.IdleMode.kBrake);
    config.smartCurrentLimit(CURRENT_LIMIT_AMPS);
    config.disableFollowerMode();

    m_loaderMotor1.configure(
        config, ResetMode.kResetSafeParameters, PersistMode.kPersistParameters);
    m_loaderMotor2.configure(
        config, ResetMode.kResetSafeParameters, PersistMode.kPersistParameters);
    m_loaderMotor3.configure(
        config, ResetMode.kResetSafeParameters, PersistMode.kPersistParameters);
  }

  @Override
  public void updateInputs(LoaderIOInputs inputs) {
    inputs.appliedVolts = m_loaderMotor1.getAppliedOutput() * m_loaderMotor1.getBusVoltage();
    inputs.currentAmps =
        m_loaderMotor1.getOutputCurrent()
            + m_loaderMotor2.getOutputCurrent()
            + m_loaderMotor3.getOutputCurrent();
    inputs.velocityRPM = m_loaderMotor1.getEncoder().getVelocity();
  }

  @Override
  public void setVoltage(double volts) {
    m_loaderMotor1.setVoltage(volts);
    m_loaderMotor2.setVoltage(volts);
    m_loaderMotor3.setVoltage(volts);
  }

  @Override
  public void stop() {
    m_loaderMotor1.stopMotor();
    m_loaderMotor2.stopMotor();
    m_loaderMotor3.stopMotor();
  }
}
