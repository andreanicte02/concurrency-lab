const $ = (sel, root = document) => root.querySelector(sel);
const fmt = (n) => n.toLocaleString("en-US");
const money = (n) => (n < 0 ? "−$" : "$") + fmt(Math.abs(n));
const esc = (s) => String(s).replace(/[&<>"]/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" }[c]));

/* ---------------------------------------------------------------- tooltip */
const tip = $("#tip");
document.addEventListener("pointermove", (e) => {
  const el = e.target.closest("[data-tip]");
  if (!el) { tip.hidden = true; return; }
  tip.innerHTML = el.dataset.tip;
  tip.hidden = false;
  const pad = 14, w = tip.offsetWidth, h = tip.offsetHeight;
  let x = e.clientX + pad, y = e.clientY + pad;
  if (x + w > innerWidth - 8) x = e.clientX - w - pad;
  if (y + h > innerHeight - 8) y = e.clientY - h - pad;
  tip.style.left = x + "px";
  tip.style.top = y + "px";
});
document.addEventListener("pointerleave", () => (tip.hidden = true));
addEventListener("scroll", () => (tip.hidden = true), { passive: true });

/* --------------------------------------------------- interleaving stepper */
const stepper = (() => {
  const OPS = {
    plain: [["read", "reg = count"], ["add", "reg = reg + 1"], ["write", "count = reg"]],
    lock: [["lock", "lock.lock()"], ["read", "reg = count"], ["add", "reg = reg + 1"], ["write", "count = reg"], ["unlock", "lock.unlock()"]],
  };
  const threadEls = [...document.querySelectorAll(".thread")];
  const memEl = $("#mem"), verdictEl = $("#verdict"), lockEl = $("#lockstate"), lockBox = $("#useLock");
  let s, timer;

  function reset() {
    clearTimeout(timer);
    s = { mem: 0, owner: null, threads: [{ pc: 0, reg: null }, { pc: 0, reg: null }] };
    say("");
    render();
  }

  const ops = () => OPS[lockBox.checked ? "lock" : "plain"];
  const name = (t) => (t === 0 ? "Thread A" : "Thread B");

  function step(t) {
    const th = s.threads[t];
    const [op] = ops()[th.pc] ?? [];
    if (!op) return;
    if (op === "lock") {
      if (s.owner !== null && s.owner !== t) {
        say(`${name(t)} is blocked: ${name(s.owner)} holds the lock.`);
        return;
      }
      s.owner = t;
    }
    if (op === "read") th.reg = s.mem;
    if (op === "add") th.reg += 1;
    if (op === "write") { s.mem = th.reg; memEl.classList.remove("flash"); void memEl.offsetWidth; memEl.classList.add("flash"); }
    if (op === "unlock") s.owner = null;
    th.pc++;
    say("");
    render();
    const done = s.threads.every((x) => x.pc >= ops().length);
    if (done) {
      if (s.mem === 2) say("✓ count = 2. Both increments survived.", "ok");
      else say("✗ count = 1. Two increments ran, one was lost — that's the race.", "bad");
    }
  }

  function say(text, cls = "") {
    verdictEl.textContent = text;
    verdictEl.className = "verdict " + cls;
  }

  function render() {
    memEl.textContent = s.mem;
    lockEl.hidden = !lockBox.checked;
    lockEl.textContent = s.owner === null ? "🔓 lock free" : `🔒 held by ${name(s.owner)}`;
    threadEls.forEach((el, t) => {
      const th = s.threads[t];
      $(".reg b", el).textContent = th.reg ?? "–";
      $(".ops", el).innerHTML = ops()
        .map(([, code], i) => `<li class="${i < th.pc ? "done" : i === th.pc ? "next" : ""}">${esc(code)}</li>`)
        .join("");
      $(".step", el).disabled = th.pc >= ops().length;
    });
  }

  function play(schedule) {
    reset();
    const next = () => {
      if (!schedule.length) return;
      step(schedule.shift());
      timer = setTimeout(next, 650);
    };
    timer = setTimeout(next, 300);
  }

  threadEls.forEach((el, t) => $(".step", el).addEventListener("click", () => { clearTimeout(timer); step(t); }));
  lockBox.addEventListener("change", reset);
  $("#reset").addEventListener("click", reset);
  $("#showBug").addEventListener("click", () =>
    play(lockBox.checked
      ? [0, 1, 0, 1, 0, 0, 0, 1, 1, 1, 1, 1] // B keeps trying and gets blocked until A unlocks
      : [0, 1, 0, 1, 0, 1]));                // both read 0 before either writes
  reset();
})();

