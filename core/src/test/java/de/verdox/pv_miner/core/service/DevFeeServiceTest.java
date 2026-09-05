package de.verdox.pv_miner.core.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DevFeeServiceTest {

    @Test
    void acceptsOnlyReferralConfirmedByFeeTargetResponse() {
        var confirmed = new DevFeeService.FeeTarget(
                "referral:solar-friend",
                "stratum+tcp://example:3333",
                "worker.",
                "x",
                1.0,
                "REFERRAL",
                "solar-friend",
                "solar-friend"
        );
        var solarMiner = new DevFeeService.FeeTarget(
                "solarminer",
                "stratum+tcp://example:3333",
                "worker.",
                "x",
                1.5,
                "SOLARMINER",
                "SolarMiner",
                null
        );

        assertTrue(DevFeeService.hasReferralTarget(List.of(solarMiner, confirmed), "solar-friend"));
        assertFalse(DevFeeService.hasReferralTarget(List.of(solarMiner), "solar-friend"));
        assertFalse(DevFeeService.hasReferralTarget(List.of(), "solar-friend"));
    }

    @Test
    void matchesAdminPortalTargetIdFormatOnTheWire() {
        // What the stratum proxy actually returns: the 5-field shape (the three
        // beneficiary fields are null) and the admin portal's targetId
        // "solarminer-referral-<code>-<coin>".
        var wireTarget = new DevFeeService.FeeTarget(
                "solarminer-referral-solar-friend-btc",
                "stratum+tcp://stratum.braiins.com:3333",
                "worker.",
                "",
                1.0,
                null,
                null,
                null
        );
        var solarMiner = new DevFeeService.FeeTarget(
                "solarminer-btc-sha256",
                "stratum+tcp://stratum.braiins.com:3333",
                "solarmining_app.",
                "",
                2.5,
                null,
                null,
                null
        );

        assertTrue(DevFeeService.hasReferralTarget(List.of(solarMiner, wireTarget), "solar-friend"));
        assertTrue(DevFeeService.hasReferralTarget(List.of(solarMiner, wireTarget), "Solar-Friend"));
        // The house target alone must never validate a referral, nor a different code.
        assertFalse(DevFeeService.hasReferralTarget(List.of(solarMiner), "solar-friend"));
        assertFalse(DevFeeService.hasReferralTarget(List.of(solarMiner, wireTarget), "other-code"));
    }

    @Test
    void legacyTargetListCannotAccidentallyValidateAnIgnoredQueryParameter() {
        var legacyTarget = new DevFeeService.FeeTarget(
                "solarminer-primary",
                "stratum+tcp://example:3333",
                "worker.",
                "x",
                2.5,
                null,
                null,
                null
        );

        assertFalse(DevFeeService.hasReferralTarget(List.of(legacyTarget), "unknown-code"));
    }
}
