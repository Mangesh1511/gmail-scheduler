package com.example.scheduler;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.ServerSocket;
import java.util.Arrays;

@SpringBootApplication
public class ScheduleServiceApplication {
    public static void main(String[] args) {
        if (!hasExplicitPort(args)) {
            int port = firstAvailablePort();
            System.setProperty("server.port", Integer.toString(port));
            System.out.println("Starting schedule service on port " + port);
        }
        SpringApplication.run(ScheduleServiceApplication.class, args);
    }

    private static boolean hasExplicitPort(String[] args) {
        String environmentPort = System.getenv("SERVER_PORT");
        String systemPort = System.getProperty("server.port");
        return (environmentPort != null && !environmentPort.isBlank())
                || (systemPort != null && !systemPort.isBlank())
                || Arrays.stream(args).anyMatch(argument -> argument.startsWith("--server.port=")
                || argument.equals("--server.port"));
    }

    private static int firstAvailablePort() {
        for (int port = 8081; port <= 8082; port++) {
            try (ServerSocket socket = new ServerSocket()) {
                socket.setReuseAddress(false);
                socket.bind(new InetSocketAddress(port));
                return port;
            } catch (IOException ignored) {
                // Try the next local development port.
            }
        }
        throw new IllegalStateException("No available application port between 8080 and 8082; "
                + "the configured local QStash port is reserved");
    }
}
