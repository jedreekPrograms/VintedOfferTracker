package pl.flipbot.negotiation;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import pl.flipbot.bot.Bot;
import pl.flipbot.listing.Listing;
import pl.flipbot.listing.ListingRepository;
import pl.flipbot.listing.ListingStatus;
import pl.flipbot.negotiation.quota.dto.DailyOfferQuotaResponse;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Slf4j
@Component
public class NegotiationPlanner {

    /*
     * The backend hard quota remains authoritative and is checked immediately
     * before every real submit. This planner is intentionally a separate,
     * softer admission-control layer: it decides how many NEW conversations may
     * be opened without unnecessarily reserving every future step on the same
     * calendar day.
     */
    private static final ZoneId PLANNING_ZONE = ZoneId.of("Europe/Warsaw");
    private static final int PLANNING_HORIZON_DAYS = 7;
    private static final int MAX_SAFETY_BUFFER_SLOTS = 3;
    private static final double SAFETY_BUFFER_RATIO = 0.12;
    private static final double PROFILE_GRANULARITY = 0.25;
    private static final double EPSILON = 0.000_001;

    /*
     * Vinted accepts offers down to roughly 60% of the listing price. Once a
     * simulated seller concession reaches the corresponding ~40% discount,
     * the concession path is treated as terminal instead of inventing further
     * price rounds below the marketplace floor.
     */
    private static final BigDecimal VINTED_MAX_DISCOUNT_PERCENT =
            new BigDecimal("40");

    /*
     * These are planning-risk weights, not claims about seller behaviour.
     *
     * 50%  - formal rejection path (configured rejection waits)
     * 30%  - seller improves by ~5 percentage points per round
     * 15%  - seller improves by ~10 percentage points per round
     *  5%  - large ~20 percentage point concessions
     *
     * The model deliberately gives most weight to the slower paths, but keeps a
     * meaningful fast-path allowance. A separate daily safety buffer and the
     * hard atomic quota protect against the model being wrong.
     */
    private static final List<PlanningScenario> SCENARIOS = List.of(
            new PlanningScenario("REJECTION", 0.50, null),
            new PlanningScenario("SELLER_5PP", 0.30, new BigDecimal("5")),
            new PlanningScenario("SELLER_10PP", 0.15, new BigDecimal("10")),
            new PlanningScenario("SELLER_20PP", 0.05, new BigDecimal("20"))
    );

    private final ListingRepository listingRepository;
    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    public NegotiationPlanner(
            ListingRepository listingRepository,
            JdbcTemplate jdbcTemplate
    ) {
        this(
                listingRepository,
                jdbcTemplate,
                Clock.system(PLANNING_ZONE)
        );
    }

    NegotiationPlanner(
            ListingRepository listingRepository,
            JdbcTemplate jdbcTemplate,
            Clock clock
    ) {
        this.listingRepository = listingRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
    }

