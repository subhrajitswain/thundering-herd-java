#!/bin/bash

echo "╔════════════════════════════════════════════════════════════════╗"
echo "║        Thundering Herd Resolver - Quick Start                 ║"
echo "╚════════════════════════════════════════════════════════════════╝"
echo ""

if ! docker info > /dev/null 2>&1; then
    echo "❌ Docker is not running. Please start Docker first."
    exit 1
fi

echo "✓ Docker is running"
echo ""

echo "📦 Starting Redis container..."
docker run -d --name redis-thundering-herd -p 6379:6379 redis:7-alpine > /dev/null 2>&1

if [ $? -eq 0 ]; then
    echo "✓ Redis started successfully on port 6379"
else
    docker start redis-thundering-herd > /dev/null 2>&1
    echo "✓ Redis container started"
fi

echo ""
echo "⏳ Waiting for Redis to be ready..."
sleep 2
echo "✓ Redis is ready"
echo ""

if ! command -v mvn &> /dev/null; then
    echo "❌ Maven is not installed. Please install Maven 3.6+ first."
    exit 1
fi

echo "✓ Maven is installed"
echo ""

echo "🔨 Building application..."
mvn clean package -DskipTests

if [ $? -ne 0 ]; then
    echo "❌ Build failed"
    exit 1
fi

echo "✓ Build successful"
echo ""

echo "🚀 Starting application..."
echo ""

mvn spring-boot:run &

echo "⏳ Waiting for application to start..."
sleep 15

if curl -s http://localhost:8080/actuator/health > /dev/null; then
    echo ""
    echo "╔════════════════════════════════════════════════════════════════╗"
    echo "║              Application Started Successfully! ✅              ║"
    echo "╠════════════════════════════════════════════════════════════════╣"
    echo "║  🌐 Web UI:        http://localhost:8080                      ║"
    echo "║  📊 Metrics:       http://localhost:8080/actuator/metrics     ║"
    echo "║  🔍 Prometheus:    http://localhost:8080/actuator/prometheus  ║"
    echo "║  💚 Health:        http://localhost:8080/actuator/health      ║"
    echo "║                                                                ║"
    echo "║  Demo Endpoints:                                              ║"
    echo "║    /demo/baseline        - No mitigation                      ║"
    echo "║    /demo/singleflight    - Single-flight only                 ║"
    echo "║    /demo/full            - Full solution                      ║"
    echo "║    /demo/stampede        - Cache stampede test                ║"
    echo "╚════════════════════════════════════════════════════════════════╝"
    echo ""
else
    echo "❌ Application failed to start. Check logs for details."
    exit 1
fi

wait