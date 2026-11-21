package entity;

/**
 * Standalone checks for the dash dodge (Team CS): distance, easing,
 * direction, recharge and reset.
 */
public final class ShipDashTest {
    private static int checks;

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    /** Runs a whole dash and returns the pixels moved in each frame. */
    private static int[] runDash(ShipDash dash) {
        int[] steps = new int[ShipDash.DURATION_FRAMES];
        for (int i = 0; i < steps.length; i++) {
            steps[i] = dash.update();
            check(dash.isDashing(), "Still dashing after frame " + i);
        }
        // Still dashing after the last step, so that frame's hits miss.
        check(dash.update() == 0 && !dash.isDashing(), "Dash ends on the next update");
        return steps;
    }

    public static void main(String[] args) {
        ShipDash dash = new ShipDash();
        check(!dash.isDashing() && dash.getCharge() == 1, "Ready at the start");
        check(dash.update() == 0, "No movement while not dashing");
        check(ShipDash.DURATION_FRAMES == 11 && ShipDash.DISTANCE == 110
                && ShipDash.COOLDOWN_FRAMES == 90, "Values match the docs");

        // A dash covers the full distance to the right, fast first, then slower.
        check(dash.start(), "Dash starts when ready");
        check(!dash.start(), "No second dash while dashing");
        check(dash.getCharge() == 0, "No charge while dashing");
        int[] steps = runDash(dash);
        int sum = 0;
        for (int i = 0; i < steps.length; i++) {
            sum += steps[i];
            check(steps[i] >= 0, "Right dash never moves left");
            if (i > 0) check(steps[i] <= steps[i - 1], "Dash slows down, frame " + i);
        }
        check(sum == ShipDash.DISTANCE, "Right dash covers " + ShipDash.DISTANCE + " px, got " + sum);
        check(steps[0] > ShipDash.DISTANCE / ShipDash.DURATION_FRAMES, "First frame is faster than average");

        // Then it recharges before the next dash.
        check(!dash.start(), "No dash right after one");
        check(dash.getCharge() == 0, "Charge empty right after a dash");
        for (int i = 1; i < ShipDash.COOLDOWN_FRAMES; i++) dash.update();
        check(dash.getCharge() > 0.95 && dash.getCharge() < 1, "Charge almost full one frame early");
        check(!dash.start(), "Still recharging one frame early");
        dash.update();
        check(dash.getCharge() == 1, "Charge full after the cooldown");

        // Direction follows the last movement, and does not change mid-dash.
        dash.face(-1);
        check(dash.getDirection() == -1, "Faces left");
        dash.face(0);
        check(dash.getDirection() == -1, "No key keeps the last direction");
        check(dash.start(), "Dash starts after the cooldown");
        dash.face(1);
        check(dash.getDirection() == -1, "Direction is locked while dashing");
        sum = 0;
        for (int step : runDash(dash)) {
            check(step <= 0, "Left dash never moves right");
            sum += step;
        }
        check(sum == -ShipDash.DISTANCE, "Left dash covers the same distance");

        // Reset (death, restart) makes the dash ready at once.
        dash.reset();
        check(dash.getCharge() == 1 && dash.start(), "Reset clears the cooldown");
        dash.update();
        dash.reset();
        check(!dash.isDashing() && dash.update() == 0, "Reset stops a dash");

        check(ShipDash.easeOut(0) == 0 && ShipDash.easeOut(1) == 1, "Easing starts at 0, ends at 1");
        System.out.println("PASS: " + checks + " checks");
    }
}
