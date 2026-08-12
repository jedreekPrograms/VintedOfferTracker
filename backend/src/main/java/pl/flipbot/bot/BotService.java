package pl.flipbot.bot;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pl.flipbot.bot.configuration.BotConfiguration;
import pl.flipbot.bot.configuration.BotConfigurationRepository;
import pl.flipbot.bot.configuration.TargetMode;
import pl.flipbot.bot.dto.BotPlaywrightResponse;
import pl.flipbot.bot.dto.BotResponse;
import pl.flipbot.bot.dto.CreateBotConfigurationRequest;
import pl.flipbot.bot.dto.CreateBotRequest;
import pl.flipbot.bot.dto.RunningBotResponse;
import pl.flipbot.exception.BotAlreadyExistsException;
import pl.flipbot.exception.BotNotFoundException;
import pl.flipbot.mapper.BotMapper;
import pl.flipbot.negotiation.NegotiationStep;
import pl.flipbot.negotiation.dto.CreateNegotiationStepRequest;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BotService {

    private final BotRepository botRepository;

    private final BotConfigurationRepository
            botConfigurationRepository;

    private final BotMapper botMapper;


    public List<BotResponse> getAllBots() {

        return botRepository.findAll()
                .stream()
                .map(
                        botMapper::map
                )
                .toList();
    }


    @Transactional
    public BotResponse createBot(
            CreateBotRequest request
    ) {

        if (
                botRepository.existsByEmail(
                        request.getEmail()
                )
        ) {

            throw new BotAlreadyExistsException(
                    request.getEmail()
            );
        }


        CreateBotConfigurationRequest configurationRequest =
                request.getConfiguration();


        validateConfiguration(
                configurationRequest
        );


        TargetMode targetMode =
                resolveTargetMode(
                        configurationRequest
                );


        boolean autoRaiseOfferToVintedMinimum =
                Boolean.TRUE.equals(
                        configurationRequest
                                .getAutoRaiseOfferToVintedMinimum()
                );


        Bot bot =
                Bot.builder()
                        .name(
                                request.getName()
                        )
                        .email(
                                request.getEmail()
                        )
                        .password(
                                request.getPassword()
                        )
                        .status(
                                BotStatus.STOPPED
                        )
                        .build();


        Bot savedBot =
                botRepository.save(
                        bot
                );


        BotConfiguration configuration =
                BotConfiguration.builder()
                        .marketplace(
                                configurationRequest
                                        .getMarketplace()
                        )
                        .categoryPath(
                                configurationRequest
                                        .getCategoryPath()
                        )
                        .brand(
                                normalizeRequiredText(
                                        configurationRequest
                                                .getBrand()
                                )
                        )
                        .targetMode(
                                targetMode
                        )
                        .model(
                                targetMode
                                        == TargetMode.VINTED_MODEL
                                        ? normalizeRequiredText(
                                                configurationRequest
                                                        .getModel()
                                        )
                                        : null
                        )
                        .searchQuery(
                                targetMode
                                        == TargetMode.SEARCH_QUERY
                                        ? normalizeRequiredText(
                                                configurationRequest
                                                        .getSearchQuery()
                                        )
                                        : null
                        )
                        .minPrice(
                                configurationRequest
                                        .getMinPrice()
                        )
                        .maxPrice(
                                configurationRequest
                                        .getMaxPrice()
                        )
                        .autoRaiseOfferToVintedMinimum(
                                autoRaiseOfferToVintedMinimum
                        )
                        .maxAutomaticOffer(
                                autoRaiseOfferToVintedMinimum
                                        ? configurationRequest
                                                .getMaxAutomaticOffer()
                                        : null
                        )
                        .dailyNegotiationBudget(
                                configurationRequest
                                        .getDailyNegotiationBudget()
                        )
                        .bot(
                                savedBot
                        )
                        .build();


        savedBot.setConfiguration(
                configuration
        );


        int stepNumber =
                1;


        for (
                CreateNegotiationStepRequest stepRequest
                : configurationRequest.getNegotiationSteps()
        ) {

            NegotiationStep step =
                    NegotiationStep.builder()
                            .stepNumber(
                                    stepNumber++
                            )
                            .offerPrice(
                                    stepRequest
                                            .getOfferPrice()
                            )
                            .maxAcceptedCounterOffer(
                                    stepRequest
                                            .getMaxAcceptedCounterOffer()
                            )
                            .message(
                                    stepRequest
                                            .getMessage()
                            )
                            .configuration(
                                    configuration
                            )
                            .build();


            configuration
                    .getNegotiationSteps()
                    .add(
                            step
                    );
        }


        botConfigurationRepository.save(
                configuration
        );


        return botMapper.map(
                savedBot
        );
    }


    @Transactional
    public void startBot(
            Long botId
    ) {

        Bot bot =
                botRepository.findById(
                                botId
                        )
                        .orElseThrow(
                                () ->
                                        new BotNotFoundException(
                                                botId
                                        )
                        );


        bot.setStatus(
                BotStatus.RUNNING
        );
    }


    @Transactional
    public void stopBot(
            Long botId
    ) {

        Bot bot =
                botRepository.findById(
                                botId
                        )
                        .orElseThrow(
                                () ->
                                        new BotNotFoundException(
                                                botId
                                        )
                        );


        bot.setStatus(
                BotStatus.STOPPED
        );
    }


    public BotPlaywrightResponse getPlaywrightBot(
            Long botId
    ) {

        Bot bot =
                botRepository.findById(
                                botId
                        )
                        .orElseThrow(
                                () ->
                                        new BotNotFoundException(
                                                botId
                                        )
                        );


        if (
                bot.getStatus()
                        != BotStatus.RUNNING
        ) {

            throw new IllegalStateException(
                    "Bot is not running."
            );
        }


        return botMapper.mapPlaywright(
                bot
        );
    }


    public List<RunningBotResponse> getRunningBotIds() {

        return botRepository
                .findByStatus(
                        BotStatus.RUNNING
                )
                .stream()
                .map(
                        botMapper::mapRunning
                )
                .toList();
    }


    private void validateConfiguration(
            CreateBotConfigurationRequest request
    ) {

        TargetMode targetMode =
                resolveTargetMode(
                        request
                );


        validatePriceRange(
                request.getMinPrice(),
                request.getMaxPrice()
        );


        if (
                request.getDailyNegotiationBudget()
                        == null
                || request.getDailyNegotiationBudget()
                        <= 0
        ) {

            throw new IllegalArgumentException(
                    "Daily negotiation budget must be greater than 0."
            );
        }


        if (
                request.getNegotiationSteps()
                        == null
                || request.getNegotiationSteps()
                        .isEmpty()
        ) {

            throw new IllegalArgumentException(
                    "At least one negotiation step is required."
            );
        }


        if (
                targetMode
                        == TargetMode.VINTED_MODEL
        ) {

            requireNonBlank(
                    request.getModel(),
                    "Model is required for target mode VINTED_MODEL."
            );

        } else if (
                targetMode
                        == TargetMode.SEARCH_QUERY
        ) {

            requireNonBlank(
                    request.getSearchQuery(),
                    "Search query is required for target mode SEARCH_QUERY."
            );
        }


        boolean autoRaiseOfferToVintedMinimum =
                Boolean.TRUE.equals(
                        request
                                .getAutoRaiseOfferToVintedMinimum()
                );


        if (
                autoRaiseOfferToVintedMinimum
        ) {

            BigDecimal maxAutomaticOffer =
                    request.getMaxAutomaticOffer();


            if (
                    maxAutomaticOffer == null
                    || maxAutomaticOffer.signum()
                    <= 0
            ) {

                throw new IllegalArgumentException(
                        "Max automatic offer must be greater than 0 "
                                + "when automatic Vinted-minimum adjustment "
                                + "is enabled."
                );
            }


            if (
                    request.getMaxPrice() != null
                    && maxAutomaticOffer.compareTo(
                            request.getMaxPrice()
                    ) > 0
            ) {

                throw new IllegalArgumentException(
                        "Max automatic offer cannot be greater than "
                                + "the configured maximum listing price."
                );
            }
        }
    }


    private TargetMode resolveTargetMode(
            CreateBotConfigurationRequest request
    ) {

        if (
                request.getTargetMode()
                        == null
        ) {

            return TargetMode.VINTED_MODEL;
        }


        return request.getTargetMode();
    }


    private void validatePriceRange(
            BigDecimal minPrice,
            BigDecimal maxPrice
    ) {

        if (
                minPrice == null
                || maxPrice == null
        ) {

            return;
        }


        if (
                minPrice.signum() < 0
                || maxPrice.signum() < 0
        ) {

            throw new IllegalArgumentException(
                    "Listing prices cannot be negative."
            );
        }


        if (
                minPrice.compareTo(
                        maxPrice
                ) > 0
        ) {

            throw new IllegalArgumentException(
                    "Minimum listing price cannot be greater than "
                            + "maximum listing price."
            );
        }
    }


    private void requireNonBlank(
            String value,
            String message
    ) {

        if (
                value == null
                || value.isBlank()
        ) {

            throw new IllegalArgumentException(
                    message
            );
        }
    }


    private String normalizeRequiredText(
            String value
    ) {

        return value
                .trim()
                .replaceAll(
                        "\\s+",
                        " "
                );
    }
}
