import { fileURLToPath, URL } from "node:url";
import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import tailwindcss from "@tailwindcss/vite";
import { keycloakify } from "keycloakify/vite-plugin";

export default defineConfig({
  plugins: [
    react(),
    tailwindcss(),
    keycloakify({
      themeName: "agricontract",
      accountThemeImplementation: "none",
      keycloakVersionTargets: { "22-to-25": true, "all-other-versions": false },
      startKeycloakOptions: {
        dockerImage: "quay.io/keycloak/keycloak:24.0.4",
        port: 8181,
      },
    }),
  ],
  resolve: { alias: { "@": fileURLToPath(new URL("./src", import.meta.url)) } },
});
