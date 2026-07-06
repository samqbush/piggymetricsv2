FROM mongo:7.0
LABEL org.opencontainers.image.authors="Alexander Lukyanchikov <sqshq@sqshq.com>"

# Native MongoDB initialization (mongo:7): the official entrypoint creates the
# root user from MONGO_INITDB_ROOT_* and then runs every script in
# /docker-entrypoint-initdb.d against MONGO_INITDB_DATABASE on a first-run
# (empty) data volume, before enabling --auth for the real server.
COPY docker-entrypoint-initdb.d/ /docker-entrypoint-initdb.d/

EXPOSE 27017
