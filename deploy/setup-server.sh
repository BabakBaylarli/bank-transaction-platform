#!/usr/bin/env bash
# One-time preparation of a fresh Ubuntu 22.04/24.04 server (ARM64 or x86_64) for the platform.
#
#   sudo ./deploy/setup-server.sh
#
# What it does (idempotent - safe to run again):
#   1. installs Docker Engine + the compose plugin from Docker's official apt repository
#   2. creates a 4 GB swap file
#   3. sets vm.max_map_count=262144 (required by Elasticsearch)
#   4. opens ports 80 and 443 in the host firewall (ufw, or the iptables rules of Oracle's Ubuntu images)
#
# NOTE: on Oracle Cloud you ALSO have to allow 80/443 in the VCN Security List (see deploy/DEPLOY.md).
set -euo pipefail

if [[ $EUID -ne 0 ]]; then
  echo "Please run as root: sudo $0" >&2
  exit 1
fi

TARGET_USER="${SUDO_USER:-ubuntu}"
SWAP_SIZE_GB="${SWAP_SIZE_GB:-4}"
log() { printf '\n\033[1;34m==> %s\033[0m\n' "$*"; }

# ------------------------------------------------------------------ 1. Docker
log "Installing Docker Engine and the compose plugin"
if ! command -v docker >/dev/null 2>&1; then
  apt-get update -y
  apt-get install -y ca-certificates curl gnupg git cron
  install -m 0755 -d /etc/apt/keyrings
  curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
  chmod a+r /etc/apt/keyrings/docker.asc
  # shellcheck disable=SC1091
  . /etc/os-release
  echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu ${VERSION_CODENAME} stable" \
    > /etc/apt/sources.list.d/docker.list
  apt-get update -y
  apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
else
  echo "Docker already installed: $(docker --version)"
  apt-get install -y git cron >/dev/null
fi
systemctl enable --now docker
usermod -aG docker "$TARGET_USER" || true

# keep container logs from filling the small boot volume
if [[ ! -f /etc/docker/daemon.json ]]; then
  cat > /etc/docker/daemon.json <<'JSON'
{
  "log-driver": "json-file",
  "log-opts": { "max-size": "10m", "max-file": "3" }
}
JSON
  systemctl restart docker
fi

# ------------------------------------------------------------------ 2. swap
log "Configuring ${SWAP_SIZE_GB} GB swap"
if ! swapon --show | grep -q '/swapfile'; then
  fallocate -l "${SWAP_SIZE_GB}G" /swapfile || dd if=/dev/zero of=/swapfile bs=1M count=$((SWAP_SIZE_GB * 1024))
  chmod 600 /swapfile
  mkswap /swapfile
  swapon /swapfile
  grep -q '^/swapfile' /etc/fstab || echo '/swapfile none swap sw 0 0' >> /etc/fstab
else
  echo "Swap already active"
fi
# prefer RAM; swap is a safety net for peaks such as image builds
echo 'vm.swappiness=10' > /etc/sysctl.d/99-swappiness.conf

# ------------------------------------------------------------------ 3. Elasticsearch kernel setting
log "Setting vm.max_map_count=262144 for Elasticsearch"
echo 'vm.max_map_count=262144' > /etc/sysctl.d/99-elasticsearch.conf
sysctl --system >/dev/null

# ------------------------------------------------------------------ 4. firewall
log "Opening ports 80 and 443"
if command -v ufw >/dev/null 2>&1 && ufw status | grep -q 'Status: active'; then
  ufw allow 22/tcp
  ufw allow 80/tcp
  ufw allow 443/tcp
  ufw allow 443/udp
  ufw reload
  echo "ufw rules added"
else
  # Oracle's Ubuntu images ship iptables rules that end in a REJECT - insert ACCEPTs in front of it
  for rule in "-p tcp --dport 80" "-p tcp --dport 443" "-p udp --dport 443"; do
    # shellcheck disable=SC2086
    if ! iptables -C INPUT -m state --state NEW $rule -j ACCEPT 2>/dev/null; then
      # shellcheck disable=SC2086
      iptables -I INPUT 1 -m state --state NEW $rule -j ACCEPT
    fi
  done
  if ! command -v netfilter-persistent >/dev/null 2>&1; then
    DEBIAN_FRONTEND=noninteractive apt-get install -y iptables-persistent
  fi
  netfilter-persistent save
  echo "iptables rules added and persisted"
fi

log "Done"
cat <<EOF
Next steps:
  1. Log out and back in, so '$TARGET_USER' can run docker without sudo
  2. In the OCI console, allow TCP 80 and 443 in the subnet's Security List (deploy/DEPLOY.md, step 3)
  3. cp .env.prod.example .env && nano .env
  4. ./deploy/deploy.sh
EOF
