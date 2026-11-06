package airplane;

import airplane.components.Engine;
import airplane.components.FuelSystem;
import airplane.components.LandingGear;
import airplane.components.NavigationSystem;
import airplane.exceptions.FlightException;
import airplane.models.FlightStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AircraftTest {
    private Engine engine;
    private FuelSystem fuelSystem;
    private LandingGear landingGear;
    private NavigationSystem navigationSystem;
    private Aircraft aircraft;

    @BeforeEach
    void setUp() {
        engine = mock(Engine.class);
        fuelSystem = mock(FuelSystem.class);
        landingGear = mock(LandingGear.class);
        navigationSystem = mock(NavigationSystem.class);

        aircraft = new Aircraft(engine, fuelSystem, landingGear, navigationSystem);
    }

    private void prepareAircraftForTakeoff() {
        when(engine.isOperational()).thenReturn(true);
        when(fuelSystem.hasEnoughFuel()).thenReturn(true);
        when(navigationSystem.isRouteAvailable()).thenReturn(true);
        aircraft.prepareForTakeoff();
    }

    @Test
    @DisplayName("LandingGear retract failure during takeoff sets status to ABORTED and throws FlightException")
    void takeOff_WhenLandingGearRetractFails_ShouldAbortAndThrowFlightException() {
        prepareAircraftForTakeoff();
        doThrow(new RuntimeException("Hydraulic failure in landing gear"))
                .when(landingGear).retract();

        FlightException exception = assertThrows(FlightException.class, () -> aircraft.takeOff());
        assertEquals("Takeoff failed.", exception.getMessage());
        assertEquals(FlightStatus.ABORTED, aircraft.getStatus());
    }

    @Test
    @DisplayName("Two-stage cruise where first succeeds and second fails due to fuel")
    void cruise_WhenSecondStageRunsOutOfFuel_ShouldCruiseFirstThenEmergencyLand() {
        prepareAircraftForTakeoff();
        aircraft.takeOff();
        doNothing()
                .doThrow(new IllegalStateException("Not enough fuel for second stage."))
                .when(fuelSystem).consumeFuel(any(Double.class));

        aircraft.cruise(30.0);
        assertEquals(FlightStatus.IN_FLIGHT, aircraft.getStatus());

        assertThrows(FlightException.class, () -> aircraft.cruise(80.0));
        assertEquals(FlightStatus.LANDED, aircraft.getStatus());
        verify(landingGear, times(1)).deploy();
        verify(engine, times(1)).stop();
    }

    @Test
    @DisplayName("Engine failure mid-flight triggers emergency landing without consuming fuel")
    void cruise_WhenEngineFailsMidFlight_ShouldPerformEmergencyLanding() {
        prepareAircraftForTakeoff();
        aircraft.takeOff();

        reset(engine);
        when(engine.isOperational()).thenReturn(false);

        aircraft.cruise(40.0);
        assertEquals(FlightStatus.LANDED, aircraft.getStatus());
        verify(fuelSystem, never()).consumeFuel(any(Double.class));
        verify(landingGear, times(1)).deploy();
        verify(engine, times(1)).stop();
    }

    @Test
    @DisplayName("Normal cruise consumes fuel and maintains IN_FLIGHT status")
    void cruise_WhenEngineOperationalAndFuelSufficient_ShouldConsumeFuelNormally() {
        prepareAircraftForTakeoff();
        aircraft.takeOff();

        doNothing().when(fuelSystem).consumeFuel(any(Double.class));
        aircraft.cruise(50.0);

        assertEquals(FlightStatus.IN_FLIGHT, aircraft.getStatus());
        verify(fuelSystem, times(1)).consumeFuel(50.0);
        verify(landingGear, never()).deploy();
        verify(engine, never()).stop();
    }

    @Test
    @DisplayName("All preflight checks passing sets status to READY without starting the flight")
    void prepareForTakeoff_WhenAllChecksPass_ShouldBecomeReadyWithoutStartingEngine() {
        when(engine.isOperational()).thenReturn(true);
        when(fuelSystem.hasEnoughFuel()).thenReturn(true);
        when(navigationSystem.isRouteAvailable()).thenReturn(true);

        aircraft.prepareForTakeoff();

        assertEquals(FlightStatus.READY, aircraft.getStatus());
        verify(engine, never()).start();
        verify(landingGear, never()).retract();
    }

    @Test
    @DisplayName("Non-operational engine stops preflight before fuel and route are checked")
    void prepareForTakeoff_WhenEngineNotOperational_ShouldThrowAndSkipRemainingChecks() {
        when(engine.isOperational()).thenReturn(false);

        FlightException exception = assertThrows(FlightException.class, () -> aircraft.prepareForTakeoff());

        assertEquals("Cannot prepare for takeoff: engine is not operational.", exception.getMessage());
        assertEquals(FlightStatus.GROUNDED, aircraft.getStatus());
        verify(fuelSystem, never()).hasEnoughFuel();
        verify(navigationSystem, never()).isRouteAvailable();
    }

    @Test
    @DisplayName("Exception from the engine check reaches the caller unchanged")
    void prepareForTakeoff_WhenEngineCheckThrows_ShouldPropagateSameExceptionAndStayGrounded() {
        IllegalStateException sensorFailure = new IllegalStateException("Engine sensor failure");
        when(engine.isOperational()).thenThrow(sensorFailure);

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> aircraft.prepareForTakeoff());

        assertSame(sensorFailure, exception);
        assertEquals(FlightStatus.GROUNDED, aircraft.getStatus());
        verify(fuelSystem, never()).hasEnoughFuel();
    }

    @Test
    @DisplayName("Takeoff starts the engine first, then retracts the gear exactly once")
    void takeOff_WhenReady_ShouldStartEngineThenRetractGear() {
        prepareAircraftForTakeoff();

        aircraft.takeOff();

        InOrder inOrder = inOrder(engine, landingGear);
        inOrder.verify(engine, times(1)).start();
        inOrder.verify(landingGear, times(1)).retract();
        assertEquals(FlightStatus.IN_FLIGHT, aircraft.getStatus());
    }

    @Test
    @DisplayName("Takeoff before preflight is rejected and touches no component")
    void takeOff_WhenNotPrepared_ShouldThrowAndNotTouchComponents() {
        assertThrows(FlightException.class, () -> aircraft.takeOff());

        assertEquals(FlightStatus.GROUNDED, aircraft.getStatus());
        verifyNoInteractions(engine, landingGear);
    }

    @Test
    @DisplayName("Normal landing deploys the gear first, then stops the engine")
    void land_WhenInFlight_ShouldDeployGearThenStopEngine() {
        prepareAircraftForTakeoff();
        aircraft.takeOff();

        aircraft.land();

        InOrder inOrder = inOrder(landingGear, engine);
        inOrder.verify(landingGear, times(1)).deploy();
        inOrder.verify(engine, times(1)).stop();
        assertEquals(FlightStatus.LANDED, aircraft.getStatus());
    }
}
