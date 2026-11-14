"""도트 친구 후보를 브라우저로 비교하는 페이지(friends.html)를 만든다. 확정되면 Pixel.kt로 옮긴다."""
import json
import pathlib

from icon_gallery import scale2x

ROOT = pathlib.Path(__file__).resolve().parent.parent

FRIENDS = {
    "현댕이": [
        "................",
        ".....KKKKKK.....",
        "...KKAAAAAAKK...",
        "..KAAAAAAAAAAK..",
        ".KNKAAAAAAAAKNK.",
        "KNNKAKWAAKWAKNNK",
        "KNNKAKKAAKKAKNNK",
        "KNNKAAAAAAAAKNNK",
        ".KNKPAAKKAAPKNK.",
        ".KNKAAAAAAAAKNK.",
        "..KKAAAPPAAAKK..",
        "...KRRRRRRRRK...",
        "..KAAAAAAAAAAK..",
        "..KAAAAAAAAAAK..",
        "..KAAAAAAAAAAK..",
        "..KAAAAAAAAAAK..",
        "..KAAAAAAAAAAK..",
        "..KKKKKKKKKKKK..",
    ],
    "현냥이": [
        ".KK..........KK.",
        ".KAK........KAK.",
        ".KAPK......KPAK.",
        ".KAAPKKKKKKPAAK.",
        "KAAAAAAAAAAAAAAK",
        "KAAAAAAAAAAAAAAK",
        "KAAAKWAAAAKWAAAK",
        "KAAAKKAAAAKKAAAK",
        "KAPAAAAPPAAAAPAK",
        "KAAAAAKAAKAAAAAK",
        ".KAAAAAKKAAAAAK.",
        "..KKAAAAAAAAKK..",
        "..KAAAAAAAAAAK..",
        "..KAAAAAAAAAAK..",
        "..KAAAAAAAAAAK..",
        "..KAAAAAAAAAAK..",
        "..KAAAAAAAAAAK..",
        "..KKKKKKKKKKKK..",
    ],
    "현룡이": [
        ".....KK..KK.....",
        "....KOOKKOOK....",
        "...KKAAAAAAKK...",
        "..KAAAAAAAAAAK..",
        ".KAAAAAAAAAAAAK.",
        ".KAAKWAAAAKWAAK.",
        ".KAAKKAAAAKKAAK.",
        ".KAPAAAAAAAAPAK.",
        ".KAAAKAAAAKAAAK.",
        "..KAAAKKKKAAAK..",
        "...KKAAAAAAKK...",
        "..KKAAAAAAAAKK..",
        "..KAAAAAAAAAAK..",
        "..KAAAAAAAAAAK..",
        "..KAAAAAAAAAAK..",
        "..KAAAAAAAAAAK..",
        "..KAAAAAAAAAAK..",
        "..KKKKKKKKKKKK..",
    ],
}
COLORS = {
    "현댕이": ["#F2C38B", "#FFFFFF", "#C98A55"],
    "현냥이": ["#FFB067", "#C3C8D2", "#FFE7C2"],
    "현룡이": ["#6CD18B", "#8EC2FF", "#C9A2FF"],
}
PALETTE = {"K": "#1F2433", "W": "#FFFFFF", "N": "#8B5A2B", "P": "#FF9AA8", "Y": "#FFD23F", "R": "#FF5A4E", "O": "#FF9A1F", "C": "#FFF1D6", "V": "#002C5F"}

for name, rows in FRIENDS.items():
    assert all(len(r) == 16 for r in rows) and len(rows) == 18, (name, [r for r in rows if len(r) != 16])

# 현대 엠블럼: 위키미디어 공용의 현대자동차 로고 SVG를 18×10으로 줄여 손으로 다듬은 것.
# 다듬은 친구 그림(64×72) 위 배 자리에 그대로 얹는다(Scale2x를 거치면 H가 뭉개져서).
EMBLEM = [
    ".....VVVVVVVV.....",
    "...VV.VV.....VVV..",
    ".VV..VV......VVVV.",
    "V....VV....VVVV..V",
    "V...VVVVVVVVVVV..V",
    "V...VVVVVVVVVV...V",
    "V..VVVV....VVV...V",
    ".VVVV......VV...V.",
    "..VVV.....VV..VV..",
    ".....VVVVVVVV.....",
]
EMBLEM_TOP, EMBLEM_LEFT = 53, 23


