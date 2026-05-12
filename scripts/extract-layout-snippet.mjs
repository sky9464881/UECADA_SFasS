import fs from "fs";
const c = fs.readFileSync("dist/assets/index-C-hY4-44.js", "utf8");
const k = 'id:"line-a"';
const i = c.indexOf(k);
console.log("idx", i);
console.log(c.slice(Math.max(0, i - 100), i + 4500));
