import { existsSync, mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { randomUUID } from "node:crypto";

// Standalone Keycloakify test realm only. Never imports or modifies backend/infra.
const root = new URL("../", import.meta.url);
const destination = new URL(".keycloakify/realm-kc-24.json", root);
if (existsSync(destination)) {
  console.log("Giữ nguyên realm kiểm thử đã có trong .keycloakify/.");
  process.exit(0);
}
const realm = JSON.parse(
  readFileSync(
    new URL(
      "node_modules/keycloakify/src/bin/start-keycloak/realmConfig/defaultConfig/realm-kc-24.json",
      root,
    ),
    "utf8",
  ),
);
Object.assign(realm, {
  registrationAllowed: true,
  registrationEmailAsUsername: true,
  loginWithEmailAllowed: true,
  resetPasswordAllowed: true,
  rememberMe: true,
  internationalizationEnabled: true,
  supportedLocales: ["vi"],
  defaultLocale: "vi",
  passwordPolicy: "length(8) and digits(1)",
  smtpServer: {
    host: "host.docker.internal",
    port: "1026",
    from: "no-reply@agricontract.test",
    auth: "false",
    ssl: "false",
    starttls: "false",
  },
  identityProviders: [],
});
for (const component of realm.components[
  "org.keycloak.userprofile.UserProfileProvider"
] ?? []) {
  const profile = JSON.parse(component.config["kc.user.profile.config"][0]);
  profile.attributes = profile.attributes.filter((attribute) =>
    ["username", "email", "firstName", "lastName"].includes(attribute.name),
  );
  for (const attribute of profile.attributes) {
    if (attribute.name !== "email") delete attribute.required;
    if (attribute.name === "email") delete attribute.validations.pattern;
  }
  profile.groups = [];
  component.config["kc.user.profile.config"] = [JSON.stringify(profile)];
}
for (const user of realm.users) {
  user.email = `${user.username}@agricontract.test`;
  user.requiredActions = [];
  user.attributes = { locale: ["vi"] };
}
for (const action of realm.requiredActions) {
  if (action.alias === "TERMS_AND_CONDITIONS") action.defaultAction = false;
}
realm.clients.push({
  id: randomUUID(),
  clientId: "theme-smoke",
  protocol: "openid-connect",
  enabled: true,
  publicClient: true,
  standardFlowEnabled: true,
  directAccessGrantsEnabled: false,
  redirectUris: ["http://localhost:5174/"],
  webOrigins: ["http://localhost:5174"],
});
mkdirSync(new URL(".keycloakify/", root), { recursive: true });
writeFileSync(destination, JSON.stringify(realm, null, 2));
console.log(`Đã tạo realm kiểm thử cục bộ: ${fileURLToPath(destination)}`);
