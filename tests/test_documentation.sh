#!/bin/bash
# Tests for US-002: Verify project_documentation.md completeness
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DOC="$SCRIPT_DIR/../project_documentation.md"
PASSED=0
FAILED=0

check_section() {
    local pattern="$1"
    local desc="$2"
    if grep -qi "$pattern" "$DOC"; then
        echo "PASS: $desc"
        ((PASSED++))
    else
        echo "FAIL: $desc (pattern: $pattern)"
        ((FAILED++))
    fi
}

echo "=== Documentation Completeness Tests ==="
echo ""

# AC 1: SmartTube reference exists
if [ -d "$SCRIPT_DIR/../../smarttube-clean" ]; then
    echo "PASS: dev/smarttube-clean/ directory exists"
    ((PASSED++))
else
    echo "FAIL: dev/smarttube-clean/ directory missing"
    ((FAILED++))
fi

# AC 2: Protobuf request/response format
check_section "Protobuf Request.*Response Format\|VideoTranslationRequest" "Protobuf request/response format documented"
check_section "VideoTranslationResponse\|Translation Status Enum" "Protobuf response fields documented"
check_section "proto.*field\|Field.*Type.*Description" "Protobuf field details documented"

# AC 3: HMAC signing (Vtrans-Signature)
check_section "HMAC.*Sign\|Vtrans-Signature" "HMAC signing section exists"
check_section "HMAC-SHA-256\|hmac.*key\|bt8xH3VOlb4" "HMAC algorithm and key documented"
check_section "Sec-Vtrans\|SecYaHeaders\|getSecYaHeaders" "SecYa headers documented"

# AC 4: Shadow Player architecture
check_section "Shadow Player" "Shadow Player section exists"
check_section "audio.*player\|second.*player\|createPlayer" "Audio player creation documented"
check_section "audioPlayer.*src\|player\.src" "Audio source assignment documented"

# AC 5: Audio sync strategy
check_section "Audio Sync Strategy\|sync.*strategy" "Audio sync section exists"
check_section "500ms\|drift\|seek" "Sync threshold documented"

# AC 6: Audio ducking
check_section "Audio Ducking" "Audio ducking section exists"
check_section "RMS\|rms.*envelope" "RMS-based detection documented"
check_section "thresholdOnRms\|thresholdOffRms\|hysteresis" "Ducking thresholds documented"
check_section "attackTauMs\|releaseTauMs" "Attack/release time constants documented"
check_section "baseline.*tracking\|baseline.*volume" "Baseline tracking documented"

# AC 7: Polling/retry logic
check_section "Polling.*Retry\|retry.*logic" "Polling/retry section exists"
check_section "20.*second\|scheduleRetry\|retry.*delay" "Retry delay documented"
check_section "AUDIO_REQUESTED\|audio.*download" "Audio download flow documented"
check_section "AbortController\|cancel" "Cancellation documented"

# AC 8: Key class names and file paths
check_section "VOTClient\|MinimalClient" "VOTClient class documented"
check_section "YandexVOTProtobuf" "Protobuf class documented"
check_section "getSignature\|getSecYaHeaders" "Signing functions documented"
check_section "translationHandler\.ts" "Translation handler file path documented"
check_section "ducking\.ts" "Ducking file path documented"
check_section "secure\.js" "Secure module file path documented"

echo ""
echo "=== Results ==="
echo "Passed: $PASSED"
echo "Failed: $FAILED"
echo "Total:  $((PASSED + FAILED))"

if [ "$FAILED" -gt 0 ]; then
    echo "SOME TESTS FAILED"
    exit 1
fi

echo "ALL TESTS PASSED"
