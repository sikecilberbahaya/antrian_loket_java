package com.panggilan.desktop;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.nio.charset.StandardCharsets;

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
    private JComboBox<TicketOption> activeTicketCombo;
    private DefaultComboBoxModel<TicketOption> activeTicketsModel;

    private CounterCallerApp(String presetServer, String presetCounter) {
        this.presetServer = presetServer == null || presetServer.isBlank() ? "http://localhost:8080" : presetServer;
        this.presetCounter = presetCounter == null ? "" : presetCounter;
    }

    private void initUi() {
        frame = new JFrame("Panggilan Loket Desktop");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(460, 320);
        frame.setLocationRelativeTo(null);

        JPanel formPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 8, 8, 8);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JLabel serverLabel = new JLabel("Server URL");
        serverField = new JTextField(presetServer, 22);
        JLabel counterLabel = new JLabel("ID Loket");
        counterField = new JTextField(presetCounter, 10);
        JLabel activeLabel = new JLabel("Nomor Aktif");
        activeTicketsModel = new DefaultComboBoxModel<>();
        activeTicketCombo = new JComboBox<>(activeTicketsModel);
        activeTicketCombo.setEnabled(false);

        JButton callNextButton = new JButton("Panggil Berikutnya");
        JButton recallButton = new JButton("Panggil Ulang");
        JButton completeButton = new JButton("Selesaikan");
    JButton stopButton = new JButton("Stop");
    stopButton.setBackground(new Color(0xC0392B));
    stopButton.setForeground(Color.WHITE);
    stopButton.setOpaque(true);
    stopButton.setBorderPainted(false);

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
        formPanel.add(activeLabel, gbc);
        gbc.gridx = 1;
        formPanel.add(activeTicketCombo, gbc);

        gbc.gridx = 0;
        gbc.gridy = 3;
        formPanel.add(callNextButton, gbc);
        gbc.gridx = 1;
        formPanel.add(recallButton, gbc);

        gbc.gridx = 0;
        gbc.gridy = 4;
        formPanel.add(completeButton, gbc);
        gbc.gridx = 1;
        formPanel.add(stopButton, gbc);

        frame.add(formPanel, BorderLayout.CENTER);
        frame.add(currentTicketLabel, BorderLayout.NORTH);
        frame.add(statusMessage, BorderLayout.SOUTH);

        callNextButton.addActionListener(this::callNextAction);
        recallButton.addActionListener(this::recallAction);
        completeButton.addActionListener(this::completeAction);
        stopButton.addActionListener(this::stopAction);

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
                refreshCurrentStatus();
            }
        });
    }

    private void recallAction(ActionEvent event) {
        withLoading(() -> {
            TicketOption selected = getSelectedTicket();
            if (selected == null) {
                setStatus("Pilih nomor aktif terlebih dahulu.", true);
                return;
            }
            JsonNode response = post(String.format("/api/counters/%s/recall?ticketId=%s",
                    counterField.getText().trim(), encode(selected.id())));
            if (response != null) {
                String number = response.path("number").asText("-");
                currentTicketLabel.setText("Nomor Saat Ini: " + number);
                setStatus("Panggilan ulang nomor " + number, false);
                refreshCurrentStatus();
            }
        });
    }

    private void completeAction(ActionEvent event) {
        withLoading(() -> {
            TicketOption selected = getSelectedTicket();
            if (selected == null) {
                setStatus("Tidak ada nomor aktif untuk diselesaikan.", true);
                return;
            }
            post(String.format("/api/counters/%s/complete?ticketId=%s",
                    counterField.getText().trim(), encode(selected.id())));
            setStatus("Selesai melayani nomor " + selected.label() + ".", false);
            refreshCurrentStatus();
        });
    }

    private void stopAction(ActionEvent event) {
    int choice = JOptionPane.showConfirmDialog(
        frame,
        "apakah anda yakin akan stop antrian?",
        "Konfirmasi Stop",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);
        if (choice != JOptionPane.YES_OPTION) {
            return;
        }
        withLoading(() -> {
            TicketOption selected = getSelectedTicket();
            if (selected == null) {
                setStatus("Tidak ada nomor aktif untuk dihentikan.", true);
                return;
            }
            JsonNode response = post(String.format("/api/counters/%s/stop?ticketId=%s",
                    counterField.getText().trim(), encode(selected.id())));
            String number = selected.label();
            if (response != null) {
                number = response.path("number").asText(number);
            }
            setStatus("Nomor " + number + " dihentikan dan tidak dilanjutkan.", false);
            refreshCurrentStatus();
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
                        updateCurrentTicketLabel(counter);
                        updateActiveSelector(counter);
                        break;
                    }
                }
            }
        } catch (Exception ex) {
            setStatus("Gagal memuat status: " + ex.getMessage(), true);
        }
    }

    private void updateCurrentTicketLabel(JsonNode counterNode) {
        if (counterNode == null || counterNode.isMissingNode()) {
            currentTicketLabel.setText("Nomor Saat Ini: -");
            return;
        }
        JsonNode active = counterNode.path("activeTickets");
        if (active.isArray() && active.size() > 0) {
            JsonNode first = active.get(0);
            String firstNumber = first.path("number").asText("-");
            String firstType = formatPatientType(first.path("patientType").asText());
            StringBuilder builder = new StringBuilder(firstNumber);
            if (!firstType.isEmpty()) {
                builder.append(" (").append(firstType).append(")");
            }
            if (active.size() > 1) {
                builder.append(" | ");
                for (int i = 1; i < active.size(); i++) {
                    if (i > 1) {
                        builder.append(", ");
                    }
                    JsonNode node = active.get(i);
                    builder.append(node.path("number").asText("-"));
                    String type = formatPatientType(node.path("patientType").asText());
                    if (!type.isEmpty()) {
                        builder.append(" (").append(type).append(")");
                    }
                }
            }
            currentTicketLabel.setText("Nomor Saat Ini: " + builder);
            return;
        }
        String number = counterNode.path("currentTicket").path("number").asText("-");
        currentTicketLabel.setText("Nomor Saat Ini: " + number);
    }

    private String formatPatientType(String patientType) {
        if (patientType == null || patientType.isBlank()) {
            return "";
        }
        if ("BARU".equalsIgnoreCase(patientType)) {
            return "Baru";
        }
        if ("LAMA".equalsIgnoreCase(patientType)) {
            return "Lama";
        }
        return patientType;
    }

    private void updateActiveSelector(JsonNode counterNode) {
        if (activeTicketsModel == null || activeTicketCombo == null) {
            return;
        }
        activeTicketsModel.removeAllElements();
        JsonNode active = counterNode.path("activeTickets");
        if (active.isArray()) {
            for (JsonNode node : active) {
                String id = node.path("id").asText();
                String number = node.path("number").asText("-");
                String patientType = formatPatientType(node.path("patientType").asText());
                String label = patientType.isEmpty() ? number : number + " (" + patientType + ")";
                if (id != null && !id.isBlank()) {
                    activeTicketsModel.addElement(new TicketOption(id, label));
                }
            }
        }
        boolean hasActive = activeTicketsModel.getSize() > 0;
        activeTicketCombo.setEnabled(hasActive);
        if (hasActive) {
            activeTicketCombo.setSelectedIndex(0);
        } else {
            activeTicketCombo.setSelectedItem(null);
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

    private TicketOption getSelectedTicket() {
        if (activeTicketCombo == null || activeTicketsModel == null || activeTicketsModel.getSize() == 0) {
            return null;
        }
        return (TicketOption) activeTicketCombo.getSelectedItem();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
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

    private static final class TicketOption {
        private final String id;
        private final String label;

        private TicketOption(String id, String label) {
            this.id = id;
            this.label = label;
        }

        private String id() {
            return id;
        }

        private String label() {
            return label;
        }

        @Override
        public String toString() {
            return label;
        }
    }
}
