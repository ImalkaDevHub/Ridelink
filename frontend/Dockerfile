FROM node:22-alpine AS dependencies
WORKDIR /app
RUN apk add --no-cache libc6-compat
COPY package.json package-lock.json ./
RUN npm ci

FROM node:22-alpine AS build
WORKDIR /app
RUN apk add --no-cache libc6-compat
COPY --from=dependencies /app/node_modules ./node_modules
COPY . .
ARG NEXT_PUBLIC_ACCOUNT_API_URL=http://localhost:8081
ARG NEXT_PUBLIC_DRIVER_API_URL=http://localhost:8082
ARG NEXT_PUBLIC_RIDE_API_URL=http://localhost:8083
ARG NEXT_PUBLIC_FARE_API_URL=http://localhost:8084
ENV NEXT_PUBLIC_ACCOUNT_API_URL=${NEXT_PUBLIC_ACCOUNT_API_URL}
ENV NEXT_PUBLIC_DRIVER_API_URL=${NEXT_PUBLIC_DRIVER_API_URL}
ENV NEXT_PUBLIC_RIDE_API_URL=${NEXT_PUBLIC_RIDE_API_URL}
ENV NEXT_PUBLIC_FARE_API_URL=${NEXT_PUBLIC_FARE_API_URL}
ENV NODE_ENV=production
RUN npm run build

FROM node:22-alpine AS runtime
WORKDIR /app
ENV NODE_ENV=production
ENV HOSTNAME=0.0.0.0
ENV PORT=3000
COPY --from=build --chown=node:node /app/.next/standalone ./
COPY --from=build --chown=node:node /app/.next/static ./.next/static
COPY --from=build --chown=node:node /app/public ./public
USER node
EXPOSE 3000
CMD ["node", "server.js"]
