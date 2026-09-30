package de.verdox.pv_miner.miningpool;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MiningCoinTest {
    @Test
    void resolvesCanonicalKeysAndSymbols() {
        assertEquals(MiningCoin.BITCOIN, MiningCoin.from("BTC"));
        assertEquals(MiningCoin.MONERO, MiningCoin.from("monero"));
        assertEquals(MiningCoin.PEARL, MiningCoin.from("PRL"));
        assertThrows(IllegalArgumentException.class, () -> MiningCoin.from("unknown"));
    }

    @Test
    void keepsAddressFormatsOnTheirOwnChains() {
        String pearlAddress = "prl1pnctccpanlhgxjgyg2m7np2r4ccjzd78hn4r8gc3skkdywk8nxt0qdzg4l0";
        assertTrue(MiningCoin.PEARL.validAddress(pearlAddress));
        assertFalse(MiningCoin.BITCOIN.validAddress(pearlAddress));
        assertFalse(MiningCoin.MONERO.validAddress(pearlAddress));
        assertFalse(MiningCoin.PEARL.validAddress("bc1qinvalid"));
    }
}
