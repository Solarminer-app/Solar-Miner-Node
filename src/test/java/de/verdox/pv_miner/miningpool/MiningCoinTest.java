package de.verdox.pv_miner.miningpool;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MiningCoinTest {
    @Test
    void resolvesCanonicalKeysAndSymbols() {
        assertEquals(MiningCoin.BITCOIN, MiningCoin.from("BTC"));
        assertEquals(MiningCoin.MONERO, MiningCoin.from("monero"));
        assertEquals(MiningCoin.PEARL, MiningCoin.from("PRL"));
        assertEquals(MiningCoin.RAVENCOIN, MiningCoin.from("RVN"));
        assertEquals(MiningCoin.ETHEREUM_CLASSIC, MiningCoin.from("ethereumclassic"));
        assertEquals(MiningCoin.DECRED, MiningCoin.from("DCR"));
        assertEquals(MiningCoin.QUANTUS, MiningCoin.from("qtc"));
        assertThrows(IllegalArgumentException.class, () -> MiningCoin.from("unknown"));
    }

    @Test
    void mirrorsEveryPcAgentWalletAndKryptexBalanceRoute() {
        assertEquals("xmr", MiningCoin.MONERO.kryptexTicker());
        assertEquals("prl", MiningCoin.PEARL.kryptexTicker());
        assertEquals("rvn", MiningCoin.RAVENCOIN.kryptexTicker());
        assertEquals("etc", MiningCoin.ETHEREUM_CLASSIC.kryptexTicker());
        assertEquals("qtc", MiningCoin.QUANTUS.kryptexTicker());
        assertNull(MiningCoin.BITCOIN.kryptexTicker());
        assertNull(MiningCoin.DECRED.kryptexTicker());
    }

    @Test
    void keepsAddressFormatsOnTheirOwnChains() {
        String pearlAddress = "prl1pnctccpanlhgxjgyg2m7np2r4ccjzd78hn4r8gc3skkdywk8nxt0qdzg4l0";
        assertTrue(MiningCoin.PEARL.validAddress(pearlAddress));
        assertFalse(MiningCoin.BITCOIN.validAddress(pearlAddress));
        assertFalse(MiningCoin.MONERO.validAddress(pearlAddress));
        assertFalse(MiningCoin.PEARL.validAddress("bc1qinvalid"));
        assertTrue(MiningCoin.RAVENCOIN.validAddress("RHaGK3iARQdKgZ6VPDP4N5chP3aVgUUfz7"));
        assertTrue(MiningCoin.ETHEREUM_CLASSIC.validAddress("0x21211c699D409Ca3802D955caD80Ccc034004993"));
    }
}
