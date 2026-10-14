package serveur.reseau;

import java.io.IOException;
import java.net.BindException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Scanner;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Serveur de clavardage : validation IP/port, boucle d'acceptation,
 * un thread par client, diffusion et arrêt propre.
 */
public class ChatServer {

    private static final Pattern IPV4 = Pattern.compile(
            "^((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)\\.){3}(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)$");

    // Liste thread-safe des utilisateurs connectés : nom -> gestionnaire
    private final ConcurrentHashMap<String, ConnectionEntrante> clients = new ConcurrentHashMap<>();
    private final ExecutorService pool = Executors.newCachedThreadPool();
    private volatile boolean running = true;
    private ServerSocket serverSocket;

    // ---------- Saisie et validation ----------

    static boolean ipValide(String ip) {
        return ip.equalsIgnoreCase("localhost") || IPV4.matcher(ip).matches();
    }

    static boolean portValide(String texte) {
        try {
            int p = Integer.parseInt(texte);
            return p >= 1024 && p <= 65535;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static String lireIp(Scanner sc) {
        while (true) {
            System.out.print("Adresse IP du serveur (ex. 127.0.0.1) : ");
            String ip = sc.nextLine().trim();
            if (ipValide(ip)) return ip;
            System.out.println("Adresse IP invalide. Veuillez réessayer.");
        }
    }

    private static int lirePort(Scanner sc) {
        while (true) {
            System.out.print("Port (1024-65535) : ");
            String p = sc.nextLine().trim();
            if (portValide(p)) return Integer.parseInt(p);
            System.out.println("Port invalide. Veuillez réessayer.");
        }
    }

    // ---------- Démarrage ----------

    /** Boucle de nouvelle saisie tant que l'adresse/port ne peuvent pas être liés. */
    private void demarrer(Scanner sc) {
        while (serverSocket == null) {
            String ip = lireIp(sc);
            int port = lirePort(sc);
            try {
                serverSocket = new ServerSocket(port, 50, InetAddress.getByName(ip));
                System.out.println("Serveur démarré sur " + ip + ":" + port
                        + " (tapez 'quit' pour arrêter).");
            } catch (BindException e) {
                System.out.println("Impossible de lier " + ip + ":" + port + " (" + e.getMessage() + ").");
            } catch (IOException e) {
                System.out.println("Erreur au démarrage : " + e.getMessage());
            }
        }
    }

    // ---------- Boucle d'acceptation ----------

    private void accepter() {
        while (running) {
            try {
                Socket socket = serverSocket.accept();
                pool.execute(new ConnectionEntrante(socket, this)); // un thread par client
            } catch (SocketException e) {
                if (running) System.out.println("Erreur socket : " + e.getMessage());
                // sinon : fermeture volontaire du ServerSocket
            } catch (IOException e) {
                if (running) System.out.println("Erreur d'acceptation : " + e.getMessage());
            }
        }
    }

    // ---------- Gestion des utilisateurs / diffusion ----------

    /** Enregistre un utilisateur ; false si le nom est déjà pris. */
    boolean enregistrer(String nom, ConnectionEntrante handler) {
        return clients.putIfAbsent(nom, handler) == null;
    }

    void retirer(String nom) {
        if (nom != null && clients.remove(nom) != null) {
            diffuser("*** " + nom + " a quitté le clavardage ***");
        }
    }

    /** Envoie le message à tous les clients connectés. */
    void diffuser(String message) {
        System.out.println(message);
        for (ConnectionEntrante c : clients.values()) {
            c.envoyer(message);
        }
    }

    String listeUtilisateurs() {
        return String.join(", ", clients.keySet());
    }

    // ---------- Arrêt propre ----------

    synchronized void arreter() {
        if (!running) return;
        running = false;
        System.out.println("Arrêt du serveur...");
        try {
            if (serverSocket != null) serverSocket.close();
        } catch (IOException ignored) { }

        for (ConnectionEntrante c : clients.values()) {
            c.envoyer("*** Le serveur s'arrête. Au revoir ! ***");
            c.fermer();
        }
        clients.clear();

        pool.shutdown();
        try {
            if (!pool.awaitTermination(3, TimeUnit.SECONDS)) pool.shutdownNow();
        } catch (InterruptedException e) {
            pool.shutdownNow();
            Thread.currentThread().interrupt();
        }
        System.out.println("Serveur arrêté.");
    }

    public static void main(String[] args) {
        ChatServer serveur = new ChatServer();
        Scanner sc = new Scanner(System.in);
        serveur.demarrer(sc);

        // Arrêt propre sur Ctrl+C
        Runtime.getRuntime().addShutdownHook(new Thread(serveur::arreter));

        // Commande console "quit" pour arrêter
        Thread console = new Thread(() -> {
            while (sc.hasNextLine()) {
                if (sc.nextLine().trim().equalsIgnoreCase("quit")) {
                    serveur.arreter();
                    break;
                }
            }
        }, "console");
        console.setDaemon(true);
        console.start();

        serveur.accepter();
    }
}
