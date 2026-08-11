import fs from 'fs';
import path from 'path';

interface CoverageData {
  pct: number;
  covered: number;
  total: number;
}

interface CoverageSummary {
  [key: string]: {
    lines: CoverageData;
    statements: CoverageData;
    branches: CoverageData;
    functions: CoverageData;
  };
}

const COVERAGE_THRESHOLD = 72; // lines must be >= this percentage

async function verifyCoverage(): Promise<void> {
  const coverageFile = path.join(process.cwd(), 'coverage', 'coverage-summary.json');

  if (!fs.existsSync(coverageFile)) {
    console.error('Coverage report not found at', coverageFile);
    process.exit(1);
  }

  const rawData = fs.readFileSync(coverageFile, 'utf-8');
  const coverage: CoverageSummary = JSON.parse(rawData);

  const totalCoverage = coverage.total;
  const lineCoveragePct = totalCoverage.lines.pct;

  console.log('Frontend Test Coverage Report:');
  console.log(`  Lines covered: ${totalCoverage.lines.covered}`);
  console.log(`  Lines total: ${totalCoverage.lines.total}`);
  console.log(`  Coverage: ${lineCoveragePct}%`);
  console.log(`  Threshold: ${COVERAGE_THRESHOLD}%`);

  if (lineCoveragePct < COVERAGE_THRESHOLD) {
    console.error(
      `\n✗ Frontend line coverage ${lineCoveragePct}% is below threshold of ${COVERAGE_THRESHOLD}%`
    );
    process.exit(1);
  }

  console.log('\n✓ Frontend coverage check passed!');
  process.exit(0);
}

verifyCoverage().catch((err) => {
  console.error('Error checking coverage:', err);
  process.exit(1);
});
