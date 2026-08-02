/**
 * Weather Metro server-side alert monitor.
 *
 * Required Script Properties (never commit their values):
 *   FIREBASE_PROJECT_ID
 *   FIREBASE_CLIENT_EMAIL
 *   FIREBASE_PRIVATE_KEY
 */

const CONFIG = Object.freeze({
  topic: 'hko_alerts',
  stateKey: 'HKO_ALERT_STATE_V3',
  triggerFunction: 'checkWeatherUpdates',
  hkoBase: 'https://data.weather.gov.hk/weatherAPI/opendata/weather.php',
  tokenUrl: 'https://oauth2.googleapis.com/token',
  fcmScope: 'https://www.googleapis.com/auth/firebase.messaging',
});

/** Installs exactly one five-minute trigger. Run this once from the Apps Script editor. */
function installFiveMinuteTrigger() {
  ScriptApp.getProjectTriggers()
    .filter(function (trigger) {
      return trigger.getHandlerFunction() === CONFIG.triggerFunction;
    })
    .forEach(function (trigger) {
      ScriptApp.deleteTrigger(trigger);
    });

  ScriptApp.newTrigger(CONFIG.triggerFunction)
    .timeBased()
    .everyMinutes(5)
    .create();
}

/** Polls official HKO warning endpoints, diffs stable state, and sends FCM v1 updates. */
function checkWeatherUpdates() {
  const lock = LockService.getScriptLock();
  if (!lock.tryLock(20000)) {
    console.log('A previous alert check is still running; this execution was skipped.');
    return;
  }

  try {
    assertConfiguration_();
    const responses = UrlFetchApp.fetchAll([
      hkoRequest_('warnsum'),
      hkoRequest_('warningInfo'),
      hkoRequest_('swt'),
    ]);
    const payloads = responses.map(parseHkoResponse_);
    const current = normaliseState_(payloads[0], payloads[1], payloads[2]);
    const properties = PropertiesService.getScriptProperties();
    const previousText = properties.getProperty(CONFIG.stateKey);

    // First execution establishes a baseline and intentionally sends no historical alerts.
    if (!previousText) {
      properties.setProperty(CONFIG.stateKey, JSON.stringify(current));
      console.log('Initial HKO alert baseline stored.');
      return;
    }

    const previous = safeParse_(previousText, {});
    const events = diffStates_(previous, current);
    if (events.length === 0) {
      console.log('No HKO alert changes.');
      return;
    }

    events.forEach(sendEvent_);
    properties.setProperty(CONFIG.stateKey, JSON.stringify(current));
    console.log('Sent ' + events.length + ' HKO alert change(s).');
  } finally {
    lock.releaseLock();
  }
}

/** Clears only the saved alert baseline. The next check becomes a silent initialisation. */
function resetAlertBaseline() {
  PropertiesService.getScriptProperties().deleteProperty(CONFIG.stateKey);
}

/** Sends a harmless connectivity check to subscribed test devices. */
function sendTestNotification() {
  assertConfiguration_();
  sendFcm_({
    title: 'Weather Metro',
    body: 'FCM HTTP v1 connection is working.',
    channel: 'weather_service_status',
    eventId: 'test:' + Date.now(),
    target: 'weathermetro://current',
  });
}

function hkoRequest_(dataType) {
  return {
    url: CONFIG.hkoBase + '?dataType=' + encodeURIComponent(dataType) + '&lang=tc',
    method: 'get',
    headers: { Accept: 'application/json' },
    muteHttpExceptions: true,
  };
}

function parseHkoResponse_(response) {
  const code = response.getResponseCode();
  if (code < 200 || code >= 300) {
    throw new Error('HKO returned HTTP ' + code);
  }
  return JSON.parse(response.getContentText('UTF-8'));
}