/* ------------------------------------------------------ real JVM results */
fetch("results.json", { cache: "no-cache" })
  .then((r) => { if (!r.ok) throw new Error(r.status); return r.json(); })
  .then(renderResults)
  .catch(() => { $("#meta").textContent = "Results unavailable — run `java -jar concurrency-lab.jar all --json > web/results.json`."; });

function renderResults(data) {
  const env = data.environment;
  const when = new Date(data.generatedAt).toLocaleString("en-US", { dateStyle: "medium", timeStyle: "short" });
  $("#meta").textContent = `Results from a real run on GitHub Actions · Java ${env.java} · ${env.os} · ${env.cpus} CPUs · ${when}`;
  renderCounter(data.counter);
  renderBank(data.bank);
  renderBarber(data.barber);
}

function renderCounter(results) {
  const r0 = results[0];
  $("#counterCaption").textContent =
    `${r0.threads} threads × ${fmt(r0.incrementsPerThread)} increments each. Every run should end at ${fmt(r0.expected)} (dashed line). Each bar is one run.`;
  let prev = null;
  const rows = results.map((r) => {
    const ok = r.actual === r.expected;
    const pct = (r.actual / r.expected) * 100;
    const label = r.counter === prev ? `<span class="rowlabel repeat">${r.counter}</span>` : `<span class="rowlabel">${r.counter}</span>`;
    const gap = prev && r.counter !== prev ? `<div class="group-gap"></div>` : "";
    prev = r.counter;
    const tipText = `<b>${esc(r.counter)}</b><br>counted ${fmt(r.actual)} of ${fmt(r.expected)}<br>${ok ? "no updates lost" : fmt(r.expected - r.actual) + " updates lost"} · ${r.millis} ms`;
    return `${gap}${label}
      <div class="track" data-tip="${esc(tipText)}"><div class="bar ${ok ? "ok" : "bad"}" style="width:${pct}%"></div><div class="ref" style="left:100%"></div></div>
      <span class="barval"><b>${fmt(r.actual)}</b> ${ok ? '<span class="ok">✓ exact</span>' : `<span class="bad">✗ ${fmt(r.expected - r.actual)} lost</span>`}</span>`;
  }).join("");
  const el = $("#counterChart");
  el.innerHTML = `<div class="bars">${rows}</div>`;
  el.setAttribute("aria-label", results.map((r) => `${r.counter}: ${r.actual} of ${r.expected}`).join("; "));
}

function renderBank(results) {
  const r0 = results[0];
  $("#bankCaption").textContent =
    `${r0.customers} customers try to withdraw ${money(r0.amount)} at a time from an account holding ${money(r0.initialBalance)} (dashed line). Anything past the line is money the bank didn't have.`;
  const max = Math.max(r0.initialBalance, ...results.map((r) => r.successfulWithdrawals * r.amount)) * 1.04;
  let prev = null;
  const rows = results.map((r) => {
    const out = r.successfulWithdrawals * r.amount;
    const ok = out <= r.initialBalance && r.initialBalance - out === r.finalBalance;
    const label = r.account === prev ? `<span class="rowlabel repeat">${r.account}</span>` : `<span class="rowlabel">${r.account}</span>`;
    const gap = prev && r.account !== prev ? `<div class="group-gap"></div>` : "";
    prev = r.account;
    const tipText = `<b>${esc(r.account)} account</b><br>handed out ${money(out)} from ${money(r.initialBalance)}<br>final balance ${money(r.finalBalance)}`;
    const status = ok ? '<span class="ok">✓ balance $0</span>'
      : out > r.initialBalance ? `<span class="bad">✗ overdrawn, balance ${money(r.finalBalance)}</span>`
      : `<span class="bad">✗ balance doesn't add up (${money(r.finalBalance)})</span>`;
    return `${gap}${label}
      <div class="track" data-tip="${esc(tipText)}"><div class="bar ${ok ? "ok" : "bad"}" style="width:${(out / max) * 100}%"></div><div class="ref" style="left:${(r0.initialBalance / max) * 100}%"></div></div>
      <span class="barval"><b>${money(out)}</b> ${status}</span>`;
  }).join("");
  const el = $("#bankChart");
  el.innerHTML = `<div class="bars">${rows}</div>`;
  el.setAttribute("aria-label", results.map((r) => `${r.account}: handed out ${r.successfulWithdrawals * r.amount}, final balance ${r.finalBalance}`).join("; "));
}

