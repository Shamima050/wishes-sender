// ============ Shared JavaScript used by every page ============

// Call the backend API. Example: const people = await api('/api/connections');
async function api(url, method = 'GET', body = null) {
  const options = { method: method, headers: {} };
  if (body !== null) {
    options.headers['Content-Type'] = 'application/json';
    options.body = JSON.stringify(body);
  }
  const res = await fetch(url, options);

  if (res.status === 401 && !location.pathname.endsWith('index.html') && location.pathname !== '/') {
    location.href = 'index.html';   // not logged in -> go to login page
    throw new Error('Not logged in');
  }
  const text = await res.text();
  const data = text ? JSON.parse(text) : null;
  if (!res.ok) {
    throw new Error((data && data.message) || 'Something went wrong');
  }
  return data;
}

// Read a value from the page URL, e.g. person.html?id=5 -> param('id') = "5"
function param(name) {
  return new URLSearchParams(location.search).get(name);
}

// Escape text before putting it inside HTML (prevents broken pages / XSS)
function esc(value) {
  if (value === null || value === undefined) return '';
  return String(value).replace(/[&<>"']/g, c =>
    ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
}

// "2026-12-05" -> "5 Dec 2026"
function fmtDate(value) {
  if (!value) return '';
  const d = new Date(String(value).substring(0, 10) + 'T00:00:00');
  return d.toLocaleDateString('en-GB', { day: 'numeric', month: 'short', year: 'numeric' });
}

function daysText(days) {
  if (days === 0) return 'Today';
  if (days === 1) return 'Tomorrow';
  return 'In ' + days + ' days';
}

function eventIcon(type) {
  return { BIRTHDAY: '🎂', ANNIVERSARY: '💍', FESTIVAL: '🎉', CUSTOM: '⭐' }[type] || '⭐';
}

function initials(name) {
  return esc(name.split(' ').map(w => w[0]).join('').substring(0, 2).toUpperCase());
}

// Gift suggestion card (used on the person page and in Wish Studio)
function giftCard(g) {
  return '<div class="card" style="margin:0">' +
    '<b>' + esc(g.name) + '</b><br>' +
    '<span class="tag gold">' + g.price + ' BDT</span><span class="tag">' + esc(g.category) + '</span>' +
    (g.matches_interest ? '<span class="tag green">matches interests</span>' : '') +
    '<p class="muted">💡 Why this gift? ' + esc(g.reason) + '</p></div>';
}

// Small popup message at the bottom right
function toast(message) {
  let t = document.getElementById('toast');
  if (!t) {
    t = document.createElement('div');
    t.id = 'toast';
    t.className = 'toast';
    document.body.appendChild(t);
  }
  t.textContent = message;
  t.style.display = 'block';
  setTimeout(() => (t.style.display = 'none'), 2500);
}

// Builds the left menu. Call it at the top of every logged-in page: await loadLayout('home');
async function loadLayout(active) {
  const me = await api('/api/auth/me');
  const unread = await api('/api/notifications/unread-count');

  const links = [
    ['home', 'home.html', '🏠 Home'],
    ['people', 'people.html', '👥 People'],
    ['connections', 'connections.html', '🔗 Connections'],
    ['wish', 'wish.html', '✍️ Wish Studio'],
    ['reminders', 'reminders.html', '🔔 Reminders' + (unread.count ? '<span class="badge">' + unread.count + '</span>' : '')],
    ['festivals', 'festivals.html', '🎉 Festivals'],
    ['insights', 'insights.html', '📊 Insights'],
    ['settings', 'settings.html', '⚙️ Settings'],
  ];
  // Admin section: platform-wide pages (all users)
  const adminLinks = [
    ['admin', 'admin.html', '🛡️ Admin'],
    ['admin-people', 'admin-people.html', '🧑‍🤝‍🧑 People'],
    ['admin-connections', 'connections.html?scope=all', '🌐 Connections'],
  ];

  const link = ([key, href, label]) =>
    '<a href="' + href + '" class="' + (key === active ? 'active' : '') + '">' + label + '</a>';
  let html = '<div class="logo">Wishes<span>Sender</span></div>';
  html += links.map(link).join('');
  if (me.role === 'ADMIN') {
    html += '<div class="nav-section">Admin</div>' + adminLinks.map(link).join('');
  }
  html += '<a href="#" onclick="logout()">🚪 Logout</a>';
  document.getElementById('sidebar').innerHTML = html;
  return me;
}

async function logout() {
  await api('/api/auth/logout', 'POST');
  location.href = 'index.html';
}
