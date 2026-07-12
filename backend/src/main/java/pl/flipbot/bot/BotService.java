package pl.flipbot.bot;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pl.flipbot.bot.configuration.BotConfiguration;
import pl.flipbot.bot.configuration.BotConfigurationRepository;
import pl.flipbot.bot.dto.BotResponse;
import pl.flipbot.bot.dto.CreateBotConfigurationRequest;
import pl.flipbot.bot.dto.CreateBotRequest;
import pl.flipbot.exception.BotAlreadyExistsException;
import pl.flipbot.exception.BotNotFoundException;
import pl.flipbot.mapper.BotMapper;
import pl.flipbot.negotiation.NegotiationStep;
import pl.flipbot.negotiation.dto.CreateNegotiationStepRequest;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BotService {

    private final BotRepository botRepository;
    private final BotConfigurationRepository botConfigurationRepository;
    private final BotMapper botMapper;

    public List<BotResponse> getAllBots() {
        return botRepository.findAll()
                .stream()
                .map(botMapper::map)
                .toList();
    }

    @Transactional
    public BotResponse createBot(CreateBotRequest request) {

        if (botRepository.existsByEmail(request.getEmail())) {
            throw new BotAlreadyExistsException(request.getEmail());
        }

        Bot bot = Bot.builder()
                .name(request.getName())
                .email(request.getEmail())
                .password(request.getPassword())
                .status(BotStatus.STOPPED)
                .build();

        Bot savedBot = botRepository.save(bot);

        CreateBotConfigurationRequest configurationRequest = request.getConfiguration();

        BotConfiguration configuration = BotConfiguration.builder()
                .marketplace(configurationRequest.getMarketplace())
                .category(configurationRequest.getCategory())
                .subCategory(configurationRequest.getSubCategory())
                .brand(configurationRequest.getBrand())
                .model(configurationRequest.getModel())
                .minPrice(configurationRequest.getMinPrice())
                .maxPrice(configurationRequest.getMaxPrice())
                .bot(savedBot)
                .build();

        savedBot.setConfiguration(configuration);

        int stepNumber = 1;

        for (CreateNegotiationStepRequest stepRequest : configurationRequest.getNegotiationSteps()) {

            NegotiationStep step = NegotiationStep.builder()
                    .stepNumber(stepNumber++)
                    .offerPrice(stepRequest.getOfferPrice())
                    .maxAcceptedCounterOffer(stepRequest.getMaxAcceptedCounterOffer())
                    .message(stepRequest.getMessage())
                    .configuration(configuration)
                    .build();

            configuration.getNegotiationSteps().add(step);
        }

        botConfigurationRepository.save(configuration);

        return botMapper.map(savedBot);
    }

    @Transactional
    public void startBot(Long botId) {

        Bot bot = botRepository.findById(botId)
                .orElseThrow(() ->
                        new BotNotFoundException(botId));

        bot.setStatus(BotStatus.RUNNING);
    }

    @Transactional
    public void stopBot(Long botId) {

        Bot bot = botRepository.findById(botId)
                .orElseThrow(() ->
                        new BotNotFoundException(botId));

        bot.setStatus(BotStatus.STOPPED);

    }

    public List<BotResponse> getRunningBots() {

        return botRepository.findByStatus(BotStatus.RUNNING)
                .stream()
                .map(botMapper::map)
                .toList();

    }
}