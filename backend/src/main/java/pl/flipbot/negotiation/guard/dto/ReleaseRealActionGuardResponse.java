package pl.flipbot.negotiation.guard.dto;

public record ReleaseRealActionGuardResponse(
        boolean released,
        boolean alreadyAbsent
) {
}
