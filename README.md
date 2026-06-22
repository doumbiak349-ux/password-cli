# Password CLI — Générateur et auditeur de mots de passe

Outil en ligne de commande développé en **Java 21** permettant de générer des
mots de passe robustes et d'auditer leur solidité réelle via un conteneur
**Docker** exposant l'algorithme **zxcvbn**.

## 1. Architecture

```
password-cli/
├── docker/                  Service d'audit (Flask + zxcvbn)
│   ├── app.py
│   ├── requirements.txt
│   └── Dockerfile
├── docker-compose.yml       Lancement simplifié du conteneur
└── java-app/                Application CLI Java 21
    ├── pom.xml
    └── src/main/java/ci/proxyma/passwordcli/
        ├── Main.java              Point d'entrée CLI (picocli)
        ├── PasswordOptions.java   Configuration immuable (record)
        ├── PasswordGenerator.java Génération des mots de passe (SecureRandom)
        ├── AuditClient.java       Client HTTP vers le conteneur Docker
        ├── AuditResult.java       Réponse JSON désérialisée (record)
        └── StrengthLevel.java     Les 5 niveaux de robustesse
```

## 2. Communication Java ↔ Docker

L'application Java communique avec le conteneur via une **API REST HTTP/JSON**
(et non via `docker exec`). Ce choix découple totalement le client Java de la
technologie utilisée dans le conteneur : on pourrait remplacer zxcvbn par
CrackLib ou un modèle Ollama sans modifier une ligne d'`AuditClient.java`,
tant que le contrat `POST /audit {"password": "..."}` → JSON est respecté.

- `GET /health` : vérifie que le service est prêt avant de lancer les audits.
- `POST /audit` : envoie un mot de passe, reçoit le score zxcvbn (0-4), le
  niveau correspondant, le temps de cassage estimé, les motifs détectés
  (dictionnaire, séquence clavier...) et des suggestions d'amélioration.

## 3. Installation et exécution

### Prérequis
- Java 21 (JDK)
- Maven 3.9+
- Docker et Docker Compose

### Étape 1 — Démarrer le conteneur d'audit

```bash
docker compose up -d --build
```

Vérifier que le service répond :
```bash
curl http://localhost:5000/health
# {"status":"ok"}
```

### Étape 2 — Construire l'application Java

```bash
cd java-app
mvn clean package
```

Le jar exécutable est généré dans `java-app/target/password-cli.jar`.

### Étape 3 — Utiliser l'outil

```bash
# Un mot de passe de 16 caractères avec tous les types, audité automatiquement
java -jar target/password-cli.jar --length 16 --upper --lower --digits --symbols

# Mode rafale : 5 mots de passe de 20 caractères
java -jar target/password-cli.jar -l 20 -u -w -d -s -c 5

# Sans audit (génération seule, conteneur non requis)
java -jar target/password-cli.jar -l 12 -u -w -d --no-audit

# Aide complète
java -jar target/password-cli.jar --help
```

### Options disponibles

| Option | Description | Défaut |
|---|---|---|
| `-l, --length` | Longueur du mot de passe | 16 |
| `-u, --upper` | Inclure des majuscules | désactivé |
| `-w, --lower` | Inclure des minuscules | désactivé |
| `-d, --digits` | Inclure des chiffres | désactivé |
| `-s, --symbols` | Inclure des symboles | désactivé |
| `-c, --count` | Nombre de mots de passe (mode rafale) | 1 |
| `--no-audit` | Désactive l'appel au conteneur Docker | désactivé |
| `--audit-url` | URL du conteneur d'audit | http://localhost:5000 |

Si aucun type de caractère n'est précisé, les quatre sont activés par défaut.

## 4. Arrêter le conteneur

```bash
docker compose down
```
