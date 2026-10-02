package com.example.platform.workerfabric.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** P2-5b-2a-2a-3a2: bounded V1 reservation feasibility fails closed. */
class BoundedV1ReservationFeasibilityTest {

    @Test
    void reservationFeasibilityIsUnknownWithoutAReservationView() {
        assertThat(BoundedV1ReservationFeasibility.value())
                .isEqualTo(ReservationFeasibility.UNKNOWN);
    }

    @Test
    void reservationFeasibilityValueIsDeterministic() {
        assertThat(BoundedV1ReservationFeasibility.value())
                .isEqualTo(BoundedV1ReservationFeasibility.value());
    }

    @Test
    void reservationFeasibilityEnumRetainsItsClosedValueSet() {
        assertThat(ReservationFeasibility.values())
                .containsExactly(ReservationFeasibility.FEASIBLE,
                        ReservationFeasibility.CONFLICT,
                        ReservationFeasibility.UNKNOWN);
    }
}
