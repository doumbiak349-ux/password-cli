package ci.proxyma.passwordcli;

/**
 * Représente la configuration de génération demandée par l'utilisateur.
 *
 * Choix d'un record (Java 21) plutôt qu'une classe classique : ces options
 * sont une simple donnée immuable une fois lues depuis la ligne de commande —
 * aucune logique métier ne leur est attachée, donc pas besoin de getters/setters
 * manuels ni d'equals/hashCode/toString à écrire à la main.
 */
public record PasswordOptions(
        int length,
        boolean includeUppercase,
        boolean includeLowercase,
        boolean includeDigits,
        boolean includeSymbols,
        int count
) {
    /**
     * Validation invoquée automatiquement par le constructeur canonique du record.
     * On s'assure qu'au moins un type de caractère est sélectionné, sinon
     * la génération serait impossible (alphabet vide).
     */
    public PasswordOptions {
        if (!includeUppercase && !includeLowercase && !includeDigits && !includeSymbols) {
            throw new IllegalArgumentException(
                    "Au moins un type de caractère doit être sélectionné (majuscule, minuscule, chiffre ou symbole).");
        }
        if (length < 1) {
            throw new IllegalArgumentException("La longueur du mot de passe doit être au moins 1.");
        }
        if (count < 1) {
            throw new IllegalArgumentException("Le nombre de mots de passe à générer doit être au moins 1.");
        }
    }
}
