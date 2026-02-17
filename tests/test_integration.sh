#!/bin/bash
# Tests for US-015: Full VOT module integration
set -e

PASS=0
FAIL=0
BASE_DIR="$(cd "$(dirname "$0")/.." && pwd)"
VOT_BASE="$BASE_DIR/vot-module/src/main/java/app/revanced/integrations/youtube/vot"
VOT_TEST="$BASE_DIR/vot-module/src/test/java/app/revanced/integrations/youtube/vot"

pass() { PASS=$((PASS + 1)); echo "  ✅ $1"; }
fail() { FAIL=$((FAIL + 1)); echo "  ❌ $1"; }

echo "=== US-015: Full Integration Tests ==="

# --- 1. All source files present ---
echo ""
echo "--- Source completeness ---"
for f in VotModule.java VotTranslationCoordinator.java; do
  [ -f "$VOT_BASE/$f" ] && pass "$f exists" || fail "$f missing"
done
for f in YandexSignature.java YandexTranslationClient.java; do
  [ -f "$VOT_BASE/api/$f" ] && pass "api/$f exists" || fail "api/$f missing"
done
for f in TranslationAudioManager.java AudioSyncController.java AudioDuckingManager.java AudioDucking.java; do
  [ -f "$VOT_BASE/player/$f" ] && pass "player/$f exists" || fail "player/$f missing"
done
for f in VotButtonController.java VotButtonPatch.java VotButtonState.java TranslationButton.java VotSettingsPatch.java; do
  [ -f "$VOT_BASE/ui/$f" ] && pass "ui/$f exists" || fail "ui/$f missing"
done
[ -f "$VOT_BASE/settings/VotSettings.java" ] && pass "settings/VotSettings.java exists" || fail "settings/VotSettings.java missing"
[ -f "$VOT_BASE/proto/TranslationProto.java" ] && pass "proto/TranslationProto.java exists" || fail "proto/TranslationProto.java missing"
[ -f "$VOT_BASE/patch/VotPatch.java" ] && pass "patch/VotPatch.java exists" || fail "patch/VotPatch.java missing"

# --- 2. No circular dependencies ---
echo ""
echo "--- Circular dependency check ---"

# Build dependency graph from imports and check for cycles
# Package hierarchy: proto -> (no deps), api -> proto, player -> (no vot deps),
# settings -> (no vot deps), ui -> coordinator+settings, patch -> coordinator,
# coordinator -> api+player
# Allowed: lower layers don't import higher layers

# Check that proto/ doesn't import from other vot packages
PROTO_IMPORTS=$(grep -r "import app.revanced.integrations.youtube.vot\." "$VOT_BASE/proto/" 2>/dev/null | grep -v "\.vot\.proto\." || true)
[ -z "$PROTO_IMPORTS" ] && pass "proto/ has no upward dependencies" || fail "proto/ has circular deps: $PROTO_IMPORTS"

# Check that api/ doesn't import from player/, ui/, patch/, settings/
API_BAD=$(grep -r "import app.revanced.integrations.youtube.vot\." "$VOT_BASE/api/" 2>/dev/null | grep -E "\.(player|ui|patch|settings)\." || true)
[ -z "$API_BAD" ] && pass "api/ has no upward dependencies" || fail "api/ has circular deps: $API_BAD"

# Check that player/ doesn't import from api/, ui/, patch/, coordinator
PLAYER_BAD=$(grep -r "import app.revanced.integrations.youtube.vot\." "$VOT_BASE/player/" 2>/dev/null | grep -E "\.(api|ui|patch)\." || true)
[ -z "$PLAYER_BAD" ] && pass "player/ has no upward dependencies" || fail "player/ has circular deps: $PLAYER_BAD"

# Check that settings/ doesn't import from api/, player/, ui/, patch/
SETTINGS_BAD=$(grep -r "import app.revanced.integrations.youtube.vot\." "$VOT_BASE/settings/" 2>/dev/null | grep -E "\.(api|player|ui|patch)\." || true)
[ -z "$SETTINGS_BAD" ] && pass "settings/ has no upward dependencies" || fail "settings/ has circular deps: $SETTINGS_BAD"

# Check that patch/ doesn't import from ui/
PATCH_BAD=$(grep -r "import app.revanced.integrations.youtube.vot\." "$VOT_BASE/patch/" 2>/dev/null | grep -E "\.ui\." || true)
[ -z "$PATCH_BAD" ] && pass "patch/ doesn't depend on ui/" || fail "patch/ has circular dep on ui/"

# --- 3. Compilation check ---
echo ""
echo "--- Full compilation check ---"

TMPDIR=$(mktemp -d)
trap "rm -rf $TMPDIR" EXIT

