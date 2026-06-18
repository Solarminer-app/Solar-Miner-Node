package de.verdox.phoenixdjava;

import org.apache.logging.log4j.util.InternalApi;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

/**
 * ⚠️ This API must be secured. It gives access to your funds. You are responsible for securing it. Specifically, this API should not be accessible from the outside world.
 * <p>
 * The API uses a Basic authentication scheme. Passwords are generated on first start (see ~/.phoenix/phoenix.conf).
 * <p>
 * Primary password
 * http-password is the primary password and gives access to all the API endpoints.
 * <p>
 * Secondary password
 * http-password-limited-access is less sensitive than the primary password, but it must still not be shared, as other attacks are possible, e.g. resource exhaustion by creating millions of invoices, etc...
 * <p>
 * The following endpoints are not available with this secondary password: payinvoice, payoffer, paylnaddress, lnurlpay, lnurlauth, sendtoaddress, closechannel, export.
 */
public interface PhoenixClient {
    // Payments

    /**
     * Creates a Bolt11 invoice with a description.
     * <p>
     * A Bolt11 invoice is a non-reusable, expirable payment request for Lightning, well suited for a retail payment flow. It can only be paid once.
     *
     * @param amountSat     (optional) the amount requested by the invoice, in satoshi. If not set, the invoice can be paid by any amount.
     * @param expirySeconds (optional) the invoice expiry in seconds, by default 3600 (1 hour).
     * @param description   the description of the invoice (max. 128 characters).
     * @param externalId    (optional) a custom identifier. Use that to link the invoice to an external system.
     * @param webhookUrl    (optional) a webhook url that will be notified when this specific payment has been received. This notification is done in addition to the normal webhooks defined in the configuration. This webhook is authenticated.
     */
    PhoenixDTOs.CreateInvoiceResponse createBolt11Invoice(long amountSat, int expirySeconds, String description, String externalId, String webhookUrl) throws IOException, InterruptedException;

    /**
     * Creates a Bolt12 offer with an optional description and amount.
     * <p>
     * An offer is a static and reusable payment request that does not expire. It can be paid many times. It's well suited for donations or tips.
     * <p>
     * Note: a getoffer call is also available but it is deprecated. This getoffer endpoint returns an offer, but always the same one. createoffer should be used instead.
     * <p>
     * Parameters
     *
     * @param amountSat   (optional) the description of the offer (max. 128 characters).
     * @param description (optional) the amount requested by the offer, in satoshi. If not set, the offer can be paid by any amount.
     */
    String createBolt12Offer(long amountSat, String description) throws IOException, InterruptedException;

    /**
     * Gets a BIP-353 Lightning address from the LSP. Only works if you have a channel.
     * <p>
     * Note you can also use third-party services or self-host the address.
     */
    String getLightningAddress() throws IOException, InterruptedException;

    /**
     * Pays a BOLT11 Lightning invoice. A 0.4% fee applies. Response includes the internal paymentId for that payment.
     *
     * @param invoice   BOLT11 invoice.
     * @param amountSat optional amount in satoshi. If unset, will pay the amount requested in the invoice.
     */
    default PhoenixDTOs.PayResponse payBolt11Invoice(String invoice, long amountSat) throws IOException, InterruptedException {
        return payBolt11InvoiceRaw(invoice, amountSat, false);
    }

    /**
     * Pays a BOLT11 Lightning invoice. A 0.4% fee applies. Response includes the internal paymentId for that payment.
     *
     * @param invoice BOLT11 invoice.
     */
    default PhoenixDTOs.PayResponse payBolt11InvoiceAll(String invoice) throws IOException, InterruptedException {
        return payBolt11InvoiceRaw(invoice, 0, true);
    }

    /**
     * Pays a BOLT12 Lightning offer. A 0.4% fee applies. Response includes the internal paymentId for that payment.
     *
     * @param offer               BOLT12 offer.
     * @param amountSat           optional amount in satoshi. If unset, will pay the amount requested in the invoice.
     * @param messageForRecipient an optional message for the recipient
     */
    default PhoenixDTOs.PayResponse payBolt12Offer(String offer, long amountSat, String messageForRecipient) throws IOException, InterruptedException {
        return payBolt12OfferRaw(offer, amountSat, false, messageForRecipient);
    }

