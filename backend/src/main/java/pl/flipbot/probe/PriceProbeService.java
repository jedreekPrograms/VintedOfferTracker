package pl.flipbot.probe;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.flipbot.bot.Bot;
import pl.flipbot.bot.BotRepository;
import pl.flipbot.bot.BotStatus;
import pl.flipbot.bot.configuration.BotConfiguration;
import pl.flipbot.bot.configuration.TargetMode;
import pl.flipbot.exception.BotNotFoundException;
import pl.flipbot.listing.Listing;
import pl.flipbot.listing.ListingRepository;
import pl.flipbot.listing.ListingStatus;
import pl.flipbot.probe.dto.PriceProbeAssignmentResponse;
import pl.flipbot.probe.dto.PriceProbeOutcome;
import pl.flipbot.probe.dto.PriceProbeOutcomeRequest;
import pl.flipbot.probe.dto.PriceProbeOutcomeResponse;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
@RequiredArgsConstructor
public class PriceProbeService {

    public static final int MAX_PROBES_PER_LISTING = 15;

    private static final BigDecimal MIN_PROBE_RATIO =
            new BigDecimal("0.78");

    private static final BigDecimal MAX_PROBE_RATIO =
            new BigDecimal("0.86");

    private static final BigDecimal PRICE_BUCKET =
            new BigDecimal("10");

    private static final List<String> MESSAGE_TEMPLATES = List.of(
            "Cześć, mogę zaproponować %s PLN.",
            "Hej, jeśli ogłoszenie jest aktualne, mogę dać %s PLN.",
            "Dzień dobry, czy %s PLN wchodziłoby w grę?",
            "Cześć, z mojej strony mogę zaproponować %s PLN.",
            "Hej, byłaby możliwość zejścia do %s PLN?",
            "Dzień dobry, mogę zaoferować %s PLN.",
            "Cześć, czy rozważył(a)byś %s PLN?",
            "Hej, jeśli pasuje, mogę zaproponować %s PLN.",
            "Czy %s PLN byłoby do rozważenia?",
            "Cześć, moja propozycja to %s PLN.",
            "Hej, mogę podejść do %s PLN.",
            "Dzień dobry, czy jest szansa na %s PLN?",
            "Cześć, mogę zaproponować w tej chwili %s PLN.",
            "Jeśli cena jest do negocjacji, mogę zaoferować %s PLN.",
            "Hej, czy zaakceptował(a)byś %s PLN?"
    );

    private static final List<ListingStatus> ACTIVE_SOURCE_STATUSES =
            List.of(
                    ListingStatus.NEGOTIATING,
                    ListingStatus.ACTION_REQUIRED
            );

    private final BotRepository botRepository;
    private final ListingRepository listingRepository;
    private final PriceProbeRepository priceProbeRepository;

    /**
     * Claims at most one one-shot price probe for a bot.
     *
     * The claim itself is persisted before Playwright receives the assignment.
     * This is deliberate: if a browser crashes after the message was submitted
     * but before it reports success, the same bot/listing pair is never claimed
     * again automatically and therefore cannot duplicate the message.
     */
    @Transactional
    public Optional<PriceProbeAssignmentResponse> claimNext(
            Long probeBotId
    ) {
        Bot probeBot = botRepository.findById(probeBotId)
                .orElseThrow(() -> new BotNotFoundException(probeBotId));

        if (probeBot.getStatus() != BotStatus.RUNNING) {
            return Optional.empty();
        }

        if (Boolean.TRUE.equals(probeBot.getMarketStatsObserver())
                || probeBot.getConfiguration() == null) {
            return Optional.empty();
        }

        List<Listing> candidates = listingRepository
                .findByStatusInOrderByIdAsc(ACTIVE_SOURCE_STATUSES);

        for (Listing candidate : candidates) {
            if (!isEligibleCandidate(probeBot, candidate)) {
                continue;
            }

            Optional<PriceProbeAssignmentResponse> claimed =
                    claimCandidateUnderLock(probeBot, candidate.getId());

            if (claimed.isPresent()) {
                return claimed;
            }
        }

        return Optional.empty();
    }