def with_logo(rows):
    out = [list(r) for r in scale2x(scale2x(rows))]
    for y, line in enumerate(EMBLEM):
        for x, ch in enumerate(line):
            if ch == "V":
                out[EMBLEM_TOP + y][EMBLEM_LEFT + x] = "V"
    return ["".join(r) for r in out]


# 표정: 앱의 Friends.rows(mood, headRoom=true)와 같은 규칙(머리 위 6줄 장식 자리)
EYES = {"현댕이": ([(5, 5), (5, 9)], 10), "현냥이": ([(6, 4), (6, 10)], 11), "현룡이": ([(5, 4), (5, 10)], 10)}
UMBRELLA = ["........K.......", "....KKRRRRKK....", "L.KKRRWRRWRRKK..", ".KRRRRWRRWRRRRK.", "KKKKKKKKKKKKKKKK", "L.......K.....L."]
SPARKLE, ZZZ, HEART = [".Y.", "YYY", ".Y."], ["KKKK", "..K.", ".K..", "KKKK"], ["R.R", "RRR", ".R."]
MOODS = {"NORMAL": "보통", "JOY": "기쁨", "UMBRELLA": "우산", "SLEEPY": "졸림", "YUM": "냠냠"}


def mood_rows(name, mood):
    raw = [list("." * 16) for _ in range(6)] + [list(r) for r in FRIENDS[name]]
    def put(top, left, art):
        for y, line in enumerate(art):
            for x, ch in enumerate(line):
                if ch != ".":
                    raw[top + y][left + x] = ch
    eyes, tongue = EYES[name]
    def eye(a, b):
        for r, c in eyes:
            put(6 + r, c - 1, [a, b])
    if mood == "JOY":
        eye("AKKA", "KAAK"); put(2, 0, SPARKLE); put(2, 13, SPARKLE)
    elif mood == "UMBRELLA":
        put(1, 0, UMBRELLA)
    elif mood == "SLEEPY":
        eye("AAAA", "KKKK"); put(0, 11, ZZZ)
    elif mood == "YUM":
        eye("AKKA", "KAAK"); put(6 + tongue, 7, ["PP"]); put(2, 12, HEART)
    out = [list(r) for r in scale2x(scale2x(["".join(r) for r in raw]))]
    for y, line in enumerate(EMBLEM):
        for x, ch in enumerate(line):
            if ch == "V":
                out[EMBLEM_TOP + 24 + y][EMBLEM_LEFT + x] = "V"
    return ["".join(r) for r in out]


# 키우기 단계별 머리 장식(앱 Friends.HATS와 같은 그림). 머리 꼭대기 바로 위에 얹는다.
HEAD_TOP = {"현댕이": 1, "현냥이": 3, "현룡이": 1}
HATS = {
    "sprout": [".KK..KK.", "KEEKKEEK", ".KKEEKK.", "...KK..."],
    "star": ["...KK...", "..KYYK..", "KKYYYYKK", ".KYYYYK.", "KYYKKYYK", "KKK..KKK"],
    "cap": ["..KKKK....", ".KDDDDK...", "KDDWWDDK..", "KKKKKKKKKK"],
    "crown": ["K..KK..K", "KYKYYKYK", "KYYRRYYK", "KKKKKKKK"],
}
LEVELS = [("아기", None), ("새싹", "sprout"), ("반짝별", "star"), ("탐험가", "cap"), ("길동무 대장", "crown")]


def level_rows(name, hat):
    raw = [list("." * 16) for _ in range(6)] + [list(r) for r in FRIENDS[name]]
    if hat:
        art = HATS[hat]
        bottom = 6 + HEAD_TOP[name] - 1
        for y, line in enumerate(art):
            for x, ch in enumerate(line):
                if ch != ".":
                    raw[bottom - len(art) + 1 + y][4 + x] = ch
    out = [list(r) for r in scale2x(scale2x(["".join(r) for r in raw]))]
    for y, line in enumerate(EMBLEM):
        for x, ch in enumerate(line):
            if ch == "V":
                out[EMBLEM_TOP + 24 + y][EMBLEM_LEFT + x] = "V"
    return ["".join(r) for r in out]


