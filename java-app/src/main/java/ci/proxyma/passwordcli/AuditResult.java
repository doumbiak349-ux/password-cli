package ci.proxyma.passwordcli;

import java.util.List;

/**
 * Représente la réponse JSON renvoyée par le micro-service d'audit (conteneur Docker).
 * Les noms de champs correspondent exactement aux clés JSON produites par app.py
 * pour permettre une désérialisation directe avec Jackson.
 */
public record AuditResult(
        int score,
        String niveau,
        String tempsEstimeCassage,
        List<String> motifsDetectes,
        List<String> suggestions
) {
    public StrengthLevel toStrengthLevel() {
        return StrengthLevel.fromScore(score);
    }
}
