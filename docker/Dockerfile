# Image Python légère : on n'a pas besoin d'un OS complet, seulement
# d'un interpréteur Python pour faire tourner Flask + zxcvbn.
FROM python:3.12-slim

WORKDIR /app

# Copie séparée de requirements.txt avant le reste du code : si le code change
# mais pas les dépendances, Docker réutilise le cache de cette étape et
# évite de retélécharger les paquets à chaque build.
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY app.py .

EXPOSE 5000

# On lance directement app.py (pas de serveur WSGI type gunicorn) : le volume
# de requêtes attendu pour un usage pédagogique/CLI est trop faible pour
# justifier la complexité supplémentaire.
CMD ["python", "app.py"]
