package de.verdox.pv_miner.controller;

import de.verdox.pv_miner.central.NodeReferralService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Same-origin proxy for the central referral catalogue, so the SPA can list the
 * selectable referral codes (fee conditions + how many nodes use each, most-used
 * first) without a cross-origin call to the admin service.
 */
@RestController
@RequestMapping("/api")
@Tag(name = "Referral")
public class ReferralCatalogController {

    private final NodeReferralService nodeReferralService;

    public ReferralCatalogController(NodeReferralService nodeReferralService) {
        this.nodeReferralService = nodeReferralService;
    }

    @GetMapping("/referral-codes")
    public List<NodeReferralService.ReferralCode> referralCodes() {
        return nodeReferralService.catalog();
    }
}
