#!/usr/bin/env node
/**
 * 观影中心 (MovieHub) 独立源站运维服务端 (零依赖 Node.js 版)
 *
 * 启动方式:
 *   PORT=8787 ADMIN_KEY=123456 node server.js
 *
 * 功能:
 *   1. GET  /              -> 可视化源站运维管理后台 (index.html)
 *   2. GET  /sources.json  -> 安卓客户端写死读取的在线源同步接口 (自动同步根级 ../sources.json)
 *   3. POST /api/save      -> 在网页后台直接保存修改并同步写入 sources.json
 *   4. GET  /api/probe     -> 服务端代理检测各影视源 API 的连通性与真实响应耗时 (ms)
 */

const http = require('http');
const https = require('https');
const fs = require('fs');
const path = require('path');
const { URL } = require('url');

const PORT = Number(process.env.PORT || 8787);
const ADMIN_KEY = process.env.ADMIN_KEY || '';
const LOCAL_SOURCES_PATH = path.join(__dirname, 'sources.json');
const ROOT_SOURCES_PATH = path.join(__dirname, '..', 'sources.json');
const INDEX_HTML_PATH = path.join(__dirname, 'index.html');

function sendJson(res, code, obj) {
  const body = JSON.stringify(obj, null, 2);
  res.writeHead(code, {
    'Content-Type': 'application/json; charset=utf-8',
    'Access-Control-Allow-Origin': '*',
    'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
    'Access-Control-Allow-Headers': 'Content-Type, X-Admin-Key',
    'Cache-Control': 'no-store, no-cache, must-revalidate'
  });
  res.end(body);
}

function readSourcesJson() {
  if (fs.existsSync(LOCAL_SOURCES_PATH)) {
    return fs.readFileSync(LOCAL_SOURCES_PATH, 'utf-8');
  }
  if (fs.existsSync(ROOT_SOURCES_PATH)) {
    return fs.readFileSync(ROOT_SOURCES_PATH, 'utf-8');
  }
  return JSON.stringify({ version: '1.0.0', vodSources: [], liveChannels: [], lxMusicScripts: [] }, null, 2);
}

function probeTargetUrl(targetUrl, timeoutMs = 4500) {
  return new Promise((resolve) => {
    const start = Date.now();
    try {
      const u = new URL(targetUrl);
      const client = u.protocol === 'https:' ? https : http;
      const sep = targetUrl.includes('?') ? '&' : '?';
      const fullUrl = `${targetUrl}${sep}ac=list&pg=1`;
      const req = client.get(
        fullUrl,
        {
          headers: {
            'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/144.0.0.0 Safari/537.36',
            'Accept': 'application/json,*/*'
          },
          timeout: timeoutMs,
          rejectUnauthorized: false
        },
        (resp) => {
          let raw = '';
          resp.setEncoding('utf8');
          resp.on('data', (chunk) => {
            if (raw.length < 2048) raw += chunk;
          });
          resp.on('end', () => {
            const latencyMs = Date.now() - start;
            const ok = resp.statusCode >= 200 && resp.statusCode < 300 && (raw.includes('"list"') || raw.includes('"class"') || raw.trim().startsWith('{'));
            resolve({ ok, status: resp.statusCode, latencyMs });
          });
        }
      );
      req.on('timeout', () => {
        req.destroy();
        resolve({ ok: false, status: 408, latencyMs: Date.now() - start, error: 'Timeout' });
      });
      req.on('error', (err) => {
        resolve({ ok: false, status: 502, latencyMs: Date.now() - start, error: err.message });
      });
    } catch (e) {
      resolve({ ok: false, status: 400, latencyMs: 0, error: e.message });
    }
  });
}

const server = http.createServer(async (req, res) => {
  if (req.method === 'OPTIONS') {
    res.writeHead(204, {
      'Access-Control-Allow-Origin': '*',
      'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
      'Access-Control-Allow-Headers': 'Content-Type, X-Admin-Key'
    });
    return res.end();
  }

  const parsedUrl = new URL(req.url, `http://${req.headers.host || 'localhost'}`);
  const pathname = parsedUrl.pathname;

  if (req.method === 'GET' && (pathname === '/sources.json' || pathname === '/api/sources')) {
    const raw = readSourcesJson();
    res.writeHead(200, {
      'Content-Type': 'application/json; charset=utf-8',
      'Access-Control-Allow-Origin': '*',
      'Cache-Control': 'no-store, no-cache, must-revalidate'
    });
    return res.end(raw);
  }

  if (req.method === 'GET' && pathname === '/api/probe') {
    const target = parsedUrl.searchParams.get('url') || '';
    if (!target.startsWith('http')) {
      return sendJson(res, 400, { ok: false, error: 'Invalid URL' });
    }
    const result = await probeTargetUrl(target);
    return sendJson(res, 200, result);
  }

  if (req.method === 'POST' && pathname === '/api/save') {
    if (ADMIN_KEY) {
      const provided = req.headers['x-admin-key'] || '';
      if (provided !== ADMIN_KEY) {
        return sendJson(res, 403, { ok: false, error: '管理密钥 (ADMIN_KEY) 不正确' });
      }
    }
    let body = '';
    req.on('data', (chunk) => {
      body += chunk;
      if (body.length > 2 * 1024 * 1024) req.destroy();
    });
    req.on('end', () => {
      try {
        const parsed = JSON.parse(body);
        parsed.updatedAt = new Date().toISOString().slice(0, 19).replace('T', ' ');
        const formatted = JSON.stringify(parsed, null, 2);
        fs.writeFileSync(LOCAL_SOURCES_PATH, formatted, 'utf-8');
        try {
          fs.writeFileSync(ROOT_SOURCES_PATH, formatted, 'utf-8');
        } catch (_) {}
        return sendJson(res, 200, { ok: true, updatedAt: parsed.updatedAt });
      } catch (e) {
        return sendJson(res, 400, { ok: false, error: 'JSON 格式错误: ' + e.message });
      }
    });
    return;
  }

  if (req.method === 'GET' && (pathname === '/' || pathname === '/index.html')) {
    const html = fs.readFileSync(INDEX_HTML_PATH, 'utf-8');
    res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
    return res.end(html);
  }

  sendJson(res, 404, { error: 'Not Found' });
});

server.listen(PORT, '0.0.0.0', () => {
  console.log(`\n========================================================`);
  console.log(`  观影中心 (MovieHub) 独立源站运维中心已启动`);
  console.log(`  可视化管理后台: http://127.0.0.1:${PORT}/`);
  console.log(`  客户端同步地址: http://127.0.0.1:${PORT}/sources.json`);
  console.log(`========================================================\n`);
});
