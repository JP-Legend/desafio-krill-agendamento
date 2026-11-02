// Códigos de barras do vale-palete, copiados do Gestão CD (src/paletes-codigos.ts, transpilado sem alterações):
// Code128 (subconjunto B) e QR Code (modo byte, correção M), sem dependências, desenhados em SVG.
const CODE128_PATTERNS = [
  "212222",
  "222122",
  "222221",
  "121223",
  "121322",
  "131222",
  "122213",
  "122312",
  "132212",
  "221213",
  "221312",
  "231212",
  "112232",
  "122132",
  "122231",
  "113222",
  "123122",
  "123221",
  "223211",
  "221132",
  "221231",
  "213212",
  "223112",
  "312131",
  "311222",
  "321122",
  "321221",
  "312212",
  "322112",
  "322211",
  "212123",
  "212321",
  "232121",
  "111323",
  "131123",
  "131321",
  "112313",
  "132113",
  "132311",
  "211313",
  "231113",
  "231311",
  "112133",
  "112331",
  "132131",
  "113123",
  "113321",
  "133121",
  "313121",
  "211331",
  "231131",
  "213113",
  "213311",
  "213131",
  "311123",
  "311321",
  "331121",
  "312113",
  "312311",
  "332111",
  "314111",
  "221411",
  "431111",
  "111224",
  "111422",
  "121124",
  "121421",
  "141122",
  "141221",
  "112214",
  "112412",
  "122114",
  "122411",
  "142112",
  "142211",
  "241211",
  "221114",
  "413111",
  "241112",
  "134111",
  "111242",
  "121142",
  "121241",
  "114212",
  "124112",
  "124211",
  "411212",
  "421112",
  "421211",
  "212141",
  "214121",
  "412121",
  "111143",
  "111341",
  "131141",
  "114113",
  "114311",
  "411113",
  "411311",
  "113141",
  "114131",
  "311141",
  "411131",
  "211412",
  "211214",
  "211232",
  "2331112"
];
function code128Widths(text) {
  const clean = String(text || "").replace(/[^\x20-\x7e]/g, "");
  const values = [104, ...[...clean].map((ch) => ch.charCodeAt(0) - 32)];
  let checksum = 104;
  for (let i = 1; i < values.length; i += 1) checksum += values[i] * i;
  values.push(checksum % 103, 106);
  return values.flatMap((v) => [...CODE128_PATTERNS[v]].map(Number));
}
function code128Bars(text) {
  const widths = code128Widths(text);
  const bars = [];
  let x = 0;
  widths.forEach((w, i) => {
    if (i % 2 === 0) bars.push({ x, w });
    x += w;
  });
  return { bars, total: x };
}
function code128Svg(text, opts = {}) {
  const m = opts.module ?? 2;
  const h = opts.height ?? 60;
  const quiet = 10;
  const { bars, total } = code128Bars(text);
  const w = (total + quiet * 2) * m;
  return `<svg xmlns="http://www.w3.org/2000/svg" class="pal-barcode" viewBox="0 0 ${w} ${h}" width="${w}" height="${h}" role="img" aria-label="C\xF3digo de barras ${text.replace(/[<>&"]/g, "")}" shape-rendering="crispEdges"><rect width="${w}" height="${h}" fill="#fff"/>${bars.map((b) => `<rect x="${(b.x + quiet) * m}" y="0" width="${b.w * m}" height="${h}" fill="#000"/>`).join("")}</svg>`;
}
const ECC_M_PER_BLOCK = [-1, 10, 16, 26, 18, 24, 16, 18, 22, 22, 26, 30, 22, 22, 24, 24];
const ECC_M_BLOCKS = [-1, 1, 1, 1, 2, 2, 4, 4, 4, 5, 5, 5, 8, 9, 9, 10];
function gfMul(x, y) {
  let z = 0;
  for (let i = 7; i >= 0; i -= 1) {
    z = z << 1 ^ (z >>> 7) * 285;
    z ^= (y >>> i & 1) * x;
  }
  return z & 255;
}
function rsDivisor(degree) {
  const result = new Array(degree).fill(0);
  result[degree - 1] = 1;
  let root = 1;
  for (let i = 0; i < degree; i += 1) {
    for (let j = 0; j < result.length; j += 1) {
      result[j] = gfMul(result[j], root);
      if (j + 1 < result.length) result[j] ^= result[j + 1];
    }
    root = gfMul(root, 2);
  }
  return result;
}
function rsRemainder(data, divisor) {
  const result = divisor.map(() => 0);
  for (const b of data) {
    const factor = b ^ result.shift();
    result.push(0);
    divisor.forEach((coef, i) => {
      result[i] ^= gfMul(coef, factor);
    });
  }
  return result;
}
function rawDataModules(ver) {
  let result = (16 * ver + 128) * ver + 64;
  if (ver >= 2) {
    const n = Math.floor(ver / 7) + 2;
    result -= (25 * n - 10) * n - 55;
    if (ver >= 7) result -= 36;
  }
  return result;
}
function alignmentPositions(ver, size) {
  if (ver === 1) return [];
  const n = Math.floor(ver / 7) + 2;
  const step = Math.ceil((ver * 4 + 4) / (n * 2 - 2)) * 2;
  const result = [6];
  for (let pos = size - 7; result.length < n; pos -= step) result.splice(1, 0, pos);
  return result;
}
const bit = (x, i) => (x >>> i & 1) !== 0;
function qrMatrix(text) {
  const bytes = [...new TextEncoder().encode(String(text || ""))];
  let ver = 1;
  for (; ver <= 15; ver += 1) {
    const cap = Math.floor(rawDataModules(ver) / 8) - ECC_M_PER_BLOCK[ver] * ECC_M_BLOCKS[ver];
    const bitsNeeded = 4 + (ver < 10 ? 8 : 16) + bytes.length * 8;
    if (bitsNeeded <= cap * 8) break;
  }
  if (ver > 15) throw new Error("Texto longo demais para o QR.");
  const dataCap = Math.floor(rawDataModules(ver) / 8) - ECC_M_PER_BLOCK[ver] * ECC_M_BLOCKS[ver];
  const bits = [];
  const push = (val, len) => {
    for (let i = len - 1; i >= 0; i -= 1) bits.push(val >>> i & 1);
  };
  push(4, 4);
  push(bytes.length, ver < 10 ? 8 : 16);
  bytes.forEach((b) => push(b, 8));
  push(0, Math.min(4, dataCap * 8 - bits.length));
  push(0, (8 - bits.length % 8) % 8);
  const data = [];
  for (let i = 0; i < bits.length; i += 8) data.push(bits.slice(i, i + 8).reduce((a, b) => a << 1 | b, 0));
  for (let pad = 236; data.length < dataCap; pad ^= 236 ^ 17) data.push(pad);
  const numBlocks = ECC_M_BLOCKS[ver];
  const eccLen = ECC_M_PER_BLOCK[ver];
  const raw = Math.floor(rawDataModules(ver) / 8);
  const numShort = numBlocks - raw % numBlocks;
  const shortLen = Math.floor(raw / numBlocks);
  const div = rsDivisor(eccLen);
  const blocks = [];
  for (let i = 0, k = 0; i < numBlocks; i += 1) {
    const block = data.slice(k, k + shortLen - eccLen + (i < numShort ? 0 : 1));
    k += block.length;
    const ecc = rsRemainder(block, div);
    if (i < numShort) block.push(0);
    blocks.push(block.concat(ecc));
  }
  const codewords = [];
  for (let i = 0; i < blocks[0].length; i += 1) blocks.forEach((block, j) => {
    if (i !== shortLen - eccLen || j >= numShort) codewords.push(block[i]);
  });
  const size = ver * 4 + 17;
  const modules = Array.from({ length: size }, () => new Array(size).fill(false));
  const isFn = Array.from({ length: size }, () => new Array(size).fill(false));
  const set = (x, y, dark) => {
    modules[y][x] = dark;
    isFn[y][x] = true;
  };
  for (let i = 0; i < size; i += 1) {
    set(6, i, i % 2 === 0);
    set(i, 6, i % 2 === 0);
  }
  for (const [cx, cy] of [[3, 3], [size - 4, 3], [3, size - 4]]) {
    for (let dy = -4; dy <= 4; dy += 1) for (let dx = -4; dx <= 4; dx += 1) {
      const d = Math.max(Math.abs(dx), Math.abs(dy));
      const xx = cx + dx;
      const yy = cy + dy;
      if (xx >= 0 && xx < size && yy >= 0 && yy < size) set(xx, yy, d !== 2 && d !== 4);
    }
  }
  const al = alignmentPositions(ver, size);
  al.forEach((ax, i) => al.forEach((ay, j) => {
    if (i === 0 && j === 0 || i === 0 && j === al.length - 1 || i === al.length - 1 && j === 0) return;
    for (let dy = -2; dy <= 2; dy += 1) for (let dx = -2; dx <= 2; dx += 1) set(ax + dx, ay + dy, Math.max(Math.abs(dx), Math.abs(dy)) !== 1);
  }));
  const drawFormat = (mask) => {
    const d = 0 << 3 | mask;
    let rem = d;
    for (let i = 0; i < 10; i += 1) rem = rem << 1 ^ (rem >>> 9) * 1335;
    const b = (d << 10 | rem) ^ 21522;
    for (let i = 0; i <= 5; i += 1) set(8, i, bit(b, i));
    set(8, 7, bit(b, 6));
    set(8, 8, bit(b, 7));
    set(7, 8, bit(b, 8));
    for (let i = 9; i < 15; i += 1) set(14 - i, 8, bit(b, i));
    for (let i = 0; i < 8; i += 1) set(size - 1 - i, 8, bit(b, i));
    for (let i = 8; i < 15; i += 1) set(8, size - 15 + i, bit(b, i));
    set(8, size - 8, true);
  };
  drawFormat(0);
  if (ver >= 7) {
    let rem = ver;
    for (let i = 0; i < 12; i += 1) rem = rem << 1 ^ (rem >>> 11) * 7973;
    const b = ver << 12 | rem;
    for (let i = 0; i < 18; i += 1) {
      const a = size - 11 + i % 3;
      const c = Math.floor(i / 3);
      set(a, c, bit(b, i));
      set(c, a, bit(b, i));
    }
  }
  let idx = 0;
  for (let right = size - 1; right >= 1; right -= 2) {
    if (right === 6) right = 5;
    for (let vert = 0; vert < size; vert += 1) for (let j = 0; j < 2; j += 1) {
      const x = right - j;
      const upward = (right + 1 & 2) === 0;
      const y = upward ? size - 1 - vert : vert;
      if (!isFn[y][x] && idx < codewords.length * 8) {
        modules[y][x] = bit(codewords[idx >>> 3], 7 - (idx & 7));
        idx += 1;
      }
    }
  }
  const maskFn = (m, x, y) => [
    (x + y) % 2 === 0,
    y % 2 === 0,
    x % 3 === 0,
    (x + y) % 3 === 0,
    (Math.floor(x / 3) + Math.floor(y / 2)) % 2 === 0,
    x * y % 2 + x * y % 3 === 0,
    (x * y % 2 + x * y % 3) % 2 === 0,
    ((x + y) % 2 + x * y % 3) % 2 === 0
  ][m];
  const applyMask = (m) => {
    for (let y = 0; y < size; y += 1) for (let x = 0; x < size; x += 1) if (!isFn[y][x] && maskFn(m, x, y)) modules[y][x] = !modules[y][x];
  };
  const penalty = () => {
    let p = 0;
    let dark = 0;
    for (let y = 0; y < size; y += 1) {
      let runX = 1;
      let runY = 1;
      for (let x = 0; x < size; x += 1) {
        if (modules[y][x]) dark += 1;
        if (x > 0) {
          if (modules[y][x] === modules[y][x - 1]) {
            runX += 1;
            if (runX === 5) p += 3;
            else if (runX > 5) p += 1;
          } else runX = 1;
          if (modules[x][y] === modules[x - 1][y]) {
            runY += 1;
            if (runY === 5) p += 3;
            else if (runY > 5) p += 1;
          } else runY = 1;
        }
        if (x > 0 && y > 0 && modules[y][x] === modules[y - 1][x] && modules[y][x] === modules[y][x - 1] && modules[y][x] === modules[y - 1][x - 1]) p += 3;
      }
    }
    return p + Math.floor(Math.abs(dark * 20 - size * size * 10) / (size * size)) * 10;
  };
  let best = 0;
  let bestScore = Infinity;
  for (let m = 0; m < 8; m += 1) {
    applyMask(m);
    drawFormat(m);
    const s = penalty();
    if (s < bestScore) {
      bestScore = s;
      best = m;
    }
    applyMask(m);
  }
  applyMask(best);
  drawFormat(best);
  return modules;
}
function qrSvg(text, px = 120) {
  const m = qrMatrix(text);
  const n = m.length + 8;
  const path = m.flatMap((row, y) => row.map((dark, x) => dark ? `M${x + 4} ${y + 4}h1v1h-1z` : "")).join("");
  return `<svg xmlns="http://www.w3.org/2000/svg" class="pal-qr" viewBox="0 0 ${n} ${n}" width="${px}" height="${px}" role="img" aria-label="QR Code" shape-rendering="crispEdges"><rect width="${n}" height="${n}" fill="#fff"/><path d="${path}" fill="#000"/></svg>`;
}
export {
  code128Bars,
  code128Svg,
  code128Widths,
  qrMatrix,
  qrSvg

};
