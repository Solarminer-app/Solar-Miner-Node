package de.verdox.phoenixdjava;

import com.fasterxml.jackson.annotation.JsonProperty;

public class PhoenixDTOs {
    public record NodeInfo(
            String nodeId,
            ChannelInfo[] channels
    ) {
    }

    public record ChannelInfo(
            String state,
            String channelId,
            long balanceSat,
            long inboundLiquiditySat,
            long capacitySat,
            String fundingTxId
    ) {
    }

    public record WalletBalance(
            long balanceSat,
            long feeCreditSat
    ) {
    }

    public record CreateInvoiceResponse(
            String paymentHash,
            String serialized
    ) {
    }

    public record PayInvoiceResponse(
            String paymentId,
            String paymentHash,
            long preimage,
            long amountSat,
            long feeSat
    ) {
    }

    public record EstimatedLiquidityResponse(long miningFeeSat, long serviceFeeSat) {
    }

    public record PayResponse(
            long recipientAmountSat,
            long routingFeeSat,
            String paymentId,
            String paymentHash,
            String paymentPreimage

    ) {

    }

    public record IncomingPayment(
            String type,
            String subType,
            String paymentHash,
            String preimage,
            String description,
            String invoice,
            boolean isPaid,
            boolean isExpired,

            @JsonProperty("amountSat") long amountSat,
            Long receivedSat,
            Long fees,
            long expiresAt,
            long completedAt,
            long createdAt
    ) {
    }


}