    @Transactional
    public PriceProbeOutcomeResponse complete(
            Long probeBotId,
            Long probeId,
            PriceProbeOutcomeRequest request
    ) {
        Objects.requireNonNull(request, "Price probe outcome request cannot be null");
        Objects.requireNonNull(request.outcome(), "Price probe outcome cannot be null");

        PriceProbe probe = priceProbeRepository
                .findByIdAndProbeBot_Id(probeId, probeBotId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Price probe " + probeId
                                + " does not belong to bot " + probeBotId
                ));

        if (probe.getStatus() != PriceProbeStatus.CLAIMED) {
            return toOutcomeResponse(probe);
        }

        PriceProbeStatus status = switch (request.outcome()) {
            case SENT -> PriceProbeStatus.SENT;
            case FAILED -> PriceProbeStatus.FAILED;
            case UNKNOWN -> PriceProbeStatus.UNKNOWN;
        };

        probe.setStatus(status);
        probe.setCompletedAt(LocalDateTime.now());
        probe.setFailureReason(
                status == PriceProbeStatus.SENT
                        ? null
                        : normalizeDetails(request.details())
        );

        priceProbeRepository.save(probe);

        log.info(
                "[PRICE PROBE] Probe {} for bot {} / source listing {} completed with status {}.",
                probe.getId(),
                probeBotId,
                probe.getSourceListing().getId(),
                probe.getStatus()
        );

