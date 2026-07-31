package pl.flipbot.playwright.processing;

import lombok.extern.slf4j.Slf4j;
import pl.flipbot.playwright.scanner.model.Listing;

import java.util.List;

@Slf4j
public class ListingProcessingService {

    public void process(List<Listing> listings) {

        log.info("Processing {} listings", listings.size());

        for (Listing listing : listings) {

            log.info(
                    "{} | {} | {}",
                    listing.getId(),
                    listing.getTitle(),
                    listing.getPrice()
            );

        }

    }

}
