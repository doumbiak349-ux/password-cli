"""
Service d'audit de mots de passe — exposé en HTTP, consommé par l'application Java.

Rôle : ce micro-service encapsule la bibliothèque zxcvbn (développée à l'origine
par Dropbox) qui estime la robustesse RÉELLE d'un mot de passe en simulant des
attaques par dictionnaire, motifs clavier, dates, répétitions, etc. — contrairement
à un simple calcul d'entropie théorique basé sur la longueur et le jeu de caractères.

C'est volontairement un service minimaliste (Flask, pas de framework lourd) car
son seul rôle est de transformer la sortie de zxcvbn en JSON consommable par Java.
"""

from flask import Flask, request, jsonify
from zxcvbn import zxcvbn

app = Flask(__name__)

# Table de correspondance entre le score zxcvbn (0 à 4) et nos 5 niveaux métier.
# zxcvbn ne fournit que 5 scores bruts (0-4) ; on les renomme pour qu'ils
# correspondent exactement au cahier des charges (Très faible -> Très fort).
NIVEAUX = {
    0: "Très faible",
    1: "Faible",
    2: "Moyen",
    3: "Fort",
    4: "Très fort",
}


@app.route("/health", methods=["GET"])
def health():
    """Endpoint de vivacité, utilisé par Java pour vérifier que le conteneur est prêt."""
    return jsonify({"status": "ok"}), 200


@app.route("/audit", methods=["POST"])
def audit():
    """
    Reçoit un mot de passe et retourne l'audit de robustesse complet.

    On ne se contente pas du score : on renvoie aussi le temps de cassage estimé
    et les motifs détectés (séquence clavier, mot du dictionnaire, etc.), car
    ce sont ces éléments qui justifient le score auprès de l'utilisateur final.
    """
    data = request.get_json(silent=True)
    if not data or "password" not in data:
        return jsonify({"error": "Le champ 'password' est requis dans le corps JSON."}), 400

    password = data["password"]
    resultat = zxcvbn(password)

    score = resultat["score"]
    # Le temps de cassage "online no throttling" est le scénario le plus réaliste
    # pour un attaquant qui n'a pas accès au hash (ex : formulaire de login web).
    temps_estime = resultat["crack_times_display"]["online_no_throttling_10_per_second"]

    # On extrait uniquement les motifs détectés (pas toute la structure interne
    # de zxcvbn, qui est verbeuse) pour garder une réponse JSON légère et utile.
    motifs_detectes = [seq.get("pattern", "inconnu") for seq in resultat.get("sequence", [])]

    return jsonify({
        "score": score,
        "niveau": NIVEAUX[score],
        "tempsEstimeCassage": temps_estime,
        "motifsDetectes": motifs_detectes,
        "suggestions": resultat["feedback"]["suggestions"],
    }), 200


if __name__ == "__main__":
    # host=0.0.0.0 indispensable : le conteneur doit accepter les connexions
    # venant de l'extérieur (la machine hôte où tourne le client Java),
    # pas seulement du localhost interne au conteneur.
    app.run(host="0.0.0.0", port=5000)