    public int calculateNewNegotiations(
            Bot bot,
            DailyOfferQuotaResponse quota,
            List<NegotiationStep> requestedProductSteps
    ) {
        if (bot == null
                || bot.getId() == null
                || quota == null
                || quota.limit() <= 0
                || quota.remaining() <= 0
                || requestedProductSteps == null
                || requestedProductSteps.isEmpty()) {
            return 0;
        }

        List<NegotiationStep> orderedRequestedSteps =
                orderedSteps(requestedProductSteps);
        if (orderedRequestedSteps.isEmpty()) {
            return 0;
        }

        LocalDateTime now = LocalDateTime.now(clock);
        ReservationProfile existing = calculateExistingReservationLoad(
                bot.getId(),
                orderedRequestedSteps,
                now
        );
        ReservationProfile candidate = buildWeightedProfile(
                orderedRequestedSteps,
                null,
                now
        );

        if (candidate.load(0) <= 0.0) {
            log.error(
                    "[NEGOTIATION CAPACITY] New-conversation profile for bot {} has no day-0 action. Failing closed.",
                    bot.getId()
            );
            return 0;
        }

        int planningLimit = planningLimit(quota.limit());
        int hardRemaining = Math.max(quota.remaining(), 0);
        int allowed = 0;

        for (int conversations = 1;
             conversations <= hardRemaining;
             conversations++) {
            if (!fitsPlanningBudget(
                    quota.used(),
                    planningLimit,
                    existing,
                    candidate,
                    conversations
            )) {
                break;
            }
            allowed = conversations;
        }

        log.info(
                "[NEGOTIATION CAPACITY] Bot {} rolling planner: hardLimit={}, usedToday={}, hardRemaining={}, planningLimit={} (buffer={}), existing7d={}, newConversation7d={}, allowedNewNegotiations={}. Hard quota remains authoritative before every submit.",
                bot.getId(),
                quota.limit(),
                quota.used(),
                quota.remaining(),
                planningLimit,
                Math.max(quota.limit() - planningLimit, 0),
                existing.format(),
                candidate.format(),
                allowed
        );

        return Math.max(allowed, 0);
    }

    private ReservationProfile calculateExistingReservationLoad(
            Long botId,
            List<NegotiationStep> fallbackSteps,
            LocalDateTime now
    ) {
        List<Listing> activeListings = new ArrayList<>(
                listingRepository.findByBotIdAndStatusOrderByIdAsc(
                        botId,
                        ListingStatus.NEGOTIATING
                )
        );
        activeListings.addAll(
                listingRepository.findByBotIdAndStatusOrderByIdAsc(
                        botId,
                        ListingStatus.ACTION_REQUIRED
                )
        );

        ReservationProfile total = ReservationProfile.empty();
        int supersededActiveDuplicates = 0;

        for (Listing listing : activeListings) {
            if (isSupersededByActiveMarketplaceOwner(listing)) {
                supersededActiveDuplicates++;
                continue;
            }

            List<NegotiationStep> steps = resolveSteps(
                    listing,
                    fallbackSteps
            );
            ReservationProfile profile = buildWeightedProfile(
                    steps,
                    listing,
                    now
            );
            total = total.plus(profile);

            log.info(
                    "[NEGOTIATION CAPACITY] Bot {} active listing backendId={}, marketplaceId={}, product={}, status={}, currentStep={} reserves rolling7d={}.",
                    botId,
                    listing.getId(),
                    listing.getListingId(),
                    listing.getAdditionalTarget() == null
                            ? "MAIN"
                            : listing.getAdditionalTarget().getId(),
                    listing.getStatus(),
                    listing.getCurrentStep(),
                    profile.format()
            );
        }

        log.info(
                "[NEGOTIATION CAPACITY] Bot {} rolling reservations from {} active row(s), {} superseded duplicate(s): {}.",
                botId,
                activeListings.size(),
                supersededActiveDuplicates,
                total.format()
        );

        return total;
    }

    private ReservationProfile buildWeightedProfile(
            List<NegotiationStep> rawSteps,
            Listing activeListing,
            LocalDateTime now
    ) {
        List<NegotiationStep> steps = orderedSteps(rawSteps);
        if (steps.isEmpty()) {
            return ReservationProfile.empty();
        }

        int currentIndex = -1;
        if (activeListing != null) {
            Integer currentStep = activeListing.getCurrentStep();
            if (currentStep == null || currentStep < 1) {
                log.warn(
                        "[NEGOTIATION CAPACITY] Active backend listing {} has no valid current step. Reserving the full ladder on day 0 fail-closed.",
                        activeListing.getId()
                );
                return ReservationProfile.failClosedToday(steps.size());
            }

            currentIndex = findStepIndex(steps, currentStep);
            if (currentIndex < 0) {
                log.warn(
                        "[NEGOTIATION CAPACITY] Active backend listing {} references currentStep={} which is absent from its ladder. Reserving the full ladder on day 0 fail-closed.",
                        activeListing.getId(),
                        currentStep
                );
                return ReservationProfile.failClosedToday(steps.size());
            }

            if (currentIndex >= steps.size() - 1) {
                return ReservationProfile.empty();
            }
        }

        double[] weighted = new double[PLANNING_HORIZON_DAYS];

        for (PlanningScenario scenario : SCENARIOS) {
            int[] counts = simulateScenario(
                    steps,
                    currentIndex,
                    activeListing,
                    now,
                    scenario
            );

            for (int day = 0; day < PLANNING_HORIZON_DAYS; day++) {
                weighted[day] += counts[day] * scenario.weight();
            }
        }

        for (int day = 0; day < PLANNING_HORIZON_DAYS; day++) {
            weighted[day] = roundUpToGranularity(weighted[day]);
        }

        return new ReservationProfile(weighted);
    }

