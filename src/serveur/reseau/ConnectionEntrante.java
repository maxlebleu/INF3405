package serveur.reseau;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;


/** Gère un client dans son propre thread. */
public class ConnectionEntrante implements Runnable {

    private final Socket socket;
    private final ChatServer serveur;
    private BufferedReader in;
    private PrintWriter out;
    private String nom;

    public ConnectionEntrante(Socket socket, ChatServer serveur) {
        this.socket = socket;
        this.serveur = serveur;
    }

    @Override
    public void run() {
        try {
            in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            out = new PrintWriter(socket.getOutputStream(), true, StandardCharsets.UTF_8);
 
            // L'utilisateur est déjà authentifié (autre classe) : la première ligne
            // reçue est son nom. On vérifie seulement qu'il n'est pas déjà connecté.
            String saisie = in.readLine();
            if (saisie == null || saisie.trim().isEmpty()) return;
            saisie = saisie.trim();
            if (!serveur.enregistrer(saisie, this)) {
                envoyer("Erreur : " + saisie + " est déjà connecté.");
                return;
            }
            nom = saisie;
 
            envoyer("Bienvenue " + nom + " ! Connectés : " + serveur.listeUtilisateurs()
                    + " (tapez /quit pour quitter)");
            serveur.diffuser("*** " + nom + " a rejoint le clavardage ***");
 
            String ligne;
            while ((ligne = in.readLine()) != null) {
                ligne = ligne.trim();
                if (ligne.isEmpty()) continue;
                if (ligne.equalsIgnoreCase("/quit")) break;
                serveur.diffuser("[" + nom + "] " + ligne);
            }
        } catch (IOException e) {
            // connexion interrompue : on nettoie dans finally
        } finally {
            serveur.retirer(nom);
            fermer();
        }
    }

    void envoyer(String message) {
        if (out != null) out.println(message);
    }

    /** Ferme le socket et les flux (idempotent). */
    void fermer() {
        try { if (in != null) in.close(); } catch (IOException ignored) { }
        if (out != null) out.close();
        try { if (!socket.isClosed()) socket.close(); } catch (IOException ignored) { }
    }
}

