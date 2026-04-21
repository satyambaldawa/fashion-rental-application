#!/bin/bash

ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"

echo "Stopping any processes on ports 8080 and 5173..."
lsof -ti:8080 | xargs kill -9 2>/dev/null
lsof -ti:5173 | xargs kill -9 2>/dev/null

echo "Starting backend..."
cd "$ROOT_DIR/backend"
./gradlew bootRun --args='--spring.profiles.active=dev' > "$ROOT_DIR/backend.log" 2>&1 &
BACKEND_PID=$!
echo "Backend PID: $BACKEND_PID (logs: backend.log)"

echo "Waiting for backend to be ready..."
until curl -s http://localhost:8080/actuator/health > /dev/null 2>&1; do
  sleep 2
done
echo "Backend is up."

echo "Starting frontend..."
cd "$ROOT_DIR/frontend"
pnpm dev --host