    private int[] simulateScenario(
            List<NegotiationStep> steps,
            int currentIndex,
            Listing activeListing,
            LocalDateTime now,
            PlanningScenario scenario
    ) {
        int[] counts = new int[PLANNING_HORIZON_DAYS];
        int nextIndex;
        LocalDateTime nextActionAt;
        BigDecimal simulatedDiscount = BigDecimal.ZERO;

        if (currentIndex < 0) {
            nextIndex = 0;
            nextActionAt = now;
        } else {
            nextIndex = currentIndex + 1;

            if (scenario.sellerConcessionPercent() != null) {
                simulatedDiscount = scenario.sellerConcessionPercent()
                        .multiply(BigDecimal.valueOf(currentIndex + 1L))
                        .min(VINTED_MAX_DISCOUNT_PERCENT);

                if (simulatedDiscount.compareTo(
                        VINTED_MAX_DISCOUNT_PERCENT
                ) >= 0) {
                    return counts;
                }
            }

            LocalDateTime responseAnchor = responseAnchor(
                    activeListing,
                    now
            );
            int waitHours = waitHoursAfterStep(
                    steps.get(currentIndex),
                    scenario,
                    simulatedDiscount
            );
            nextActionAt = responseAnchor.plusHours(waitHours);
            if (nextActionAt.isBefore(now)) {
                nextActionAt = now;
            }
        }

        LocalDate planningDate = now.toLocalDate();

        for (int index = nextIndex; index < steps.size(); index++) {
            long dayOffset = ChronoUnit.DAYS.between(
                    planningDate,
                    nextActionAt.toLocalDate()
            );

            if (dayOffset >= PLANNING_HORIZON_DAYS) {
                break;
            }

            if (dayOffset >= 0) {
                counts[(int) dayOffset]++;
            }

            if (index >= steps.size() - 1) {
                break;
            }

            if (scenario.sellerConcessionPercent() != null) {
                simulatedDiscount = simulatedDiscount
                        .add(scenario.sellerConcessionPercent())
                        .min(VINTED_MAX_DISCOUNT_PERCENT);

                /*
                 * Reaching the ~40% marketplace floor ends this simulated
                 * concession path. This is precisely what prevents a fast
                 * seller from being treated as if all five configured bot
                 * offers must still be sent.
                 */
                if (simulatedDiscount.compareTo(
                        VINTED_MAX_DISCOUNT_PERCENT
                ) >= 0) {
                    break;
                }
            }

            int waitHours = waitHoursAfterStep(
                    steps.get(index),
                    scenario,
                    simulatedDiscount
            );
            nextActionAt = nextActionAt.plusHours(waitHours);
        }

        return counts;
    }

    private LocalDateTime responseAnchor(
            Listing listing,
            LocalDateTime now
    ) {
        if (listing == null) {
            return now;
        }

        /*
         * A persisted formal-response timestamp is the best anchor. If none is
         * present, currentStepStartedAt is intentionally used rather than
         * "now": this assumes the seller COULD have responded immediately after
         * our previous offer and therefore packs the next action earlier. That
         * is the safer planning direction.
         */
        LocalDateTime anchor = listing.getFormalResponseDetectedAt();
        if (anchor == null) {
            anchor = listing.getCurrentStepStartedAt();
        }
        if (anchor == null) {
            anchor = now;
        }

        return anchor;
    }

