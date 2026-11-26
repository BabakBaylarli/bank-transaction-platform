package com.bankplatform.e2e;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Runs one service's executable jar as a child process, exactly as it would run in production. */
final class ServiceProcess implements AutoCloseable {

    private final String name;
    private final int port;
    private final Path logFile;
    private final Process process;

    private ServiceProcess(String name, int port, Path logFile, Process process) {
        this.name = name;
        this.port = port;
        this.logFile = logFile;
        this.process = process;
    }

    static ServiceProcess start(String module, Map<String, String> env) throws IOException {
        String root = System.getProperty("e2e.root", "..");
        String version = System.getProperty("e2e.version", "1.0.0-SNAPSHOT");
        Path jar = Path.of(root, module, "target", module + "-" + version + "-exec.jar").toAbsolutePath().normalize();
        if (!Files.exists(jar)) {
            throw new IllegalStateException("Executable jar not found: " + jar + " - run 'mvnw verify' from the root");
        }
        Path logs = Path.of(System.getProperty("e2e.logs", "target/e2e-logs"));
        Files.createDirectories(logs);
        Path logFile = logs.resolve(module + ".log");

        int port = freePort();
        String java = ProcessHandle.current().info().command().orElse("java");
        List<String> command = new ArrayList<>(List.of(java, "-Xmx384m", "-jar", jar.toString(),
                "--server.port=" + port));
        ProcessBuilder builder = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .redirectOutput(logFile.toFile());
        builder.environment().putAll(env);
        return new ServiceProcess(module, port, logFile, builder.start());
    }

    void awaitHealthy(Duration timeout) throws InterruptedException {
        HttpClient client = HttpClient.newHttpClient();
        Instant deadline = Instant.now().plus(timeout);
        while (Instant.now().isBefore(deadline)) {
            if (!process.isAlive()) {
                throw new IllegalStateException(name + " exited with code " + process.exitValue() + ", see " + logFile);
            }
            try {
                HttpResponse<String> response = client.send(
                        HttpRequest.newBuilder(URI.create(baseUrl() + "/actuator/health/readiness")).build(),
                        HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    return;
                }
            } catch (IOException notUpYet) {
                // keep polling
            }
            Thread.sleep(500);
        }
        throw new IllegalStateException(name + " did not become healthy within " + timeout + ", see " + logFile);
    }

    String baseUrl() {
        return "http://localhost:" + port;
    }

    String logs() throws IOException {
        return Files.readString(logFile);
    }

    @Override
    public void close() {
        process.destroy();
        try {
            if (!process.waitFor(java.util.concurrent.TimeUnit.SECONDS.toMillis(15),
                    java.util.concurrent.TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
        }
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
