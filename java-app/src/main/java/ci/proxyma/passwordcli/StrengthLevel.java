package ci.proxyma.passwordcli;

/**
 * Les 5 niveaux de robustesse exigés par le cahier des charges.
 *
 * Chaque niveau correspond exactement à un score zxcvbn (0 à 4) renvoyé par
 * le conteneur d'audit. On garde cette correspondance ici plutôt que de la
 * recalculer côté Java, car c'est zxcvbn — pas notre code — qui doit rester
 * la source de vérité pour la notation (cf. cahier des charges : "la logique
 * de notation ne doit pas être traitée uniquement par votre programme Java").
 */
public enum StrengthLevel {
    TRES_FAIBLE(0, "Très faible"),
    FAIBLE(1, "Faible"),
    MOYEN(2, "Moyen"),
    FORT(3, "Fort"),
    TRES_FORT(4, "Très fort");

    private final int score;
    private final String libelle;

    StrengthLevel(int score, String libelle) {
        this.score = score;
        this.libelle = libelle;
    }

    public String libelle() {
        return libelle;
    }

    /**
     * Convertit un score brut zxcvbn (0-4) en niveau métier.
     * Utilise une recherche directe plutôt qu'un switch, car le score
     * fait déjà partie de la définition de l'enum.
     */
    public static StrengthLevel fromScore(int score) {
        for (StrengthLevel niveau : values()) {
            if (niveau.score == score) {
                return niveau;
            }
        }
        // Filet de sécurité si le conteneur renvoie un score hors plage (0-4) :
        // on ne plante pas l'application, on retombe sur le niveau le plus bas
        // par prudence (mieux vaut sous-estimer la robustesse que la surestimer).
        return TRES_FAIBLE;
    }
}
