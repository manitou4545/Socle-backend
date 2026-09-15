FROM python:3.12-slim
WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY app.py .

ENV PORT=5000
EXPOSE 5000

# gunicorn en production plutôt que le serveur de dev Flask (app.run)
CMD ["sh", "-c", "gunicorn --bind 0.0.0.0:${PORT} --workers 2 --timeout 60 app:app"]
