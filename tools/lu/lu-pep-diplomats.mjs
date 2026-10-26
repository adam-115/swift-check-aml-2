// Construit le fichier source unique de lu_pep_diplomats : les chefs de mission (ambassadeurs, chargés d'affaires)
// publiés sur la page « équipe » de chaque site *.mae.lu, réunis dans un seul JSON.
//
// Usage (depuis la racine du projet) :
//   node tools/lu/lu-pep-diplomats.mjs back/src/main/resources/data/lu/lu_pep_diplomats.json
//
// Les consulats généraux ne sont pas retenus : un consul général n'est pas une PEP
// (directive (UE) 2015/849, art. 3, point 9 e) : « les ambassadeurs, les chargés d'affaires »).
import { writeFileSync } from 'node:fs';

const TEAM_PAGES = [
  ...['abudhabi', 'addisababa', 'albania', 'ankara', 'armenia', 'athenes', 'bangkok', 'bosnia-herzegovina', 'kigali',
    'londres', 'moldova', 'montenegro', 'moscou', 'newdelhi', 'north-macedonia', 'pekin', 'prague', 'pristina',
    'seoul', 'serbia', 'singapour', 'tokyo', 'varsovie'].map(h => `https://${h}.mae.lu/en/ambassades/team.html`),
  'https://berlin.mae.lu/de/ambassades/team.html',
  ...['brasilia', 'bruxelles', 'cotonou', 'dakar', 'lisbonne', 'madrid', 'ottawa', 'paris', 'praia', 'rome',
    'saintsiege', 'sanjose', 'strasbourg'].map(h => `https://${h}.mae.lu/fr/ambassades/equipe.html`),
];

/** Fonction de chef de mission, en début de ligne (fr, en, de). */
const HEAD = /^(ambassadeur|ambassadrice|ambassador|botschafter(in)?|charg[ée]e? d.affaires|gesch[äa]ftstr[äa]ger(in)?|chef(fe)? de mission|chief of mission|acting head of mission|head of mission)\b/i;
const DEPUTY = /adjoint|deputy|stellvertret/i;
const CIVILITY = /^(S\.E\.M\.?(?=\s)|S\.E\.|H\.E\.|His Excellency|Her Excellency|Monsieur|Madame|Mme\.?|Mrs\.?|Ms\.?|Mr\.?|M\.|Mgr\.?|Herr|Frau)\s+/i;
const FEMALE = /\b(Madame|Mme|Mrs|Ms|Frau|Her Excellency)\b|ambassadrice|botschafterin|chargée|cheffe|gesch[äa]ftstr[äa]gerin/i;
const MALE = /\b(Monsieur|Mr|Herr|His Excellency)\b|(^|\s)M\.(\s|$)|^S\.E\.M\b/i;

