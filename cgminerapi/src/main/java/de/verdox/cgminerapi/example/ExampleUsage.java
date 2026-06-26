package de.verdox.cgminerapi.example;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.verdox.cgminerapi.CGMinerClient;
import de.verdox.cgminerapi.StandardCommand;

import java.io.IOException;

public class ExampleUsage {
    static void main() throws IOException {
        var client = new CGMinerClient(new ObjectMapper(), "192.168.178.159");
        var response = client.execute(StandardCommand.SUMMARY);
        System.out.println(response);
    }
}
