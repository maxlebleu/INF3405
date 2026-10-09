package client;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import java.net.InetSocketAddress;
import java.net.Socket;

import java.util.Scanner;

public class ChatClient {

    private final String host;
    private final int port;
    private final String username;
    private final String password;

    private Socket socket;
    private DataInputStream in;
    private DataOutputStream out;

    private volatile boolean running = true;

    public ChatClient(
            String host,
            int port,
            String username,
            String password
    ) {
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
    }

    public void start(Scanner scanner) {

        try {

            connect();

            if (!authenticate()) {
                return;
            }

            startReceiverThread();

            sendMessages(scanner);

        } catch (IOException e) {

            System.out.println(
                    "Erreur de communication : " + e.getMessage()
            );

        } finally {

            close();
        }
    }

    private void connect() throws IOException {

        socket = new Socket();

        socket.connect(
                new InetSocketAddress(host, port),
                5000
        );

        in = new DataInputStream(
                new BufferedInputStream(
                        socket.getInputStream()
                )
        );

        out = new DataOutputStream(
                new BufferedOutputStream(
                        socket.getOutputStream()
                )
        );

        System.out.println(
                "Connexion établie avec " + host + ":" + port
        );
    }

    private boolean authenticate() throws IOException {

        out.writeUTF(Protocol.AUTH);
        out.writeUTF(username);
        out.writeUTF(password);
        out.flush();

        String response = in.readUTF();
        String detail = in.readUTF();

        if (Protocol.AUTH_FAIL.equals(response)) {

            System.out.println(
                    "Authentification refusée : " + detail
            );

            return false;
        }

        if (!Protocol.AUTH_OK.equals(response)) {

            System.out.println(
                    "Réponse inattendue du serveur."
            );

            return false;
        }

        System.out.println(detail);

        int historyCount = in.readInt();

        if (historyCount > 0) {

            System.out.println();
            System.out.println(
                    "----- Historique récent -----"
            );

            for (int i = 0; i < historyCount; i++) {
                System.out.println(in.readUTF());
            }

            System.out.println(
                    "-----------------------------"
            );
        }

        return true;
    }

    private void startReceiverThread() {

        Thread receiver = new Thread(() -> {

            try {

                while (running) {

                    String type = in.readUTF();

                    if (Protocol.CHAT.equals(type)) {

                        String message = in.readUTF();
                        System.out.println(message);

                    } else if (Protocol.ERROR.equals(type)) {

                        String error = in.readUTF();

                        System.out.println(
                                "Erreur serveur : " + error
                        );

                    } else {

                        System.out.println(
                                "Message inconnu reçu : " + type
                        );
                    }
                }

            } catch (IOException e) {

                if (running) {
                    System.out.println(
                            "Connexion avec le serveur interrompue."
                    );
                }

            } finally {

                running = false;
            }
        });

        receiver.setDaemon(true);
        receiver.start();
    }

    private void sendMessages(Scanner scanner) {

        System.out.println();
        System.out.println(
                "Vous pouvez maintenant envoyer des messages."
        );

        System.out.println(
                "Tapez /quit pour quitter."
        );

        while (running) {

            String message = scanner.nextLine();

            if (message.equalsIgnoreCase("/quit")) {

                sendQuit();
                break;
            }

            if (message.isBlank()) {

                System.out.println(
                        "Le message ne peut pas être vide."
                );

                continue;
            }

            int length = message.codePointCount(
                    0,
                    message.length()
            );

            if (length > 200) {

                System.out.println(
                        "Le message dépasse la limite de 200 caractères."
                );

                continue;
            }

            try {

                out.writeUTF(Protocol.MESSAGE);
                out.writeUTF(message);
                out.flush();

                System.out.println(
                        "[Moi] : " + message
                );

            } catch (IOException e) {

                System.out.println(
                        "Impossible d'envoyer le message."
                );

                running = false;
            }
        }
    }

    private void sendQuit() {

        try {

            if (out != null) {

                out.writeUTF(Protocol.QUIT);
                out.flush();
            }

        } catch (IOException e) {

            // La connexion peut déjà être fermée.

        } finally {

            running = false;
        }
    }

    private void close() {

        running = false;

        if (socket != null) {

            try {

                socket.close();

            } catch (IOException e) {

                // Rien à faire ici.
            }
        }

        System.out.println(
                "Client déconnecté."
        );
    }
}
