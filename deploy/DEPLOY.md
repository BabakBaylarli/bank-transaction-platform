# Deploying to a free Oracle Cloud VM

This guide takes you from **no account** to **`https://yourname.duckdns.org` running the whole
platform with HTTPS** on Oracle Cloud's *Always Free* tier. Budget about 45 minutes. Most of that
is waiting for the first build.

What you get on one VM (Ampere A1 ARM64, 2 OCPU / 12 GB RAM, Ubuntu):

```
Internet ──► :80/:443  Caddy (automatic HTTPS, rate limiting, basic auth for Kibana)
                         ├─ /              → frontend (React SPA)
                         ├─ /api/...       → transaction- / processing- / search-service
                         └─ /kibana/       → Kibana  (password protected)
             internal only: PostgreSQL · Kafka · Elasticsearch
```

---

## 1. Create the VM

1. Sign up at <https://www.oracle.com/cloud/free/>. A credit card is required for identity
   verification; Always Free resources are not charged.
2. In the console, go to **Compute → Instances → Create instance**.
3. **Image and shape → Edit**:
   - Image: **Canonical Ubuntu 24.04** (22.04 also works). Pick the *aarch64* build.
   - Shape: **Ampere → VM.Standard.A1.Flex**, **2 OCPUs**, **12 GB memory**.
4. **Networking:** keep the default VCN and subnet, and make sure **Assign a public IPv4 address** is checked.
5. **Add SSH keys:** choose *Generate a key pair for me* and download **both** keys. If you
   already have a key, choose *Upload public key* and select `~/.ssh/id_ed25519.pub`.

   No key yet? Create one on your laptop:
   ```bash
   ssh-keygen -t ed25519 -C "oci-bank-demo"     # press Enter for the defaults
   ```
6. **Boot volume:** 50 GB (the default) is enough. You may raise it to 100 GB; up to 200 GB is free.
7. Click **Create** and wait until the instance is *Running*. Copy its **Public IP address**.

