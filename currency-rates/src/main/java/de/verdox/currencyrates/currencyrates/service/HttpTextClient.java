package de.verdox.currencyrates.currencyrates.service;

import java.io.IOException;
import java.net.URI;

/**
 * Small boundary around external HTTP GET requests. Providers receive the same
 * timeout and HTTP-status handling without being coupled to a large provider hierarchy.
 */
public interface HttpTextClient {

    String get(URI uri) throws IOException, InterruptedException;
}
