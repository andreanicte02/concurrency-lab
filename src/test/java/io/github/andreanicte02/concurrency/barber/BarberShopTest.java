package io.github.andreanicte02.concurrency.barber;

import io.github.andreanicte02.concurrency.barber.BarberShop.Event;
import io.github.andreanicte02.concurrency.barber.BarberShop.EventType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(10)
class BarberShopTest {

    private static BarberShop.Result busyShop() throws InterruptedException {
        // Customers arrive much faster than haircuts finish, so the waiting room overflows.
        return BarberShop.simulate(new BarberShop.Config(15, 2, 40, 5, 7));
    }

    @Test
    void everyCustomerIsServedOrTurnedAway() throws InterruptedException {
        BarberShop.Result r = busyShop();
        assertEquals(15, r.served() + r.turnedAway());
        assertTrue(r.turnedAway() > 0, "expected an overflowing waiting room");
    }

    @Test
    void barberCutsOneCustomerAtATimeAndOnlyThoseWhoSat() throws InterruptedException {
        BarberShop.Result r = busyShop();
        Set<Integer> seated = new HashSet<>();
        Integer inChair = null;
        for (Event e : r.events()) {
            switch (e.type()) {
                case SIT -> seated.add(e.customer());
                case START_CUT -> {
                    assertEquals(null, inChair, "started a cut while busy");
                    assertTrue(seated.contains(e.customer()), "cut someone who never sat");
                    inChair = e.customer();
                }
                case FINISH_CUT -> {
                    assertEquals(inChair, e.customer());
                    inChair = null;
                }
                default -> { }
            }
        }
    }

    @Test
    void waitingRoomNeverExceedsChairs() throws InterruptedException {
        BarberShop.Result r = busyShop();
        assertTrue(r.events().stream().allMatch(e -> e.waiting() <= r.config().chairs()));
    }

    @Test
    void idleBarberSleepsInsteadOfSpinning() throws InterruptedException {
        // One customer, slow arrivals: the barber must go to sleep and be woken up.
        BarberShop.Result r = BarberShop.simulate(new BarberShop.Config(1, 1, 10, 50, 1));
        List<EventType> barberEvents = r.events().stream()
                .filter(e -> e.customer() < 0).map(Event::type).toList();
        assertEquals(EventType.SLEEP, barberEvents.getFirst());
        assertTrue(barberEvents.contains(EventType.WAKE));
        assertEquals(EventType.CLOSE, barberEvents.getLast());
        assertFalse(r.events().isEmpty());
    }
}