PALETTE.update({"L": "#7FD3F7", "E": "#5CC97B", "D": "#2E6BE6"})
data = [{"name": n, "rows": with_logo(r), "colors": COLORS[n],
         "moods": [[MOODS[m], mood_rows(n, m)] for m in MOODS],
         "levels": [[lv, level_rows(n, h)] for lv, h in LEVELS]} for n, r in FRIENDS.items()]
html = """<!doctype html><html lang="ko"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1"><title>도트 친구 후보</title>
<style>
body{margin:0;background:#FFF8EF;color:#1F2433;font-family:system-ui,"Noto Sans KR",sans-serif;padding:32px 16px}
main{max-width:1100px;margin:0 auto}h1{font-size:30px;margin:0 0 6px}p{color:#7A7F8C;margin:0 0 24px}
.row{display:grid;grid-template-columns:repeat(auto-fit,minmax(300px,1fr));gap:16px}
.card{background:#fff;border-radius:24px;padding:22px;box-shadow:0 3px 12px rgba(31,36,51,.08);text-align:center}
.card h2{margin:0 0 14px;font-size:22px}.hero canvas{width:180px;height:202px;image-rendering:pixelated}
.swatch{display:flex;gap:12px;justify-content:center;margin-top:12px}
.swatch div{width:72px;height:72px;border-radius:50%;display:flex;align-items:center;justify-content:center;background:#F6F0E6}
.swatch canvas{width:48px;height:54px;image-rendering:pixelated}
.moods{display:flex;gap:6px;justify-content:center;margin-top:18px;border-top:1px solid #F0E8DC;padding-top:14px}
.moods div{display:flex;flex-direction:column;align-items:center;gap:4px;font-size:13px;color:#7A7F8C}
.moods canvas{width:60px;height:90px;image-rendering:pixelated}
</style></head><body><main>
<h1>현대 친구들</h1><p>아이가 하나 골라 키우는 친구. 동그라미는 색 바꾸기 예시(아이가 고르면 차 무드등도 이 색). 아래는 표정 5가지, 맨 아래는 키우기 5단계(머리 장식).</p>
<div class="row" id="row"></div></main>
<script>
const P=__P__, D=__D__;
function draw(rows,body){const c=document.createElement("canvas");c.width=rows[0].length;c.height=rows.length;const g=c.getContext("2d");
rows.forEach((r,y)=>[...r].forEach((ch,x)=>{if(ch===".")return;g.fillStyle=ch==="A"?body:P[ch];g.fillRect(x,y,1,1);}));return c;}
D.forEach(f=>{const card=document.createElement("div");card.className="card";card.innerHTML=`<h2>${f.name}</h2>`;
const hero=document.createElement("div");hero.className="hero";hero.appendChild(draw(f.rows,f.colors[0]));card.appendChild(hero);
const sw=document.createElement("div");sw.className="swatch";f.colors.forEach(col=>{const d=document.createElement("div");d.appendChild(draw(f.rows,col));sw.appendChild(d);});
card.appendChild(sw);
const md=document.createElement("div");md.className="moods";f.moods.forEach(([label,rows])=>{const d=document.createElement("div");d.appendChild(draw(rows,f.colors[0]));const s=document.createElement("span");s.textContent=label;d.appendChild(s);md.appendChild(d);});
card.appendChild(md);
const lv=document.createElement("div");lv.className="moods";f.levels.forEach(([label,rows])=>{const d=document.createElement("div");d.appendChild(draw(rows,f.colors[0]));const s=document.createElement("span");s.textContent=label;d.appendChild(s);lv.appendChild(d);});
card.appendChild(lv);document.getElementById("row").appendChild(card);});
</script></body></html>"""
out = ROOT.parent / "friends.html"
out.write_text(html.replace("__P__", json.dumps(PALETTE)).replace("__D__", json.dumps(data, ensure_ascii=False)), encoding="utf-8")
print(out)
