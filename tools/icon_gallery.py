"""Pixel.kt의 도트 그림을 모아 브라우저로 보는 갤러리(icons.html)를 만든다.

python tools/icon_gallery.py  →  ../icons.html
"""
import json
import pathlib
import re

ROOT = pathlib.Path(__file__).resolve().parent.parent
src = (ROOT / "app/src/main/java/kr/gildongmu/bingo/Pixel.kt").read_text(encoding="utf-8")

palette = dict(re.findall(r"'(\w)' to \"(#[0-9A-Fa-f]{6})\"", src))
sprites = {
    name: re.findall(r'"([^"]{12})"', body)
    for name, body in re.findall(r'"(\w+)" to listOf\((.*?)\n        \)', src, re.S)
}

# 그림 이름 → (빙고 칸 이름, 종류, 칸 색, 연한 색)
TONES = {
    "ROAD": ("#3D7BFF", "#E6EEFF"), "REST": ("#1DB271", "#E1F6EA"), "TOLL": ("#7B61FF", "#EEEAFF"),
    "SKY": ("#12A5DB", "#DFF4FC"), "FAMILY": ("#FF6B57", "#FFE6E1"), "FUN": ("#FF9A1F", "#FFF0DC"),
    "FOOD": ("#F0508C", "#FFE3EE"), "GOAL": ("#F2B705", "#FFF4CC"),
}
LABELS = {
    "road": ("고속도로 올라타기", "ROAD"), "exit": ("고속도로에서 내려오기", "ROAD"),
    "roundabout": ("회전교차로 돌기", "ROAD"), "toll": ("OO톨게이트", "TOLL"),
    "rest": ("OO휴게소 지나기", "REST"), "moon": ("졸음쉼터 지나기", "REST"),
    "cup": ("휴게소에서 쉬어 가기", "REST"), "rain": ("비 만나기", "SKY"), "tunnel": ("터널 통과", "SKY"),
    "blink": ("깜빡이 10번", "FUN"), "car": ("100km 달리기", "FUN"), "clock": ("1시간 달리기", "FUN"),
    "cow": ("창밖에서 소 찾기", "FUN"), "truck": ("빨간 트럭 찾기", "FUN"),
    "grandma": ("할머니께 여쭤보기", "FAMILY"), "grandpa": ("할아버지께 여쭤보기", "FAMILY"), "note": ("다 같이 노래 한 곡", "FAMILY"),
    "walnut": ("호두과자 사 먹기", "FOOD"), "sotteok": ("소떡소떡 사 먹기", "FOOD"),
    "potato": ("휴게소 감자 사 먹기", "FOOD"), "hotbar": ("핫바 사 먹기", "FOOD"),
    "udon": ("우동 사 먹기", "FOOD"), "coffee": ("아빠 커피 사 먹기", "FOOD"),
    "flag": ("도착!", "GOAL"),
}
GROUPS = [("FOOD", "휴게소 음식 (새로 추가)"), ("ROAD", "길"), ("TOLL", "톨게이트"), ("REST", "휴게소·쉼터"),
          ("SKY", "날씨·터널"), ("FUN", "놀이·기록"), ("FAMILY", "가족"), ("GOAL", "도착")]

def scale2x(rows):
    h, w = len(rows), len(rows[0])
    at = lambda y, x: rows[y][x] if 0 <= y < h and 0 <= x < w else "."
    out = []
    for y in range(h):
        top, bottom = [], []
        for x in range(w):
            e, b, d, f, hh = at(y, x), at(y - 1, x), at(y, x - 1), at(y, x + 1), at(y + 1, x)
            if b != hh and d != f:
                top += [d if d == b else e, f if b == f else e]
                bottom += [d if d == hh else e, f if hh == f else e]
            else:
                top += [e, e]; bottom += [e, e]
        out += ["".join(top), "".join(bottom)]
    return out


KEEP = {"car", "truck", "flag"}  # 촘촘한 무늬는 Scale2x가 뭉개서 그대로 키운다


def grow(rows):
    return [r2 for r in rows for r2 in ["".join(c * 2 for c in r)] * 2]


