#!/usr/bin/env bash
set -euo pipefail

# Install Java 17
apt-get update
DEBIAN_FRONTEND=noninteractive apt-get install -y openjdk-17-jdk curl git ca-certificates

# Install Leiningen
curl -fsSL https://raw.githubusercontent.com/technomancy/leiningen/stable/bin/lein -o /usr/local/bin/lein
chmod +x /usr/local/bin/lein

# Prime lein
lein -v || true

# Install deps and install the template locally
lein clean && lein deps && lein install

# Generate a sample app to validate and speed up first-time usage
rm -rf /workspaces/ciapp || true
lein new lst ciapp
cd ciapp
lein test || true

printf "\nDevcontainer setup complete. Run: cd ciapp && lein with-profile dev run\n"
printf "Note: After publishing to Clojars, you can also run 'lein new org.clojars.hector/lst <name>' anywhere.\n"
