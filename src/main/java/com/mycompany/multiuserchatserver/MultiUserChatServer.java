package com.mycompany.multiuserchatserver;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.stream.Collectors;

import org.json.JSONObject;

public class MultiUserChatServer {
    private static final int PORT = 12345;
    private static final String SERVER_IP = "192.168.7.158";
    private static final Map<String, PrintWriter> clientsMap = new HashMap<>();

    public static void main(String[] args) {
        // Carica il file JSON contenente la mappatura email-indirizzoIP
        Map<String, String> emailToIpMap = loadEmailToIpMapping();

        System.out.println("Server avviato sulla porta " + PORT);
        try (ServerSocket listener = new ServerSocket(PORT, 0, InetAddress.getByName(SERVER_IP))) {
            while (true) {
                new Handler(listener.accept(), emailToIpMap).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static Map<String, String> loadEmailToIpMapping() {
        try {
            BufferedReader reader = new BufferedReader(new FileReader("./mapping.json"));
            StringBuilder jsonContent = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                jsonContent.append(line);
            }
            reader.close();

            JSONObject jsonObject = new JSONObject(jsonContent.toString());
            // Converte il JSONObject in una mappa Java
            return jsonObject.toMap().entrySet().stream()
                    .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().toString()));
        } catch (IOException e) {
            e.printStackTrace();
            return Collections.emptyMap();
        }
    }

    private static class Handler extends Thread {
        private final Socket socket;
        private String username;
        private final Map<String, String> emailToIpMap;

        public Handler(Socket socket, Map<String, String> emailToIpMap) {
            this.socket = socket;
            this.emailToIpMap = emailToIpMap;
        }

        public void run() {
            try {
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true);

                username = in.readLine();
                System.out.println(username + " si è connesso.");
                broadcast(username + " si è connesso.");
                String ip_sender = socket.getInetAddress().getHostAddress();
                clientsMap.put(ip_sender, out);

                String message;
                while ((message = in.readLine()) != null) {

                    // Estrae l'indirizzo IP associato all'utente corrente
                    String[] parts = message.split(" - ", 3);
                    if (parts.length == 3) {
                        String receiverEmail = parts[0];
                        String senderEmail = parts[1];
                        StringBuilder restOfMessage = new StringBuilder(parts[2]);

                        // Ottenere l'indirizzo IP associato all'utente corrente
                        String userIp = emailToIpMap.get(receiverEmail);

                        for (Map.Entry<String, String> entry : emailToIpMap.entrySet()) {
                            if (entry.getValue().equals(userIp)) {
                                restOfMessage.insert(0, entry.getKey() + " - " + senderEmail + " - ");
                            }
                        }

                        // Invia il messaggio solo all'utente corrente
                        sendToSpecificUser(restOfMessage.toString(), userIp);
                    }
                }
            } catch (IOException e) {
                System.out.println(username + " si è disconnesso.");
                broadcast(username + " si è disconnesso.");
                clientsMap.remove(username);
                try {
                    socket.close();
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
        }

        private void broadcast(String message) {
            for (PrintWriter client : clientsMap.values()) {
                client.println(message);
            }
        }

        private void sendToSpecificUser(String message, String userIp) {
            if (userIp != null) {
                System.out.println(message + " - " + userIp);
                PrintWriter specificUserOut = clientsMap.get(userIp);
                if (specificUserOut != null) {
                    specificUserOut.println(message);
                }
            }
        }
    }
}
