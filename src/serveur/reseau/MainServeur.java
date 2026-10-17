package serveur.reseau;

import java.util.Scanner;

import serveur.authentificationPerformance.GestionDesUtilisateurs;
import serveur.authentificationPerformance.HistoriqueDeMessages;

public class MainServeur {

	public static void main(String[] args) {
		 ChatServer serveur = new ChatServer(new GestionDesUtilisateurs(), new HistoriqueDeMessages());
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
