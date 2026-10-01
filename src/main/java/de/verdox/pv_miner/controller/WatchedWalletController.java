package de.verdox.pv_miner.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.verdox.pv_miner.miningpool.WatchedWalletEntity;
import de.verdox.pv_miner.miningpool.MiningCoin;
import de.verdox.pv_miner.miningpool.WatchedWalletRepository;
import de.verdox.pv_miner.pvsite.PVSiteRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/pv-site/{siteId}/watched-wallets")
@CrossOrigin(origins = "http://localhost:3000")
public class WatchedWalletController {
    private static final BigDecimal ATOMIC_UNITS = new BigDecimal("100000000");
    private final WatchedWalletRepository wallets;
    private final PVSiteRepository sites;
    private final ObjectMapper mapper;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ConcurrentHashMap<String, BalanceSnapshot> cache = new ConcurrentHashMap<>();

    public WatchedWalletController(WatchedWalletRepository wallets, PVSiteRepository sites, ObjectMapper mapper) {
        this.wallets = wallets;
        this.sites = sites;
        this.mapper = mapper;
    }

    @GetMapping
    public List<WalletDto> list(@PathVariable UUID siteId) {
        requireSite(siteId);
        return wallets.findBySiteIdOrderByLabelAsc(siteId).parallelStream().map(wallet -> {
            BalanceSnapshot balance = readBalance(wallet.getCoin(), wallet.getAddress());
            return new WalletDto(wallet.getId(), wallet.getLabel(), wallet.getCoin(), wallet.getAddress(),
                    balance == null ? null : balance.confirmed(),
                    balance == null ? null : balance.unconfirmed(),
                    balance == null ? null : balance.fetchedAt(),
                    balance == null ? ("monero".equals(wallet.getCoin()) ? "NOT_SUPPORTED" : "UNAVAILABLE") : "AVAILABLE");
        }).toList();
    }

    @PostMapping
    public WalletDto create(@PathVariable UUID siteId, @RequestBody WalletRequest request) {
        requireSite(siteId);
        if (request == null || request.label() == null || request.label().isBlank() || request.label().length() > 120
                || request.address() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A coin, public address and label are required");
        }
        MiningCoin coin;
        try { coin = MiningCoin.from(request.coin()); }
        catch (IllegalArgumentException exception) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage()); }
        String address = request.address().trim();
        if (!coin.validAddress(address)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid public address format for " + coin.symbol());
        }
        if (wallets.existsBySiteIdAndCoinAndAddress(siteId, coin.key(), address)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Address is already watched");
        }
        if (wallets.findBySiteIdOrderByLabelAsc(siteId).size() >= 20) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At most 20 addresses can be watched per site");
        }
        WatchedWalletEntity entity = new WatchedWalletEntity();
        entity.setSiteId(siteId);
        entity.setLabel(request.label().trim());
        entity.setCoin(coin.key());
        entity.setAddress(address);
        entity = wallets.save(entity);
        return new WalletDto(entity.getId(), entity.getLabel(), entity.getCoin(), entity.getAddress(), null, null, null,
                coin == MiningCoin.MONERO ? "NOT_SUPPORTED" : "UNAVAILABLE");
    }

    @DeleteMapping("/{walletId}")
    public ResponseEntity<Void> delete(@PathVariable UUID siteId, @PathVariable UUID walletId) {
        requireSite(siteId);
        WatchedWalletEntity entity = wallets.findById(walletId).filter(wallet -> siteId.equals(wallet.getSiteId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Watched wallet not found"));
        wallets.delete(entity);
        cache.remove(entity.getCoin() + ":" + entity.getAddress());
        return ResponseEntity.noContent().build();
    }

    private void requireSite(UUID siteId) {
        if (!sites.existsById(siteId)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "PV site not found");
    }

    private BalanceSnapshot readBalance(String coin, String address) {
        if ("monero".equals(coin)) return null;
        String key = coin + ":" + address;
        BalanceSnapshot previous = cache.get(key);
        if (previous != null && previous.fetchedAt().isAfter(Instant.now().minusSeconds(120))) return previous;
        try {
            String endpoint = "pearl".equals(coin)
                    ? "https://pearlchain.live/api/explorer/address/" + address
                    : "https://mempool.space/api/address/" + address;
            HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(7)).GET().build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) return null;
            JsonNode root = mapper.readTree(response.body());
            if ("pearl".equals(coin)) {
                JsonNode grains = root.path("balance");
                if (!grains.isIntegralNumber() || grains.decimalValue().signum() < 0) return null;
                BalanceSnapshot snapshot = new BalanceSnapshot(grains.decimalValue().divide(ATOMIC_UNITS), null, Instant.now());
                cache.put(key, snapshot);
                return snapshot;
            }
            JsonNode chain = root.path("chain_stats");
            JsonNode mempool = root.path("mempool_stats");
            if (!chain.has("funded_txo_sum") || !chain.has("spent_txo_sum")
                    || !mempool.has("funded_txo_sum") || !mempool.has("spent_txo_sum")) return null;
            BalanceSnapshot snapshot = new BalanceSnapshot(
                    BigDecimal.valueOf(chain.path("funded_txo_sum").asLong() - chain.path("spent_txo_sum").asLong()).divide(ATOMIC_UNITS),
                    BigDecimal.valueOf(mempool.path("funded_txo_sum").asLong() - mempool.path("spent_txo_sum").asLong()).divide(ATOMIC_UNITS), Instant.now());
            cache.put(key, snapshot);
            return snapshot;
        } catch (Exception ignored) {
            return null;
        }
    }

    public record WalletRequest(String label, String coin, String address) { }
    public record WalletDto(UUID id, String label, String coin, String address, BigDecimal confirmedBalance,
                            BigDecimal unconfirmedBalance, Instant fetchedAt, String balanceStatus) { }
    private record BalanceSnapshot(BigDecimal confirmed, BigDecimal unconfirmed, Instant fetchedAt) { }
}
