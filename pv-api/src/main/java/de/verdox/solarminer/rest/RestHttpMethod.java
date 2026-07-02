package de.verdox.solarminer.rest;

import de.verdox.vserializer.generic.Serializer;

public enum RestHttpMethod {
    GET,
    POST;

    public static final Serializer<RestHttpMethod> SERIALIZER = Serializer.Enum.create("rest_http_method", RestHttpMethod.class);
}