# Compile all source files together
SOURCES=$(find "$VOT_BASE" -name "*.java")
if javac -d "$TMPDIR" $SOURCES 2>"$TMPDIR/compile_errors.txt"; then
  pass "All VOT sources compile together"
else
  fail "Compilation failed: $(cat $TMPDIR/compile_errors.txt | head -5)"
fi

# --- 4. VotPatch is registered (has required static methods) ---
echo ""
echo "--- VotPatch registration check ---"
grep -q "public static void onVideoLoaded" "$VOT_BASE/patch/VotPatch.java" && pass "VotPatch has onVideoLoaded hook" || fail "Missing onVideoLoaded"
grep -q "public static void onPlayerStateChanged" "$VOT_BASE/patch/VotPatch.java" && pass "VotPatch has onPlayerStateChanged hook" || fail "Missing onPlayerStateChanged"
grep -q "public static void onSeek" "$VOT_BASE/patch/VotPatch.java" && pass "VotPatch has onSeek hook" || fail "Missing onSeek"
grep -q "public static void onVideoChanged" "$VOT_BASE/patch/VotPatch.java" && pass "VotPatch has onVideoChanged hook" || fail "Missing onVideoChanged"
grep -q "public static void initialize" "$VOT_BASE/patch/VotPatch.java" && pass "VotPatch has initialize method" || fail "Missing initialize"

# --- 5. All test files exist ---
echo ""
echo "--- Test coverage check ---"
for f in VotTranslationCoordinatorTest.java; do
  [ -f "$VOT_TEST/$f" ] && pass "Test: $f exists" || fail "Test: $f missing"
done
for f in YandexSignatureTest.java; do
  [ -f "$VOT_TEST/api/$f" ] && pass "Test: api/$f exists" || fail "Test: api/$f missing"
done
for f in TranslationAudioManagerTest.java AudioSyncTest.java AudioDuckingManagerTest.java; do
  [ -f "$VOT_TEST/player/$f" ] && pass "Test: player/$f exists" || fail "Test: player/$f missing"
done
for f in VotPatchTest.java; do
  [ -f "$VOT_TEST/patch/$f" ] && pass "Test: patch/$f exists" || fail "Test: patch/$f missing"
done
for f in VotButtonControllerTest.java VotSettingsPatchTest.java; do
  [ -f "$VOT_TEST/ui/$f" ] && pass "Test: ui/$f exists" || fail "Test: ui/$f missing"
done
[ -f "$VOT_TEST/settings/VotSettingsTest.java" ] && pass "Test: settings/VotSettingsTest.java exists" || fail "Test missing"

# --- 6. Wiring: VotTranslationCoordinator references all subsystems ---
echo ""
echo "--- Wiring check: Coordinator uses all subsystems ---"
grep -q "YandexTranslationClient" "$VOT_BASE/VotTranslationCoordinator.java" && pass "Coordinator uses YandexTranslationClient" || fail "Missing"
grep -q "TranslationAudioManager" "$VOT_BASE/VotTranslationCoordinator.java" && pass "Coordinator uses TranslationAudioManager" || fail "Missing"
grep -q "AudioDuckingManager" "$VOT_BASE/VotTranslationCoordinator.java" && pass "Coordinator uses AudioDuckingManager" || fail "Missing"
grep -q "AudioSyncController" "$VOT_BASE/VotTranslationCoordinator.java" && pass "Coordinator uses AudioSyncController" || fail "Missing"

# --- 7. Wiring: VotPatch references Coordinator ---
grep -q "VotTranslationCoordinator" "$VOT_BASE/patch/VotPatch.java" && pass "VotPatch wires to Coordinator" || fail "Missing"

# --- 8. Wiring: UI references Coordinator and Settings ---
grep -q "VotTranslationCoordinator" "$VOT_BASE/ui/VotButtonController.java" && pass "Button wires to Coordinator" || fail "Missing"
grep -q "VotSettings" "$VOT_BASE/ui/VotButtonController.java" && pass "Button wires to Settings" || fail "Missing"

# --- 9. Documentation exists ---
echo ""
echo "--- Documentation check ---"
[ -f "$BASE_DIR/project_documentation.md" ] && pass "project_documentation.md exists" || fail "Missing"
[ -f "$BASE_DIR/project_log.md" ] && pass "project_log.md exists" || fail "Missing"
grep -q "Architecture" "$BASE_DIR/project_documentation.md" && pass "Documentation has architecture section" || fail "Missing architecture"

echo ""
echo "=== Results: $PASS passed, $FAIL failed ==="
[ $FAIL -eq 0 ] && exit 0 || exit 1
