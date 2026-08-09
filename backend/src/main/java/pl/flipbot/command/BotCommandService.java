package pl.flipbot.command;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.flipbot.command.dto.BotCommandResponse;
import pl.flipbot.listing.Listing;
import pl.flipbot.listing.ListingRepository;
import pl.flipbot.listing.ListingStatus;

import pl.flipbot.command.dto.BotCommandWorkerResponse;
import java.time.Instant;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BotCommandService {

    private final BotCommandRepository botCommandRepository;

    private final ListingRepository listingRepository;

    @Transactional
    public BotCommandResponse createOpenConversationCommand(
            Long botId,
            Long listingId
    ) {

        Listing listing =
                listingRepository
                        .findByIdAndBotId(
                                listingId,
                                botId
                        )
                        .orElseThrow(
                                () ->
                                        new ListingForCommandNotFoundException(
                                                botId,
                                                listingId
                                        )
                        );

        validateOpenConversationListing(
                listing
        );

        boolean commandAlreadyExists =
                botCommandRepository
                        .existsByBotIdAndListingIdAndTypeAndStatusIn(
                                botId,
                                listingId,
                                BotCommandType.OPEN_CONVERSATION,
                                List.of(
                                        BotCommandStatus.PENDING,
                                        BotCommandStatus.PROCESSING
                                )
                        );

        if (commandAlreadyExists) {

            throw new BotCommandCannotBeCreatedException(
                    "An OPEN_CONVERSATION command "
                            + "already exists for listing "
                            + listingId
            );
        }

        BotCommand command =
                new BotCommand();

        command.setBotId(
                botId
        );

        command.setListingId(
                listingId
        );

        command.setType(
                BotCommandType.OPEN_CONVERSATION
        );

        BotCommand savedCommand =
                botCommandRepository.save(
                        command
                );

        return mapResponse(
                savedCommand
        );
    }

    private void validateOpenConversationListing(
            Listing listing
    ) {

        if (listing.getStatus()
                != ListingStatus.ACTION_REQUIRED) {

            throw new BotCommandCannotBeCreatedException(
                    "Conversation can only be opened "
                            + "for an ACTION_REQUIRED listing. "
                            + "Current status: "
                            + listing.getStatus()
            );
        }

        if (listing.getConversationUrl() == null
                || listing.getConversationUrl()
                .isBlank()) {

            throw new BotCommandCannotBeCreatedException(
                    "Listing "
                            + listing.getId()
                            + " has no conversation URL"
            );
        }
    }

    private BotCommandResponse mapResponse(
            BotCommand command
    ) {

        return new BotCommandResponse(
                command.getId(),
                command.getBotId(),
                command.getListingId(),
                command.getType(),
                command.getStatus(),
                command.getCreatedAt()
        );
    }

    @Transactional
    public BotCommandWorkerResponse claimNextCommand(
            Long botId
    ) {

        BotCommand command =
                botCommandRepository
                        .findFirstByBotIdAndStatusOrderByIdAsc(
                                botId,
                                BotCommandStatus.PENDING
                        )
                        .orElse(null);

        if (command == null) {

            return null;
        }

        Listing listing =
                listingRepository
                        .findByIdAndBotId(
                                command.getListingId(),
                                botId
                        )
                        .orElseThrow(
                                () ->
                                        new ListingForCommandNotFoundException(
                                                botId,
                                                command.getListingId()
                                        )
                        );

        if (listing.getConversationUrl() == null
                || listing.getConversationUrl().isBlank()) {

            command.setStatus(
                    BotCommandStatus.FAILED
            );

            command.setErrorMessage(
                    "Conversation URL is missing"
            );

            command.setProcessedAt(
                    Instant.now()
            );

            return null;
        }

        command.setStatus(
                BotCommandStatus.PROCESSING
        );

        return new BotCommandWorkerResponse(
                command.getId(),
                command.getBotId(),
                command.getListingId(),
                command.getType(),
                listing.getConversationUrl()
        );
    }

    @Transactional
    public void completeCommand(
            Long botId,
            Long commandId
    ) {

        BotCommand command =
                getCommand(
                        botId,
                        commandId
                );

        requireProcessing(
                command
        );

        command.setStatus(
                BotCommandStatus.COMPLETED
        );

        command.setErrorMessage(
                null
        );

        command.setProcessedAt(
                Instant.now()
        );
    }

    private BotCommand getCommand(
            Long botId,
            Long commandId
    ) {

        return botCommandRepository
                .findByIdAndBotId(
                        commandId,
                        botId
                )
                .orElseThrow(
                        () ->
                                new BotCommandInvalidStateException(
                                        "Command "
                                                + commandId
                                                + " was not found for bot "
                                                + botId
                                )
                );
    }

    private void requireProcessing(
            BotCommand command
    ) {

        if (command.getStatus()
                != BotCommandStatus.PROCESSING) {

            throw new BotCommandInvalidStateException(
                    "Command "
                            + command.getId()
                            + " is not PROCESSING. Current status: "
                            + command.getStatus()
            );
        }
    }

    @Transactional
    public void failCommand(
            Long botId,
            Long commandId,
            String errorMessage
    ) {

        BotCommand command =
                getCommand(
                        botId,
                        commandId
                );

        requireProcessing(
                command
        );

        command.setStatus(
                BotCommandStatus.FAILED
        );

        command.setErrorMessage(
                errorMessage
        );

        command.setProcessedAt(
                Instant.now()
        );
    }
}