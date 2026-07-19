package de.verdox.pv_miner.controller;

import de.verdox.phoenixdjava.PhoenixDTOs;
import de.verdox.pv_miner.globalconstants.GlobalConstantsService;
import de.verdox.pv_miner.lightning.LightningTransaction;
import de.verdox.pv_miner.lightning.LightningWalletService;
import de.verdox.pv_miner.lightning.SolarMiningWebSocketClient;
import de.verdox.pv_miner.util.currency.CustomCurrency;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/lightning-wallet")
public class LightningWalletController {

    private final LightningWalletService walletService;
    private final GlobalConstantsService globalConstantsService;
    private final SolarMiningWebSocketClient solarMiningWebSocketClient;

    public LightningWalletController(
            LightningWalletService walletService,
            GlobalConstantsService globalConstantsService,
            SolarMiningWebSocketClient solarMiningWebSocketClient
    ) {
        this.walletService = walletService;
        this.globalConstantsService = globalConstantsService;
        this.solarMiningWebSocketClient = solarMiningWebSocketClient;
    }

    @GetMapping
    public WalletData getWalletData(
            @RequestParam(value = "currency", defaultValue = "EUR") String currencyCode,
            @RequestParam(value = "locale", defaultValue = "de") String localeTag
    ) {
        Locale userLocale = Locale.forLanguageTag(localeTag);
        CustomCurrency userCurrency = CustomCurrency.getInstance(currencyCode);

        long balance = walletService.getBalanceSat();
        long feeCredit = walletService.getFreeCreditSat();
        String address = walletService.claimFreeLightningAddress();
        String bolt12 = walletService.getBolt12();

        int activeChannels = 0;
        long localLiquidity = 0;
        long remoteLiquidity = 0;

        PhoenixDTOs.NodeInfo nodeInfo = walletService.getNodeInfo();
        if (nodeInfo != null && nodeInfo.channels() != null) {
            for (var channel : nodeInfo.channels()) {
                localLiquidity += channel.balanceSat();
                remoteLiquidity += channel.inboundLiquiditySat();
                if ("STABLE".equalsIgnoreCase(channel.state())) {
                    activeChannels++;
                }
            }
        }

        WalletStats stats = new WalletStats(
                activeChannels,
                convertSatsToUserCurrencyString(localLiquidity, userCurrency, userLocale),
                convertSatsToUserCurrencyString(remoteLiquidity, userCurrency, userLocale)
        );

        List<LightningTransactionDTO> transactions = walletService.getTransactions().stream().map(tx -> {
            // TimeZone/ZoneId könnte man analog auch als Parameter übergeben!
            ZoneId zoneId = ZoneId.systemDefault();
            ZonedDateTime zonedDateTime = tx.timestamp().atZone(zoneId);
            String formattedDate = zonedDateTime.format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"));

            String prefix = tx.type() == LightningTransaction.Type.INCOMING ? "+ " : "- ";
            String formattedAmount = prefix + convertSatsToUserCurrencyString(tx.amountSat(), userCurrency, userLocale);

            String id = UUID.randomUUID().toString();

            return new LightningTransactionDTO(
                    id,
                    tx.type().name(),
                    formattedDate,
                    tx.memo() != null ? tx.memo() : "",
                    tx.amountSat(),
                    formattedAmount,
                    tx.status().name()
            );
        }).collect(Collectors.toList());

        return new WalletData(
                balance,
                convertSatsToUserCurrencyString(balance, userCurrency, userLocale),
                feeCredit,
                convertSatsToUserCurrencyString(feeCredit, userCurrency, userLocale),
                address,
                bolt12,
                stats,
                transactions
        );
    }

    @PostMapping("/pay")
    public ResponseEntity<PaymentResponse> sendPayment(@RequestBody PaymentRequest request) {
        boolean success;

        if (request.amountSat() != null && request.amountSat() > 0) {
            success = walletService.sendPayment(request.target(), request.amountSat());
        } else {
            success = walletService.sendPayment(request.target());
        }

        return ResponseEntity.ok(new PaymentResponse(success));
    }

    @PostMapping("/connection/toggle")
    public ResponseEntity<ConnectionStatusResponse> toggleConnection() {

        boolean isEnabled = solarMiningWebSocketClient.isConnected();
        if (isEnabled) {
            solarMiningWebSocketClient.connect();
        } else {
            solarMiningWebSocketClient.disconnect();
        }

        boolean newStatus = solarMiningWebSocketClient.isConnected();
        return ResponseEntity.ok(new ConnectionStatusResponse(newStatus, solarMiningWebSocketClient.isConnected()));
    }

    @PostMapping("/withdraw/onchain")
    public ResponseEntity<PaymentResponse> withdrawOnChain(@RequestBody OnChainWithdrawRequest request) {
        boolean success = walletService.sendOnChainPayment(request.amountSat(), request.address(), request.feeRateSatPerVByte()) != null;
        return ResponseEntity.ok(new PaymentResponse(success));
    }

    private String convertSatsToUserCurrencyString(long sats, CustomCurrency userCurrency, Locale userLocale) {
        double btc = sats / 100000000.0;
        CustomCurrency btcCurrency = CustomCurrency.getInstance("BTC");

        double rate = globalConstantsService.getExchangeRate(btcCurrency, userCurrency);

        if (rate <= 0.0) {
            double fallbackBtcEurRate = 63000.0;
            rate = userCurrency.getCurrencyCode().equals("USD") ? fallbackBtcEurRate * 1.08 : fallbackBtcEurRate;
        }

        double convertedValue = btc * rate;
        return String.format("%,d sats (%s%,.2f)", sats, userCurrency.getSymbol(userLocale), convertedValue);
    }

    public record PaymentRequest(String target, Long amountSat) {
    }

    public record PaymentResponse(boolean success) {
    }

    public record ConnectionStatusResponse(boolean enabled, boolean connected) {
    }

    public record OnChainWithdrawRequest(String address, long amountSat, long feeRateSatPerVByte) {
    }

    public record WalletData(
            long balanceSat,
            String balanceFormatted,
            long feeCreditSat,
            String feeCreditFormatted,
            String lightningAddress,
            String bolt12Offer,
            WalletStats stats,
            List<LightningTransactionDTO> transactions
    ) {
    }

    public record WalletStats(
            int activeChannels,
            String localLiquidityFormatted,
            String remoteLiquidityFormatted
    ) {
    }

    public record LightningTransactionDTO(
            String id,
            String type,
            String timestamp,
            String memo,
            long amountSat,
            String amountFormatted,
            String status
    ) {
    }
}