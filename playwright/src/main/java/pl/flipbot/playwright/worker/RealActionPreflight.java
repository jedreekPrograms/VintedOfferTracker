package pl.flipbot.playwright.worker;

import lombok.extern.slf4j.Slf4j;
import pl.flipbot.playwright.api.listing.ListingClient;
import pl.flipbot.playwright.api.listing.TargetBoundListingClient;
import pl.flipbot.playwright.api.listing.dto.ListingResponseDto;
import pl.flipbot.playwright.model.BotAdditionalTargetDto;
import pl.flipbot.playwright.model.BotConfigurationDto;
import pl.flipbot.playwright.model.BotDetailsDto;
import pl.flipbot.playwright.model.NegotiationStepDto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
public class RealActionPreflight {

    public Result validate(
            BotDetailsDto bot,
            ScheduledJobType jobType,
            ListingClient listingClient,
            boolean firstOfferRequested,
            boolean nextStepRequested
    ) {
        List<String> failures = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        boolean capacityBlocked = false;

        if (bot == null || bot.getId() == null || bot.getId() <= 0) {
            failures.add("bot id is missing or invalid");
            return finish(bot, jobType, failures, notes, false);
        }

        BotConfigurationDto main = bot.getConfiguration();
        if (main == null) {
            failures.add("bot configuration is missing");
            return finish(bot, jobType, failures, notes, false);
        }

        validateMarketplace(main, "MAIN", failures);
        validateBudget(main, failures);

        if (firstOfferRequested) {
            List<ProductConfiguration> scanProducts = activeScanProducts(bot);
            if (scanProducts.size() > 5) {
                failures.add("bot has more than 4 active additional products");
            }

            for (ProductConfiguration product : scanProducts) {
                validateCatalogProduct(
                        product,
                        true,
                        false,
                        failures,
                        notes
                );
            }

            if (failures.isEmpty()) {
                capacityBlocked = !hasAnyFirstOfferCapacity(
                        bot.getId(),
                        scanProducts,
                        listingClient,
                        failures,
                        notes
                );
            }
        }

        if (nextStepRequested) {
            validateNegotiatingProducts(
                    bot,
                    listingClient,
                    failures,
                    notes
            );
        }

        /* Manual/full-run compatibility: when neither action type is explicitly
           armed, keep validating the unchanged main product just as before. */
        if (!firstOfferRequested && !nextStepRequested) {
            validateCatalogProduct(
                    new ProductConfiguration(null, main),
                    false,
                    false,
                    failures,
                    notes
            );
        }

        return finish(
                bot,
                jobType,
                failures,
                notes,
                capacityBlocked
        );
    }

    private void validateCatalogProduct(
            ProductConfiguration product,
            boolean firstOfferRequested,
            boolean nextStepRequested,
            List<String> failures,
            List<String> notes
    ) {
        String label = productLabel(product.id());
        BotConfigurationDto configuration = product.configuration();

        if (configuration == null) {
            failures.add(label + " configuration is missing");
            return;
        }

        validateMarketplace(configuration, label, failures);

        if (configuration.getCategoryPath() == null
                || configuration.getCategoryPath().isEmpty()) {
            failures.add(label + " category path is empty");
        }

        validateTarget(configuration, label, failures);
        validatePriceRange(configuration, label, failures);
        validateNegotiationSteps(
                configuration,
                label,
                firstOfferRequested,
                nextStepRequested,
                failures,
                notes
        );
    }

    private boolean hasAnyFirstOfferCapacity(
            Long botId,
            List<ProductConfiguration> products,
            ListingClient mainClient,
            List<String> failures,
            List<String> notes
    ) {
        boolean anyCapacity = false;

        for (ProductConfiguration product : products) {
            try {
                ListingClient client = product.id() == null
                        ? mainClient
                        : new TargetBoundListingClient(product.id());
                int allowed = client.getAllowedNewNegotiations(botId);

                notes.add(
                        productLabel(product.id())
                                + " backend allows "
                                + allowed
                                + " new negotiation(s)"
                );
                anyCapacity |= allowed > 0;
            } catch (Exception exception) {
                failures.add(
                        productLabel(product.id())
                                + " could not read negotiation capacity: "
                                + friendlyMessage(exception)
                );
            }
        }

        if (!anyCapacity && failures.isEmpty()) {
            notes.add(
                    "all active products have backend capacity 0; no FIRST_OFFER submit is allowed in this catalog cycle"
            );
        }
        return anyCapacity;
    }