    private int waitHoursAfterStep(
            NegotiationStep step,
            PlanningScenario scenario,
            BigDecimal simulatedDiscount
    ) {
        if (scenario.sellerConcessionPercent() == null) {
            return normalizedWait(
                    step.getRejectionAction(),
                    step.getRejectionWaitHours()
            );
        }

        SellerCounterOfferRule bestRule = null;
        if (step.getCounterOfferRules() != null) {
            for (SellerCounterOfferRule rule : step.getCounterOfferRules()) {
                if (rule == null
                        || rule.getMinimumDiscountPercent() == null
                        || rule.getAction() == null) {
                    continue;
                }

                if (simulatedDiscount.compareTo(
                        rule.getMinimumDiscountPercent()
                ) >= 0
                        && (bestRule == null
                        || rule.getMinimumDiscountPercent().compareTo(
                        bestRule.getMinimumDiscountPercent()
                ) > 0)) {
                    bestRule = rule;
                }
            }
        }

        if (bestRule != null) {
            return normalizedWait(
                    bestRule.getAction(),
                    bestRule.getWaitHours()
            );
        }

        return normalizedWait(
                step.getCounterOfferDefaultAction(),
                step.getCounterOfferDefaultWaitHours()
        );
    }

    private int normalizedWait(
            NegotiationReactionAction action,
            Integer waitHours
    ) {
        if (action == null
                || action == NegotiationReactionAction.NEXT_STEP_NOW) {
            return 0;
        }

        if (action != NegotiationReactionAction.WAIT_BEFORE_NEXT_STEP) {
            return 0;
        }

        /*
         * Invalid/missing WAIT values fail toward an EARLIER action instead of
         * a later one, so the planner never gains capacity from malformed
         * timing configuration.
         */
        return waitHours == null || waitHours <= 0
                ? 0
                : waitHours;
    }

    private boolean fitsPlanningBudget(
            int usedToday,
            int planningLimit,
            ReservationProfile existing,
            ReservationProfile candidate,
            int conversations
    ) {
        for (int day = 0; day < PLANNING_HORIZON_DAYS; day++) {
            double committed = existing.load(day)
                    + candidate.load(day) * conversations;

            if (day == 0) {
                committed += Math.max(usedToday, 0);
            }

            if (committed > planningLimit + EPSILON) {
                return false;
            }
        }

        return true;
    }

    private int planningLimit(int hardLimit) {
        int safetyBuffer = Math.min(
                MAX_SAFETY_BUFFER_SLOTS,
                (int) Math.floor(
                        Math.max(hardLimit, 0) * SAFETY_BUFFER_RATIO
                )
        );

        return Math.max(hardLimit - safetyBuffer, 0);
    }

    private double roundUpToGranularity(double value) {
        if (value <= EPSILON) {
            return 0.0;
        }

        return Math.ceil(
                (value - EPSILON) / PROFILE_GRANULARITY
        ) * PROFILE_GRANULARITY;
    }

    private List<NegotiationStep> resolveSteps(
            Listing listing,
            List<NegotiationStep> fallbackSteps
    ) {
        if (listing != null
                && listing.getAdditionalTarget() != null
                && listing.getAdditionalTarget().getNegotiationSteps() != null
                && !listing.getAdditionalTarget().getNegotiationSteps().isEmpty()) {
            return orderedSteps(
                    listing.getAdditionalTarget().getNegotiationSteps()
            );
        }

        if (listing != null
                && listing.getBot() != null
                && listing.getBot().getConfiguration() != null
                && listing.getBot().getConfiguration().getNegotiationSteps() != null
                && !listing.getBot().getConfiguration().getNegotiationSteps().isEmpty()) {
            return orderedSteps(
                    listing.getBot().getConfiguration().getNegotiationSteps()
            );
        }

        log.warn(
                "[NEGOTIATION CAPACITY] Could not resolve product ladder for backend listing {}. Using the requested-product ladder as a conservative fallback.",
                listing == null ? null : listing.getId()
        );
        return orderedSteps(fallbackSteps);
    }

