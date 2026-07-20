package pl.flipbot.playwright.filters;

import lombok.experimental.UtilityClass;

@UtilityClass
public class FilterUtils {

    public static String price(Number value) {

        return value == null ? "" : value.toString();

    }

}