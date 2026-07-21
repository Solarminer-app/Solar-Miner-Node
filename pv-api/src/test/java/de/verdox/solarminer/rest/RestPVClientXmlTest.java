package de.verdox.solarminer.rest;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RestPVClientXmlTest {
    @Test
    void readsDefaultNamespaceWithoutNamespaceConfiguration() throws Exception {
        String xml = "<?xml version=\"1.0\"?><values xmlns=\"urn:meter\"><power>2.4 kW</power></values>";
        assertEquals("2.4 kW", RestPVClient.extractFromXml(xml, "/values/power", "http://meter/values.xml"));
    }

    @Test
    void readsPrefixedNamespaceByLocalName() throws Exception {
        String xml = "noise before XML <m:values xmlns:m=\"urn:meter\"><m:power unit=\"W\">50 W</m:power></m:values> trailing noise";
        assertEquals("50 W", RestPVClient.extractFromXml(xml, "/m:values/m:power", "http://meter/values.xml"));
    }

    @Test
    void reportsMissingXmlPathClearly() {
        String xml = "<values><power>50 W</power></values>";
        assertThrows(IOException.class, () -> RestPVClient.extractFromXml(xml, "/values/voltage", "http://meter/values.xml"));
    }
}
