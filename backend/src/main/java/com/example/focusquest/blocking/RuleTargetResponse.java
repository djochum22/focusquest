package com.example.focusquest.blocking;

public record RuleTargetResponse(
        Long id,
        TargetType targetType,
        String targetValue,
        String displayName,
        boolean active
) {

    public static RuleTargetResponse from(RuleTarget target) {
        return new RuleTargetResponse(target.getId(), target.getTargetType(), target.getTargetValue(),
                target.getDisplayName(), target.isActive());
    }
}
