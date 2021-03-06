package team8.tuner.controller;

import com.ctre.phoenix.ErrorCode;
import com.ctre.phoenix.motorcontrol.ControlMode;
import com.ctre.phoenix.motorcontrol.DemandType;
import com.ctre.phoenix.motorcontrol.InvertType;
import com.ctre.phoenix.motorcontrol.NeutralMode;
import com.ctre.phoenix.motorcontrol.can.BaseMotorController;
import team8.tuner.config.Config.MasterConfig;
import team8.tuner.config.Config.SimpleConfig;

import java.util.Optional;

import static team8.tuner.Robot.kPidSlotIndex;

public abstract class CTREControllerBase<TController extends BaseMotorController> extends ControllerBase<TController> {

	protected static final int kTimeout = 100;

	CTREControllerBase(SimpleConfig config) {
		super(config.id);
		mController = controllerFactory().apply(config.id);
		check(mController.configFactoryDefault(kTimeout), "factory defaults");
		mController.setNeutralMode(config.isBraked ? NeutralMode.Brake : NeutralMode.Coast);
		if (config instanceof MasterConfig) {
			var masterConfig = (MasterConfig) config;
			configForwardSoftLimit(masterConfig.forwardLimit);
			configReverseSoftLimit(masterConfig.reverseLimit);
			mController.setInverted(masterConfig.isInverted);
			mController.setSensorPhase(masterConfig.isSensorInverted);
			mController.enableVoltageCompensation(true);
			check(mController.configVoltageCompSaturation(masterConfig.voltageCompensation, kTimeout), "voltage compensation");
			check(mController.configOpenloopRamp(masterConfig.ramp), "open loop ramp");
			check(mController.configClosedloopRamp(masterConfig.ramp), "closed loop ramp");
			check(mController.config_kP(kPidSlotIndex, masterConfig.gains.p, kTimeout), "p");
			check(mController.config_kI(kPidSlotIndex, masterConfig.gains.i, kTimeout), "i");
			check(mController.config_kD(kPidSlotIndex, masterConfig.gains.d, kTimeout), "d");
			check(mController.config_kF(kPidSlotIndex, masterConfig.gains.f, kTimeout), "f");
			check(mController.configMaxIntegralAccumulator(kPidSlotIndex, masterConfig.gains.iMax, kTimeout), "i max");
			check(mController.config_IntegralZone(kPidSlotIndex, round(masterConfig.gains.iZone), kTimeout), "i zone");
			check(mController.configPeakOutputForward(masterConfig.maximumOutput, kTimeout), "peak forward output");
			check(mController.configPeakOutputReverse(masterConfig.minimumOutput, kTimeout), "peak reverse output");
			check(mController.configMotionSCurveStrength(3, kTimeout), "s curve");
			check(mController.configMotionCruiseVelocity(round(masterConfig.gains.v), kTimeout), "max velocity");
			check(mController.configMotionAcceleration(round(masterConfig.gains.a), kTimeout), "max acceleration");
			check(mController.configAllowableClosedloopError(kPidSlotIndex, round(masterConfig.gains.allowableError), kTimeout), "allowable error");
			check(mController.setSelectedSensorPosition(round(masterConfig.startingPosition), 0, kTimeout), "starting position");
		}
	}

	protected static int round(double d) {
		return (int) Math.round(d);
	}

	private void configForwardSoftLimit(Float configLimit) {
		var limit = Optional.ofNullable(configLimit);
		check(mController.configForwardSoftLimitEnable(limit.isPresent(), kTimeout), "enable forward soft limit");
		limit.ifPresent(softLimit -> check(mController.configForwardSoftLimitThreshold(round(softLimit), kTimeout), "set forward soft limit"));
	}

	private void configReverseSoftLimit(Float configLimit) {
		var limit = Optional.ofNullable(configLimit);
		check(mController.configReverseSoftLimitEnable(limit.isPresent(), kTimeout), "enable forward soft limit");
		limit.ifPresent(softLimit -> check(mController.configReverseSoftLimitThreshold(round(softLimit), kTimeout), "set forward soft limit"));
	}

	protected void check(ErrorCode error, String name) {
		if (error != ErrorCode.OK) {
			var message = String.format("Failed to set %s! Error: %s", name, error);
			System.err.println(message);
			throw new RuntimeException(message);
		}
	}

	@Override
	public void follow(Controller master, boolean isInverted) {
		try {
			var masterCtre = (CTREControllerBase<? extends BaseMotorController>) master;
			mController.follow(masterCtre.mController);
			mController.setInverted(isInverted ? InvertType.OpposeMaster : InvertType.FollowMaster);
		} catch (Exception exception) {
			throw new RuntimeException("Could not follow!", exception);
		}
	}

	@Override
	public void setOutput(ControlMode controlMode, double reference, double arbitraryFeedForward) {
		com.ctre.phoenix.motorcontrol.ControlMode controlType;
		switch (controlMode) {
			case DISABLED:
				controlType = com.ctre.phoenix.motorcontrol.ControlMode.Disabled;
				break;
			case PERCENT_OUTPUT:
				controlType = com.ctre.phoenix.motorcontrol.ControlMode.PercentOutput;
				break;
			case SMART_MOTION:
				controlType = com.ctre.phoenix.motorcontrol.ControlMode.MotionMagic;
				break;
			case SMART_VELOCITY:
				controlType = com.ctre.phoenix.motorcontrol.ControlMode.Velocity;
				break;
			default:
				throw new IllegalStateException("Unknown control mode!");
		}
		mController.set(controlType, reference, DemandType.ArbitraryFeedForward, arbitraryFeedForward);
	}

	@Override
	public double getPosition() {
		return mController.getSelectedSensorPosition();
	}

	@Override
	public double getVelocity() {
		return mController.getSelectedSensorVelocity();
	}

	@Override
	public double getAppliedPercentOutput() {
		return mController.getMotorOutputPercent();
	}
}
