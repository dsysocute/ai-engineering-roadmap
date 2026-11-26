const fs = require('fs');
const vm = require('vm');
let bad = 0;
for (const f of process.argv.slice(2)) {
  const src = fs.readFileSync(f, 'utf8');
  if (f.endsWith('.js')) {
    try { new vm.Script(src, { filename: f }); console.log('OK   ' + f); }
    catch (e) { bad++; console.log('FAIL ' + f + ' :: ' + e.message); }
    continue;
  }
  const re = /<script\b([^>]*)>([\s\S]*?)<\/script>/gi;
  let m, i = 0;
  while ((m = re.exec(src))) {
    i++;
    if (/\bsrc\s*=/.test(m[1])) continue;
    try { new vm.Script(m[2], { filename: f + '#' + i }); console.log('OK   ' + f + ' #' + i); }
    catch (e) { bad++; console.log('FAIL ' + f + ' #' + i + ' :: ' + e.message); }
  }
}
process.exit(bad ? 1 : 0);