items = []
for name, rows in sprites.items():
    label, tone = LABELS.get(name, (name, "FUN"))
    color, soft = TONES[tone]
    items.append({"name": name, "label": label, "tone": tone, "color": color, "soft": soft, "rows": rows, "smooth": grow(grow(rows)) if name in KEEP else scale2x(scale2x(rows))})

html = """<!doctype html>
<html lang="ko"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>길동무 빙고 도트 아이콘</title>
<style>
:root{--bg:#FFF8EF;--ink:#1F2433;--sub:#7A7F8C}
*{box-sizing:border-box;margin:0;padding:0}
body{background:var(--bg);color:var(--ink);font-family:system-ui,"Apple SD Gothic Neo","Noto Sans KR",sans-serif;padding:32px 16px 64px}
main{max-width:1180px;margin:0 auto}
h1{font-size:32px;font-weight:800}
p.lead{color:var(--sub);margin:6px 0 28px;font-size:16px}
h2{font-size:20px;margin:28px 0 12px}
.grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(210px,1fr));gap:14px}
.card{background:#fff;border-radius:20px;padding:18px 14px;display:flex;flex-direction:column;gap:12px;align-items:center;text-align:center;box-shadow:0 3px 10px rgba(31,36,51,.07)}
.pair{display:flex;gap:10px}
.badge{width:64px;height:64px;border-radius:50%;display:flex;align-items:center;justify-content:center}
.badge canvas{width:44px;height:44px;image-rendering:pixelated}
.big{display:flex;gap:8px}
.big canvas{width:96px;height:96px;image-rendering:pixelated;background:repeating-conic-gradient(#f3eee6 0 25%,#fff 0 50%) 0 0/20px 20px;border-radius:10px;display:block}
.meta b{display:block;font-size:15px;word-break:keep-all}
.meta code{color:var(--sub);font-size:12px}
</style></head><body><main>
<h1>길동무 빙고 도트 아이콘</h1>
<p class="lead">12×12 도트 그림. 칸마다 왼쪽은 아직 안 채운 칸, 오른쪽은 채운 칸 모습. 큰 그림 왼쪽은 원본 12×12, 오른쪽은 앱에 들어가는 48×48(Scale2x 두 번, 차·트럭·깃발은 원본 4배).</p>
<div id="groups"></div>
</main>
<script>
const PALETTE = __PALETTE__, ITEMS = __ITEMS__, GROUPS = __GROUPS__;
function draw(c, rows, accent){const g=c.getContext("2d");rows.forEach((r,y)=>[...r].forEach((ch,x)=>{if(ch===".")return;g.fillStyle=ch==="A"?accent:PALETTE[ch];g.fillRect(x,y,1,1);}));}
function canvas(rows, accent){const c=document.createElement("canvas");c.width=c.height=rows.length;draw(c,rows,accent);return c;}
const root=document.getElementById("groups");
GROUPS.forEach(([tone,title])=>{
  const list=ITEMS.filter(i=>i.tone===tone); if(!list.length)return;
  const h=document.createElement("h2");h.textContent=title;root.appendChild(h);
  const grid=document.createElement("div");grid.className="grid";root.appendChild(grid);
  list.forEach(i=>{
    const card=document.createElement("div");card.className="card";
    const pair=document.createElement("div");pair.className="pair";
    const off=document.createElement("div");off.className="badge";off.style.background=i.soft;off.appendChild(canvas(i.smooth,i.color));
    const on=document.createElement("div");on.className="badge";on.style.background=i.color;on.appendChild(canvas(i.smooth,i.soft));
    pair.append(off,on);
    const meta=document.createElement("div");meta.className="meta";meta.innerHTML=`<b>${i.label}</b><code>${i.name}</code>`;
    const big=document.createElement("div");big.className="big";const raw=canvas(i.rows,i.color);raw.title="원본 12×12";big.append(raw,canvas(i.smooth,i.color));
    card.append(big,pair,meta);grid.appendChild(card);
  });
});
</script></body></html>"""
html = (html.replace("__PALETTE__", json.dumps(palette))
        .replace("__ITEMS__", json.dumps(items, ensure_ascii=False))
        .replace("__GROUPS__", json.dumps(GROUPS, ensure_ascii=False)))
out = ROOT.parent / "icons.html"
out.write_text(html, encoding="utf-8")
print(out, len(items), "icons")