function decode(s) {
  return s.replace(/&nbsp;|&#160;/g, ' ').replace(/&#39;|&rsquo;|&#8217;/g, "'").replace(/&amp;/g, '&')
    .replace(/&quot;/g, '"').replace(/&#(\d+);/g, (_, n) => String.fromCharCode(+n));
}

/** Texte du contenu principal, une ligne par bloc : après la date de mise à jour, avant les encadrés. */
function pageLines(html) {
  const main = (html.match(/<main[\s\S]*?<\/main>/) || [html])[0];
  const updated = main.search(/class="cmp-lastupdate/);
  const afterDate = updated < 0 ? main : main.slice(updated).replace(/^[\s\S]*?<\/p>/, '');
  const body = afterDate.split(/class="cmp-focus|class="cmp-contentbox|<div data-noindex/)[0];
  return decode(body
    .replace(/<(script|style)[\s\S]*?<\/\1>/gi, '')
    .replace(/<!--[\s\S]*?-->/g, '')
    .replace(/<(br|\/p|\/li|\/h\d|\/div|\/td|\/th|\/tr|\/strong|\/b|\/span)\b[^>]*>/gi, '\n')
    .replace(/<[^>]*>/g, ''))
    .split('\n')
    .map(s => s.replace(/\s+/g, ' ').trim())
    .filter(s => s && !/^(Normal \d|\/\*|table\.Mso|\( ?contact me ?\))/.test(s));
}

const roleOf = line => line.replace(/^[,\s]+/, '').replace(/^L'/, '').replace(/\s*:\s*$/, '').trim();
const isHead = line => HEAD.test(roleOf(line)) && !DEPUTY.test(line);
const looksLikeName = s => !!s && s.length <= 60 && !/[@:]/.test(s) && !HEAD.test(roleOf(s)) && /\p{Lu}/u.test(s);
const oneWord = s => !!s && !/\s/.test(s) && looksLikeName(s);

/** Chefs de mission d'une page : { name, role }. */
function heads(lines) {
  const found = [];
  lines.forEach((line, i) => {
    // « M. Roland Reiland, Ambassador »
    const inline = line.match(/^(.+?),\s*(.+)$/);
    if (!isHead(line) && inline && isHead(inline[2]) && looksLikeName(inline[1])) {
      found.push({ name: inline[1], role: inline[2] });
      return;
    }
    if (!isHead(line)) {
      return;
    }
    // Consulat : « Consul General | Chief of Mission | Nom »
    if (/consul/i.test(lines[i - 1] ?? '') && /mission/i.test(line)) {
      return;
    }
    let name = null;
    if (/:\s*$/.test(line)) {
      name = lines[i + 1];                                   // « Ambassador: | H.E. Mr. X »
    } else if (oneWord(lines[i - 1]) && oneWord(lines[i - 2])) {
      name = `${lines[i - 2]} ${lines[i - 1]}`;              // tableau « Prénom | NOM | Fonction »
    } else if (looksLikeName(lines[i - 1])) {
      name = lines[i - 1];                                   // « M. X | Ambassadeur »
    } else if (looksLikeName(lines[i + 1])) {
      name = lines[i + 1];
    }
    if (name && looksLikeName(name)) {
      found.push({ name, role: roleOf(line) });
    }
  });
  return found;
}

const slug = s => s.normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '');

const retrievedAt = new Date().toISOString().slice(0, 10);
const records = [];
const missing = [];

for (const url of TEAM_PAGES) {
  const res = await fetch(url, { headers: { 'User-Agent': 'SwiftCheckAML/1.0' } });
  if (!res.ok) {
    missing.push(`${url} (HTTP ${res.status})`);
    continue;
  }
  const html = await res.text();
  const title = decode((html.match(/<title>([\s\S]*?)<\/title>/) || [])[1] ?? '').trim();
  const mission = title.replace(/^[^-]*-\s*/, '').trim();
  const missionId = new URL(url).hostname.split('.')[0];
  const found = heads(pageLines(html));
  if (found.length === 0) {
    missing.push(url);
  }
  for (const { name: rawName, role } of found) {
    let name = rawName;
    while (CIVILITY.test(name)) {
      name = name.replace(CIVILITY, '');
    }
    const civility = rawName.slice(0, rawName.length - name.length).trim() || null;
    // Genre seulement s'il est publié (civilité ou fonction accordée), jamais déduit du prénom.
    const gender = FEMALE.test(civility ?? '') || FEMALE.test(role) ? 'female' : MALE.test(civility ?? '') ? 'male' : null;
    records.push({
      id: `${missionId}:${slug(name)}`,
      personId: slug(name),
      name,
      civility,
      gender,
      role,
      missionId,
      mission,
      positionName: `${role.replace(/\s+du Luxembourg.*$/i, '')} — ${mission}`,
      country: 'LU',
      sourceUrl: url,
      retrievedAt,
    });
  }
}

const [, , outFile] = process.argv;
const document = {
  source: 'lu_pep_diplomats',
  publisher: 'Ministère des Affaires étrangères et européennes (mae.lu)',
  retrievedAt,
  pages: TEAM_PAGES.length,
  records,
};
writeFileSync(outFile, JSON.stringify(document, null, 2) + '\n');
console.log(`${TEAM_PAGES.length} pages, ${records.length} chefs de mission -> ${outFile}`);
missing.forEach(m => console.log('  sans chef de mission : ' + m));
