package serveur.authentificationPerformance;
import java.io.BufferedReader;
	import java.io.BufferedWriter;
	import java.io.IOException;
	import java.nio.file.Files;
	import java.nio.file.Path;
	import java.nio.file.Paths;
	import java.nio.file.StandardOpenOption;
	import java.time.LocalDateTime;
	import java.time.format.DateTimeFormatter;
	import java.util.ArrayList;
	import java.util.List;
public class HistoriqueDeMessages {
	    private static final String FICHIER_HISTORIQUE = "historique.txt";
	    private static final int NB_MESSAGES_RECENTS = 15;
	    private static final DateTimeFormatter FORMAT_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd@HH:mm:ss");

	    private final List<String> messages = new ArrayList<>();

	    public HistoriqueDeMessages() {
	        chargerHistorique();
	    }

	    public static String formater(String nom, String ip, int port, String contenu) {
	        String date = LocalDateTime.now().format(FORMAT_DATE);
	        return "[" + nom + " - " + ip + ":" + port + " - " + date + "] : " + contenu;
	    }

	    public synchronized void ajouter(String messageFormate) {
	        messages.add(messageFormate);
	        sauvegarderMessage(messageFormate);
	    }

	    // Retourne les 15 derniers messages (ou moins)
	    public synchronized List<String> derniersMessages() {
	        int debut = messages.size() - NB_MESSAGES_RECENTS;
	        if (debut <0 ) {
	        	debut = 0;
	        }
	        return new ArrayList<>(messages.subList(debut, messages.size()));
	    }

	    private void chargerHistorique() {
	        Path chemin = Paths.get(FICHIER_HISTORIQUE);
	        if (!Files.exists(chemin)) {
	            return;
	        }
	        try (BufferedReader lecteur = Files.newBufferedReader(chemin)) {
	            String ligne;
	            while ((ligne = lecteur.readLine()) != null) {
	                messages.add(ligne);
	            }
	        } catch (IOException e) {
	            System.err.println("Erreur de lecture de l'historique : " + e.getMessage());
	        }
	    }

	    private void sauvegarderMessage(String message) {
	        try (BufferedWriter ecrivain = Files.newBufferedWriter(
	                Paths.get(FICHIER_HISTORIQUE),
	                StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
	            ecrivain.write(message);
	            ecrivain.newLine();
	        } catch (IOException e) {
	            System.err.println("Erreur d'écriture de l'historique : " + e.getMessage());
	        }
	}
}
