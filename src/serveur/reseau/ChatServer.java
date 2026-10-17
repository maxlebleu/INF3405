package serveur.reseau;

import serveur.authentificationPerformance.EtatDeConnexion;
import serveur.authentificationPerformance.GestionDesUtilisateurs;
import serveur.authentificationPerformance.HistoriqueDeMessages;

import java.io.IOException;
import java.net.BindException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.List;
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

    private static final int PORT_MIN = 5000;
    private static final int PORT_MAX = 5050;

    private static final Pattern IPV4 = Pattern.compile(
            "^((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)\\.){3}(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)$");

    // Utilisateurs connectés (thread-safe) : nom -> gestionnaire
    private final ConcurrentHashMap<String, ConnectionEntrante> clients = new ConcurrentHashMap<>();
    private final ExecutorService pool = Executors.newCachedThreadPool();
    private final GestionDesUtilisateurs gestion;
    private final HistoriqueDeMessages historique;
    private volatile boolean running = true;
    private ServerSocket serverSocket;

    public ChatServer(GestionDesUtilisateurs gestion, HistoriqueDeMessages historique) {
        this.gestion = gestion;
        this.historique = historique;
    }

    // ---------- Saisie et validation ----------

    static boolean ipValide(String ip) {
        return IPV4.matcher(ip).matches();
    }

    static boolean portValide(String texte) {
        try {
            int p = Integer.parseInt(texte);
            return p >= PORT_MIN && p <= PORT_MAX;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static String lireIp(Scanner sc) {
        while (true) {
            System.out.print("Adresse IP du serveur (ex. 127.0.0.1) : ");
            String ip = sc.nextLine().trim();
            if (ipValide(ip)) return ip;
            System.out.println("Adresse IP invalide (format attendu : quatre octets, ex. 192.168.1.10). "
                    + "Veuillez réessayer.");
        }
    }

    private static int lirePort(Scanner sc) {
        while (true) {
            System.out.print("Port d'écoute (" + PORT_MIN + "-" + PORT_MAX + ") : ");
            String p = sc.nextLine().trim();
            if (portValide(p)) return Integer.parseInt(p);
            System.out.println("Port invalide : il doit être compris entre " + PORT_MIN
                    + " et " + PORT_MAX + ". Veuillez réessayer.");
        }
    }

    // ---------- Démarrage ----------

    /** Boucle de nouvelle saisie tant que l'adresse/port ne peuvent pas être liés. */
    void demarrer(Scanner sc) {
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

    void accepter() {
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

    // ---------- Authentification (déléguée à GestionDesUtilisateurs) ----------

    EtatDeConnexion authentifier(String utilisateur, String motDePasse) {
        return gestion.authentification(utilisateur, motDePasse);
    }

    void deconnecter(String utilisateur) {
        gestion.deconnecter(utilisateur);
    }

    // ---------- Utilisateurs, historique, diffusion ----------

    /** Ajoute le client à la liste de diffusion ; false si ce nom y est déjà. */
    boolean enregistrer(String nom, ConnectionEntrante handler) {
        return clients.putIfAbsent(nom, handler) == null;
    }

    void retirer(String nom, ConnectionEntrante handler) {
        if (nom != null && clients.remove(nom, handler) && running) {
            annoncer("*** " + nom + " a quitté le clavardage ***", null);
        }
    }

    String listeUtilisateurs() {
        return String.join(", ", clients.keySet());
    }

    /** Les 15 derniers messages (ou moins) pour un client qui vient de se connecter. */
    List<String> historique() {
        return historique.derniersMessages();
    }

    /**
     * Message de clavardage déjà formaté : affiché sur le serveur, enregistré dans
     * l'historique (fichier) et envoyé à tous les clients sauf {@code exclu}.
     */
    void diffuserMessage(String messageFormate, ConnectionEntrante exclu) {
        System.out.println(messageFormate);
        historique.ajouter(messageFormate);
        envoyerATous(messageFormate, exclu);
    }

    /** Notification (arrivée/départ) : diffusée mais non enregistrée dans l'historique. */
    void annoncer(String texte, ConnectionEntrante exclu) {
        System.out.println(texte);
        envoyerATous(texte, exclu);
    }

    private void envoyerATous(String message, ConnectionEntrante exclu) {
        for (ConnectionEntrante c : clients.values()) {
            if (c != exclu) c.envoyerChat(message);
        }
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
            c.envoyerChat("*** Le serveur s'arrête. Au revoir ! ***");
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
}