    /**
     * Pays a BOLT12 Lightning offer. A 0.4% fee applies. Response includes the internal paymentId for that payment.
     *
     * @param offer               BOLT12 offer.
     * @param messageForRecipient an optional message for the recipient
     */
    default PhoenixDTOs.PayResponse payBolt12OfferAll(String offer, String messageForRecipient) throws IOException, InterruptedException {
        return payBolt12OfferRaw(offer, 0, true, messageForRecipient);
    }

    /**
     * Pays an email-like Lightning address, either based on BIP-353 or LNURL. A 0.4% fee applies. Response includes the internal paymentId for that payment.
     *
     * @param lightningAddress    the lightning address
     * @param amountSat           optional amount in satoshi. If unset, will pay the amount requested in the invoice.
     * @param messageForRecipient an optional message for the recipient
     */
    default PhoenixDTOs.PayResponse payLightningAddress(String lightningAddress, long amountSat, String messageForRecipient) throws IOException, InterruptedException {
        return payLightningAddressRaw(lightningAddress, amountSat, false, messageForRecipient);
    }

    /**
     * Pays an email-like Lightning address, either based on BIP-353 or LNURL. A 0.4% fee applies. Response includes the internal paymentId for that payment.
     *
     * @param lightningAddress    the lightning address
     * @param messageForRecipient an optional message for the recipient
     */
    default PhoenixDTOs.PayResponse payLightningAddressAll(String lightningAddress, String messageForRecipient) throws IOException, InterruptedException {
        return payLightningAddressRaw(lightningAddress, 0, true, messageForRecipient);
    }

    /**
     * Sends part of your current balance to a Bitcoin address. The spliced channel is not closed and remains active. Returns the transaction id if the splice was successful.
     *
     * @param amountSat      amount in satoshi
     * @param address        Bitcoin address where funds will be sent
     * @param feeRateSatByte fee rate in satoshi per vbyte
     */
    String payOnChain(long amountSat, String address, long feeRateSatByte) throws IOException, InterruptedException;

    /**
     * Makes all your unconfirmed transactions use a higher fee rate, using CPFP. Returns the ID of the child transaction.
     *
     * @param feeRateSatByte fee rate, in satoshi per vbyte.
     */
    String bumpFee(long feeRateSatByte) throws IOException, InterruptedException;

    /**
     * Lists incoming payments.
     *
     * @param from                  start timestamp in millis from epoch, default 0
     * @param to                    end timestamp in millis from epoch, default now
     * @param limit                 number of payments in the page, default 20
     * @param offset                page offset, default 0
     * @param includeUnpaidInvoices also return unpaid invoices
     * @param externalId            only include payments that use this external id.
     */
    List<PhoenixDTOs.IncomingPayment> listIncomingPayments(Instant from, Instant to, int limit, int offset, boolean includeUnpaidInvoices, String externalId) throws IOException, InterruptedException;

    /**
     * Lists incoming payments.
     *
     * @param limit                 number of payments in the page, default 20
     * @param offset                page offset, default 0
     * @param includeUnpaidInvoices also return unpaid invoices
     * @param externalId            only include payments that use this external id.
     */
    default List<PhoenixDTOs.IncomingPayment> listIncomingPayments(int limit, int offset, boolean includeUnpaidInvoices, String externalId) throws IOException, InterruptedException {
        return listIncomingPayments(Instant.ofEpochMilli(0), Instant.now(), limit, offset, includeUnpaidInvoices, externalId);
    }

    /**
     * Lists incoming payments.
     *
     * @param includeUnpaidInvoices also return unpaid invoices
     * @param externalId            only include payments that use this external id.
     */
    default List<PhoenixDTOs.IncomingPayment> listIncomingPayments(boolean includeUnpaidInvoices, String externalId) throws IOException, InterruptedException {
        return listIncomingPayments(Instant.ofEpochMilli(0), Instant.now(), 20, 0, includeUnpaidInvoices, externalId);
    }

