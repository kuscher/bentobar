// SPDX-License-Identifier: MIT
// Puts a signed App Bundle on Google Play as a DRAFT release (closed testing by default), with the
// release notes from the store kit. A draft is never served or reviewed: sending it for review
// stays a button in the Play Console. No dependencies (Node 20+); prints no secret.
//
//   node tools/play-upload.mjs <package> <app.aab> <release-notes.txt> [--name 1.2.3] [--track alpha]
//   node tools/play-upload.mjs <package> --check     only: the key works and the app is reachable
//
// The service-account key comes from PLAY_SERVICE_ACCOUNT_JSON (the JSON text; the release workflow
// passes the secret) or PLAY_SERVICE_ACCOUNT_FILE (a path).
import { readFileSync } from 'node:fs';
import { createSign } from 'node:crypto';

const args = process.argv.slice(2);
const opt = (name, fallback) => { const i = args.indexOf(name); return i < 0 ? fallback : args.splice(i, 2)[1]; };
const flag = (name) => { const i = args.indexOf(name); if (i < 0) return false; args.splice(i, 1); return true; };
const track = opt('--track', 'alpha');
const name = opt('--name', null);
const check = flag('--check');
const [pkg, aab, notesFile] = args;
const fail = (m) => { console.error(m); process.exit(1); };
if (!pkg || (!check && (!aab || !notesFile))) fail('usage: node tools/play-upload.mjs <package> <app.aab> <release-notes.txt> [--name 1.2.3] [--track alpha] | <package> --check');

const keyText = process.env.PLAY_SERVICE_ACCOUNT_JSON || (process.env.PLAY_SERVICE_ACCOUNT_FILE && readFileSync(process.env.PLAY_SERVICE_ACCOUNT_FILE, 'utf8'));
if (!keyText) fail('no Play key: set PLAY_SERVICE_ACCOUNT_JSON or PLAY_SERVICE_ACCOUNT_FILE');
let key;
try { key = JSON.parse(keyText); } catch { fail('the Play key is not valid JSON'); }

async function token() {
  const now = Math.floor(Date.now() / 1000);
  const b64 = (o) => Buffer.from(JSON.stringify(o)).toString('base64url');
  const unsigned = `${b64({ alg: 'RS256', typ: 'JWT' })}.${b64({ iss: key.client_email, scope: 'https://www.googleapis.com/auth/androidpublisher', aud: key.token_uri, iat: now, exp: now + 3600 })}`;
  const sig = createSign('RSA-SHA256').update(unsigned).sign(key.private_key, 'base64url');
  const res = await fetch(key.token_uri, { method: 'POST', headers: { 'Content-Type': 'application/x-www-form-urlencoded' }, body: new URLSearchParams({ grant_type: 'urn:ietf:params:oauth:grant-type:jwt-bearer', assertion: `${unsigned}.${sig}` }) });
  const body = await res.json();
  if (!body.access_token) fail(`Google refused the Play key: ${res.status} ${body.error_description ?? body.error ?? ''}`);
  return body.access_token;
}

const API = `https://androidpublisher.googleapis.com/androidpublisher/v3/applications/${pkg}`;
const UP = `https://androidpublisher.googleapis.com/upload/androidpublisher/v3/applications/${pkg}`;
const auth = { Authorization: `Bearer ${await token()}` };
async function call(method, url, body, type = 'application/json') {
  const r = await fetch(url, { method, headers: { ...auth, ...(body ? { 'Content-Type': type } : {}) }, body: body && type === 'application/json' ? JSON.stringify(body) : body });
  const text = await r.text();
  if (!r.ok) throw new Error(`${method} ${url.replace(/.*applications\//, '')}: ${r.status} ${text.slice(0, 600)}`);
  return text ? JSON.parse(text) : {};
}
const show = (releases) => JSON.stringify((releases ?? []).map((r) => ({ name: r.name, v: r.versionCodes, status: r.status })));

const edit = await call('POST', `${API}/edits`, {});
const E = `${API}/edits/${edit.id}`;
try {
  const before = await call('GET', `${E}/tracks/${track}`).catch(() => ({ releases: [] }));
  if (check) {
    console.log(`${pkg}: the Play key works; ${track} has ${show(before.releases)}`);
    await call('DELETE', E);
    process.exit(0);
  }
  const text = readFileSync(notesFile, 'utf8').trim();
  if ([...text].length > 500) throw new Error(`${notesFile} has ${[...text].length} characters; Play allows 500`);
  const bundle = await call('POST', `${UP}/edits/${edit.id}/bundles?uploadType=media`, readFileSync(aab), 'application/octet-stream');
  console.log(`${pkg}: uploaded version code ${bundle.versionCode}`);
  // Keep what is live or rolling out; replace any earlier draft with this one.
  const kept = (before.releases ?? []).filter((r) => r.status !== 'draft');
  const draft = { ...(name ? { name } : {}), versionCodes: [String(bundle.versionCode)], status: 'draft', releaseNotes: [{ language: 'en-US', text }] };
  await call('PUT', `${E}/tracks/${track}`, { track, releases: [draft, ...kept] });
  const after = await call('GET', `${E}/tracks/${track}`);
  await call('POST', `${E}:commit`);
  console.log(`${pkg}: ${track} is now ${show(after.releases)}. The new release is a draft: send it for review in the Play Console.`);
} catch (e) {
  await call('DELETE', E).catch(() => {});
  const message = String(e.message ?? e);
  // A re-run of a release whose bundle is on Play already (uploaded by hand, or by the first run).
  if (/has already been used/i.test(message)) { console.log(`${pkg}: this version code is on Play already; nothing uploaded.`); process.exit(0); }
  fail(message);
}
