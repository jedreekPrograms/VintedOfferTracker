import { describe, expect, it } from "vitest";
import { getListingStatusLabel } from "./historyStatusLabel";

describe("getListingStatusLabel", () => {
    it("describes a cross-bot negotiation claim conflict", () => {
        expect(getListingStatusLabel("SKIPPED_ALREADY_NEGOTIATED"))
            .toBe("Pominięto — negocjowane przez innego bota");
    });
});
