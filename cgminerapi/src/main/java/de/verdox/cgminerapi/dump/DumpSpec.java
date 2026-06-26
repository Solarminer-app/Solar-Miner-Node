package de.verdox.cgminerapi.dump;

import de.verdox.cgminerapi.StandardCommand;

public record DumpSpec(
        StandardCommand command,
        String parameter
) {

    public static DumpSpec of(StandardCommand command) {
        return new DumpSpec(command, null);
    }

    public static DumpSpec of(
            StandardCommand command,
            String parameter
    ) {
        return new DumpSpec(command, parameter);
    }
}
