package serveur.reseau;

import serveur.authentificationPerformance.EtatDeConnexion;
import serveur.authentificationPerformance.HistoriqueDeMessages;
import shared.Protocol;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.List;

/** Gère un client dans son propre thread (protocole binaire writeUTF/readUTF). */
public class ConnectionEntrante implements Runnable {

    private static final int DELAI_AUTH_MS = 30_000;
    private static final int LONGUEUR_MAX = 200;

    private final Socket socket;
    private final ChatServer serveur;
    private final String ipClient;
    private final int portClient;
    private DataInputStream in;
    private DataOutputStream out;

    private String nom;
    // true dès que GestionDesUtilisateurs a accepté la connexion : il faudra alors
    // appeler deconnecter(nom) à la fin, quoi qu'il arrive.
    private boolean authentifie = false;
    // Tant que l'authentification n'est pas terminée, on n'envoie aucun message de chat
    // (sinon on corromprait le flux attendu par le client).
    private volatile boolean pret = false;

    public ConnectionEntrante(Socket socket, ChatServer serveur) {
        this.socket = socket;
        this.serveur = serveur;
        this.ipClient = socket.getInetAddress().getHostAddress();
        this.portClient = socket.getPort();
    }

    @Override
    public void run() {
        try {
            in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
            out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));

            // Un client qui ne s'authentifie pas ne doit pas bloquer un thread indéfiniment
            socket.setSoTimeout(DELAI_AUTH_MS);
            if (!authentifier()) return;
            socket.setSoTimeout(0);

            serveur.annoncer("*** " + nom + " a rejoint le clavardage ***", this);

            boucleMessages();

        } catch (IOException e) {
            // connexion interrompue : nettoyage dans finally
        } finally {
            serveur.retirer(nom, this);
            // Ne jamais déconnecter un nom qu'on n'a pas authentifié nous-mêmes
            // (ex. DEJA_CONNECTE : ce nom appartient à un autre client).
            if (authentifie) serveur.deconnecter(nom);
            fermer();
        }
    }

    // ---------- Authentification ----------

    private boolean authentifier() throws IOException {
        String commande = in.readUTF();
        if (!Protocol.AUTH.equals(commande)) {
            refuser("Authentification requise.");
            return false;
        }
        String utilisateur = in.readUTF().trim();
        String motDePasse = in.readUTF();

        // comptes.txt utilise ':' comme séparateur et une ligne par compte
        if (utilisateur.isEmpty() || utilisateur.contains(":")
                || utilisateur.contains("\n") || utilisateur.contains("\r")
                || motDePasse.isEmpty() || motDePasse.contains("\n") || motDePasse.contains("\r")) {
            refuser("Nom d'utilisateur ou mot de passe invalide "
                    + "(non vide, sans ':' ni saut de ligne).");
            return false;
        }

        EtatDeConnexion etat = serveur.authentifier(utilisateur, motDePasse);
        switch (etat) {
            case CONNEXION_ACCEPTEE:
                break;
            case MOT_DE_PASSE_INVALIDE:
                refuser("Mot de passe invalide.");
                return false;
            case DEJA_CONNECTE:
                refuser("Cet utilisateur est déjà connecté.");
                return false;
            default:
                refuser("Authentification impossible.");
                return false;
        }

        nom = utilisateur;
        authentifie = true;

        if (!serveur.enregistrer(nom, this)) { // ne devrait pas arriver (déjà vérifié ci-dessus)
            refuser("Cet utilisateur est déjà connecté.");
            return false;
        }

        // Les 15 derniers messages (ou moins), fournis par HistoriqueDeMessages
        List<String> historique = serveur.historique();
        synchronized (this) {
            out.writeUTF(Protocol.AUTH_OK);
            out.writeUTF("Bienvenue " + nom + " ! Connectés : " + serveur.listeUtilisateurs());
            out.writeInt(historique.size());
            for (String ligne : historique) {
                out.writeUTF(ligne);
            }
            out.flush();
        }
        pret = true;
        return true;
    }

    private void refuser(String detail) throws IOException {
        synchronized (this) {
            out.writeUTF(Protocol.AUTH_FAIL);
            out.writeUTF(detail);
            out.flush();
        }
    }

    // ---------- Réception ----------

    private void boucleMessages() throws IOException {
        while (true) {
            String type = in.readUTF();

            if (Protocol.QUIT.equals(type)) {
                return;
            }
            if (Protocol.MESSAGE.equals(type)) {
                String message = in.readUTF();
                if (message.isBlank()) {
                    envoyerErreur("Le message ne peut pas être vide.");
                } else if (message.codePointCount(0, message.length()) > LONGUEUR_MAX) {
                    envoyerErreur("Le message dépasse la limite de " + LONGUEUR_MAX + " caractères.");
                } else if (message.contains("\n") || message.contains("\r")) {
                    // historique.txt contient un message par ligne
                    envoyerErreur("Le message ne peut pas contenir de saut de ligne.");
                } else {
                    String formate = HistoriqueDeMessages.formater(nom, ipClient, portClient, message);
                    // Le client affiche déjà son propre message : on ne le lui renvoie pas
                    serveur.diffuserMessage(formate, this);
                }
            } else {
                envoyerErreur("Commande inconnue : " + type);
            }
        }
    }

    // ---------- Envoi (synchronisé : plusieurs threads peuvent écrire vers ce client) ----------

    void envoyerChat(String message) {
        if (!pret) return;
        try {
            synchronized (this) {
                out.writeUTF(Protocol.CHAT);
                out.writeUTF(message);
                out.flush();
            }
        } catch (IOException e) {
            fermer(); // le thread de lecture se terminera et nettoiera
        }
    }

    private void envoyerErreur(String detail) {
        try {
            synchronized (this) {
                out.writeUTF(Protocol.ERROR);
                out.writeUTF(detail);
                out.flush();
            }
        } catch (IOException e) {
            fermer();
        }
    }

    /** Ferme le socket et les flux (idempotent). */
    void fermer() {
        pret = false;
        try { if (!socket.isClosed()) socket.close(); } catch (IOException ignored) { }
    }
}