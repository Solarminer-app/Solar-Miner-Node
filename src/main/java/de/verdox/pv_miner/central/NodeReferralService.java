package de.verdox.pv_miner.central;

import de.verdox.pv_miner.pvsite.PVSiteEntity;
import de.verdox.pv_miner.pvsite.PVSiteRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Always-on, node-bound referral reporting + the selectable referral catalogue
 * (independent of the telemetry data-sharing opt-in). Mirrors the
 * {@code TelemetryReporter} RestClient pattern against the same admin service
 * ({@code solarmining.admin.telemetry.url}):
 *
 * <ul>
 *   <li><b>Report</b> — every site's stable {@code nodeIdentity} + bound referral
 *       code is POSTed to {@code /api/node/referral} so the portal knows the code
 *       of <i>every</i> node (this is how a referrer sees how many nodes use their
 *       code). Triggered on referral save/delete and by a periodic sweep.</li>
 *   <li><b>Catalogue</b> — {@code GET /api/node/referral-codes} returns the ACTIVE
 *       non-house codes with fee conditions + node counts (served to the UI via
 *       the Node's own same-origin endpoint).</li>
 * </ul>
 *
 * <p>Best-effort: a blank base URL (self-hosted/offline) or any transport error is
 * swallowed — the referral feature must never disturb mining.
 */
@Service
public class NodeReferralService {

    private static final Logger log = LoggerFactory.getLogger(NodeReferralService.class);

    private final PVSiteRepository pvSiteRepository;
    private final RestClient restClient;

    public NodeReferralService(PVSiteRepository pvSiteRepository, RestClient.Builder restClientBuilder,
                               @Value("${solarmining.admin.telemetry.url:}") String adminUrl) {
        this.pvSiteRepository = pvSiteRepository;
        String base = trimEnd(adminUrl);
        this.restClient = base.isBlank() ? null : restClientBuilder.baseUrl(base).build();
    }

    /** Report every site's bound referral code (always on; the opt-in is irrelevant). */
    @Scheduled(fixedDelay = 300_000, initialDelay = 30_000)
    public void reportAll() {
        if (restClient == null) {
            return; // no admin endpoint configured (self-hosted/offline)
        }
        for (PVSiteEntity site : pvSiteRepository.findAll()) {
            reportSite(site);
        }
    }

    /** Report a single site's bound referral code (minting its identity if needed). */
    public void reportSite(PVSiteEntity site) {
        if (restClient == null) {
            return;
        }
        String uuid = ensureIdentity(site);
        try {
            restClient.post()
                    .uri("/api/node/referral")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "uuid", uuid,
                            "referralCode", site.getReferralCode() == null ? "" : site.getReferralCode()))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RuntimeException e) {
            log.info("Referral report failed for site {}: {}", site.getId(), e.getMessage());
        }
    }

    /** The selectable referral catalogue from the admin service (empty when offline). */
    public List<ReferralCode> catalog() {
        if (restClient == null) {
            return List.of();
        }
        try {
            ReferralCode[] codes = restClient.get()
                    .uri("/api/node/referral-codes")
                    .retrieve()
                    .body(ReferralCode[].class);
            return codes == null ? List.of() : List.of(codes);
        } catch (RuntimeException e) {
            log.info("Referral catalogue fetch failed: {}", e.getMessage());
            return List.of();
        }
    }

    /** Mint + persist a stable random node identity on first use (separate from the PK). */
    private String ensureIdentity(PVSiteEntity site) {
        String identity = site.getNodeIdentity();
        if (identity == null || identity.isBlank()) {
            identity = UUID.randomUUID().toString();
            site.setNodeIdentity(identity);
            pvSiteRepository.save(site);
        }
        return identity;
    }

    private static String trimEnd(String s) {
        if (s == null) {
            return "";
        }
        String t = s.strip();
        return t.endsWith("/") ? t.substring(0, t.length() - 1) : t;
    }

    /** One row of the admin referral catalogue (field names mirror the admin DTO). */
    public record ReferralCode(String code, String name, double totalFee, double referralShare,
                               double solarMinerShare, long userCount) {
    }
}
