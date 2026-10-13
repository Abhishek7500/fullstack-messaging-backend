package com.messenger.domain;

/**
 * Subscription tiers used to gate premium capabilities.
 * <p>
 * The ordinal order matters: {@link #atLeast(TierType)} treats a higher
 * ordinal as a "greater" entitlement, so {@code PAID_FAMILY} satisfies a
 * {@code FREE_PERSONAL} requirement but not vice-versa.
 */
public enum TierType {
    FREE_PERSONAL,
    PAID_FAMILY;

    /** @return true when this tier is at least as high as {@code other}. */
    public boolean atLeast(TierType other) {
        return this.ordinal() >= other.ordinal();
    }
}
