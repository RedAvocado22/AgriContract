#!/usr/bin/env python3
"""Smoke test of self-registration (D47): registers through the Keycloak form like a browser, then
exchanges the code for a token. Example: python3 backend/infra/keycloak/smoke-registration.py http://localhost:8180 a@example.test SELLER

Prints the realm roles of the first access token; exit 1 when the form refuses the registration."""
import base64, hashlib, html, json, re, secrets, sys, urllib.parse
import http.cookiejar, urllib.request

base, email = sys.argv[1], sys.argv[2]
account_type = sys.argv[3] if len(sys.argv) > 3 else None
realm = f"{base}/realms/agricontract/protocol/openid-connect"
redirect = "http://localhost:5173/callback"
jar = http.cookiejar.CookieJar()
class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, *a, **k): return None
opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(jar), NoRedirect)

verifier = secrets.token_urlsafe(48)
challenge = base64.urlsafe_b64encode(hashlib.sha256(verifier.encode()).digest()).rstrip(b"=").decode()
q = urllib.parse.urlencode({"client_id": "agricontract-frontend", "response_type": "code", "scope": "openid",
                            "redirect_uri": redirect, "code_challenge": challenge, "code_challenge_method": "S256"})
page = opener.open(f"{realm}/registrations?{q}").read().decode()
action = html.unescape(re.search(r'id="kc-register-form"[^>]*action="([^"]+)"', page, re.S).group(1))
fields = re.findall(r'name="([^"]+)"', page)
print("form fields:", sorted(set(f for f in fields if f not in ("register",))))
data = {"email": email, "username": email, "password": "Passw0rd!x", "password-confirm": "Passw0rd!x",
        "firstName": "Test", "lastName": "User"}
if account_type is not None:
    data["accountType"] = account_type
try:
    resp = opener.open(urllib.request.Request(action, urllib.parse.urlencode(data).encode()))
    body = resp.read().decode()
    errs = re.findall(r'(?:kc-feedback-text|input-error[^"]*)"[^>]*>\s*([^<]+)', body)
    print("REGISTER FAILED", resp.status, [e.strip() for e in errs][:5])
    sys.exit(1)
except urllib.error.HTTPError as e:
    loc = e.headers.get("Location", "")
    if e.code != 302 or "code=" not in loc:
        print("REGISTER FAILED", e.code, loc[:200]); sys.exit(1)
code = urllib.parse.parse_qs(urllib.parse.urlparse(loc).query)["code"][0]
tok = json.load(urllib.request.urlopen(f"{realm}/token", urllib.parse.urlencode({
    "grant_type": "authorization_code", "client_id": "agricontract-frontend", "code": code,
    "redirect_uri": redirect, "code_verifier": verifier}).encode()))
payload = tok["access_token"].split(".")[1]; payload += "=" * (-len(payload) % 4)
claims = json.loads(base64.urlsafe_b64decode(payload))
print("REGISTERED", email, "roles:", sorted(r for r in claims.get("realm_access", {}).get("roles", [])
      if r in ("BUYER", "SELLER", "ADMIN", "OPERATOR", "ARBITRATOR", "INSPECTOR")), "email_in_token:", claims.get("email"))
