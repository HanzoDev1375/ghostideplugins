#!/usr/bin/env bash
set -e
export DEBIAN_FRONTEND=noninteractive

# ── 1. Ensure Node.js >= 18 ──────────────────────────────────────────────────
install_nodejs() {
  if command -v node >/dev/null 2>&1; then
    MAJOR=$(node -v 2>/dev/null | tr -d 'v' | cut -d. -f1)
    if [ -n "$MAJOR" ] && [ "$MAJOR" -ge 18 ]; then
      echo "Node.js $(node -v) detected (>=18)."
      return 0
    fi
    echo "WARNING: node $(node -v) is older than 18; jj-language-server may fail to run."
  fi
  if command -v apt-get >/dev/null 2>&1; then
    echo 'Installing Node.js via apt...'
    apt-get update >/dev/null
    apt-get install -y nodejs npm >/dev/null
  fi
  command -v node >/dev/null 2>&1 || { echo 'ERROR: node not found after install' >&2; exit 1; }
  echo "Node.js $(node -v)"
}
install_nodejs

# ── 2. Install jj-language-server globally via npm ──────────────────────────
BIN=$(command -v jj-language-server 2>/dev/null || true)
if [ -z "$BIN" ] || [ ! -x "$BIN" ]; then
  echo 'Installing jj-language-server globally via npm...'
  if ! npm install -g jj-language-server 2>/dev/null; then
    echo 'Default npm registry failed; retrying with npmmirror.com...'
    npm install -g --registry https://registry.npmmirror.com jj-language-server
  fi
  BIN=$(command -v jj-language-server 2>/dev/null || true)
  if [ -z "$BIN" ]; then
    GBIN=$(npm prefix -g 2>/dev/null || true)
    [ -n "$GBIN" ] && BIN="$GBIN/bin/jj-language-server"
  fi
fi
[ -n "$BIN" ] && [ -x "$BIN" ] || { echo 'ERROR: jj-language-server binary not found' >&2; exit 1; }
echo "jj-language-server binary: $BIN"

# ── 3. Wrapper launcher ─────────────────────────────────────────────────────
# Makes sure Node and the npm global bin dir are reachable however the IDE
# spawns us (a login shell is not guaranteed). Also normalizes the entrypoint
# to a stable /usr/local/bin/java-language-server path for the provider.
cat > /usr/local/bin/java-language-server <<'WRAPPER'
#!/usr/bin/env bash
NODE_BIN=$(command -v node 2>/dev/null)
if [ -z "$NODE_BIN" ]; then
  for c in /usr/bin/node /usr/local/bin/node /data/data/com.termux/files/usr/bin/node; do
    [ -x "$c" ] && NODE_BIN="$c" && break
  done
fi
if [ -z "$NODE_BIN" ]; then
  echo 'java-language-server: node not found; run the setup action first' >&2
  exit 1
fi
export PATH="$(dirname "$NODE_BIN"):$PATH"
JJ=$(command -v jj-language-server 2>/dev/null)
if [ -z "$JJ" ]; then
  for c in /usr/local/bin/jj-language-server /usr/bin/jj-language-server; do
    [ -x "$c" ] && JJ="$c" && break
  done
fi
[ -n "$JJ" ] || { echo 'java-language-server: jj-language-server not found' >&2; exit 1; }
exec "$JJ" "$@"
WRAPPER
chmod +x /usr/local/bin/java-language-server

# ── 4. Smoke test ───────────────────────────────────────────────────────────
echo 'Running smoke test...'
VER=$(timeout 20 /usr/local/bin/java-language-server --version 2>/dev/null) && echo "Smoke test passed: jj-language-server v${VER}" || echo 'WARNING: smoke test failed - check Node.js and npm global bin.' >&2
echo 'Installation complete.'