function renderBarber(b) {
  const c = b.config, ev = b.events;
  $("#barberCaption").textContent =
    `${c.customers} customers, ${c.chairs} waiting chairs, ${c.haircutMillis} ms per haircut. Served ${b.served}, turned away ${b.turnedAway}. Hover a bar for details.`;
  const end = ev[ev.length - 1].millis || 1;
  const x = (ms) => (ms / end) * 100;
  const lanes = new Map([["barber", []]]);
  for (let i = 1; i <= c.customers; i++) lanes.set(i, []);
  const open = {}; // start time of the current segment, keyed by "<lane>:<kind>"
  const seg = (lane, kind, from, to, text) =>
    lanes.get(lane).push(`<div class="seg ${kind}" style="left:${x(from)}%;width:${Math.max(x(to) - x(from), 0.4)}%" data-tip="${esc(text)}"></div>`);

  for (const e of ev) {
    const id = e.customer;
    switch (e.type) {
      case "SLEEP": open.sleep = e.millis; break;
      case "WAKE": seg("barber", "sleep", open.sleep, e.millis, `<b>Barber asleep</b><br>${open.sleep}–${e.millis} ms`); delete open.sleep; break;
      case "CLOSE": if (open.sleep !== undefined) seg("barber", "sleep", open.sleep, e.millis, "<b>Barber asleep</b>"); break;
      case "SIT": open["w" + id] = e.millis; break;
      case "START_CUT":
        seg(id, "wait", open["w" + id], e.millis, `<b>Customer ${id} waiting</b><br>${e.millis - open["w" + id]} ms in the waiting room`);
        open["c" + id] = e.millis;
        break;
      case "FINISH_CUT": {
        const t = `<b>Haircut · customer ${id}</b><br>${open["c" + id]}–${e.millis} ms`;
        seg(id, "cut", open["c" + id], e.millis, t);
        seg("barber", "cut", open["c" + id], e.millis, t);
        break;
      }
      case "LEAVE_FULL":
        lanes.get(id).push(`<span class="left" style="left:${x(e.millis)}%" data-tip="<b>Customer ${id} left</b><br>all ${c.chairs} chairs taken at ${e.millis} ms">✕</span>`);
        break;
    }
  }

  const step = end > 4000 ? 1000 : end > 1500 ? 500 : 250;
  let ticks = "";
  for (let t = 0; t <= end; t += step) ticks += `<span style="left:${x(t)}%">${t >= 1000 ? t / 1000 + " s" : t + " ms"}</span>`;

  let html = "";
  for (const [lane, items] of lanes) {
    html += `<span class="who ${lane === "barber" ? "barber" : ""}">${lane === "barber" ? "barber" : "customer " + lane}</span><div class="lane">${items.join("")}</div>`;
  }
  $("#barberChart").innerHTML = `<div class="tl">${html}<div class="ticks">${ticks}</div></div>`;

  $("#barberLog tbody").innerHTML = ev.map((e) =>
    `<tr><td>${e.millis} ms</td><td>${e.customer < 0 ? "barber" : "customer " + e.customer}</td><td>${e.type.toLowerCase().replace("_", " ")}</td><td>${e.waiting}</td></tr>`).join("");
}
