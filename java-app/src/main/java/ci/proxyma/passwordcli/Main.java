package ci.proxyma.passwordcli;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.List;
import java.util.concurrent.Callable;

/**
 * Point d'entrée CLI. Utilise picocli pour le parsing des arguments :
 * cela évite d'écrire à la main la gestion de --help, la validation de
 * types (ex: --length doit être un entier), et les messages d'erreur
 * en cas d'argument invalide.
 *
 * Exemple d'utilisation :
 *   java -jar password-cli.jar --length 16 --upper --lower --digits --symbols --count 5
 */
@Command(
        name = "password-cli",
        description = "Génère des mots de passe robustes et audite leur solidité via un conteneur Docker (zxcvbn).",
        mixinStandardHelpOptions = true,
        version = "password-cli 1.0.0"
)
public class Main implements Callable<Integer> {

    @Option(names = {"-l", "--length"}, description = "Longueur du mot de passe (défaut : 16).", defaultValue = "16")
    private int length;

    @Option(names = {"-u", "--upper"}, description = "Inclure des lettres majuscules.")
    private boolean includeUppercase;

    @Option(names = {"-w", "--lower"}, description = "Inclure des lettres minuscules.")
    private boolean includeLowercase;

    @Option(names = {"-d", "--digits"}, description = "Inclure des chiffres.")
    private boolean includeDigits;

    @Option(names = {"-s", "--symbols"}, description = "Inclure des symboles.")
    private boolean includeSymbols;

    @Option(names = {"-c", "--count"}, description = "Nombre de mots de passe à générer (mode rafale, défaut : 1).", defaultValue = "1")
    private int count;

    @Option(names = {"--no-audit"}, description = "Désactive l'audit via le conteneur Docker (génération seule).")
    private boolean noAudit;

    @Option(names = {"--audit-url"}, description = "URL du conteneur d'audit (défaut : http://localhost:5000).", defaultValue = "http://localhost:5000")
    private String auditUrl;

    public static void main(String[] args) {
        int codeRetour = new CommandLine(new Main()).execute(args);
        System.exit(codeRetour);
    }

    @Override
    public Integer call() {
        // Si aucun type de caractère n'est explicitement demandé, on active
        // les 4 par défaut : un utilisateur qui lance juste "password-cli"
        // sans option s'attend à un mot de passe complet, pas à une erreur.
        boolean aucunTypeSpecifie = !includeUppercase && !includeLowercase && !includeDigits && !includeSymbols;
        if (aucunTypeSpecifie) {
            includeUppercase = includeLowercase = includeDigits = includeSymbols = true;
        }

        PasswordOptions options;
        try {
            options = new PasswordOptions(length, includeUppercase, includeLowercase, includeDigits, includeSymbols, count);
        } catch (IllegalArgumentException e) {
            System.err.println("Erreur de configuration : " + e.getMessage());
            return 1;
        }

        PasswordGenerator generator = new PasswordGenerator();
        List<String> motsDePasse = generator.generateBatch(options);

        AuditClient auditClient = noAudit ? null : new AuditClient(auditUrl);
        boolean serviceDisponible = auditClient != null && auditClient.isServiceAvailable();

        if (auditClient != null && !serviceDisponible) {
            System.err.println(
                    "Attention : le conteneur d'audit (" + auditUrl + ") est inaccessible. "
                            + "Les mots de passe seront affichés sans audit de robustesse.");
            System.err.println("Démarrez-le avec : docker compose up -d");
            System.err.println();
        }

        for (int i = 0; i < motsDePasse.size(); i++) {
            String motDePasse = motsDePasse.get(i);
            System.out.printf("[%d] %s%n", i + 1, motDePasse);

            if (serviceDisponible) {
                afficherAudit(auditClient, motDePasse);
            }
            System.out.println();
        }

        return 0;
    }

    /**
     * Interroge le conteneur d'audit pour un mot de passe donné et affiche
     * le résultat de façon lisible. Les erreurs d'audit (conteneur tombé en
     * cours d'exécution, timeout réseau) sont isolées ici pour ne jamais
     * faire échouer toute la génération en mode rafale à cause d'un seul appel.
     */
    private void afficherAudit(AuditClient auditClient, String motDePasse) {
        try {
            AuditResult resultat = auditClient.audit(motDePasse);
            System.out.printf("    Robustesse : %s (score zxcvbn %d/4)%n",
                    resultat.toStrengthLevel().libelle(), resultat.score());
            System.out.printf("    Temps de cassage estimé : %s%n", resultat.tempsEstimeCassage());

            if (!resultat.motifsDetectes().isEmpty()) {
                System.out.printf("    Motifs détectés : %s%n", String.join(", ", resultat.motifsDetectes()));
            }
            if (!resultat.suggestions().isEmpty()) {
                System.out.printf("    Suggestions : %s%n", String.join(" ", resultat.suggestions()));
            }
        } catch (AuditClient.AuditException e) {
            System.err.println("    Audit indisponible pour ce mot de passe : " + e.getMessage());
        }
    }
}
