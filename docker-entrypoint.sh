#!/bin/sh
set -eu

# O volume pode ter sido criado por uma imagem anterior com outro UID/GID.
# Ajusta apenas o diretório persistente e executa a aplicação sem privilégios.
chown -R app:app /data
exec su-exec app java -jar /app/app.jar
