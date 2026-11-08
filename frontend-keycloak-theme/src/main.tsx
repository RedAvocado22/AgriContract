import { createRoot } from "react-dom/client";
import { StrictMode } from "react";
import { KcPage } from "./kc.gen";
import { themeMessages } from "./login/messages";

async function bootstrap() {
  // Preview fixtures are not included in the production build.
  if (import.meta.env.DEV && !window.kcContext) {
    const { installPreview } = await import("./preview");
    installPreview();
  }
  createRoot(document.getElementById("root")!).render(
    <StrictMode>
      {!window.kcContext ? (
        <h1 lang="vi">{themeMessages.missingContext}</h1>
      ) : (
        <KcPage kcContext={window.kcContext} />
      )}
    </StrictMode>,
  );
}
void bootstrap();
