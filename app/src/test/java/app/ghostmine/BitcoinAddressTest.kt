package app.ghostmine

import app.ghostmine.btc.BitcoinAddress
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BitcoinAddressTest {
    @Test fun validAddresses() {
        assertTrue(BitcoinAddress.isValid("1BvBMSEYstWetqTFn5Au4m4GFg7xJaNVN2"))
        assertTrue(BitcoinAddress.isValid("3J98t1WpEZ73CNmQviecrnyiWrnqRhWNLy"))
        assertTrue(BitcoinAddress.isValid("bc1qar0srrr7xfkvy5l643lydnw9re59gtzzwf5mdq"))
        assertTrue(BitcoinAddress.isValid("bc1p0xlxvlhemja6c4dqv22uapctqupfhlxm9h8z3k2e72q4k9hcz7vqzk5jj0"))
    }

    @Test fun invalidAddresses() {
        assertFalse(BitcoinAddress.isValid("bc1qar0srrr7xfkvy5l643lydnw9re59gtzzwf5mdp"))
        assertFalse(BitcoinAddress.isValid("1BvBMSEYstWetqTFn5Au4m4GFg7xJaNVN3"))
        assertFalse(BitcoinAddress.isValid("tb1qw508d6qejxtdg4y5r3zarvary0c5xw7kxpjzsx"))
        assertFalse(BitcoinAddress.isValid("abandon ability able about above absent"))
        assertFalse(BitcoinAddress.isValid(""))
    }
}
