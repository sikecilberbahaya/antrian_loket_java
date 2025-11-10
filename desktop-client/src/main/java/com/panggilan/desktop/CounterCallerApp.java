package com.panggilan.desktop;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public final class CounterCallerApp {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(REQUEST_TIMEOUT)
            .build();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String presetServer;
    private final String presetCounter;

    private JFrame frame;
    private JTextField serverField;
    private JTextField counterField;
    private JLabel currentTicketLabel;
    private JLabel statusMessage;

    private CounterCallerApp(String presetServer, String presetCounter) {
        this.presetServer = presetServer == null || presetServer.isBlank() ? "http://localhost:8080" : presetServer;
        this.presetCounter = presetCounter == null ? "" : presetCounter;
    }

    private void initUi() {
        frame = new JFrame("Panggilan Loket Desktop");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(420, 260);
        frame.setLocationRelativeTo(null);

        JPanel formPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 8, 8, 8);
        gbc.anchor = GridBagConstraints.WEST;

        JLabel serverLabel = new JLabel("Server URL");
    serverField = new JTextField(presetServer, 22);
        JLabel counterLabel = new JLabel("ID Loket");
    counterField = new JTextField(presetCounter, 10);

        JButton callNextButton = new JButton("Panggil Berikutnya");
        JButton recallButton = new JButton("Panggil Ulang");
        JButton completeButton = new JButton("Selesaikan");

    currentTicketLabel = new JLabel("Nomor Saat Ini: -");
    currentTicketLabel.setHorizontalAlignment(JLabel.CENTER);
    currentTicketLabel.setFont(currentTicketLabel.getFont().deriveFont(Font.BOLD, 24f));
        statusMessage = new JLabel(" ");

        gbc.gridx = 0;
        gbc.gridy = 0;
        formPanel.add(serverLabel, gbc);
        gbc.gridx = 1;
        formPanel.add(serverField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        formPanel.add(counterLabel, gbc);
        gbc.gridx = 1;
        formPanel.add(counterField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 2;
        formPanel.add(callNextButton, gbc);
        gbc.gridx = 1;
        formPanel.add(recallButton, gbc);

        gbc.gridx = 0;
        gbc.gridy = 3;
        formPanel.add(completeButton, gbc);

        frame.add(formPanel, BorderLayout.CENTER);
        frame.add(currentTicketLabel, BorderLayout.NORTH);
        frame.add(statusMessage, BorderLayout.SOUTH);

        callNextButton.addActionListener(this::callNextAction);
        recallButton.addActionListener(this::recallAction);
        completeButton.addActionListener(this::completeAction);

        Timer refreshTimer = new Timer(4000, e -> SwingUtilities.invokeLater(this::refreshCurrentStatus));
        refreshTimer.setInitialDelay(0);
        refreshTimer.start();
    }

    private void callNextAction(ActionEvent event) {
        withLoading(() -> {
            JsonNode response = post(String.format("/api/counters/%s/call-next", counterField.getText().trim()));
            if (response != null) {
                String number = response.path("number").asText("-");
                currentTicketLabel.setText("Nomor Saat Ini: " + number);
                setStatus("Memanggil nomor " + number, false);
            }
        });
    }

    private void recallAction(ActionEvent event) {
        withLoading(() -> {
            JsonNode response = post(String.format("/api/counters/%s/recall", counterField.getText().trim()));
            if (response != null) {
                String number = response.path("number").asText("-");
                currentTicketLabel.setText("Nomor Saat Ini: " + number);
                setStatus("Panggilan ulang nomor " + number, false);
            }
        });
    }

    private void completeAction(ActionEvent event) {
        withLoading(() -> {
            JsonNode response = post(String.format("/api/counters/%s/complete", counterField.getText().trim()));
            if (response != null) {
                setStatus(response.path("message").asText("Loket siap untuk nomor berikutnya."), false);
                refreshCurrentStatus();
            } else {
                setStatus("Loket siap untuk nomor berikutnya.", false);
                refreshCurrentStatus();
            }
        });
    }

    private void refreshCurrentStatus() {
        String counterId = counterField.getText().trim();
        if (counterId.isEmpty()) {
            return;
        }
        try {
            JsonNode counters = get("/api/counters");
            if (counters != null && counters.isArray()) {
                for (JsonNode counter : counters) {
                    if (counterId.equalsIgnoreCase(counter.path("id").asText())) {
                        String number = counter.path("currentTicket").path("number").asText("-");
                        currentTicketLabel.setText("Nomor Saat Ini: " + number);
                        break;
                    }
                }
            }
        } catch (Exception ex) {
            setStatus("Gagal memuat status: " + ex.getMessage(), true);
        }
    }

    private void withLoading(Runnable action) {
        String counterId = counterField.getText().trim();
        if (counterId.isEmpty()) {
            JOptionPane.showMessageDialog(frame, "Masukkan ID loket terlebih dahulu.", "Validasi", JOptionPane.WARNING_MESSAGE);
            return;
        }
        try {
            action.run();
        } catch (Exception ex) {
            setStatus(ex.getMessage(), true);
        }
    }

    private JsonNode post(String path) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(buildUrl(path)))
                    .timeout(REQUEST_TIMEOUT)
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 204) {
                setStatus("Tidak ada data untuk operasi ini.", true);
                return null;
            }
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                if (response.body() == null || response.body().isBlank()) {
                    return null;
                }
                return objectMapper.readTree(response.body());
            }
            JsonNode error = response.body() == null ? null : objectMapper.readTree(response.body());
            String message = error != null && error.has("error") ? error.get("error").asText() : "Gagal memanggil API (" + response.statusCode() + ")";
            throw new IOException(message);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Operasi dibatalkan.", ex);
        } catch (IOException ex) {
            throw new RuntimeException("Gagal terhubung ke server: " + ex.getMessage(), ex);
        }
    }

    private JsonNode get(String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(buildUrl(path)))
                .timeout(REQUEST_TIMEOUT)
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 200 && response.statusCode() < 300 && response.body() != null && !response.body().isBlank()) {
            return objectMapper.readTree(response.body());
        }
        return null;
    }

    private String buildUrl(String path) {
        String baseUrl = serverField.getText().trim();
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        return baseUrl + path;
    }

    private void setStatus(String message, boolean isError) {
        statusMessage.setText(message == null ? " " : message);
        statusMessage.setForeground(isError ? java.awt.Color.RED : java.awt.Color.DARK_GRAY);
    }

    public static void main(String[] args) {
        String argServer = extractOption(args, "--server=");
        String argCounter = extractOption(args, "--counter=");
        String propertyServer = System.getProperty("panggilan.server");
        String propertyCounter = System.getProperty("panggilan.counter");
        String server = firstNonBlank(argServer, propertyServer, "http://localhost:8080");
        String counter = firstNonBlank(argCounter, propertyCounter, "");

        SwingUtilities.invokeLater(() -> {
            CounterCallerApp app = new CounterCallerApp(server, counter);
            app.initUi();
            app.frame.setVisible(true);
        });
    }

    private static String extractOption(String[] args, String prefix) {
        if (args == null) {
            return null;
        }
        for (String arg : args) {
            if (arg != null && arg.startsWith(prefix)) {
                return arg.substring(prefix.length());
            }
        }
        return null;
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }
}
