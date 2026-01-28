🌐 Languages: [󠁧󠁢󠁥󠁮󠁧󠁿**English**](README.md) | [**Русский**](README.ru.md)

# DNS Block&Redirect Configurer
**Allows to set Redirect and Block rules to your Cloudflare and NextDNS accounts.**

**Ready-to-run via GitHub Actions.** [Video guide](#step-by-step-video-guide-redirect-for-nextdns)

[General comparison: Cloudflare vs NextDNS](#cloudflare-vs-nextdns)

[Setup credentials: Cloudflare](#cloudflare-credentials-setup)

[Setup credentials: NextDNS](#nextdns-credentials-setup)

[Setup profile](#setup-profile)

[Setup data sources](#setup-data-sources)

[GitHub Actions](#github-actions-setup)

## Cloudflare vs NextDNS

Both providers have free plans, but there are some limitations

### Cloudflare limitations
+ 100 000 DNS requests per day
+ Ipv4 DNS requests are restricted by the only one IP. But you are free to use other methods: DoH, DoT, Ipv6
### NextDNS limitations
+ 300 000 DNS requests per month (still more than enough for personal use)
+ Slow API speed is restricted by 60 requests per minute. Takes significantly more time for script to save settings

### Cloudflare credentials setup
1) After signing up into a **Cloudflare**, navigate to _Zero Trust_ tab and create an account.
- Free Plan has decent limits, so just choose it.
- Skip providing payment method step by choosing _Cancel and exit_ (top right corner)
- Go back to _Zero Trust_ tab

2) Create a **Cloudflare API token**, from https://dash.cloudflare.com/profile/api-tokens

with 2 permissions:

    Account.Zero Trust : Edit

    Account.Account Firewall Access Rules : Edit

Set API token to **environment variable** `AUTH_SECRET`

3) Get your **Account ID** from : https://dash.cloudflare.com/?to=/:account/workers

Set **Account ID** to **environment variable** `CLIENT_ID`

### NextDNS credentials setup
1) Generate **API KEY**, from https://my.nextdns.io/account and set as **environment variable** `AUTH_SECRET`

2) Click on **NextDNS** logo. On the opened page, copy ID from Endpoints section.
   Set it as **environment variable** `CLIENT_ID`


## Setup profile
Set **environment variable** `DNS` with DNS provider name (**Cloudflare** or **NextDNS**)

## Setup data sources
Each data source must be a link to a hosts file, e.g. https://raw.githubusercontent.com/Internet-Helper/GeoHideDNS/refs/heads/main/hosts/hosts

You can provide multiple sources split by coma:
https://first.com/hosts,https://second.com/hosts

### 1) Setup Redirects
Set sources to **environment variable** `REDIRECT`

Script will parse sources, filtering out redirects to `0.0.0.0` and `127.0.0.1`

Thus, parsing lines:

    0.0.0.0 domain.to.block
    1.2.3.4 domain.to.redirect
    127.0.0.1 another.to.block

will keep only `1.2.3.4 domain.to.redirect` for the further redirect processing.


+ Redirect priority follows sources order. If domain appears more than one time, the first only IP will be applied.


### 2) Setup Blocklist
Set sources to **environment variable** `BLOCK`

Script will parse sources, keeping only redirects to `0.0.0.0` and `127.0.0.1`.

Thus, parsing lines

    0.0.0.0 domain.to.block
    1.2.3.4 domain.to.redirect
    127.0.0.1 another.to.block

will keep only `domain.to.block` and `another.to.block` for the further block processing.

+ You may want to provide the same source for both `BLOCK` and `REDIRECT` for **Cloudflare**.
+ For **NextDNS**, the best option might be to set `REDIRECT` only, and then manually choose any blocklists at the _Privacy_ tab.

## Script Behaviour
### Cloudflare
Previously generated data will be removed. Script recognizes old data by marks:


+ Name prefix for List: **_Blocked websites by script_** and **_Override websites by script_**
+ Name prefix for Rule: **_Rules set by script_**
+ Different **_Session id_**. **_Session id_** is stored in a description field.


After removing old data, new lists and rules will be generated and applied.

If you want to clear **Cloudflare** block/redirect settings, launch the script without providing sources in related **environment variables**. E.g. providing no value for **environment variable** `BLOCK` will cause removing old related data: lists and rules used to setup blocks.

### NextDNS

For `REDIRECT`:
+ Existing domain will be updated if redirect IP has changed
+ If new domains are provided, they will be added
+ The rest redirect settings are kept untouched

For `BLOCK`:
+ If new domains are provided, they will be added
+ The rest block settings are kept untouched

Previously generated data is removed **ONLY** when both `BLOCK` and `REDIRECT` sources were not provided.

## Advanced Features

### EXTERNAL_IP - Override All IP Addresses

If you want to redirect all domains from hosts files to a single IP address (e.g., your own server), you can use the `EXTERNAL_IP` environment variable.

Set **environment variable** `EXTERNAL_IP` with your target IP address (e.g., `10.20.30.40`)

**How it works:**
- When `EXTERNAL_IP` is set, all IP addresses from the hosts files will be replaced with the specified IP
- When `EXTERNAL_IP` is not set or empty, original IP addresses from hosts files will be used
- The script will log each processed domain showing which IP is being used

**Example:**

If your hosts file contains:
```
1.2.3.4 example.com
5.6.7.8 domain.org
```

And `EXTERNAL_IP=10.20.30.40`, both domains will be redirected to `10.20.30.40`

Console output will show:
```
>>> Processing: example.com -> 10.20.30.40 (IP changed from 1.2.3.4 to 10.20.30.40)
>>> Processing: domain.org -> 10.20.30.40 (IP changed from 5.6.7.8 to 10.20.30.40)
```

### FORCE_REWRITE - Force Complete Rewrite (NextDNS only)

By default, the script works in incremental mode for NextDNS:
- Only new domains are added
- Only domains with changed IPs are updated
- Existing settings remain untouched

If you want to force a complete rewrite of all NextDNS settings, set **environment variable** `FORCE_REWRITE` to `true`

**How it works:**
- When `FORCE_REWRITE=true`, ALL existing rewrites and deny lists will be removed from NextDNS before adding new ones
- When `FORCE_REWRITE` is not set or set to any other value, the script works in incremental mode

**Use cases:**
- You want to ensure NextDNS exactly matches your hosts files
- You've made manual changes in NextDNS and want to reset everything
- You're troubleshooting and want a clean state

**⚠️ Warning:** This will remove ALL existing rewrites and deny lists from NextDNS, including manually added ones!

### Multiple NextDNS Profiles Support

You can configure multiple NextDNS profiles to be updated simultaneously. This is useful if you:
- Manage multiple NextDNS accounts
- Have multiple devices or locations with different NextDNS profiles
- Want to apply the same blocklists/redirects to different profiles

**Basic Configuration:**

Set multiple profile IDs in `CLIENT_ID` separated by commas:

```bash
CLIENT_ID=abc123,def456,ghi789
```

**API Keys Configuration:**

1. **Same API key for all profiles (same account):**
```bash
CLIENT_ID=abc123,def456,ghi789
AUTH_SECRET=your_api_key
```
All profiles belong to the same NextDNS account

2. **Different API keys for each profile (different accounts):**
```bash
CLIENT_ID=abc123,def456,ghi789
AUTH_SECRET=api_key_1,api_key_2,api_key_3
```
Each profile belongs to a different NextDNS account

**EXTERNAL_IP Configuration:**

1. **Same IP for all profiles:**
```bash
CLIENT_ID=abc123,def456,ghi789
AUTH_SECRET=key1,key2,key3
EXTERNAL_IP=10.20.30.40
```
All profiles redirect domains to `10.20.30.40`

2. **Different IP for each profile:**
```bash
CLIENT_ID=abc123,def456,ghi789
AUTH_SECRET=key1,key2,key3
EXTERNAL_IP=10.20.30.40,10.20.30.41,10.20.30.42
```
- Profile `abc123` uses API `key1` and redirects to `10.20.30.40`
- Profile `def456` uses API `key2` and redirects to `10.20.30.41`
- Profile `ghi789` uses API `key3` and redirects to `10.20.30.42`

3. **Mixed configuration (some with EXTERNAL_IP, some without):**
```bash
CLIENT_ID=abc123,def456,ghi789
AUTH_SECRET=key1,key2,key3
EXTERNAL_IP=10.20.30.40,,10.20.30.42
```
- Profile `abc123` uses `10.20.30.40`
- Profile `def456` uses IPs from hosts files
- Profile `ghi789` uses `10.20.30.42`

**How it works:**
- The script loads hosts files once (not per profile)
- Each profile is processed independently with its own API key
- If one profile fails, others will continue processing
- Final summary shows how many profiles succeeded/failed

**Benefits:**
- Update multiple profiles/accounts with one configuration
- Different API keys for different accounts
- Different EXTERNAL_IP per profile
- Atomic operation per profile (failure in one doesn't affect others)

## GitHub Actions setup

#### Step-by-step video guide: [REDIRECT for NextDNS](https://www.youtube.com/watch?v=vbAXM_xAL5I)

#### Steps

1) Fork repository
2) Go _Settings_ => _Environments_
3) Create _New environment_ with name `DNS`
4) Provide `AUTH_SECRET` and `CLIENT_ID` to **Environment secrets**
5) Provide `DNS`, `REDIRECT` and `BLOCK` to **Environment variables**
6) (Optional) Add `EXTERNAL_IP` to **Environment variables** if you want to override all IPs
7) (Optional) Add `FORCE_REWRITE` (value: `true`) to **Environment variables** if you want to force complete rewrite

+ The action will be launched every day at **01:30 UTC**. To set another time, change cron at `.github/workflows/github_action.yml`
+ You can run the action manually via `Run workflow` button: switch to _Actions_ tab and choose workflow named **DNS Block&Redirect Configurer cron task**