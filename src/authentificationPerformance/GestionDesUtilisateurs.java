package authentificationPerformance;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class GestionDesUtilisateurs {
	private final Map<String, String> comptes = new ConcurrentHashMap<>();
	private final Set<String> connectes = ConcurrentHashMap.newKeySet();
	private static final String SEPARATEUR = ":";
	 private static final String FICHIER_COMPTES = "comptes.txt";
	 
	 public GestionDesUtilisateurs() {
	        chargerComptes();
	    }
	
	
	public void deconnecter(String nom) {
		connectes.remove(nom);
	}
	public synchronized EtatDeConnexion authentification(String nom, String mdp) {
		if (comptes.containsKey(nom)){
			if (comptes.get(nom).equals(mdp)) {
				if (connectes.contains(nom)) {
					return EtatDeConnexion.DEJA_CONNECTE;
				}
				else {
					/*connexion accepté et ajouté dans la liste de compte connexté*/
					connectes.add(nom);
					return EtatDeConnexion.CONNEXION_ACCEPTEE;
				}
			}
			else {
				return  EtatDeConnexion.MOT_DE_PASSE_INVALIDE;
			}
	}
		else {
			comptes.put(nom, mdp);
			sauvegarderCompte(nom, mdp);           // persistance (4.5)
	        connectes.add(nom);
	        return EtatDeConnexion.CONNEXION_ACCEPTEE;
			/*ajouté l'utlisateur au serveur avec son mot de passe dans la liste de compte*/
		}
				
	}
	
	 private void chargerComptes() {
	        Path chemin = Paths.get(FICHIER_COMPTES);
	        if (!Files.exists(chemin)) {
	            return; 
	        }
	        try (BufferedReader lecteur = Files.newBufferedReader(chemin)) {
	            String ligne;
	            while ((ligne = lecteur.readLine()) != null) {
	                String[] parties = ligne.split(SEPARATEUR, 2);
	                if (parties.length == 2) {
	                    comptes.put(parties[0], parties[1]);
	                }
	            }
	        } catch (IOException e) {
	            System.err.println("Erreur de lecture des comptes : " + e.getMessage());
	        }
	    }

	 private void sauvegarderCompte(String nom, String mdp) {
		    try (BufferedWriter ecrivain = Files.newBufferedWriter(
		            Paths.get(FICHIER_COMPTES),
		            StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
		        ecrivain.write(nom + SEPARATEUR + mdp);
		        ecrivain.newLine();
		    } catch (IOException e) {
		        System.err.println("Erreur d'écriture du compte : " + e.getMessage());
		    }
		}
}