    private void validateNegotiatingProducts(
            BotDetailsDto bot,
            ListingClient listingClient,
            List<String> failures,
            List<String> notes
    ) {
        List<ListingResponseDto> negotiating;
        try {
            negotiating = listingClient.getNegotiatingListings(bot.getId());
        } catch (Exception exception) {
            failures.add(
                    "could not read active negotiations: "
                            + friendlyMessage(exception)
            );
            return;
        }

        notes.add(
                "active NEGOTIATING listings: "
                        + negotiating.size()
                        + (negotiating.isEmpty()
                        ? " (no next step can be sent right now)"
                        : "")
        );

        if (negotiating.isEmpty()) {
            return;
        }

        Map<Long, BotConfigurationDto> configurations =
                configurationsByTargetId(bot);
        Set<Long> validatedTargetIds = new HashSet<>();

        for (ListingResponseDto listing : negotiating) {
            Long targetId = listing.additionalTargetId();
            if (!validatedTargetIds.add(targetId)) {
                continue;
            }

            BotConfigurationDto configuration = configurations.get(targetId);
            if (configuration == null) {
                failures.add(
                        productLabel(targetId)
                                + " has an active negotiation but its product configuration is unavailable"
                );
                continue;
            }

            validateCatalogProduct(
                    new ProductConfiguration(targetId, configuration),
                    false,
                    true,
                    failures,
                    notes
            );
        }
    }

    private List<ProductConfiguration> activeScanProducts(BotDetailsDto bot) {
        List<ProductConfiguration> products = new ArrayList<>();
        products.add(new ProductConfiguration(null, bot.getConfiguration()));

        if (bot.getAdditionalTargets() == null) {
            return products;
        }

        for (BotAdditionalTargetDto target : bot.getAdditionalTargets()) {
            if (target == null
                    || target.getAdditionalTargetId() == null
                    || !Boolean.TRUE.equals(target.getActive())) {
                continue;
            }
            products.add(new ProductConfiguration(
                    target.getAdditionalTargetId(),
                    target
            ));
        }
        return products;
    }

    private Map<Long, BotConfigurationDto> configurationsByTargetId(
            BotDetailsDto bot
    ) {
        Map<Long, BotConfigurationDto> result = new LinkedHashMap<>();
        result.put(null, bot.getConfiguration());

        if (bot.getAdditionalTargets() != null) {
            for (BotAdditionalTargetDto target : bot.getAdditionalTargets()) {
                if (target != null && target.getAdditionalTargetId() != null) {
                    result.put(target.getAdditionalTargetId(), target);
                }
            }
        }
        return result;
    }

    private void validateMarketplace(
            BotConfigurationDto configuration,
            String label,
            List<String> failures
    ) {
        if (!"VINTED".equalsIgnoreCase(configuration.getMarketplace())) {
            failures.add(label + " marketplace must be VINTED");
        }
    }

    private void validateTarget(
            BotConfigurationDto configuration,
            String label,
            List<String> failures
    ) {
        String targetMode = trim(configuration.getTargetMode());

        if ("SEARCH_QUERY".equalsIgnoreCase(targetMode)) {
            if (trim(configuration.getSearchQuery()).isBlank()) {
                failures.add(label + " SEARCH_QUERY target has an empty searchQuery");
            }
            return;
        }

        if ("VINTED_MODEL".equalsIgnoreCase(targetMode)) {
            if (trim(configuration.getBrand()).isBlank()) {
                failures.add(label + " VINTED_MODEL target has an empty brand");
            }
            if (trim(configuration.getModel()).isBlank()) {
                failures.add(label + " VINTED_MODEL target has an empty model");
            }
            return;
        }

        failures.add(label + " unsupported or missing targetMode: " + targetMode);
    }

    private void validatePriceRange(
            BotConfigurationDto configuration,
            String label,
            List<String> failures
    ) {
        BigDecimal minPrice = configuration.getMinPrice();
        BigDecimal maxPrice = configuration.getMaxPrice();

        if (minPrice == null || minPrice.signum() < 0) {
            failures.add(label + " minPrice is missing or negative");
        }

        if (maxPrice == null || maxPrice.signum() <= 0) {
            failures.add(label + " maxPrice is missing or not positive");
        }

        if (minPrice != null
                && maxPrice != null
                && minPrice.compareTo(maxPrice) > 0) {
            failures.add(label + " minPrice is greater than maxPrice");
        }
    }

    private void validateBudget(
            BotConfigurationDto configuration,
            List<String> failures
    ) {
        Integer budget = configuration.getDailyNegotiationBudget();

        if (budget == null || budget <= 0) {
            failures.add("shared dailyNegotiationBudget must be positive");
        }
    }

