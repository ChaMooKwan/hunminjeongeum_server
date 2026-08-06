package kr.ac.sunmoon.hunminjeongeum_server;

import java.io.PrintStream;
import java.lang.instrument.Instrumentation;
import java.nio.charset.StandardCharsets;

public final class Utf8ConsoleAgent {
    private Utf8ConsoleAgent() {
    }

    public static void premain(String agentArgs, Instrumentation instrumentation) {
        System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(System.err, true, StandardCharsets.UTF_8));
    }
}