    /**
     * Lists incoming payments.
     *
     * @param includeUnpaidInvoices also return unpaid invoices
     */
    default List<PhoenixDTOs.IncomingPayment> listIncomingPayments(boolean includeUnpaidInvoices) throws IOException, InterruptedException {
        return listIncomingPayments(Instant.ofEpochMilli(0), Instant.now(), 20, 0, includeUnpaidInvoices, null);
    }

    /**
     * Gets an incoming payment
     *
     * @param paymentHash the payment hash
     */
    PhoenixDTOs.IncomingPayment getIncomingPayment(String paymentHash) throws IOException, InterruptedException;

    /**
     * Lists incoming payments.
     *
     * @param from                  start timestamp in millis from epoch, default 0
     * @param to                    end timestamp in millis from epoch, default now
     * @param limit                 number of payments in the page, default 20
     * @param offset                page offset, default 0
     * @param includeFailedPayments also return payments that have failed
     */
    List<PhoenixDTOs.IncomingPayment> listOutgoingPayments(Instant from, Instant to, int limit, int offset, boolean includeFailedPayments) throws IOException, InterruptedException;

    /**
     * Lists incoming payments.
     *
     * @param limit                 number of payments in the page, default 20
     * @param offset                page offset, default 0
     * @param includeFailedPayments also return payments that have failed
     */
    default List<PhoenixDTOs.IncomingPayment> listOutgoingPayments(int limit, int offset, boolean includeFailedPayments) throws IOException, InterruptedException {
        return listOutgoingPayments(Instant.ofEpochMilli(0), Instant.now(), limit, offset, includeFailedPayments);
    }

    /**
     * Lists incoming payments.
     *
     * @param includeFailedPayments also return payments that have failed
     */
    default List<PhoenixDTOs.IncomingPayment> listOutgoingPayments(boolean includeFailedPayments) throws IOException, InterruptedException {
        return listOutgoingPayments(Instant.ofEpochMilli(0), Instant.now(), 20, 0, includeFailedPayments);
    }

    /**
     * Gets an incoming payment
     *
     * @param paymentHash the payment hash
     */
    PhoenixDTOs.IncomingPayment getOutgoingPaymentByHash(String paymentHash) throws IOException, InterruptedException;

    /**
     * Gets an incoming payment
     *
     * @param paymentId the payment id
     */
    PhoenixDTOs.IncomingPayment getOutgoingPaymentById(String paymentId) throws IOException, InterruptedException;


    // Node management
    PhoenixDTOs.NodeInfo getInfo() throws IOException, InterruptedException;

    PhoenixDTOs.WalletBalance getBalance() throws IOException, InterruptedException;

    /**
     * Closes a given channel, and send all funds to an on-chain address. Returns the ID of the closing transaction.
     * <p>
     * Attention: closing a channel is final, it cannot be cancelled.
     *
     * @param channelId      identifier of the channel to close
     * @param refundAddress  bitcoin address where your balance will be sent to
     * @param feerateSatByte fee rate in satoshi per vbyte
     */
    String closeChannel(String channelId, String refundAddress, long feerateSatByte) throws IOException, InterruptedException;

    /**
     * Estimates a liquidity fee for a given amount. Note that it depends on the current mining feerate, which is volatile. The estimate returned is the full cost and does not take into account any fee credit you may have.
     *
     * @param amountSat the liquidiy amount, in satoshi.
     */
    PhoenixDTOs.EstimatedLiquidityResponse estimateLiquidityFees(long amountSat) throws IOException, InterruptedException;

    // Internal API
    @InternalApi
    PhoenixDTOs.PayResponse payBolt11InvoiceRaw(String invoice, long amountSat, boolean sendAll) throws IOException, InterruptedException;

    @InternalApi
    PhoenixDTOs.PayResponse payBolt12OfferRaw(String offer, long amountSat, boolean sendAll, String messageForRecipient) throws IOException, InterruptedException;

    PhoenixDTOs.PayResponse payLightningAddressRaw(String address, long amountSat, boolean sendAll, String messageForRecipient) throws IOException, InterruptedException;
}
