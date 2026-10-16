const fs = require("node:fs"), path = require("node:path"), {execFileSync} = require("node:child_process");
const root = path.resolve(__dirname, "../src/main/webapp");
fs.copyFileSync(require.resolve("dompurify/dist/purify.min.js"), path.join(root, "assets/js/vendor/purify.min.js"));
const css = path.join(__dirname, "input.css");
execFileSync(process.execPath, [require.resolve("tailwindcss/lib/cli.js"), "-c", path.join(__dirname,"tailwind.config.cjs"), "-i", css, "-o", path.join(root,"assets/css/tailwind.css"), "--minify"], {stdio:"inherit"});
