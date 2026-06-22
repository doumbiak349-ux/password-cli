package ci.proxyma.passwordcli;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Génère des mots de passe selon les options demandées.
 *
 * Utilise {@link SecureRandom} plutôt que {@link java.util.Random} : pour un
 * outil de sécurité, le générateur de nombres aléatoires doit être
 * cryptographiquement imprévisible, sinon un attaquant qui connaît l'algorithme
 * pourrait reconstituer la séquence de mots de passe générés.
 */
public class PasswordGenerator {

    private static final String MAJUSCULES = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final String MINUSCULES = "abcdefghijklmnopqrstuvwxyz";
    private static final String CHIFFRES = "0123456789";
    // Symboles choisis pour être lisibles à l'oeil et sans ambiguïté avec
    // des caractères alphanumériques (on évite par exemple les guillemets
    // qui posent souvent des problèmes d'échappement dans les terminaux/scripts).
    private static final String SYMBOLES = "!@#$%^&*()-_=+[]{};:,.?";

    private final SecureRandom random = new SecureRandom();

    /**
     * Génère un unique mot de passe respectant les options fournies.
     *
     * Stratégie en deux temps :
     *   1. On garantit la présence d'au moins un caractère de chaque type
     *      sélectionné (sinon un mot de passe de longueur 8 avec les 4 types
     *      activés pourrait par pur hasard ne contenir aucun symbole).
     *   2. On complète le reste de la longueur en tirant aléatoirement
     *      dans l'alphabet combiné, puis on mélange le tout pour ne pas
     *      avoir un motif prévisible du type "une majuscule toujours en tête".
     */
    public String generate(PasswordOptions options) {
        StringBuilder alphabetCombine = new StringBuilder();
        List<Character> caracteresObligatoires = new ArrayList<>();

        if (options.includeUppercase()) {
            alphabetCombine.append(MAJUSCULES);
            caracteresObligatoires.add(tirerCaractere(MAJUSCULES));
        }
        if (options.includeLowercase()) {
            alphabetCombine.append(MINUSCULES);
            caracteresObligatoires.add(tirerCaractere(MINUSCULES));
        }
        if (options.includeDigits()) {
            alphabetCombine.append(CHIFFRES);
            caracteresObligatoires.add(tirerCaractere(CHIFFRES));
        }
        if (options.includeSymbols()) {
            alphabetCombine.append(SYMBOLES);
            caracteresObligatoires.add(tirerCaractere(SYMBOLES));
        }

        // Si la longueur demandée est plus petite que le nombre de types
        // obligatoires (cas limite : longueur 2 avec les 4 types activés),
        // on tronque les caractères obligatoires pour ne pas dépasser la longueur.
        if (caracteresObligatoires.size() > options.length()) {
            caracteresObligatoires = caracteresObligatoires.subList(0, options.length());
        }

        List<Character> motDePasse = new ArrayList<>(caracteresObligatoires);
        int restant = options.length() - motDePasse.size();
        for (int i = 0; i < restant; i++) {
            motDePasse.add(tirerCaractere(alphabetCombine.toString()));
        }

        Collections.shuffle(motDePasse, random);

        StringBuilder resultat = new StringBuilder(motDePasse.size());
        for (char c : motDePasse) {
            resultat.append(c);
        }
        return resultat.toString();
    }

    /**
     * Génère une liste de mots de passe (mode "rafale").
     * Chaque mot de passe est indépendant — pas de mémorisation des
     * précédents — pour éviter toute corrélation entre eux.
     */
    public List<String> generateBatch(PasswordOptions options) {
        List<String> resultats = new ArrayList<>(options.count());
        for (int i = 0; i < options.count(); i++) {
            resultats.add(generate(options));
        }
        return resultats;
    }

    private char tirerCaractere(String source) {
        return source.charAt(random.nextInt(source.length()));
    }
}
