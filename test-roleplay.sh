#!/bin/bash

echo "🎭 Testing RobinLLM with Roleplay Prompts"
echo "========================================"

# Kill any running instances
pkill -f "quarkus-run.jar" 2>/dev/null || true
sleep 2

# Start application with valid API key
echo "Starting RobinLLM with OpenRouter API key..."
export OPENROUTER_API_KEY={OPENROUTER_API_KEY}
java -jar target/quarkus-app/quarkus-run.jar > app.log 2>&1 &
APP_PID=$!

# Wait for startup
echo "Waiting for startup..."
for i in {1..30}; do
    if curl -s http://localhost:8080/v1/health > /dev/null 2>&1; then
        echo "✅ Started on port 8080"
        break
    fi
    if [ $i -eq 30 ]; then
        echo "❌ Failed to start"
        kill $APP_PID 2>/dev/null || true
        exit 1
    fi
    sleep 1
done

# Test 1: Simple roleplay prompt
echo -e "\n📝 Test 1: Creative storyteller roleplay"
RESPONSE1=$(curl -s -X POST http://localhost:8080/v1/chat/completions \
    -H "Content-Type: application/json" \
    -d '{
        "model": "auto",
        "messages": [
            {"role": "system", "content": "You are a creative storyteller."},
            {"role": "user", "content": "Write a short story about a dragon who bakes cookies."}
        ]
    }')

MODEL1=$(echo "$RESPONSE1" | jq -r '.model // "error"')
if [[ "$MODEL1" != "error" ]]; then
    echo "✅ Request successful with model: $MODEL1"
    STORY=$(echo "$RESPONSE1" | jq -r '.choices[0].message.content // "No content"')
    echo "Story preview: $(echo "$STORY" | head -c 100)..."
else
    echo "❌ Request failed: $MODEL1"
fi

# Test 2: More complex roleplay prompt
echo -e "\n🎭 Test 2: Technical roleplay prompt"
RESPONSE2=$(curl -s -X POST http://localhost:8080/v1/chat/completions \
    -H "Content-Type: application/json" \
    -d '{
        "model": "auto",
        "messages": [
            {"role": "system", "content": "You are an expert software architect."},
            {"role": "user", "content": "Explain microservices architecture as if you were explaining it to a 5-year-old."}
        ]
    }')

MODEL2=$(echo "$RESPONSE2" | jq -r '.model // "error"')
if [[ "$MODEL2" != "error" ]]; then
    echo "✅ Request successful with model: $MODEL2"
    EXPLANATION=$(echo "$RESPONSE2" | jq -r '.choices[0].message.content // "No content"')
    echo "Explanation preview: $(echo "$EXPLANATION" | head -c 100)..."
else
    echo "❌ Request failed: $MODEL2"
fi

# Show process logs
echo -e "\n📊 Recent Process Logs:"
grep -a -E "(Received chat|route|Falling back|Request completed)" app.log | tail -10

# Clean up
kill $APP_PID 2>/dev/null || true
sleep 2

echo -e "\n✅ Roleplay test completed!"
