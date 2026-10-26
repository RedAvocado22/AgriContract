(() => {
  "use strict";

  const root = document.documentElement;
  const $ = (selector, scope = document) => scope.querySelector(selector);
  const $$ = (selector, scope = document) => [...scope.querySelectorAll(selector)];
  const systemTheme = matchMedia("(prefers-color-scheme: dark)");
  const reducedMotion = matchMedia("(prefers-reduced-motion: reduce)");
  const themeNames = { system: "Hệ thống", light: "Sáng", dark: "Tối" };
  let theme = "system";
  try { theme = localStorage.getItem("design-theme") || "system"; } catch (_) { /* file:// may deny storage. */ }
  if (!(theme in themeNames)) theme = "system";

  function setTheme(value) {
    theme = value;
    if (value === "system") root.removeAttribute("data-theme");
    else root.dataset.theme = value;
    $$(".theme-toggle").forEach(button => {
      button.textContent = `Giao diện: ${themeNames[value]}`;
      button.setAttribute("aria-label", `Giao diện: ${themeNames[value]}. Bấm để đổi sáng, tối, hệ thống.`);
    });
    try { localStorage.setItem("design-theme", value); } catch (_) { /* Reading remains available. */ }
    renderFlows();
  }

  let toastTimer;
  function announce(message) {
    const toast = $(".toast");
    if (!toast) return;
    clearTimeout(toastTimer);
    toast.textContent = message;
    toast.hidden = false;
    toastTimer = setTimeout(() => { toast.hidden = true; }, 1500);
  }

  async function copyText(text, button) {
    let copied = false;
    try {
      await navigator.clipboard.writeText(text);
      copied = true;
    } catch (_) {
      const input = document.createElement("textarea");
      input.className = "sr-only";
      input.value = text;
      input.setAttribute("aria-label", "Nội dung cần sao chép");
      document.body.append(input);
      input.select();
      try { copied = document.execCommand("copy"); } catch (_) { /* Select source below. */ }
      if (copied) {
        input.remove();
        button.focus({ preventScroll: true });
      } else {
        input.className = "copy-fallback";
        input.readOnly = true;
        input.rows = 3;
        input.focus();
        input.select();
        input.addEventListener("blur", () => input.remove(), { once: true });
        input.addEventListener("keydown", event => {
          if (event.key === "Escape") { input.remove(); button.focus({ preventScroll: true }); }
        });
      }
    }
    announce(copied ? "Đã copy" : "Đã chọn nội dung. Nhấn Ctrl/Cmd+C để sao chép, Esc để đóng.");
    if (copied) {
      const label = button.dataset.copyLabel || button.textContent;
      button.dataset.copyLabel = label;
      button.textContent = "Đã copy";
      clearTimeout(button.copyTimer);
      button.copyTimer = setTimeout(() => { button.textContent = label; }, 1500);
    }
  }

  $$(".code").forEach(figure => {
    const code = $("pre code", figure);
    if (!code) return;
    if (figure.dataset.lang === "sql") {
      const source = code.textContent;
      const parts = source.split(/(--[^\n]*)/g);
      code.replaceChildren(...parts.map((part, index) => {
        if (index % 2 === 0) return document.createTextNode(part);
        const comment = document.createElement("span");
        comment.className = "code-comment";
        comment.textContent = part;
        return comment;
      }));
    }
    const caption = document.createElement("figcaption");
    const language = document.createElement("span");
    language.textContent = figure.dataset.lang || "text";
    const button = document.createElement("button");
    button.type = "button";
    button.textContent = "Copy";
    button.setAttribute("aria-label", `Sao chép code ${language.textContent}`);
    button.addEventListener("click", () => copyText(code.textContent, button));
    caption.append(language, button);
    figure.prepend(caption);
  });
  $$(".anchor").forEach(anchor => anchor.addEventListener("click", event => {
    event.preventDefault();
    copyText(anchor.href, anchor);
  }));
  $$("code").filter(code => !code.closest("pre")).forEach(code => {
    const value = code.textContent.trim();
    if (/^(?:\/|(?:GET|POST|PUT|PATCH|DELETE|HEAD|OPTIONS)\s)/.test(value)) code.classList.add("tok-endpoint");
    else if (/^[A-Z][A-Z0-9]*(?:_[A-Z0-9]+)+$/.test(value)) code.classList.add("tok-enum");
    else if (/^[a-z][\w-]*\.[a-z*][\w.*]*(?:\s|$)/.test(value) && !/\.(?:md|html|docx|yaml|json)$/.test(value)) code.classList.add("tok-event");
  });

  const toc = $(".toc");
  const toggle = $(".mobile-toc-toggle");
  const backdrop = $(".drawer-backdrop");
  const mobile = matchMedia("(max-width: 767px)");
  let previouslyFocused;
  function closeDrawer(restoreFocus = true) {
    if (!toc) return;
    const wasOpen = toc.classList.contains("is-open");
    toc.classList.remove("is-open");
    backdrop?.classList.remove("is-open");
    document.body.classList.remove("drawer-open");
    toggle?.setAttribute("aria-expanded", "false");
    toc.removeAttribute("aria-modal");
    toc.setAttribute("role", "navigation");
    $$("[data-drawer-inert]").forEach(node => { node.inert = false; node.removeAttribute("data-drawer-inert"); });
    if (wasOpen && restoreFocus) previouslyFocused?.focus();
  }
  toggle?.addEventListener("click", () => {
    previouslyFocused = document.activeElement;
    toc.classList.add("is-open");
    toc.setAttribute("role", "dialog");
    toc.setAttribute("aria-modal", "true");
    backdrop?.classList.add("is-open");
    document.body.classList.add("drawer-open");
    toggle.setAttribute("aria-expanded", "true");
    $$(".topbar, .content, .meta-rail").forEach(node => { node.inert = true; node.dataset.drawerInert = ""; });
    $(".toc__close", toc)?.focus();
  });
  $(".toc__close")?.addEventListener("click", () => closeDrawer());
  backdrop?.addEventListener("click", () => closeDrawer());
  mobile.addEventListener("change", () => closeDrawer());
  $$(".toc a").forEach(link => link.addEventListener("click", () => {
    closeDrawer(false);
    const target = document.getElementById(link.hash.slice(1));
    if (target) {
      target.setAttribute("tabindex", "-1");
      target.focus({ preventScroll: true });
    }
  }));
  document.addEventListener("keydown", event => {
    if (!toc?.classList.contains("is-open")) return;
    if (event.key === "Escape") { event.preventDefault(); closeDrawer(); }
    if (event.key === "Tab") {
      const items = $$("a[href], button", toc).filter(item => item.getClientRects().length);
      const first = items[0];
      const last = items[items.length - 1];
      if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last.focus(); }
      else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus(); }
    }
  });

  const sections = $$("section[id^='sec-']");
  const tocLinks = new Map($$(".toc a[href^='#sec-']").map(link => [link.hash.slice(1), link]));
  let activeId;
  function spy() {
    const offset = ($(".topbar")?.getBoundingClientRect().height || 0) + 64;
    let current = sections[0];
    for (const section of sections) {
      if (section.getBoundingClientRect().top <= offset) current = section;
      else break;
    }
    if (!current || activeId === current.id) return;
    activeId = current.id;
    tocLinks.forEach((link, id) => id === activeId ? link.setAttribute("aria-current", "location") : link.removeAttribute("aria-current"));
    const link = tocLinks.get(activeId);
    if (link && toc && !mobile.matches) {
      const frame = toc.getBoundingClientRect();
      const rect = link.getBoundingClientRect();
      if (rect.top < frame.top || rect.bottom > frame.bottom) toc.scrollTop += rect.top - frame.top - toc.clientHeight / 2;
    }
  }
  if (sections.length && "IntersectionObserver" in window) {
    const observer = new IntersectionObserver(spy, { rootMargin: "-12% 0px -65% 0px", threshold: [0, 1] });
    sections.forEach(section => observer.observe(section));
  }
  let spyQueued = false;
  window.addEventListener("scroll", () => {
    if (spyQueued) return;
    spyQueued = true;
    requestAnimationFrame(() => { spy(); spyQueued = false; });
  }, { passive: true });
  spy();

  $$("[data-details]").forEach(button => button.addEventListener("click", () => {
    $$("details").forEach(details => { details.open = button.dataset.details === "open"; });
  }));

  const normalize = value => value.normalize("NFD").replace(/[\u0300-\u036f]/g, "").replace(/đ/gi, "d").toLowerCase();
  const search = $("#page-search");
  const resultCount = $("#search-count");
  const headings = $$(".section > h2, .section > h3, .section > h4");
  let results = [];
  let resultIndex = -1;
  function jumpToResult() {
    $$(".search-hit").forEach(heading => heading.classList.remove("search-hit"));
    if (!results.length) return;
    resultIndex = (resultIndex + 1) % results.length;
    const heading = results[resultIndex];
    for (let parent = heading.parentElement; parent; parent = parent.parentElement) {
      if (parent.tagName === "DETAILS") parent.open = true;
    }
    heading.classList.add("search-hit");
    heading.scrollIntoView({ block: "start", behavior: reducedMotion.matches ? "instant" : "smooth" });
    resultCount.textContent = `${resultIndex + 1}/${results.length}`;
    resultCount.setAttribute("aria-label", `Kết quả ${resultIndex + 1} trên ${results.length}`);
  }
  search?.addEventListener("input", () => {
    const value = normalize(search.value.trim());
    results = value ? headings.filter(heading => normalize(heading.textContent).includes(value)) : [];
    resultIndex = -1;
    resultCount.textContent = value ? `0/${results.length}` : "";
    resultCount.setAttribute("aria-label", value ? `${results.length} mục phù hợp. Nhấn Enter để tới mục.` : "");
    $$(".search-hit").forEach(heading => heading.classList.remove("search-hit"));
    tocLinks.forEach(link => { link.hidden = Boolean(value) && !normalize(link.textContent).includes(value); });
  });
  search?.addEventListener("keydown", event => {
    if (event.key === "Enter") { event.preventDefault(); jumpToResult(); }
    if (event.key === "Escape") { search.value = ""; search.dispatchEvent(new Event("input")); search.blur(); }
  });
  document.addEventListener("keydown", event => {
    if (event.key !== "/" || event.ctrlKey || event.metaKey || event.altKey || event.target.closest("input, textarea, select, [contenteditable='true']") || toc?.classList.contains("is-open")) return;
    const target = search || $("#doc-filter");
    if (target) { event.preventDefault(); target.focus(); }
  });

  const filter = $("#doc-filter");
  filter?.addEventListener("input", () => {
    const value = normalize(filter.value.trim());
    const rows = $$(".doc-row");
    rows.forEach(row => { row.hidden = !normalize($(".doc-row__main", row).textContent).includes(value); });
    $$(".catalog-group").forEach(group => { group.hidden = !$(".doc-row:not([hidden])", group); });
    const count = rows.filter(row => !row.hidden).length;
    $("#doc-count").textContent = `${count} / ${rows.length} tài liệu`;
    $("#no-results").hidden = count !== 0;
  });

  // Preview links retain their final HTML destination and offer a working MD fallback.
  $$('a[data-pending][data-source]').forEach(link => link.addEventListener("click", event => {
    if (event.ctrlKey || event.metaKey || event.shiftKey || event.altKey) return;
    event.preventDefault();
    window.location.href = link.dataset.source;
  }));

  let mermaidPromise;
  let flowRevision = 0;
  let flowQueue = Promise.resolve();
  const flowSources = new Map($$(".flow pre.mermaid").map(node => [node, node.textContent]));
  function loadMermaid() {
    if (!mermaidPromise) mermaidPromise = new Promise((resolve, reject) => {
      if (window.mermaid) { resolve(window.mermaid); return; }
      const script = document.createElement("script");
      script.src = "https://cdn.jsdelivr.net/npm/mermaid@10.9.1/dist/mermaid.min.js";
      script.onload = () => resolve(window.mermaid);
      script.onerror = reject;
      document.head.append(script);
    });
    return mermaidPromise;
  }
  function renderFlows() {
    if (!flowSources.size) return;
    const revision = ++flowRevision;
    flowQueue = flowQueue.then(async () => {
      if (revision !== flowRevision) return;
      try {
        const mermaid = await loadMermaid();
        if (revision !== flowRevision) return;
        const css = getComputedStyle(root);
        const token = name => css.getPropertyValue(name).trim();
        mermaid.initialize({ startOnLoad: false, securityLevel: "strict", theme: "base", themeVariables: {
          darkMode: theme === "dark" || (theme === "system" && systemTheme.matches),
          background: token("--surface"), primaryColor: token("--surface"), primaryTextColor: token("--ink"),
          primaryBorderColor: token("--accent"), lineColor: token("--ink-2"), secondaryColor: token("--code-bg"),
          actorBkg: token("--surface"), actorBorder: token("--accent"), actorTextColor: token("--ink"),
          signalColor: token("--ink-2"), signalTextColor: token("--ink"), labelBoxBkgColor: token("--code-bg"),
          labelTextColor: token("--ink"), loopTextColor: token("--ink"), noteBkgColor: token("--code-bg"),
          noteTextColor: token("--ink"), noteBorderColor: token("--line"), fontFamily: token("--font-body")
        } });
        let index = 0;
        for (const [node, source] of flowSources) {
          const { svg } = await mermaid.render(`flow-${revision}-${index++}`, source);
          if (revision !== flowRevision) return;
          let diagram = $(".flow__diagram", node.parentElement);
          if (!diagram) { diagram = document.createElement("div"); diagram.className = "flow__diagram"; node.before(diagram); }
          diagram.innerHTML = svg;
          diagram.setAttribute("role", "img");
          diagram.setAttribute("aria-label", node.parentElement.getAttribute("aria-label") || "Sơ đồ luồng event");
          node.hidden = true;
          $(".flow__status", node.parentElement)?.remove();
        }
      } catch (_) {
        for (const [node] of flowSources) {
          node.hidden = false;
          $(".flow__diagram", node.parentElement)?.remove();
          if (!$(".flow__status", node.parentElement)) {
            const note = document.createElement("p");
            note.className = "flow__status";
            note.textContent = "Không tải được sơ đồ; nội dung Mermaid vẫn hiển thị để tra cứu.";
            node.before(note);
          }
        }
      }
    });
  }
  $$(".theme-toggle").forEach(button => button.addEventListener("click", () => {
    const order = ["system", "light", "dark"];
    setTheme(order[(order.indexOf(theme) + 1) % order.length]);
  }));
  systemTheme.addEventListener("change", () => { if (theme === "system") renderFlows(); });
  setTheme(theme);
})();
