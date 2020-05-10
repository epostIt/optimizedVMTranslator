FROM atlassianlabs/docker-node-jdk-chrome-firefox AS builder

WORKDIR /app

COPY package*.json tsconfig*.json ./
RUN npm i

COPY src src
COPY public public

RUN npm run build

# Stage 2
FROM artifactory.spacecamp.ninja/docker/platform/hardened-ubi-stigd-nginx:8.0
USER appuser

COPY --from=builder --chown=appuser:appuser /app/build /var/www

EXPOSE 8080

CMD [ "nginx", "-g", "daemon off;" ]
