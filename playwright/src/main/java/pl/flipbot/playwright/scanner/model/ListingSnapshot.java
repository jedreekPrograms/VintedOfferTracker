package pl.flipbot.playwright.scanner.model;

public record ListingSnapshot(

        String testId,

        String title,

        String condition,

        String price,

        String url,

        String imageUrl,

        String favoriteCount

) {
}