package serveur.reseau;

public interface Authentificateur {
	/**
     * @return null si les identifiants sont valides, sinon la raison du refus
     *         (envoyée au client dans le détail de AUTH_FAIL).
     */
    String verifier(String utilisateur, String motDePasse);

}