function normaliseState_(summary, detailPayload, tipPayload) {
  const details = Array.isArray(detailPayload.details) ? detailPayload.details : [];
  const state = {};

  Object.keys(summary || {}).sort().forEach(function (family) {
    const row = summary[family] || {};
    const action = String(row.actionCode || 'ISSUE').toUpperCase();
    if (action === 'CANCEL') return;
    const code = String(row.code || family);
    const detail = details.find(function (candidate) {
      return candidate.subtype === code ||
        candidate.warningStatementCode === family ||
        candidate.warningStatementCode === familyForCode_(code);
    }) || {};
    const content = (Array.isArray(detail.contents) ? detail.contents : [])
      .map(cleanText_)
      .filter(Boolean)
      .join('\n\n');
    const title = cleanText_(row.type || row.name || warningName_(code));
    const updatedAt = String(row.updateTime || detail.updateTime || '');
    const id = 'warning:' + code;
    state[id] = {
      id: id,
      code: code,
      title: title,
      body: content || title,
      updatedAt: updatedAt,
      severity: severity_(code, false),
      isTip: false,
      fingerprint: digest_([code, title, content].join('|')),
    };
  });

  const tips = Array.isArray(tipPayload.swt) ? tipPayload.swt : [];
  tips.forEach(function (tip) {
    const body = cleanText_(tip.desc || '');
    if (!body) return;
    const id = 'tip:' + digest_(body);
    const updatedAt = String(tip.updateTime || '');
    state[id] = {
      id: id,
      code: 'SWT',
      title: '特別天氣提示',
      body: body,
      updatedAt: updatedAt,
      severity: severity_(body, true),
      isTip: true,
      fingerprint: digest_(body),
    };
  });
  return state;
}

function diffStates_(previous, current) {
  const events = [];
  Object.keys(current).sort().forEach(function (id) {
    if (!previous[id]) {
      events.push({ kind: 'ISSUE', item: current[id] });
    } else if (previous[id].fingerprint !== current[id].fingerprint) {
      events.push({ kind: 'UPDATE', item: current[id] });
    }
  });
  Object.keys(previous).sort().forEach(function (id) {
    if (!current[id]) events.push({ kind: 'CANCEL', item: previous[id] });
  });
  return events;
}

function sendEvent_(event) {
  const item = event.item;
  const prefix = event.kind === 'CANCEL' ? '已取消' : event.kind === 'UPDATE' ? '已更新' : '已發出';
  const eventId = 'hko:' + digest_([event.kind, item.id, item.fingerprint].join('|'));
  const target = 'weathermetro://current/alerts' +
    '?alertId=' + encodeURIComponent(item.id) +
    '&code=' + encodeURIComponent(item.code) +
    '&kind=' + encodeURIComponent(event.kind);
  sendFcm_({
    title: prefix + '：' + item.title,
    body: item.body,
    channel: channelFor_(item.severity, item.isTip),
    eventId: eventId,
    alertId: item.id,
    alertCode: item.code,
    eventKind: event.kind,
    target: target,
  });
}

function sendFcm_(message) {
  const props = PropertiesService.getScriptProperties();
  const projectId = props.getProperty('FIREBASE_PROJECT_ID');
  const endpoint = 'https://fcm.googleapis.com/v1/projects/' + encodeURIComponent(projectId) + '/messages:send';
  const payload = {
    message: {
      topic: CONFIG.topic,
      data: {
        title: message.title,
        body: message.body,
        channel: message.channel,
        eventId: message.eventId,
        alertId: message.alertId || '',
        alertCode: message.alertCode || '',
        eventKind: message.eventKind || '',
        target: message.target,
      },
      android: {
        priority: message.channel === 'weather_alert_urgent' ? 'HIGH' : 'NORMAL',
        ttl: '3600s',
        collapse_key: message.alertId || message.eventId,
      },
    },
  };
  const response = UrlFetchApp.fetch(endpoint, {
    method: 'post',
    contentType: 'application/json',
    headers: { Authorization: 'Bearer ' + accessToken_() },
    payload: JSON.stringify(payload),
    muteHttpExceptions: true,
  });
  if (response.getResponseCode() < 200 || response.getResponseCode() >= 300) {
    throw new Error('FCM HTTP ' + response.getResponseCode() + ': ' + response.getContentText());
  }
}

