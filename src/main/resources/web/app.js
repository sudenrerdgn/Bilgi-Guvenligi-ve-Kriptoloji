const element = id => document.getElementById(id);
let currentState;
let historySignature = '';
let busy = false;
let filesSignature = '';
let transferSignature = '';

function showError(message) {
  element('error').textContent = message;
  element('error').hidden = !message;
}

function updateControls() {
  const connected = Boolean(currentState?.connected);
  element('connection-fields').disabled = busy || connected;
  element('disconnect').hidden = !connected;
  element('disconnect').disabled = busy;
  element('message').disabled = !connected;
  element('send').disabled = busy || !connected;
  element('pdf').disabled = busy || !connected;
  element('send-file').disabled = busy || !connected;
}

function render(state) {
  currentState = state;
  const isServer = state.role === 'server';
  element('title').textContent = isServer ? 'Sunucu Kontrol Merkezi' : 'Güvenli Haberleşme';
  element('subtitle').textContent = isServer ? 'Bağlantıları ve sunucudan geçen mesajları takip et.' : 'İki istemci, ortak bir haberleşme sunucusu.';
  element('client-panel').hidden = isServer;
  element('server-panel').hidden = !isServer;
  element('message-form').hidden = isServer;
  element('pdf-panel').hidden = isServer;
  element('history-title').textContent = isServer ? 'Mesaj ve işlem kayıtları' : 'Mesajlar';
  element('status').textContent = isServer ? 'Sunucu çalışıyor' : state.connected ? `${state.name} · Bağlı` : 'Bağlı değil';
  element('status').classList.toggle('online', state.connected);
  updateControls();
  const newTransferSignature = JSON.stringify(state.transfer || {});
  if (state.transfer?.status && newTransferSignature !== transferSignature) {
    transferSignature = newTransferSignature;
    element('file-progress').value = state.transfer.percent;
    element('file-status').textContent = `${state.transfer.name}: ${state.transfer.status}`;
  }
  if (state.storage) element('storage').textContent = `Kayıt klasörü: ${state.storage}`;
  const newFilesSignature = JSON.stringify(state.files || []);
  if (newFilesSignature !== filesSignature) {
    filesSignature = newFilesSignature;
    const files = (state.files || []).map(file => {
      const row = document.createElement('div');
      row.className = 'file-row';
      const label = document.createElement('p');
      label.textContent = `${file.name} · ${(file.size / 1024).toFixed(1)} KB · ${file.sender}`;
      const link = document.createElement('a');
      link.href = `/api/files/${encodeURIComponent(file.id)}`;
      link.download = file.name;
      link.textContent = 'İndir';
      row.append(label, link);
      return row;
    });
    if (!files.length) {
      const empty = document.createElement('p');
      empty.className = 'hint';
      empty.textContent = 'Henüz dosya alınmadı.';
      files.push(empty);
    }
    element('files').replaceChildren(...files);
  }
  if (isServer) {
    element('client-count').textContent = `${state.clients.length} / 2`;
    element('server-info').textContent = `Haberleşme portu: ${state.tcpPort}`;
    element('clients').replaceChildren(...state.clients.map(name => {
      const item = document.createElement('li');
      item.textContent = name;
      return item;
    }));
    if (!state.clients.length) element('clients').textContent = 'İstemciler bekleniyor…';
  }
  const signature = `${state.history[0]?.id}:${state.history.at(-1)?.id}:${state.name}`;
  if (signature === historySignature) return;
  historySignature = signature;
  const history = element('history');
  const follow = history.scrollHeight - history.scrollTop - history.clientHeight < 90;
  const nodes = state.history.map(entry => {
    const article = document.createElement('article');
    article.className = `entry ${entry.kind}${!isServer && entry.kind === 'message' && entry.sender === state.name ? ' own' : ''}`;
    const label = document.createElement('small');
    label.textContent = `${entry.sender} · ${new Date(entry.time).toLocaleTimeString('tr-TR')}`;
    const text = document.createElement('p');
    // Mesaji HTML olarak yorumlamayiz; kullanici icerigini yalnizca metin olarak gosteririz.
    text.textContent = entry.text;
    article.append(label, text);
    return article;
  });
  if (!nodes.length) {
    const empty = document.createElement('p');
    empty.className = 'empty';
    empty.textContent = isServer ? 'İstemciler bağlandığında kayıtlar burada görünecek.' : 'Sunucuya bağlanarak konuşmaya başla.';
    nodes.push(empty);
  }
  history.replaceChildren(...nodes);
  if (follow) history.scrollTop = history.scrollHeight;
}

async function refresh() {
  const response = await fetch('/api/state');
  if (!response.ok) throw new Error('Uygulama durumuna ulaşılamadı.');
  render(await response.json());
}

async function submit(path, data) {
  busy = true;
  updateControls();
  showError('');
  try {
    const response = await fetch(path, { method: 'POST', body: new URLSearchParams(data) });
    const result = await response.json();
    if (!response.ok) throw new Error(result.error || 'İşlem tamamlanamadı.');
    await refresh();
    return true;
  } catch (error) {
    showError(error.message);
    return false;
  } finally {
    busy = false;
    updateControls();
  }
}

element('connect-form').addEventListener('submit', event => {
  event.preventDefault();
  if (!busy) submit('/api/connect', new FormData(event.currentTarget));
});
element('disconnect').addEventListener('click', () => {
  if (!busy) submit('/api/disconnect', {});
});
element('message-form').addEventListener('submit', async event => {
  event.preventDefault();
  if (busy) return;
  if (await submit('/api/send', new FormData(event.currentTarget))) element('message').value = '';
});

element('file-form').addEventListener('submit', async event => {
  event.preventDefault();
  if (busy) return;
  const file = element('pdf').files[0];
  if (!file || !file.name.toLowerCase().endsWith('.pdf') || file.size < 5 || file.size > 10 * 1024 * 1024) {
    showError('En fazla 10 MB boyutunda bir PDF seçin.');
    return;
  }
  busy = true;
  updateControls();
  showError('');
  element('file-progress').value = 0;
  element('file-status').textContent = 'Dosya Java istemcisine yükleniyor…';
  try {
    const response = await fetch(`/api/file?name=${encodeURIComponent(file.name)}`, {
      method: 'POST', headers: { 'Content-Type': 'application/pdf' }, body: file
    });
    const result = await response.json();
    if (!response.ok) throw new Error(result.error || 'PDF gönderilemedi.');
    element('pdf').value = '';
    await refresh();
  } catch (error) {
    showError(error.message);
    element('file-status').textContent = `Aktarım tamamlanamadı: ${error.message}`;
  } finally {
    busy = false;
    updateControls();
  }
});

async function poll() {
  try {
    await refresh();
  } catch (error) {
    element('status').textContent = 'Java uygulamasına ulaşılamıyor';
    element('status').classList.remove('online');
    currentState = undefined;
    updateControls();
    showError('Java uygulamasının çalıştığını kontrol et. Bağlantı yeniden denenecek.');
  } finally {
    // Bir onceki istek bitmeden yeni sorgu baslatmayiz.
    setTimeout(poll, 800);
  }
}
poll();
