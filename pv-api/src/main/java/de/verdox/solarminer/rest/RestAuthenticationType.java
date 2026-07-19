package de.verdox.solarminer.rest;

import de.verdox.vserializer.generic.Serializer;

/** Authentication applied to every request of one REST device profile. */
public enum RestAuthenticationType {
    NONE,
    BEARER,
    BASIC;

    public static final Serializer<RestAuthenticationType> SERIALIZER =
            Serializer.Enum.create("rest_authentication_type", RestAuthenticationType.class);
}
