#!/usr/bin/env python3
"""Smoke test of "forgot password" (realm SMTP -> MailHog): asks a reset for USER through the login page, takes
the link from MailHog, sets NEW_PASSWORD, logs in with it, and checks the link cannot be used twice.
Example: python3 backend/infra/keycloak/smoke-reset-password.py http://localhost:8180 http://localhost:8025 a@example.test "N3w-pass!"
"""
import html, json, re, sys, time, urllib.parse, urllib.request, http.cookiejar, quopri
base, mh, user, newpw = sys.argv[1:5]
jar = http.cookiejar.CookieJar()
op = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(jar))
q = urllib.parse.urlencode({"client_id": "agricontract-frontend", "response_type": "code", "scope": "openid",
                            "redirect_uri": "http://localhost:5173/callback"})
page = op.open(f"{base}/realms/agricontract/protocol/openid-connect/auth?{q}").read().decode()
reset = html.unescape(re.search(r'href="([^"]*login-actions/reset-credentials[^"]*)"', page).group(1))
page = op.open(base + reset if reset.startswith("/") else reset).read().decode()
action = html.unescape(re.search(r'id="kc-reset-password-form"[^>]*action="([^"]+)"', page, re.S).group(1))
page = op.open(urllib.request.Request(action, urllib.parse.urlencode({"username": user}).encode())).read().decode()
print("after request:", re.findall(r'kc-feedback-text"?>\s*([^<]+)', page)[:1])
time.sleep(2)
msgs = json.load(urllib.request.urlopen(f"{mh}/api/v2/search?kind=to&query={urllib.parse.quote(user)}"))["items"]
if not msgs: print("NO MAIL"); sys.exit(1)
m = msgs[0]; body = quopri.decodestring(m["Content"]["Body"].encode()).decode("utf-8", "replace")
print("mail from:", m["Content"]["Headers"]["From"][0], "| subject:", m["Content"]["Headers"]["Subject"][0])
link = html.unescape(re.search(r'(http[^\s"<>]*action-token[^\s"<>]*)', body).group(1))
op2 = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
page = op2.open(link).read().decode()
if "kc-passwd-update-form" not in page:
    m2 = re.search(r'href="([^"]*action-token[^"]*)"', page)  # "click here to proceed" info page
    page = op2.open(html.unescape(m2.group(1))).read().decode()
action = html.unescape(re.search(r'id="kc-passwd-update-form"[^>]*action="([^"]+)"', page, re.S).group(1))
try:
    op2.open(urllib.request.Request(action, urllib.parse.urlencode({"password-new": newpw, "password-confirm": newpw}).encode()))
except urllib.error.HTTPError as e:
    pass
tok = urllib.request.urlopen(f"{base}/realms/agricontract/protocol/openid-connect/token", urllib.parse.urlencode({
    "client_id": "agricontract-frontend", "grant_type": "password", "username": user, "password": newpw}).encode())
print("login with new password:", "OK" if "access_token" in json.load(tok) else "FAIL")
try:
    op2.open(link).read(); print("reused link: page served (check content)")
except urllib.error.HTTPError as e:
    print("reused link:", e.code)