function accessToken_() {
  const cache = CacheService.getScriptCache();
  const cached = cache.get('fcm_access_token');
  if (cached) return cached;

  const props = PropertiesService.getScriptProperties();
  const clientEmail = props.getProperty('FIREBASE_CLIENT_EMAIL');
  const privateKey = props.getProperty('FIREBASE_PRIVATE_KEY').replace(/\\n/g, '\n');
  const now = Math.floor(Date.now() / 1000);
  const header = base64WebSafe_(JSON.stringify({ alg: 'RS256', typ: 'JWT' }));
  const claim = base64WebSafe_(JSON.stringify({
    iss: clientEmail,
    scope: CONFIG.fcmScope,
    aud: CONFIG.tokenUrl,
    iat: now,
    exp: now + 3600,
  }));
  const unsigned = header + '.' + claim;
  const signature = Utilities.computeRsaSha256Signature(unsigned, privateKey);
  const assertion = unsigned + '.' + Utilities.base64EncodeWebSafe(signature).replace(/=+$/, '');
  const response = UrlFetchApp.fetch(CONFIG.tokenUrl, {
    method: 'post',
    payload: {
      grant_type: 'urn:ietf:params:oauth:grant-type:jwt-bearer',
      assertion: assertion,
    },
    muteHttpExceptions: true,
  });
  if (response.getResponseCode() < 200 || response.getResponseCode() >= 300) {
    throw new Error('OAuth token HTTP ' + response.getResponseCode() + ': ' + response.getContentText());
  }
  const token = JSON.parse(response.getContentText()).access_token;
  cache.put('fcm_access_token', token, 3300);
  return token;
}

function assertConfiguration_() {
  const props = PropertiesService.getScriptProperties();
  ['FIREBASE_PROJECT_ID', 'FIREBASE_CLIENT_EMAIL', 'FIREBASE_PRIVATE_KEY'].forEach(function (key) {
    if (!props.getProperty(key)) throw new Error('Missing Script Property: ' + key);
  });
}

function safeParse_(text, fallback) {
  try { return JSON.parse(text); } catch (error) { return fallback; }
}

function cleanText_(value) {
  return String(value || '').replace(/\s+/g, ' ').trim();
}

function digest_(value) {
  const bytes = Utilities.computeDigest(Utilities.DigestAlgorithm.SHA_256, String(value), Utilities.Charset.UTF_8);
  return bytes.map(function (byte) {
    return ('0' + ((byte + 256) % 256).toString(16)).slice(-2);
  }).join('').slice(0, 24);
}

function base64WebSafe_(value) {
  return Utilities.base64EncodeWebSafe(value, Utilities.Charset.UTF_8).replace(/=+$/, '');
}

function channelFor_(severity, isTip) {
  if (severity === 'URGENT') return 'weather_alert_urgent';
  if (isTip) return 'weather_tips';
  return 'weather_alert_general';
}

function severity_(value, isTip) {
  if (isTip) {
    return /水浸|猛烈陣風|冰雹|水龍捲|山泥傾瀉/.test(value) ? 'WARNING' : 'TIP';
  }
  if (/^TC(8|9|10)/.test(value) || ['WRAINR', 'WRAINB', 'WTMW'].indexOf(value) >= 0) return 'URGENT';
  if (['WRAINA', 'WTS', 'TC3', 'WL', 'WFIRER'].indexOf(value) >= 0) return 'WARNING';
  return 'ADVISORY';
}

function familyForCode_(code) {
  if (code.indexOf('TC') === 0) return 'WTCSGNL';
  if (code.indexOf('WRAIN') === 0) return 'WRAIN';
  if (code.indexOf('WFIRE') === 0) return 'WFIRE';
  if (code === 'WFNW' || code === 'WFNTSA') return 'WFNTSA';
  if (code === 'WMSGN' || code === 'WMSGNL') return 'WMSGNL';
  return code;
}

function warningName_(code) {
  const names = {
    WTS: '雷暴警告', WRAINA: '黃色暴雨警告', WRAINR: '紅色暴雨警告', WRAINB: '黑色暴雨警告',
    TC1: '一號戒備信號', TC3: '三號強風信號', TC8NE: '八號東北烈風或暴風信號',
    TC8SE: '八號東南烈風或暴風信號', TC8NW: '八號西北烈風或暴風信號',
    TC8SW: '八號西南烈風或暴風信號', TC9: '九號烈風或暴風風力增強信號', TC10: '十號颶風信號',
    WHOT: '酷熱天氣警告', WCOLD: '寒冷天氣警告', WMSGNL: '強烈季候風信號',
    WL: '山泥傾瀉警告', WFROST: '霜凍警告', WFIREY: '黃色火災危險警告',
    WFIRER: '紅色火災危險警告', WTMW: '海嘯警告', WFNTSA: '新界北部水浸特別報告',
  };
  return names[code] || '天氣警告';
}
