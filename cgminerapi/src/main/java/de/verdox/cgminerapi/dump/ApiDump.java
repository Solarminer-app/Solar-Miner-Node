package de.verdox.cgminerapi.dump;

import de.verdox.cgminerapi.StandardCommand;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public class ApiDump {
    static void main() throws IOException {
        List<DumpSpec> dumps = List.of(

                DumpSpec.of(
                        StandardCommand.VERSION
                ),

                DumpSpec.of(
                        StandardCommand.CONFIG
                ),

                DumpSpec.of(
                        StandardCommand.SUMMARY
                ),

                DumpSpec.of(
                        StandardCommand.POOLS
                ),

                DumpSpec.of(
                        StandardCommand.DEVS
                ),

                DumpSpec.of(
                        StandardCommand.ASC_COUNT
                ),

                DumpSpec.of(
                        StandardCommand.ASC,
                        "0"
                ),

                DumpSpec.of(
                        StandardCommand.PGA_COUNT
                ),

                DumpSpec.of(
                        StandardCommand.CHECK,
                        "summary"
                ),

                DumpSpec.of(
                        StandardCommand.DEBUG,
                        "normal"
                ),

                DumpSpec.of(
                        StandardCommand.PRIVILEGED
                ),

                DumpSpec.of(
                        StandardCommand.ASC_IDENTIFY,
                        "0"
                )
        );

        new CGMinerApiDumper(
                "192.168.178.22",
                4028
        ).dump(
                Path.of(
                        "src/test/resources/cgminer"
                ),
                dumps
        );
    }
}
