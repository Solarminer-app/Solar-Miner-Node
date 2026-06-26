package de.verdox.cgminerapi;

import de.verdox.cgminerapi.dto.CGMinerDTO;
public interface CGMinerCommand<R extends CGMinerDTO> {

    String command();

    APIVersion minimumVersion();

    boolean privileged();

    ResponseSection responseSection();
}
