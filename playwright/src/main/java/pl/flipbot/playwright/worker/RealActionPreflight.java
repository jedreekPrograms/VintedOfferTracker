package pl.flipbot.playwright.worker;

import lombok.extern.slf4j.Slf4j;
import pl.flipbot.playwright.api.listing.ListingClient;
import pl.flipbot.playwright.model.BotConfigurationDto;
import pl.flipbot.playwright.model.BotDetailsDto;
import pl.flipbot.playwright.model.NegotiationStepDto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
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

        BotConfigurationDto configuration = bot.getConfiguration();

        if (configuration == null) {
            failures.add("bot configuration is missing");
            return finish(bot, jobType, failures, notes, false);
        }

        if (!"VINTED".equalsIgnoreCase(configuration.getMarketplace())) {
            failures.add("marketplace must be VINTED");
        }

        if (configuration.getCategoryPath() == null
                || configuration.getCategoryPath().isEmpty()) {
            failures.add("category path is empty");
        }

        validateTarget(configuration, failures);
        validatePriceRange(configuration, failures);
        validateBudget(configuration, failures);
        validateNegotiationSteps(
                configuration,
                firstOfferRequested,
                nextStepRequested,
                failures,
                notes
        );

        if (firstOfferRequested) {
            try {
                int allowed = listingClient.getAllowedNewNegotiations(bot.getId());

                if (allowed <= 0) {
                    capacityBlocked = true;
                    notes.add(
                            "backend capacity is 0; no FIRST_OFFER submit is allowed in this catalog cycle"
                    );
                } else {
                    notes.add("backend allows " + allowed + " new negotiation(s)");
                }
            } catch (Exception exception) {
                failures.add(
                        "could not read negotiation capacity: "
                                + friendlyMessage(exception)
                );
            }
        }

        if (nextStepRequested) {
            try {
                int negotiating =
                        listingClient.getNegotiatingListings(bot.getId()).size();

                notes.add(
                        "active NEGOTIATING listings: "
                                + negotiating
                                + (negotiating == 0
                                ? " (no next step can be sent right now)"
                                : "")
                );
            } catch (Exception exception) {
                failures.add(
                        "could not read active negotiations: "
                                + friendlyMessage(exception)
                );
            }
        }

        return finish(
                bot,
                jobType,
                failures,
                notes,
                capacityBlocked
        );
    }

    private void validateTarget(
            BotConfigurationDto configuration,
            List<String> failures
    ) {
        String targetMode = trim(configuration.getTargetMode());

        if ("SEARCH_QUERY".equalsIgnoreCase(targetMode)) {
            if (trim(configuration.getSearchQuery()).isBlank()) {
                failures.add("SEARCH_QUERY target has an empty searchQuery");
            }
            return;
        }

        if ("VINTED_MODEL".equalsIgnoreCase(targetMode)) {
            if (trim(configuration.getBrand()).isBlank()) {
                failures.add("VINTED_MODEL target has an empty brand");
            }
            if (trim(configuration.getModel()).isBlank()) {
                failures.add("VINTED_MODEL target has an empty model");
            }
            return;
        }

        failures.add("unsupported or missing targetMode: " + targetMode);
    }

    private void validatePriceRange(
            BotConfigurationDto configuration,
            List<String> failures
    ) {
        BigDecimal minPrice = configuration.getMinPrice();
        BigDecimal maxPrice = configuration.getMaxPrice();

        if (minPrice == null || minPrice.signum() < 0) {
            failures.add("minPrice is missing or negative");
        }

        if (maxPrice == null || maxPrice.signum() <= 0) {
            failures.add("maxPrice is missing or not positive");
        }

        if (minPrice != null
                && maxPrice != null
                && minPrice.compareTo(maxPrice) > 0) {
            failures.add("minPrice is greater than maxPrice");
        }
    }

    private void validateBudget(
            BotConfigurationDto configuration,
            List<String> failures
    ) {
        Integer budget = configuration.getDailyNegotiationBudget();

        if (budget == null || budget <= 0) {
            failures.add("dailyNegotiationBudget must be positive");
        }
    }

    private void validateNegotiationSteps(
            BotConfigurationDto configuration,
            boolean firstOfferRequested,
            boolean nextStepRequested,
            List<String> failures,
            List<String> notes
    ) {
        List<NegotiationStepDto> steps = configuration.getNegotiationSteps();

        if (steps == null || steps.isEmpty()) {
            failures.add("negotiationSteps are empty");
            return;
        }

        long nullEntries = steps.stream().filter(step -> step == null).count();
        if (nullEntries > 0) {
            failures.add("negotiationSteps contain " + nullEntries + " null entrie(s)");
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
                        "negotiation steps must be sequential starting at 1; expected "
                                + expectedStepNumber
                                + " but got "
                                + stepNumber
                );
            } else if (!seen.add(stepNumber)) {
                failures.add("duplicate negotiation step number: " + stepNumber);
            }

            BigDecimal offerPrice = step.getOfferPrice();
            if (offerPrice == null || offerPrice.signum() <= 0) {
                failures.add(
                        "step "
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
                failures.add("FIRST_OFFER requires negotiation step #1");
            } else if (trim(firstStep.getMessage()).isBlank()) {
                failures.add(
                        "step #1 message is blank; controlled first-offer test requires offer + message"
                );
            }
        }

        if (nextStepRequested && sorted.size() < 2) {
            failures.add("NEXT_STEP test requires at least two negotiation steps");
        }

        notes.add("configured negotiation steps: " + sorted.size());
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

    public record Result(
            boolean ready,
            boolean expectedCapacityBlock,
            List<String> failures,
            List<String> notes
    ) {
    }
}
