// Renders each SVG given on the command line to a PNG next to its target path, at 2x scale.
// Usage: node render.js in.svg out.png [in2.svg out2.png ...]
const { chromium } = require('playwright');
const fs = require('fs');
(async () => {
  const browser = await chromium.launch();
  const page = await browser.newPage({ deviceScaleFactor: 2 });
  const args = process.argv.slice(2);
  for (let i = 0; i < args.length; i += 2) {
    const svg = fs.readFileSync(args[i], 'utf8');
    const w = Number(/width="(\d+)"/.exec(svg)[1]);
    const h = Number(/height="(\d+)"/.exec(svg)[1]);
    await page.setViewportSize({ width: w, height: h });
    await page.setContent(`<html><body style="margin:0">${svg}</body></html>`);
    await page.screenshot({ path: args[i + 1], clip: { x: 0, y: 0, width: w, height: h } });
  }
  await browser.close();
})();