> **"Out of capacity for shape VM.Standard.A1.Flex"** is common: free ARM capacity is limited.
> See [Troubleshooting](#troubleshooting).

## 2. Connect with SSH

```bash
chmod 600 ~/Downloads/ssh-key-*.key                     # only for a downloaded key
ssh -i ~/Downloads/ssh-key-XXXX.key ubuntu@<PUBLIC_IP>   # or: ssh ubuntu@<PUBLIC_IP>
```

On Windows, use PowerShell (OpenSSH is built in) or Windows Terminal. The user name is `ubuntu`.

## 3. Open ports 80 and 443 in Oracle Cloud (Security List)

Oracle filters traffic **before** it reaches your VM. Two firewalls must allow it: this one, and the
VM's own firewall in step 5.

1. Open the instance page and click the **subnet** link (under *Primary VNIC*).
2. Open **Security Lists → Default Security List for …** and click **Add Ingress Rules**.
3. Add two rules:

   | Source CIDR | IP protocol | Destination port range | Description |
   |---|---|---|---|
   | `0.0.0.0/0` | TCP | `80` | HTTP (Let's Encrypt + redirect) |
   | `0.0.0.0/0` | TCP | `443` | HTTPS |

   Optionally add UDP `443` for HTTP/3. Port 22 (SSH) is already open by default.

## 4. Get a free domain (DuckDNS)

1. Go to <https://www.duckdns.org> and sign in with GitHub or Google.
2. Create a subdomain, e.g. `mybank` → **`mybank.duckdns.org`**.
3. Enter the VM's **public IP** in *current ip* and click **update ip**.
4. Copy your **token** from the top of the page. `deploy.sh` can keep the IP updated for you.

Check it after a minute: `nslookup mybank.duckdns.org` should return your VM's IP.

## 5. Prepare the server (once)

```bash
sudo apt-get update && sudo apt-get install -y git
git clone https://github.com/<you>/bank-transaction-platform.git
cd bank-transaction-platform
sudo ./deploy/setup-server.sh
```

`setup-server.sh` does four things:
- installs Docker Engine and the compose plugin (official repository, ARM64)
- creates a **4 GB swap file** (a safety net while images compile)
- sets **`vm.max_map_count=262144`**, which Elasticsearch requires
- opens **80/443** in the VM firewall: `ufw` if it is active, otherwise the iptables rules that
  Oracle's Ubuntu images use (persisted with `netfilter-persistent`)

Then **log out and back in** (`exit`, then `ssh` again), so you can run `docker` without `sudo`.

## 6. Configure

```bash
cp .env.prod.example .env
nano .env
```

Fill in at least:

| Variable | Example | Notes |
|---|---|---|
| `DOMAIN` | `mybank.duckdns.org` | must already resolve to the VM |
| `ACME_EMAIL` | `you@gmail.com` | Let's Encrypt expiry notices |
| `JWT_SECRET` | *generated* | ≥ 32 chars |
| `POSTGRES_PASSWORD` | *generated* | |
| `KIBANA_PASSWORD` | *generated* | login for `/kibana/` (user `KIBANA_USER`, default `admin`) |
| `DUCKDNS_TOKEN` | *from duckdns.org* | optional: keeps the DNS record current |

Generate a strong value with:
```bash
openssl rand -base64 48 | tr -d '\n/+=' | cut -c1-48
```

`.env` is git-ignored. Never commit it.

## 7. Deploy

```bash
./deploy/deploy.sh
```

The script:
1. pulls the latest code (`git pull --ff-only`)
2. builds all images on the VM; the **first build takes about 10–15 minutes** on 2 ARM cores
3. runs `docker compose -f docker-compose.prod.yml up -d`
4. installs cron jobs: the **nightly demo reset** (03:00, `DEMO_RESET_CRON`) and the DuckDNS update
5. waits until every container is healthy, then smoke-tests the site, the login and Kibana

To update later, run the same command again.

## 8. Verify

| Check | Expected |
|---|---|
| `https://mybank.duckdns.org` | login page with the **Demo mode** banner, valid padlock |
| Log in as `alice` → **Transfer** 10 AZN to `AZ31BTPX00000000000000000003` | status turns **Completed** within about a second |
| **Transactions** page, search `transfer` | the transfer is listed |
| Log in as `admin` → **Admin** | charts with today's totals |
| `https://mybank.duckdns.org/kibana/` | basic-auth prompt, then the *Transactions Overview* dashboard |
| `docker compose -f docker-compose.prod.yml ps` | every service `healthy` (kibana-init `exited (0)`) |

Useful commands:
```bash
docker compose -f docker-compose.prod.yml ps
docker compose -f docker-compose.prod.yml logs -f --tail=100 transaction-service
docker stats --no-stream                       # memory per container
./deploy/reset-demo.sh                         # reset the demo data right now
cat deploy/reset-demo.log                      # history of the nightly resets
```

---

## Security notes

- Only **80/443** are published. PostgreSQL, Kafka, Elasticsearch, Kibana and the Spring services
  live on the internal Docker network.
- **HTTPS** comes from Let's Encrypt and renews automatically; certificates persist in the `caddy-data` volume.
- **CORS is locked to the domain.** Frontend and API share one origin, and Caddy rejects `/api`
  requests that carry a foreign `Origin` header.
- **Rate limits** are per client IP: 10 auth requests/min and 30 new transfers/min (configurable in `.env`).
  Excess requests get `429` as an RFC 7807 problem response.
- Kibana sits behind **basic auth**. Elasticsearch security is off, but it is unreachable from outside.
- Secrets come only from `.env`, and `deploy.sh` refuses to start with an empty or short `JWT_SECRET`.
- The demo is **reset every night**: all users, accounts and transactions are deleted, the demo
  users are seeded again, and the search index is rebuilt.

## Troubleshooting

**"Out of capacity for shape VM.Standard.A1.Flex"**
Free ARM capacity is often exhausted in popular regions.
- Try every **availability domain** (AD-1/2/3) on the create page.
- Try again at another time of day; capacity frees up regularly.
- Start smaller (1 OCPU / 6 GB) and resize later under **Edit → Shape**.
- Upgrading to *Pay As You Go* keeps the Always Free resources free and usually unlocks capacity.
  Set a budget alert to be safe.
- Your **home region** cannot be changed after sign-up, so pick a less busy one when registering.

**Elasticsearch restarts with `max virtual memory areas vm.max_map_count [65530] is too low`**
```bash
sudo sysctl -w vm.max_map_count=262144
echo 'vm.max_map_count=262144' | sudo tee /etc/sysctl.d/99-elasticsearch.conf
docker compose -f docker-compose.prod.yml up -d elasticsearch
```

**The browser cannot connect, or the certificate is not issued** (`docker compose … logs caddy`)
- Does `nslookup <DOMAIN>` return the VM's public IP?
- Is the OCI **Security List** open for TCP 80 and 443 (step 3)?
- Is the VM firewall open? `sudo iptables -L INPUT -n --line-numbers` should show ACCEPT rules
  for 80/443 **above** the REJECT rule. Re-run `sudo ./deploy/setup-server.sh` if not.
- Let's Encrypt rate-limits repeated failures. Fix the cause, wait a few minutes, then
  `docker compose -f docker-compose.prod.yml restart caddy`.

**A build is killed or the VM freezes during the build**
Check that swap is active with `swapon --show`. Then build one image at a time:
`docker compose -f docker-compose.prod.yml build transaction-service`, and so on.

**A service stays `unhealthy`**
Look at `docker compose -f docker-compose.prod.yml logs <service>`. processing-service waits for
transaction-service, which creates the shared tables. Kibana needs about 2 minutes on first start.

**Kibana shows "Kibana server is not ready yet"**
Wait a minute. It starts after Elasticsearch, and the `kibana-init` job imports the dashboard once
Kibana is up. Check it with `docker compose -f docker-compose.prod.yml logs kibana-init`.

**Test the production stack locally**, without a domain or certificates:
```bash
cp .env.prod.example .env
# set DOMAIN=localhost, SITE_ADDRESS=http://localhost, PUBLIC_ORIGIN=http://localhost and the secrets
docker compose -f docker-compose.prod.yml up -d --build
# → http://localhost  and  http://localhost/kibana/
```

## Optional: deploy from GitHub Actions

`.github/workflows/deploy.yml` deploys over SSH, but it only runs manually (*Actions → Deploy →
Run workflow*) and only when the repository variable `ENABLE_DEPLOY` is `true`. To set it up:

1. **Settings → Secrets and variables → Actions → Variables:** `ENABLE_DEPLOY = true`
2. **Secrets:**
   - `DEPLOY_HOST` – the VM's IP or domain
   - `DEPLOY_USER` – `ubuntu`
   - `DEPLOY_SSH_KEY` – a **dedicated** private key whose public key is in `~/.ssh/authorized_keys` on the VM
   - `DEPLOY_PATH` – optional, defaults to `~/bank-transaction-platform`
