package de.verdox.cgminerapi;

import de.verdox.cgminerapi.dto.CGMinerDTO;

public interface CGMinerRequest<R extends CGMinerDTO> {

    CGMinerCommand<R> command();

    String parameter();
}
