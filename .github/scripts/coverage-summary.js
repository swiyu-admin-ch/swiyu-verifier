// SPDX-FileCopyrightText: 2025 Swiss Confederation
// SPDX-License-Identifier: MIT

// JaCoCo coverage summary generator.
// Reads module reports, computes per-module and combined coverage, writes to GITHUB_STEP_SUMMARY.
// Mirrors prior inline logic from workflow YAML, but uses standard Node APIs only.

const fs = require('fs');
const path = require('path');

// Configuration: list JaCoCo XML report paths with labels & category tags
const REPORTS = [
  { path: 'verifier-service/target/site/jacoco/jacoco.xml', label: 'Service Module (Unit Tests)', category: 'unit' },
  { path: 'verifier-application/target/site/jacoco/jacoco.xml', label: 'Application Module (Integration Tests)', category: 'integration' }
];

function fileExists(p) {
  try { return fs.statSync(p).isFile(); } catch { return false; }
}

function parseReport(file) {
  const xml = fs.readFileSync(file, 'utf8');
  const matches = [...xml.matchAll(/<counter type="LINE" missed="(\d+)" covered="(\d+)"\/>/g)];
  let missed = 0, covered = 0;
  for (const m of matches) { missed += +m[1]; covered += +m[2]; }
  const total = missed + covered;
  const pct = total === 0 ? 0 : (covered / total) * 100;
  return { missed, covered, pct };
}

function buildMarkdown(reports) {
  // Combined numbers
  const totalCovered = reports.reduce((a, r) => a + r.covered, 0);
  const totalMissed = reports.reduce((a, r) => a + r.missed, 0);
  const overall = (totalCovered + totalMissed) === 0 ? 0 : (totalCovered / (totalCovered + totalMissed)) * 100;

  let md = '### Test Coverage (Separated)\n';
  for (const r of reports) {
    md += `- ${r.label}: ${r.pct.toFixed(2)}% (covered ${r.covered}, missed ${r.missed})\n`;
  }
  md += `\n**Combined Overall:** ${overall.toFixed(2)}% (covered ${totalCovered}, missed ${totalMissed})`;
  md += '\n\n| Category | Coverage % | Covered | Missed |\n|----------|-----------:|--------:|-------:|';
  for (const r of reports) {
    md += `\n| ${r.category} | ${r.pct.toFixed(2)} | ${r.covered} | ${r.missed} |`;
  }
  md += `\n| overall | ${overall.toFixed(2)} | ${totalCovered} | ${totalMissed} |`;
  return md;
}

function main() {
  const existing = REPORTS.filter(r => fileExists(r.path));

  if (existing.length === 0) {
    const msg = '### Test Coverage\nNo JaCoCo reports found.';
    writeSummary(msg);
    console.warn('No JaCoCo reports found for summary');
    return;
  }

  // Parse and augment
  for (const r of existing) {
    Object.assign(r, parseReport(r.path));
  }

  const markdown = buildMarkdown(existing);
  writeSummary(markdown);
  console.log('Coverage summary written.');
}

function writeSummary(markdown) {
  const summaryFile = process.env.GITHUB_STEP_SUMMARY;
  if (summaryFile) {
    fs.appendFileSync(summaryFile, markdown + '\n');
  } else {
    // Fallback to console if not in GitHub Actions
    console.log(markdown);
  }
  // Also export a short plaintext variant for potential downstream steps
  const exportFile = path.join(process.cwd(), 'coverage-summary.md');
  try { fs.writeFileSync(exportFile, markdown + '\n'); } catch {}
}

main();

