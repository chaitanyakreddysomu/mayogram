/* Mayogram access-code admin page.
 *
 * Talks to Supabase through the admin_* RPC functions only. The admin password
 * is held in sessionStorage for the tab's lifetime and sent with each call; it
 * is never written to disk.
 */

(function () {
  "use strict";

  var cfg = window.MAYOGRAM_CONFIG || {};
  var PASSWORD_KEY = "mayogram_admin_password";

  var el = {
    lock: document.getElementById("lock"),
    app: document.getElementById("app"),
    password: document.getElementById("password"),
    unlock: document.getElementById("unlock"),
    lockError: document.getElementById("lockError"),
    generate: document.getElementById("generate"),
    refresh: document.getElementById("refresh"),
    lockBtn: document.getElementById("lockBtn"),
    count: document.getElementById("count"),
    rows: document.getElementById("rows"),
    empty: document.getElementById("empty"),
    banner: document.getElementById("banner"),
    summary: document.getElementById("summary")
  };

  function password() {
    try { return sessionStorage.getItem(PASSWORD_KEY) || ""; } catch (e) { return ""; }
  }

  function setPassword(value) {
    try {
      if (value === null) sessionStorage.removeItem(PASSWORD_KEY);
      else sessionStorage.setItem(PASSWORD_KEY, value);
    } catch (e) { /* private mode - the page still works for this session */ }
  }

  /* Calls a Postgres function via PostgREST. */
  function rpc(fn, body) {
    return fetch(cfg.SUPABASE_URL + "/rest/v1/rpc/" + fn, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        apikey: cfg.SUPABASE_ANON_KEY,
        Authorization: "Bearer " + cfg.SUPABASE_ANON_KEY
      },
      body: JSON.stringify(body)
    }).then(function (res) {
      return res.text().then(function (text) {
        var data = null;
        try { data = text ? JSON.parse(text) : null; } catch (e) { /* non-JSON error body */ }
        if (!res.ok) {
          var message = (data && (data.message || data.hint)) || text || ("HTTP " + res.status);
          var err = new Error(message);
          err.unauthorized = /unauthorized/i.test(message);
          throw err;
        }
        return data;
      });
    });
  }

  function banner(message, bad) {
    el.banner.textContent = message;
    el.banner.classList.toggle("bad", !!bad);
    el.banner.hidden = false;
    if (!bad) {
      clearTimeout(banner._t);
      banner._t = setTimeout(function () { el.banner.hidden = true; }, 4000);
    }
  }

  function formatDate(value) {
    if (!value) return "—";
    var d = new Date(value);
    if (isNaN(d)) return "—";
    return d.toLocaleString(undefined, {
      year: "numeric", month: "short", day: "numeric",
      hour: "2-digit", minute: "2-digit"
    });
  }

  function cell(text, className) {
    var td = document.createElement("td");
    td.textContent = text == null || text === "" ? "—" : text;
    if (className) td.className = className;
    if (text == null || text === "") td.classList.add("dim");
    return td;
  }

  function render(list) {
    el.rows.textContent = "";
    el.empty.hidden = list.length > 0;

    var counts = { active: 0, used: 0, revoked: 0 };

    list.forEach(function (row) {
      counts[row.status] = (counts[row.status] || 0) + 1;

      var tr = document.createElement("tr");

      var tokenTd = document.createElement("td");
      tokenTd.className = "token";
      tokenTd.textContent = row.access_token;
      tokenTd.title = "Click to copy";
      tokenTd.addEventListener("click", function () {
        navigator.clipboard.writeText(row.access_token).then(function () {
          banner("Copied " + row.access_token);
        }).catch(function () {
          banner("Could not copy to clipboard", true);
        });
      });
      tr.appendChild(tokenTd);

      var statusTd = document.createElement("td");
      var pill = document.createElement("span");
      pill.className = "pill " + row.status;
      pill.textContent = row.status;
      statusTd.appendChild(pill);
      tr.appendChild(statusTd);

      tr.appendChild(cell(row.device_id));
      tr.appendChild(cell(row.installation_id));
      tr.appendChild(cell(
        row.telegram_username ? "@" + row.telegram_username
          : (row.telegram_user_id ? String(row.telegram_user_id) : "")
      ));
      tr.appendChild(cell(formatDate(row.created_at)));
      tr.appendChild(cell(formatDate(row.used_at)));

      var actionTd = document.createElement("td");
      if (row.status === "active") {
        var revoke = document.createElement("button");
        revoke.className = "btn ghost";
        revoke.textContent = "Revoke";
        revoke.addEventListener("click", function () {
          if (!confirm("Revoke " + row.access_token + "? It can no longer be redeemed.")) return;
          revoke.disabled = true;
          rpc("admin_revoke_token", { p_password: password(), p_token: row.access_token })
            .then(function () { banner("Revoked " + row.access_token); return load(); })
            .catch(function (e) { revoke.disabled = false; fail(e); });
        });
        actionTd.appendChild(revoke);
      }
      tr.appendChild(actionTd);

      el.rows.appendChild(tr);
    });

    el.summary.textContent = list.length + " total · " +
      (counts.active || 0) + " active · " +
      (counts.used || 0) + " used · " +
      (counts.revoked || 0) + " revoked";
  }

  function fail(error) {
    if (error && error.unauthorized) {
      setPassword(null);
      showLock("Wrong admin password.");
      return;
    }
    banner((error && error.message) || "Connection error. Please try again.", true);
  }

  function load() {
    return rpc("admin_list_tokens", { p_password: password() })
      .then(function (data) { render(data || []); })
      .catch(function (e) { fail(e); throw e; });
  }

  function showApp() {
    el.lock.hidden = true;
    el.app.hidden = false;
    load().catch(function () { /* fail() already reported it */ });
  }

  function showLock(message) {
    el.app.hidden = true;
    el.lock.hidden = false;
    el.lockError.textContent = message || "";
    el.lockError.hidden = !message;
    el.password.value = "";
    el.password.focus();
  }

  el.unlock.addEventListener("click", function () {
    var value = el.password.value;
    if (!value) { showLock("Enter the admin password."); return; }
    el.unlock.disabled = true;
    setPassword(value);
    // Listing doubles as the password check.
    rpc("admin_list_tokens", { p_password: value })
      .then(function (data) { el.unlock.disabled = false; showApp(); render(data || []); })
      .catch(function (e) {
        el.unlock.disabled = false;
        setPassword(null);
        showLock(e && e.unauthorized ? "Wrong admin password." : (e.message || "Connection error."));
      });
  });

  el.password.addEventListener("keydown", function (e) {
    if (e.key === "Enter") el.unlock.click();
  });

  el.generate.addEventListener("click", function () {
    var count = Math.max(1, Math.min(100, parseInt(el.count.value, 10) || 1));
    el.generate.disabled = true;
    rpc("admin_generate_tokens", { p_password: password(), p_count: count })
      .then(function (created) {
        el.generate.disabled = false;
        var tokens = (created || []).map(function (r) { return r.access_token; });
        banner(tokens.length === 1
          ? "Generated " + tokens[0]
          : "Generated " + tokens.length + " codes");
        return load();
      })
      .catch(function (e) { el.generate.disabled = false; fail(e); });
  });

  el.refresh.addEventListener("click", function () {
    el.refresh.disabled = true;
    load().catch(function () {}).then(function () { el.refresh.disabled = false; });
  });

  el.lockBtn.addEventListener("click", function () {
    setPassword(null);
    showLock("");
  });

  if (!cfg.SUPABASE_URL || !cfg.SUPABASE_ANON_KEY) {
    showLock("config.js is missing the Supabase URL or anon key.");
  } else if (password()) {
    showApp();
  } else {
    showLock("");
  }
})();
