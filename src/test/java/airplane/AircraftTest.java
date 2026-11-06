package airplane;

import airplane.components.Engine;
import airplane.components.FuelSystem;
import airplane.components.LandingGear;
import airplane.components.NavigationSystem;
import airplane.exceptions.FlightException;
import airplane.models.FlightStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

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

    @Test
    void prepareForTakeoff_whenAllChecksPass_statusBecomesReady() {
        when(engine.isOperational()).thenReturn(true);
        when(fuelSystem.hasEnoughFuel()).thenReturn(true);
        when(navigationSystem.isRouteAvailable()).thenReturn(true);

        aircraft.prepareForTakeoff();

        assertEquals(FlightStatus.READY, aircraft.getStatus());
    }

    @Test
    void prepareForTakeoff_whenEngineNotOperational_throwsAndDoesNotCheckFuel() {
        when(engine.isOperational()).thenReturn(false);

        assertThrows(FlightException.class, () -> aircraft.prepareForTakeoff());

        assertEquals(FlightStatus.GROUNDED, aircraft.getStatus());
        verify(fuelSystem, never()).hasEnoughFuel();
        verify(navigationSystem, never()).isRouteAvailable();
    }

    @Test
    void prepareForTakeoff_whenEngineCheckThrows_propagatesExceptionAndStaysGrounded() {
        when(engine.isOperational()).thenThrow(IllegalStateException.class);

        assertThrows(IllegalStateException.class, () -> aircraft.prepareForTakeoff());

        assertEquals(FlightStatus.GROUNDED, aircraft.getStatus());
    }

    @Test
    void takeOff_whenReady_startsEngineAndRetractsGearOnce() {
        makeAircraftReady();

        aircraft.takeOff();

        verify(engine, times(1)).start();
        verify(landingGear, times(1)).retract();
        assertEquals(FlightStatus.IN_FLIGHT, aircraft.getStatus());
    }

    private void makeAircraftReady() {
        when(engine.isOperational()).thenReturn(true);
        when(fuelSystem.hasEnoughFuel()).thenReturn(true);
        when(navigationSystem.isRouteAvailable()).thenReturn(true);
        aircraft.prepareForTakeoff();
    }
}
