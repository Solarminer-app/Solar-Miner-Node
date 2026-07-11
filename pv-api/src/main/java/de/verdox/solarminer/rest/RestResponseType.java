package de.verdox.solarminer.rest;

import de.verdox.vserializer.generic.Serializer;

public enum RestResponseType {
    JSON,
    XML,
    PLAIN_TEXT;

    public static final Serializer<RestResponseType> SERIALIZER = Serializer.Enum.create("rest_response_type", RestResponseType.class);
}
