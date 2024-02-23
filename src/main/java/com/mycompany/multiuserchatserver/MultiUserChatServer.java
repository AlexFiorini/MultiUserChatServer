package com.mycompany.multiuserchatserver;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.stream.Collectors;

import org.json.JSONObject;

public class MultiUserChatServer {
    private static final int PORT = 12345;
    private static final String SERVER_IP = "192.168.178.196";
    private static Map<String, PrintWriter> clientsMap = new HashMap<>();

    public static void main(String[] args) {
        // Carica il file JSON contenente la mappatura email-indirizzoIP
        Map<String, String> emailToIpMap = loadEmailToIpMapping("./mapping.json");

        System.out.println("Server avviato sulla porta " + PORT);
        try (ServerSocket listener = new ServerSocket(PORT, 0, InetAddress.getByName(SERVER_IP))) {
            while (true) {
                new Handler(listener.accept(), emailToIpMap).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static Map<String, String> loadEmailToIpMapping(String filePath) {
        try {
            BufferedReader reader = new BufferedReader(new FileReader(filePath));
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
        private Socket socket;
        private PrintWriter out;
        private String username;
        private Map<String, String> emailToIpMap;

        public Handler(Socket socket, Map<String, String> emailToIpMap) {
            this.socket = socket;
            this.emailToIpMap = emailToIpMap;
        }

        public void run() {
            try {
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);

                username = in.readLine();
                System.out.println(username + " si è connesso.");
                broadcast(username + " si è connesso.");
                String ip_sender = socket.getInetAddress().getHostAddress();
                clientsMap.put(ip_sender, out);

                String message;
                while ((message = in.readLine()) != null) {

                    // Estrae l'indirizzo IP associato all'utente corrente
                    String[] parts = message.split(" - ", 2);
                    if (parts.length == 2) {
                        String userEmail = parts[0];
                        String restOfMessage = parts[1];

                        // Ottenere l'indirizzo IP associato all'utente corrente
                        String userIp = emailToIpMap.get(userEmail);

                        for (Map.Entry<String, String> entry : emailToIpMap.entrySet()) {
                            if (entry.getValue().equals(userIp)) {
                                restOfMessage = entry.getKey() + " - " + restOfMessage;
                            }
                        }

                        // Invia il messaggio solo all'utente corrente
                        sendToSpecificUser(restOfMessage, userIp);
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
                PrintWriter specificUserOut = clientsMap.get(userIp);
                if (specificUserOut != null) {
                    specificUserOut.println(message);
                }
            }
        }
    }
}
