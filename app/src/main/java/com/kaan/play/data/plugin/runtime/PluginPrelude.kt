package com.kaan.play.data.plugin.runtime

/**
 * Eklenti JS'inin çalıştığı ortamı kuran, **bize ait** başlangıç betiği. Davranış sözleşmesi `docs/nuvio-plugin-format.md`
 * §3'tedir; burada yalnızca o arayüz uygulanır (Nuvio'nun GPL-3.0 kaynağından kod alınmadı).
 *
 * Sunucu (Kotlin) tarafı yalnızca `__host_*` işlevlerini verir; geri kalan her şey (fetch, URL, cheerio, CryptoJS, zamanlayıcılar...)
 * aşağıda bu işlevlerin üstüne kurulur. Dosya sistemi, veritabanı, Intent gibi hiçbir şey köprülenmez.
 */
internal object PluginPrelude {

    /** TMDB için yer tutucu: eklentiye gerçek anahtar verilmez; ağ köprüsü yalnızca api.themoviedb.org isteklerinde değiştirir. */
    const val TMDB_PLACEHOLDER = "PLAY_TMDB_KEY_PLACEHOLDER"

    fun source(scraperId: String, settingsJson: String): String =
        HEADER
            .replace("@@PLACEHOLDER@@", TMDB_PLACEHOLDER)
            .replace("@@SCRAPER_ID@@", scraperId.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " "))
            .replace("@@SETTINGS@@", settingsJson) + BODY

    /** Eklenti kodunu `module`/`exports` ile sarar; yalnızca global `getStreams` tanımlayanlar da çalışsın diye dışa aktarılır. */
    fun wrap(code: String): String =
        "var module = { exports: {} };\nvar exports = module.exports;\n(function () {\n" + code +
            "\n;if (typeof getStreams === 'function' && !module.exports.getStreams) { module.exports.getStreams = getStreams; }\n})();"

    /** getStreams çağrısı: sonuç `__host_capture` ile tek JSON olarak geri verilir. */
    val CALL = """
        (async function () {
          try {
            var gs = (module.exports && module.exports.getStreams) || globalThis.getStreams;
            if (typeof gs !== 'function') { __host_capture(JSON.stringify({ ok: false, error: 'no_getStreams' })); return; }
            var a = JSON.parse(__host_args());
            var r = await gs(a.tmdbId, a.mediaType, a.season == null ? undefined : a.season, a.episode == null ? undefined : a.episode);
            __host_capture(JSON.stringify({ ok: true, streams: Array.isArray(r) ? r : [] }));
          } catch (e) {
            __host_capture(JSON.stringify({ ok: false, error: String((e && e.message) || e) }));
          }
        })();
    """.trimIndent()

    private const val HEADER = """
(function (g) {
  'use strict';
  g.global = g; g.window = g; g.self = g;
  g.TMDB_API_KEY = "@@PLACEHOLDER@@";
  g.SCRAPER_ID = "@@SCRAPER_ID@@";
  g.SCRAPER_SETTINGS = @@SETTINGS@@;
"""

    private const val BODY = """
  // ---- console ----
  function show(a) {
    try {
      if (typeof a === 'string') return a;
      if (a instanceof Error) return a.stack || a.message;
      return JSON.stringify(a);
    } catch (e) { return String(a); }
  }
  g.console = {};
  ['log', 'info', 'warn', 'error', 'debug'].forEach(function (level) {
    g.console[level] = function () { __host_log(level, Array.prototype.map.call(arguments, show).join(' ')); };
  });

  // ---- base64 / UTF-8 ----
  var B64 = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/';
  function bytesToB64(bytes) {
    var out = '';
    for (var i = 0; i < bytes.length; i += 3) {
      var a = bytes[i], b = i + 1 < bytes.length ? bytes[i + 1] : 0, c = i + 2 < bytes.length ? bytes[i + 2] : 0;
      out += B64.charAt(a >> 2) + B64.charAt(((a & 3) << 4) | (b >> 4));
      out += i + 1 < bytes.length ? B64.charAt(((b & 15) << 2) | (c >> 6)) : '=';
      out += i + 2 < bytes.length ? B64.charAt(c & 63) : '=';
    }
    return out;
  }
  function b64ToBytes(str) {
    str = String(str).replace(/[\s=]+/g, '').replace(/-/g, '+').replace(/_/g, '/');
    if (str.length % 4 === 1) throw new Error('InvalidCharacterError');
    var out = [], buf = 0, bits = 0;
    for (var i = 0; i < str.length; i++) {
      var v = B64.indexOf(str.charAt(i));
      if (v < 0) throw new Error('InvalidCharacterError');
      buf = (buf << 6) | v; bits += 6;
      if (bits >= 8) { bits -= 8; out.push((buf >> bits) & 255); }
    }
    return out;
  }
  g.btoa = function (s) {
    s = String(s); var bytes = [];
    for (var i = 0; i < s.length; i++) { var c = s.charCodeAt(i); if (c > 255) throw new Error('InvalidCharacterError'); bytes.push(c); }
    return bytesToB64(bytes);
  };
  g.atob = function (s) {
    var bytes = b64ToBytes(s), out = '';
    for (var i = 0; i < bytes.length; i++) out += String.fromCharCode(bytes[i]);
    return out;
  };
  function utf8Encode(str) {
    var out = [];
    for (var i = 0; i < str.length; i++) {
      var c = str.charCodeAt(i);
      if (c >= 0xD800 && c <= 0xDBFF && i + 1 < str.length) {
        var d = str.charCodeAt(i + 1);
        if (d >= 0xDC00 && d <= 0xDFFF) { c = 0x10000 + ((c - 0xD800) << 10) + (d - 0xDC00); i++; }
      }
      if (c < 0x80) out.push(c);
      else if (c < 0x800) out.push(0xC0 | (c >> 6), 0x80 | (c & 63));
      else if (c < 0x10000) out.push(0xE0 | (c >> 12), 0x80 | ((c >> 6) & 63), 0x80 | (c & 63));
      else out.push(0xF0 | (c >> 18), 0x80 | ((c >> 12) & 63), 0x80 | ((c >> 6) & 63), 0x80 | (c & 63));
    }
    return out;
  }
  function utf8Decode(bytes) {
    var out = '', i = 0;
    while (i < bytes.length) {
      var c = bytes[i++], cp;
      if (c < 0x80) cp = c;
      else if (c >= 0xC0 && c < 0xE0 && i < bytes.length) cp = ((c & 31) << 6) | (bytes[i++] & 63);
      else if (c >= 0xE0 && c < 0xF0 && i + 1 < bytes.length) cp = ((c & 15) << 12) | ((bytes[i++] & 63) << 6) | (bytes[i++] & 63);
      else if (c >= 0xF0 && i + 2 < bytes.length) cp = ((c & 7) << 18) | ((bytes[i++] & 63) << 12) | ((bytes[i++] & 63) << 6) | (bytes[i++] & 63);
      else cp = 0xFFFD;
      if (cp > 0xFFFF) { cp -= 0x10000; out += String.fromCharCode(0xD800 + (cp >> 10), 0xDC00 + (cp & 1023)); }
      else out += String.fromCharCode(cp);
    }
    return out;
  }
  g.TextEncoder = function () { this.encoding = 'utf-8'; };
  g.TextEncoder.prototype.encode = function (s) { return new Uint8Array(utf8Encode(String(s == null ? '' : s))); };
  g.TextDecoder = function () { this.encoding = 'utf-8'; };
  g.TextDecoder.prototype.decode = function (d) {
    if (d == null) return '';
    var b = d instanceof ArrayBuffer ? new Uint8Array(d) : (ArrayBuffer.isView(d) ? new Uint8Array(d.buffer, d.byteOffset, d.byteLength) : d);
    return utf8Decode(b);
  };

  // ---- zamanlayıcılar (gecikme sunucuda; JS olay döngüsü yok) ----
  var timers = {}, timerSeq = 0;
  g.setTimeout = function (fn, ms) {
    var id = ++timerSeq, extra = Array.prototype.slice.call(arguments, 2);
    timers[id] = true;
    __host_sleep(String(Math.max(0, +ms || 0))).then(function () {
      if (timers[id]) { delete timers[id]; try { fn.apply(null, extra); } catch (e) { g.console.error('timer: ' + (e && e.message)); } }
    });
    return id;
  };
  g.clearTimeout = function (id) { delete timers[id]; };
  g.setInterval = function (fn, ms) {
    var id = ++timerSeq, extra = Array.prototype.slice.call(arguments, 2);
    timers[id] = true;
    (function tick() {
      __host_sleep(String(Math.max(1, +ms || 1))).then(function () {
        if (!timers[id]) return;
        try { fn.apply(null, extra); } catch (e) { g.console.error('timer: ' + (e && e.message)); }
        tick();
      });
    })();
    return id;
  };
  g.clearInterval = function (id) { delete timers[id]; };

  // ---- AbortController ----
  function AbortSignal() { this.aborted = false; this.reason = undefined; this._l = []; }
  AbortSignal.prototype.addEventListener = function (t, f) { if (t === 'abort' && typeof f === 'function') this._l.push(f); };
  AbortSignal.prototype.removeEventListener = function (t, f) { this._l = this._l.filter(function (x) { return x !== f; }); };
  AbortSignal.prototype.throwIfAborted = function () { if (this.aborted) throw abortError(); };
  function abortError() { var e = new Error('The operation was aborted.'); e.name = 'AbortError'; return e; }
  function AbortController() { this.signal = new AbortSignal(); }
  AbortController.prototype.abort = function (reason) {
    var s = this.signal; if (s.aborted) return;
    s.aborted = true; s.reason = reason;
    s._l.slice().forEach(function (f) { try { f.call(s, { type: 'abort' }); } catch (e) {} });
  };
  g.AbortSignal = AbortSignal; g.AbortController = AbortController;

  // ---- URL / URLSearchParams ----
  function enc(s) { return encodeURIComponent(s).replace(/%20/g, '+'); }
  function dec(s) { try { return decodeURIComponent(String(s).replace(/\+/g, ' ')); } catch (e) { return s; } }
  function URLSearchParams(init) {
    this._p = [];
    if (init == null) return;
    if (typeof init === 'string') {
      var s = init.charAt(0) === '?' ? init.slice(1) : init;
      if (s) s.split('&').forEach(function (kv) {
        if (!kv) return; var i = kv.indexOf('=');
        this._p.push(i < 0 ? [dec(kv), ''] : [dec(kv.slice(0, i)), dec(kv.slice(i + 1))]);
      }, this);
    } else if (Array.isArray(init)) {
      init.forEach(function (kv) { this._p.push([String(kv[0]), String(kv[1])]); }, this);
    } else if (typeof init === 'object') {
      Object.keys(init).forEach(function (k) { this._p.push([k, String(init[k])]); }, this);
    }
  }
  var USP = URLSearchParams.prototype;
  USP.append = function (k, v) { this._p.push([String(k), String(v)]); };
  USP.delete = function (k) { this._p = this._p.filter(function (e) { return e[0] !== k; }); };
  USP.get = function (k) { for (var i = 0; i < this._p.length; i++) if (this._p[i][0] === k) return this._p[i][1]; return null; };
  USP.getAll = function (k) { return this._p.filter(function (e) { return e[0] === k; }).map(function (e) { return e[1]; }); };
  USP.has = function (k) { return this._p.some(function (e) { return e[0] === k; }); };
  USP.set = function (k, v) {
    k = String(k); v = String(v); var done = false, out = [];
    this._p.forEach(function (e) { if (e[0] === k) { if (!done) { out.push([k, v]); done = true; } } else out.push(e); });
    if (!done) out.push([k, v]); this._p = out;
  };
  USP.sort = function () { this._p.sort(function (a, b) { return a[0] < b[0] ? -1 : a[0] > b[0] ? 1 : 0; }); };
  USP.forEach = function (fn, t) { this._p.forEach(function (e) { fn.call(t, e[1], e[0], this); }, this); };
  USP.keys = function () { return this._p.map(function (e) { return e[0]; })[Symbol.iterator](); };
  USP.values = function () { return this._p.map(function (e) { return e[1]; })[Symbol.iterator](); };
  USP.entries = function () { return this._p.map(function (e) { return [e[0], e[1]]; })[Symbol.iterator](); };
  USP[Symbol.iterator] = USP.entries;
  USP.toString = function () { return this._p.map(function (e) { return enc(e[0]) + '=' + enc(e[1]); }).join('&'); };
  g.URLSearchParams = URLSearchParams;

  function URL(u, base) {
    var r = __host_url(String(u), base == null ? '' : String(base.href || base));
    if (!r) { throw new TypeError('Invalid URL'); }
    var p = JSON.parse(r);
    this.href = p.href; this.protocol = p.protocol; this.host = p.host; this.hostname = p.hostname; this.port = p.port;
    this.pathname = p.pathname; this.search = p.search; this.hash = p.hash; this.origin = p.origin;
    this.username = p.username || ''; this.password = p.password || '';
    this.searchParams = new URLSearchParams(p.search);
  }
  URL.prototype.toString = function () { return this.href; };
  URL.prototype.toJSON = function () { return this.href; };
  g.URL = URL;

  // ---- fetch ----
  function toHeaderObject(h) {
    var o = {};
    if (!h) return o;
    if (Array.isArray(h)) { h.forEach(function (kv) { o[String(kv[0])] = String(kv[1]); }); return o; }
    if (typeof h.forEach === 'function' && typeof h.get === 'function') { h.forEach(function (v, k) { o[k] = String(v); }); return o; }
    Object.keys(h).forEach(function (k) { if (h[k] != null) o[k] = String(h[k]); });
    return o;
  }
  function bytesOf(b) {
    if (b instanceof ArrayBuffer) return new Uint8Array(b);
    if (ArrayBuffer.isView(b)) return new Uint8Array(b.buffer, b.byteOffset, b.byteLength);
    return null;
  }
  function makeResponse(r) {
    var bytes = null;
    function raw() { if (bytes === null) bytes = b64ToBytes(r.bodyBase64 || ''); return bytes; }
    var headers = r.headers || {};
    var res = {
      ok: !!r.ok, status: r.status | 0, statusText: r.statusText || '', url: r.url || '', redirected: !!r.redirected, type: 'basic',
      bodyUsed: false,
      headers: {
        get: function (n) { var v = headers[String(n).toLowerCase()]; return v === undefined ? null : v; },
        has: function (n) { return headers[String(n).toLowerCase()] !== undefined; },
        forEach: function (fn) { Object.keys(headers).forEach(function (k) { fn(headers[k], k); }); },
        entries: function () { return Object.keys(headers).map(function (k) { return [k, headers[k]]; })[Symbol.iterator](); },
        keys: function () { return Object.keys(headers)[Symbol.iterator](); }
      },
      text: function () { return Promise.resolve(r.body || ''); },
      // Sözleşme: ayrıştırılamayan gövdede reddetmez, null döner.
      json: function () {
        try { return Promise.resolve(r.body ? JSON.parse(r.body) : null); }
        catch (e) { g.console.error('fetch.json: ' + (e && e.message)); return Promise.resolve(null); }
      },
      arrayBuffer: function () { var a = new Uint8Array(raw()); return Promise.resolve(a.buffer); },
      clone: function () { return makeResponse(r); }
    };
    return res;
  }
  g.fetch = async function (input, init) {
    init = init || {};
    var url = typeof input === 'string' ? input : (input && (input.href || input.url)) || String(input);
    var signal = init.signal || null;
    if (signal && signal.aborted) throw abortError();
    var method = String(init.method || 'GET').toUpperCase();
    var headers = toHeaderObject(init.headers);
    var kind = 'none', body = '';
    var b = init.body;
    if (b != null) {
      var bytes = bytesOf(b);
      if (typeof b === 'string') { kind = 'text'; body = b; }
      else if (b instanceof URLSearchParams) {
        kind = 'text'; body = b.toString();
        if (!Object.keys(headers).some(function (k) { return k.toLowerCase() === 'content-type'; })) headers['Content-Type'] = 'application/x-www-form-urlencoded;charset=UTF-8';
      } else if (bytes) { kind = 'base64'; body = bytesToB64(bytes); }
      else { kind = 'text'; body = String(b); }
    }
    var out = await __host_fetch(JSON.stringify({ url: url, method: method, headers: headers, bodyKind: kind, body: body, redirect: init.redirect || 'follow' }));
    if (signal && signal.aborted) throw abortError();
    return makeResponse(JSON.parse(out));
  };

  // ---- cheerio benzeri kabuk (yerel HTML ayrıştırıcıya köprü; tam cheerio API'si değil) ----
  var CH = { load: function (html) {
    var doc = __host_dom_load(String(html == null ? '' : html));
    function ids(json) { return JSON.parse(json); }
    function wrap(nodes) {
      var w = { __ch: true, length: nodes.length, _n: nodes };
      nodes.forEach(function (n, i) { w[i] = { __ch: true, length: 1, _n: [n], __single: true }; });
      // Tek düğümlü sarmalayıcılar da tam API'ye sahip olsun.
      for (var i = 0; i < nodes.length; i++) { var one = w[i]; copyApi(one); one[0] = one; }
      copyApi(w);
      return w;
    }
    function single(n) { return wrap([n])[0]; }
    function copyApi(w) { Object.keys(api).forEach(function (k) { w[k] = api[k]; }); }
    function each(self, fn) { for (var i = 0; i < self._n.length; i++) fn(self._n[i], i); }
    function flat(self, kind, sel) {
      var out = [], seen = {};
      each(self, function (n) {
        ids(__host_dom_rel(doc, n, kind, sel || '')).forEach(function (x) { if (!seen[x]) { seen[x] = 1; out.push(x); } });
      });
      return wrap(out);
    }
    var api = {
      find: function (sel) {
        var out = [], seen = {};
        each(this, function (n) { ids(__host_dom_select(doc, n, String(sel))).forEach(function (x) { if (!seen[x]) { seen[x] = 1; out.push(x); } }); });
        return wrap(out);
      },
      children: function (sel) { return flat(this, 'children', sel); },
      parent: function (sel) { return flat(this, 'parent', sel); },
      closest: function (sel) { return flat(this, 'closest', sel); },
      next: function (sel) { return flat(this, 'next', sel); },
      prev: function (sel) { return flat(this, 'prev', sel); },
      nextAll: function (sel) { return flat(this, 'nextAll', sel); },
      prevAll: function (sel) { return flat(this, 'prevAll', sel); },
      siblings: function (sel) { return flat(this, 'siblings', sel); },
      each: function (fn) {
        for (var i = 0; i < this._n.length; i++) { if (fn.call(single(this._n[i]), i, single(this._n[i])) === false) break; }
        return this;
      },
      map: function (fn) {
        var vals = [];
        for (var i = 0; i < this._n.length; i++) {
          var el = single(this._n[i]), v = fn.call(el, i, el);
          if (v != null) vals.push(v);
        }
        return { __vals: true, length: vals.length, get: function (i) { return i === undefined ? vals.slice() : vals[i]; },
          toArray: function () { return vals.slice(); }, each: function (f) { vals.forEach(function (v, i) { f.call(v, i, v); }); return this; },
          join: function (s) { return vals.join(s); } };
      },
      filter: function (x) {
        var keep = [];
        for (var i = 0; i < this._n.length; i++) {
          var n = this._n[i], ok;
          if (typeof x === 'function') { var el = single(n); ok = x.call(el, i, el); }
          else ok = __host_dom_is(doc, n, String(x)) === '1';
          if (ok) keep.push(n);
        }
        return wrap(keep);
      },
      not: function (x) {
        var keep = [];
        for (var i = 0; i < this._n.length; i++) {
          var n = this._n[i], hit;
          if (typeof x === 'function') { var el = single(n); hit = x.call(el, i, el); } else hit = __host_dom_is(doc, n, String(x)) === '1';
          if (!hit) keep.push(n);
        }
        return wrap(keep);
      },
      has: function (sel) {
        var keep = [];
        for (var i = 0; i < this._n.length; i++) if (ids(__host_dom_select(doc, this._n[i], String(sel))).length) keep.push(this._n[i]);
        return wrap(keep);
      },
      is: function (x) {
        for (var i = 0; i < this._n.length; i++) {
          if (typeof x === 'function') { var el = single(this._n[i]); if (x.call(el, i, el)) return true; }
          else if (__host_dom_is(doc, this._n[i], String(x)) === '1') return true;
        }
        return false;
      },
      first: function () { return wrap(this._n.slice(0, 1)); },
      last: function () { return wrap(this._n.slice(-1)); },
      eq: function (i) { i = i < 0 ? this._n.length + i : i; return wrap(this._n.slice(i, i + 1)); },
      slice: function (a, b) { return wrap(this._n.slice(a, b)); },
      get: function (i) {
        if (i === undefined) return this._n.map(single);
        i = i < 0 ? this._n.length + i : i;
        return this._n[i] === undefined ? undefined : single(this._n[i]);
      },
      toArray: function () { return this._n.map(single); },
      text: function () { var s = ''; each(this, function (n) { s += __host_dom_text(doc, n); }); return s; },
      html: function () { return this._n.length ? __host_dom_html(doc, this._n[0], '0') : null; },
      attr: function (name) {
        if (!this._n.length) return undefined;
        var v = __host_dom_attr(doc, this._n[0], String(name));
        return v === null ? undefined : v;
      },
      data: function (name) { var v = this.attr('data-' + String(name).replace(/[A-Z]/g, function (m) { return '-' + m.toLowerCase(); })); return v; },
      prop: function (name) { return this.attr(name); },
      val: function () {
        if (!this._n.length) return undefined;
        var v = __host_dom_attr(doc, this._n[0], 'value');
        return v === null ? __host_dom_text(doc, this._n[0]) : v;
      },
      hasClass: function (c) {
        var cls = this.attr('class'); if (!cls) return false;
        return (' ' + cls.replace(/\s+/g, ' ') + ' ').indexOf(' ' + c + ' ') >= 0;
      }
    };
    var $ = function (sel, ctx) {
      if (sel && sel.__ch) return sel;
      if (typeof sel === 'string') {
        if (ctx && ctx.__ch) return ctx.find(sel);
        return wrap(ids(__host_dom_select(doc, -1, sel)));
      }
      return wrap([]);
    };
    $.html = function (el) {
      if (el && el.__ch && el._n.length) return __host_dom_html(doc, el._n[0], '1');
      return __host_dom_html(doc, -1, '1');
    };
    $.root = function () { return wrap([-1]); };
    return $;
  } };
  g.cheerio = CH;

  // ---- CryptoJS benzeri (yerel şifreleme köprüsü; kullanılan alt küme) ----
  var HEX = '0123456789abcdef';
  function hexOf(bytes) { var s = ''; for (var i = 0; i < bytes.length; i++) s += HEX.charAt(bytes[i] >> 4) + HEX.charAt(bytes[i] & 15); return s; }
  function bytesFromHex(h) { var o = []; for (var i = 0; i + 1 < h.length; i += 2) o.push(parseInt(h.substr(i, 2), 16)); return o; }
  function WordArray(words, sigBytes) { this.words = words || []; this.sigBytes = sigBytes == null ? this.words.length * 4 : sigBytes; }
  function waFromBytes(bytes) {
    var words = [];
    for (var i = 0; i < bytes.length; i++) words[i >>> 2] |= (bytes[i] & 255) << (24 - (i % 4) * 8);
    return new WordArray(words, bytes.length);
  }
  function waToBytes(wa) {
    var out = [];
    for (var i = 0; i < wa.sigBytes; i++) out.push((wa.words[i >>> 2] >>> (24 - (i % 4) * 8)) & 255);
    return out;
  }
  WordArray.prototype.toString = function (encoder) { return (encoder || Hex).stringify(this); };
  WordArray.prototype.concat = function (o) { return waFromBytes(waToBytes(this).concat(waToBytes(o))); };
  WordArray.prototype.clone = function () { return waFromBytes(waToBytes(this)); };
  WordArray.create = function (words, sigBytes) {
    if (words && typeof words === 'object' && !Array.isArray(words) && (ArrayBuffer.isView(words) || words instanceof ArrayBuffer)) {
      return waFromBytes(Array.prototype.slice.call(bytesOf(words)));
    }
    return new WordArray(words ? words.slice() : [], sigBytes);
  };
  var Hex = { stringify: function (wa) { return hexOf(waToBytes(wa)); }, parse: function (s) { return waFromBytes(bytesFromHex(String(s))); } };
  var Latin1 = {
    stringify: function (wa) { return waToBytes(wa).map(function (b) { return String.fromCharCode(b); }).join(''); },
    parse: function (s) { var o = []; for (var i = 0; i < s.length; i++) o.push(s.charCodeAt(i) & 255); return waFromBytes(o); }
  };
  var Utf8 = {
    stringify: function (wa) { return utf8Decode(waToBytes(wa)); },
    parse: function (s) { return waFromBytes(utf8Encode(String(s))); }
  };
  var Base64 = { stringify: function (wa) { return bytesToB64(waToBytes(wa)); }, parse: function (s) { return waFromBytes(b64ToBytes(s)); } };
  var Base64url = {
    stringify: function (wa) { return bytesToB64(waToBytes(wa)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, ''); },
    parse: function (s) { return waFromBytes(b64ToBytes(s)); }
  };
  function asWA(x) { return typeof x === 'string' ? Utf8.parse(x) : (x instanceof WordArray ? x : WordArray.create(x)); }
  function hashFn(alg) { return function (msg) { return waFromBytes(bytesFromHex(__host_digest(alg, hexOf(waToBytes(asWA(msg)))))); }; }
  function hmacFn(alg) {
    return function (msg, key) { return waFromBytes(bytesFromHex(__host_hmac(alg, hexOf(waToBytes(asWA(key))), hexOf(waToBytes(asWA(msg)))))); };
  }
  function CipherParams(o) { for (var k in o) this[k] = o[k]; }
  CipherParams.create = function (o) { return new CipherParams(o || {}); };
  CipherParams.prototype.toString = function (fmt) {
    if (fmt) return fmt.stringify(this);
    return OpenSSL.stringify(this);
  };
  var OpenSSL = {
    stringify: function (p) {
      if (p.salt) return Base64.stringify(Latin1.parse('Salted__').concat(p.salt).concat(p.ciphertext));
      return Base64.stringify(p.ciphertext);
    },
    parse: function (s) {
      var wa = Base64.parse(s), b = waToBytes(wa), salt;
      if (b.length >= 16 && Latin1.stringify(waFromBytes(b.slice(0, 8))) === 'Salted__') { salt = waFromBytes(b.slice(8, 16)); b = b.slice(16); }
      return CipherParams.create({ ciphertext: waFromBytes(b), salt: salt });
    }
  };
  var Pkcs7 = {}, NoPadding = {}, ZeroPadding = {};
  var CBC = { n: 'CBC' }, ECB = { n: 'ECB' }, CTR = { n: 'CTR' };
  function evpKdf(pass, salt, keyLen, ivLen) {
    var out = [], prev = [], need = keyLen + ivLen;
    var p = waToBytes(pass), s = salt ? waToBytes(salt) : [];
    while (out.length < need) { prev = bytesFromHex(__host_digest('MD5', hexOf(prev.concat(p, s)))); out = out.concat(prev); }
    return { key: waFromBytes(out.slice(0, keyLen)), iv: waFromBytes(out.slice(keyLen, keyLen + ivLen)) };
  }
  function makeCipher(name, blockBytes, keyLens) {
    function cfgOf(cfg) { cfg = cfg || {}; return { mode: cfg.mode || CBC, padding: cfg.padding || Pkcs7, iv: cfg.iv }; }
    function run(encrypt, dataBytes, key, cfg, p) {
      var mode = cfg.mode.n, pad = cfg.padding === NoPadding || cfg.padding === ZeroPadding ? 'NO' : 'PKCS7';
      var keyBytes = waToBytes(key), ivBytes = cfg.iv ? waToBytes(cfg.iv) : [];
      if (encrypt && cfg.padding === ZeroPadding) { while (dataBytes.length % blockBytes) dataBytes = dataBytes.concat([0]); }
      var r = __host_cipher(name, mode, pad, encrypt ? '1' : '0', hexOf(keyBytes), hexOf(ivBytes), hexOf(dataBytes));
      if (r.indexOf('ERR:') === 0) throw new Error(r.slice(4));
      var out = bytesFromHex(r);
      if (!encrypt && cfg.padding === ZeroPadding) { while (out.length && out[out.length - 1] === 0) out.pop(); }
      return out;
    }
    return {
      encrypt: function (msg, key, cfg) {
        cfg = cfgOf(cfg); var salt, iv = cfg.iv;
        if (typeof key === 'string') {
          salt = waFromBytes(bytesFromHex(__host_random(8)));
          var d = evpKdf(Utf8.parse(key), salt, keyLens[keyLens.length - 1], blockBytes);
          key = d.key; cfg.iv = d.iv; iv = d.iv;
        }
        var ct = run(true, waToBytes(asWA(msg)), asWA(key), cfg);
        return CipherParams.create({ ciphertext: waFromBytes(ct), key: asWA(key), iv: iv, salt: salt });
      },
      decrypt: function (cipher, key, cfg) {
        cfg = cfgOf(cfg);
        var p = typeof cipher === 'string' ? OpenSSL.parse(cipher) : cipher;
        if (typeof key === 'string') {
          var d = evpKdf(Utf8.parse(key), p.salt, keyLens[keyLens.length - 1], blockBytes);
          key = d.key; cfg.iv = d.iv;
        }
        return waFromBytes(run(false, waToBytes(p.ciphertext), asWA(key), cfg, p));
      }
    };
  }
  var CryptoJS = {
    lib: { WordArray: WordArray, CipherParams: CipherParams },
    enc: { Hex: Hex, Latin1: Latin1, Utf8: Utf8, Base64: Base64, Base64url: Base64url },
    format: { OpenSSL: OpenSSL },
    pad: { Pkcs7: Pkcs7, NoPadding: NoPadding, ZeroPadding: ZeroPadding },
    mode: { CBC: CBC, ECB: ECB, CTR: CTR },
    MD5: hashFn('MD5'), SHA1: hashFn('SHA-1'), SHA224: hashFn('SHA-224'), SHA256: hashFn('SHA-256'),
    SHA384: hashFn('SHA-384'), SHA512: hashFn('SHA-512'),
    HmacMD5: hmacFn('MD5'), HmacSHA1: hmacFn('SHA-1'), HmacSHA256: hmacFn('SHA-256'), HmacSHA384: hmacFn('SHA-384'), HmacSHA512: hmacFn('SHA-512'),
    AES: makeCipher('AES', 16, [16, 24, 32]),
    TripleDES: makeCipher('DESede', 8, [24])
  };
  g.CryptoJS = CryptoJS;

  // ---- require: yalnızca izin listesi ----
  g.require = function (name) {
    if (name === 'cheerio' || name === 'cheerio-without-node-native' || name === 'react-native-cheerio') return CH;
    if (name === 'crypto-js') return CryptoJS;
    throw new Error("Module '" + name + "' is not available");
  };
})(globalThis);
"""
}