    private List<NegotiationStep> orderedSteps(
            List<NegotiationStep> steps
    ) {
        if (steps == null || steps.isEmpty()) {
            return List.of();
        }

        return steps.stream()
                .filter(step -> step != null && step.getStepNumber() != null)
                .sorted(Comparator.comparing(NegotiationStep::getStepNumber))
                .toList();
    }

    private int findStepIndex(
            List<NegotiationStep> steps,
            Integer stepNumber
    ) {
        for (int index = 0; index < steps.size(); index++) {
            if (stepNumber.equals(steps.get(index).getStepNumber())) {
                return index;
            }
        }
        return -1;
    }

    private boolean isSupersededByActiveMarketplaceOwner(
            Listing listing
    ) {
        if (listing == null
                || listing.getId() == null
                || listing.getListingId() == null
                || listing.getBot() == null
                || listing.getBot().getId() == null
                || listing.getBot().getConfiguration() == null
                || listing.getBot().getConfiguration().getMarketplace() == null) {
            return false;
        }

        Boolean superseded = jdbcTemplate.queryForObject(
                """
                SELECT EXISTS (
                    SELECT 1
                    FROM marketplace_negotiation_claim c
                    JOIN listing owner
                      ON owner.id = c.owner_listing_id
                    WHERE c.marketplace = ?
                      AND c.marketplace_listing_id = ?
                      AND c.confirmed_at IS NOT NULL
                      AND (
                           c.owner_bot_id <> ?
                           OR c.owner_listing_id <> ?
                      )
                      AND owner.status IN ('NEGOTIATING', 'ACTION_REQUIRED')
                )
                """,
                Boolean.class,
                listing.getBot().getConfiguration().getMarketplace().name(),
                listing.getListingId(),
                listing.getBot().getId(),
                listing.getId()
        );

        return Boolean.TRUE.equals(superseded);
    }

    private record PlanningScenario(
            String name,
            double weight,
            BigDecimal sellerConcessionPercent
    ) {
    }

    private static final class ReservationProfile {

        private final double[] loads;

        private ReservationProfile(double[] loads) {
            this.loads = loads;
        }

        private static ReservationProfile empty() {
            return new ReservationProfile(
                    new double[PLANNING_HORIZON_DAYS]
            );
        }

        private static ReservationProfile failClosedToday(int actions) {
            double[] loads = new double[PLANNING_HORIZON_DAYS];
            loads[0] = Math.max(actions, 0);
            return new ReservationProfile(loads);
        }

        private ReservationProfile plus(ReservationProfile other) {
            double[] sum = new double[PLANNING_HORIZON_DAYS];
            for (int day = 0; day < PLANNING_HORIZON_DAYS; day++) {
                sum[day] = load(day) + other.load(day);
            }
            return new ReservationProfile(sum);
        }

        private double load(int day) {
            if (day < 0 || day >= loads.length) {
                return 0.0;
            }
            return loads[day];
        }

        private String format() {
            List<String> buckets = new ArrayList<>();
            for (int day = 0; day < loads.length; day++) {
                if (loads[day] <= EPSILON) {
                    continue;
                }
                buckets.add(
                        "D+" + day + "="
                                + String.format(
                                Locale.ROOT,
                                "%.2f",
                                loads[day]
                        )
                );
            }
            return buckets.isEmpty()
                    ? "[]"
                    : "[" + String.join(", ", buckets) + "]";
        }
    }
}