    private void validateNegotiationSteps(
            BotConfigurationDto configuration,
            String label,
            boolean firstOfferRequested,
            boolean nextStepRequested,
            List<String> failures,
            List<String> notes
    ) {
        List<NegotiationStepDto> steps = configuration.getNegotiationSteps();

        if (steps == null || steps.isEmpty()) {
            failures.add(label + " negotiationSteps are empty");
            return;
        }

        long nullEntries = steps.stream().filter(step -> step == null).count();
        if (nullEntries > 0) {
            failures.add(
                    label + " negotiationSteps contain "
                            + nullEntries
                            + " null entrie(s)"
            );
        }

        List<NegotiationStepDto> sorted =
                steps.stream()
                        .filter(step -> step != null)
                        .sorted(Comparator.comparing(
                                NegotiationStepDto::getStepNumber,
                                Comparator.nullsLast(Integer::compareTo)
                        ))
                        .toList();

        Set<Integer> seen = new HashSet<>();

        for (int index = 0; index < sorted.size(); index++) {
            NegotiationStepDto step = sorted.get(index);
            int expectedStepNumber = index + 1;
            Integer stepNumber = step.getStepNumber();

            if (stepNumber == null || stepNumber != expectedStepNumber) {
                failures.add(
                        label
                                + " negotiation steps must be sequential starting at 1; expected "
                                + expectedStepNumber
                                + " but got "
                                + stepNumber
                );
            } else if (!seen.add(stepNumber)) {
                failures.add(label + " duplicate negotiation step number: " + stepNumber);
            }

            BigDecimal offerPrice = step.getOfferPrice();
            if (offerPrice == null || offerPrice.signum() <= 0) {
                failures.add(
                        label
                                + " step "
                                + stepNumber
                                + " has a missing or non-positive offerPrice"
                );
            }
        }

        NegotiationStepDto firstStep =
                sorted.stream()
                        .filter(step -> Integer.valueOf(1).equals(step.getStepNumber()))
                        .findFirst()
                        .orElse(null);

        if (firstOfferRequested) {
            if (firstStep == null) {
                failures.add(label + " FIRST_OFFER requires negotiation step #1");
            } else if (trim(firstStep.getMessage()).isBlank()) {
                failures.add(
                        label
                                + " step #1 message is blank; controlled first-offer test requires offer + message"
                );
            }
        }

        if (nextStepRequested && sorted.size() == 1) {
            notes.add(
                    label
                            + " has a one-step negotiation ladder; an active conversation may finish without sending a NEXT_STEP"
            );
        }

        notes.add(label + " configured negotiation steps: " + sorted.size());
    }

    private Result finish(
            BotDetailsDto bot,
            ScheduledJobType jobType,
            List<String> failures,
            List<String> notes,
            boolean capacityBlocked
    ) {
        Long botId = bot == null ? null : bot.getId();
        boolean hasHardFailure = !failures.isEmpty();
        boolean ready = !hasHardFailure && !capacityBlocked;

        if (hasHardFailure) {
            log.error(
                    "[REAL ACTION PREFLIGHT] BLOCKED for bot {} / {}. Failures: {}. Notes: {}",
                    botId,
                    jobType,
                    String.join("; ", failures),
                    String.join("; ", notes)
            );
        } else if (capacityBlocked) {
            log.info(
                    "[REAL ACTION PREFLIGHT] NO_CAPACITY for bot {} / {}. {} Catalog discovery may still continue; only real FIRST_OFFER submission is disabled for this cycle.",
                    botId,
                    jobType,
                    String.join("; ", notes)
            );
        } else {
            log.info(
                    "[REAL ACTION PREFLIGHT] READY for bot {} / {}. {}",
                    botId,
                    jobType,
                    String.join("; ", notes)
            );
        }

        return new Result(
                ready,
                capacityBlocked && !hasHardFailure,
                List.copyOf(failures),
                List.copyOf(notes)
        );
    }

    private String productLabel(Long targetId) {
        return targetId == null ? "MAIN" : "ADDITIONAL:" + targetId;
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private String friendlyMessage(Throwable exception) {
        if (exception == null) {
            return "unknown error";
        }

        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return exception.getClass().getSimpleName();
        }

        return message.lines().findFirst().orElse(message).trim();
    }

    private record ProductConfiguration(
            Long id,
            BotConfigurationDto configuration
    ) {
    }

    public record Result(
            boolean ready,
            boolean expectedCapacityBlock,
            List<String> failures,
            List<String> notes
    ) {
    }
}