        return toOutcomeResponse(probe);
    }

    private Optional<PriceProbeAssignmentResponse> claimCandidateUnderLock(
            Bot probeBot,
            Long sourceListingId
    ) {
        Listing source = listingRepository
                .findByIdForUpdate(sourceListingId)
                .orElse(null);

        if (source == null || !isEligibleCandidate(probeBot, source)) {
            return Optional.empty();
        }

        if (priceProbeRepository.existsByProbeBot_IdAndSourceListing_Id(
                probeBot.getId(),
                source.getId()
        )) {
            return Optional.empty();
        }

        long existingProbeCount =
                priceProbeRepository.countBySourceListing_Id(source.getId());

        if (existingProbeCount >= MAX_PROBES_PER_LISTING) {
            return Optional.empty();
        }

        BigDecimal referencePrice = source.getCurrentPrice();
        BigDecimal probePrice = generateProbePrice(referencePrice);
        String message = generateMessage(probePrice);

        PriceProbe probe = PriceProbe.builder()
                .probeBot(probeBot)
                .sourceListing(source)
                .referenceOfferPrice(referencePrice)
                .probePrice(probePrice)
                .message(message)
                .status(PriceProbeStatus.CLAIMED)
                .claimedAt(LocalDateTime.now())
                .build();

        probe = priceProbeRepository.saveAndFlush(probe);

        int probeNumber = (int) Math.min(
                Integer.MAX_VALUE,
                existingProbeCount + 1L
        );

        log.info(
                "[PRICE PROBE] Bot {} claimed probe {}/{} for source listing {} / marketplace {}. referencePrice={}, probePrice={}.",
                probeBot.getId(),
                probeNumber,
                MAX_PROBES_PER_LISTING,
                source.getId(),
                source.getListingId(),
                referencePrice,
                probePrice
        );

        return Optional.of(
                new PriceProbeAssignmentResponse(
                        probe.getId(),
                        source.getId(),
                        source.getListingId(),
                        source.getTitle(),
                        source.getUrl(),
                        referencePrice,
                        probePrice,
                        message,
                        probeNumber,
                        MAX_PROBES_PER_LISTING
                )
        );
    }

    private boolean isEligibleCandidate(
            Bot probeBot,
            Listing source
    ) {
        if (source == null
                || source.getId() == null
                || source.getBot() == null
                || source.getBot().getId() == null
                || Objects.equals(source.getBot().getId(), probeBot.getId())
                || Boolean.TRUE.equals(source.getBot().getMarketStatsObserver())
                || !ACTIVE_SOURCE_STATUSES.contains(source.getStatus())
                || source.getCurrentPrice() == null
                || source.getCurrentPrice().signum() <= 0
                || source.getUrl() == null
                || source.getUrl().isBlank()) {
            return false;
        }

        if (priceProbeRepository.existsByProbeBot_IdAndSourceListing_Id(
                probeBot.getId(),
                source.getId()
        )) {
            return false;
        }

        return sameTarget(
                probeBot.getConfiguration(),
                source.getBot().getConfiguration()
        );
    }

    private boolean sameTarget(
            BotConfiguration left,
            BotConfiguration right
    ) {
        if (left == null || right == null) {
            return false;
        }

        if (!Objects.equals(left.getMarketplace(), right.getMarketplace())
                || !sameText(left.getBrand(), right.getBrand())
                || !sameCategoryPath(left.getCategoryPath(), right.getCategoryPath())) {
            return false;
        }

        TargetMode leftMode = resolveTargetMode(left);
        TargetMode rightMode = resolveTargetMode(right);

        if (leftMode != rightMode) {
            return false;
        }

        return switch (leftMode) {
            case VINTED_MODEL -> sameText(left.getModel(), right.getModel());
            case SEARCH_QUERY -> sameText(left.getSearchQuery(), right.getSearchQuery());
        };
    }

    private TargetMode resolveTargetMode(BotConfiguration configuration) {
        return configuration.getTargetMode() == null
                ? TargetMode.VINTED_MODEL
                : configuration.getTargetMode();
    }

    private boolean sameCategoryPath(
            List<String> left,
            List<String> right
    ) {
        if (left == null || right == null || left.size() != right.size()) {
            return false;
        }

        for (int index = 0; index < left.size(); index++) {
            if (!sameText(left.get(index), right.get(index))) {
                return false;
            }
        }

        return true;
    }

    private boolean sameText(String left, String right) {
        if (left == null || right == null) {
            return left == right;
        }

        return normalizeText(left).equals(normalizeText(right));
    }

    private String normalizeText(String value) {
        return value.trim()
                .replaceAll("\\s+", " ")
                .toLowerCase(Locale.ROOT);
    }

    private BigDecimal generateProbePrice(BigDecimal referencePrice) {
        double ratio = ThreadLocalRandom.current().nextDouble(
                MIN_PROBE_RATIO.doubleValue(),
                MAX_PROBE_RATIO.doubleValue()
        );

        BigDecimal raw = referencePrice.multiply(
                BigDecimal.valueOf(ratio)
        );

        BigDecimal rounded = raw
                .divide(PRICE_BUCKET, 0, RoundingMode.HALF_UP)
                .multiply(PRICE_BUCKET)
                .setScale(2, RoundingMode.UNNECESSARY);

        if (rounded.signum() <= 0) {
            rounded = referencePrice
                    .multiply(MIN_PROBE_RATIO)
                    .setScale(2, RoundingMode.HALF_UP);
        }

        if (rounded.compareTo(referencePrice) >= 0) {
            rounded = referencePrice
                    .subtract(BigDecimal.ONE)
                    .max(new BigDecimal("0.01"))
                    .setScale(2, RoundingMode.HALF_UP);
        }

        return rounded;
    }

    private String generateMessage(BigDecimal probePrice) {
        String template = MESSAGE_TEMPLATES.get(
                ThreadLocalRandom.current().nextInt(MESSAGE_TEMPLATES.size())
        );

        String price = probePrice
                .stripTrailingZeros()
                .toPlainString();

        return template.formatted(price);
    }

    private String normalizeDetails(String details) {
        if (details == null || details.isBlank()) {
            return null;
        }

        String normalized = details.trim().replaceAll("\\s+", " ");
        return normalized.length() <= 1000
                ? normalized
                : normalized.substring(0, 1000);
    }

    private PriceProbeOutcomeResponse toOutcomeResponse(PriceProbe probe) {
        return new PriceProbeOutcomeResponse(
                probe.getId(),
                probe.getStatus(),
                probe.getCompletedAt()
        );
    }
}
