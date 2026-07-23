package pl.flipbot.playwright.filters;

import com.microsoft.playwright.Page;
import pl.flipbot.playwright.context.BotContext;
import pl.flipbot.playwright.filters.category.CategoryNavigator;
import pl.flipbot.playwright.model.BotDetailsDto;

public class FilterService {

    private final BotContext context;

    private final Page page;

    private final FilterActions actions;

    private final CategoryNavigator categoryNavigator;

    public FilterService(BotContext context) {

        this.context = context;

        this.page = context.getPage();

        this.actions = new FilterActions(page);

        this.categoryNavigator =
                new CategoryNavigator(actions);

    }

    public void applyFilters(BotDetailsDto bot) {

        if (hasCategory(bot)) {
            applyCategory(bot);
        }

        if (hasBrand(bot)) {
            applyBrand(bot);
        }

        if (hasModel(bot)) {
            applyModel(bot);
        }

        if (hasPrice(bot)) {
            applyPrice(bot);
        }

    }

    private void applyCategory(BotDetailsDto bot) {

        categoryNavigator.select(
                bot.getConfiguration().getCategoryPath()
        );

    }

    private void applyBrand(BotDetailsDto bot) {

        actions.openFilter(FilterSelectors.BRAND_FILTER);

        actions.waitForOption(
                bot.getConfiguration().getBrand()
        );

        actions.selectOption(
                bot.getConfiguration().getBrand()
        );

        actions.clickSelector(
                FilterSelectors.FILTER_SELECTION
        );

    }

    private void applyModel(BotDetailsDto bot) {

        actions.openFilter(
                FilterSelectors.MODEL_FILTER
        );

        actions.fillInputBySelector(
                FilterSelectors.MODEL_SEARCH_INPUT,
                bot.getConfiguration().getModel()
        );

        actions.clickModel(
                bot.getConfiguration().getModel()
        );

        actions.clickConfirmButton();

    }


    private void applyPrice(BotDetailsDto bot) {

        if (bot.getConfiguration().getMinPrice() != null) {

            actions.fillInput(
                    FilterSelectors.MIN_PRICE,
                    FilterUtils.price(
                            bot.getConfiguration().getMinPrice()
                    )
            );

        }

        if (bot.getConfiguration().getMaxPrice() != null) {

            actions.fillInput(
                    FilterSelectors.MAX_PRICE,
                    FilterUtils.price(
                            bot.getConfiguration().getMaxPrice()
                    )
            );

        }

    }

    private boolean hasCategory(BotDetailsDto bot) {

        return bot.getConfiguration().getCategoryPath() != null
                && !bot.getConfiguration().getCategoryPath().isEmpty();

    }

    private boolean hasBrand(BotDetailsDto bot) {

        return bot.getConfiguration().getBrand() != null
                && !bot.getConfiguration().getBrand().isBlank();

    }

    private boolean hasModel(BotDetailsDto bot) {

        return bot.getConfiguration().getModel() != null
                && !bot.getConfiguration().getModel().isBlank();

    }

    private boolean hasPrice(BotDetailsDto bot) {

        return bot.getConfiguration().getMinPrice() != null
                || bot.getConfiguration().getMaxPrice() != null;

    }

}