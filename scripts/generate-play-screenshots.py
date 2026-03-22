from __future__ import annotations

from pathlib import Path
from PIL import Image, ImageDraw, ImageFont


ROOT = Path(__file__).resolve().parents[1]
OUT_DIR = ROOT / "play-assets" / "screenshots-phone-zhCN"
W, H = 1080, 1920


def load_font(size: int, bold: bool = False) -> ImageFont.FreeTypeFont:
    candidates = []
    if bold:
        candidates.extend(
            [
                r"C:\Windows\Fonts\msyhbd.ttc",
                r"C:\Windows\Fonts\simhei.ttf",
            ]
        )
    candidates.extend(
        [
            r"C:\Windows\Fonts\msyh.ttc",
            r"C:\Windows\Fonts\simsun.ttc",
            r"C:\Windows\Fonts\arial.ttf",
        ]
    )
    for path in candidates:
        p = Path(path)
        if p.exists():
            return ImageFont.truetype(str(p), size=size)
    return ImageFont.load_default()


FONT_H1 = load_font(66, bold=True)
FONT_H2 = load_font(42, bold=True)
FONT_H3 = load_font(34, bold=True)
FONT_BODY = load_font(30)
FONT_SMALL = load_font(24)


def gradient_bg(top: tuple[int, int, int], bottom: tuple[int, int, int]) -> Image.Image:
    img = Image.new("RGB", (W, H), top)
    draw = ImageDraw.Draw(img)
    for y in range(H):
        t = y / (H - 1)
        r = int(top[0] + (bottom[0] - top[0]) * t)
        g = int(top[1] + (bottom[1] - top[1]) * t)
        b = int(top[2] + (bottom[2] - top[2]) * t)
        draw.line([(0, y), (W, y)], fill=(r, g, b))
    return img


def card(draw: ImageDraw.ImageDraw, xy, fill=(255, 255, 255), radius=28, outline=None):
    draw.rounded_rectangle(xy, radius=radius, fill=fill, outline=outline, width=2 if outline else 1)


def chip(
    draw: ImageDraw.ImageDraw,
    x: int,
    y: int,
    text: str,
    bg=(227, 242, 253),
    fg=(24, 91, 160),
):
    tw = draw.textlength(text, font=FONT_SMALL)
    w = int(tw) + 36
    h = 44
    card(draw, (x, y, x + w, y + h), fill=bg, radius=18)
    draw.text((x + 18, y + 10), text, font=FONT_SMALL, fill=fg)
    return w + 12


def draw_top_title(draw: ImageDraw.ImageDraw, title: str, subtitle: str):
    draw.text((72, 72), title, font=FONT_H1, fill=(255, 255, 255))
    draw.text((72, 152), subtitle, font=FONT_BODY, fill=(235, 245, 255))


def draw_phone_canvas(
    top: tuple[int, int, int],
    bottom: tuple[int, int, int],
    title: str,
    subtitle: str,
):
    img = gradient_bg(top, bottom)
    draw = ImageDraw.Draw(img)
    draw_top_title(draw, title, subtitle)
    return img, draw


def screenshot_01_dashboard():
    img, draw = draw_phone_canvas(
        (14, 69, 141),
        (30, 132, 226),
        "远程掌控",
        "实时查看全局传输与种子状态",
    )
    card(draw, (54, 270, W - 54, H - 90), fill=(248, 252, 255), radius=34)
    draw.text((96, 320), "qbremote", font=FONT_H2, fill=(18, 48, 93))
    draw.text((96, 374), "全局传输", font=FONT_BODY, fill=(80, 107, 143))

    card(draw, (90, 444, W - 90, 620), fill=(233, 244, 255), radius=24)
    draw.text((120, 476), "下载速度", font=FONT_SMALL, fill=(74, 109, 145))
    draw.text((120, 520), "32.8 MB/s", font=FONT_H2, fill=(19, 97, 196))
    draw.text((560, 476), "上传速度", font=FONT_SMALL, fill=(74, 109, 145))
    draw.text((560, 520), "5.7 MB/s", font=FONT_H2, fill=(0, 126, 88))

    x = 96
    for t in ["下载中 12", "上传中 28", "暂停中 7", "错误 0"]:
        x += chip(draw, x, 654, t)

    y = 736
    items = [
        ("Ubuntu-24.04.iso", "下载中  |  11.2 MB/s"),
        ("Movie.Collection.4K", "做种中  |  2.6 MB/s"),
        ("dev-packages-2026", "校验中"),
    ]
    for name, st in items:
        card(draw, (90, y, W - 90, y + 150), fill=(255, 255, 255), radius=20, outline=(220, 232, 244))
        draw.text((118, y + 28), name, font=FONT_H3, fill=(20, 44, 78))
        draw.text((118, y + 84), st, font=FONT_SMALL, fill=(76, 110, 148))
        y += 170
    return img


def screenshot_02_unified_sort():
    img, draw = draw_phone_canvas(
        (7, 73, 118),
        (4, 104, 78),
        "统一排序",
        "所有种子同一列表，按你选择的规则排序",
    )
    card(draw, (54, 270, W - 54, H - 90), fill=(246, 253, 250), radius=34)

    card(draw, (90, 332, W - 90, 414), fill=(233, 248, 241), radius=18)
    draw.text((112, 358), "搜索：名称 / 标签 / 分类 / Hash / 路径", font=FONT_SMALL, fill=(58, 109, 90))

    card(draw, (90, 432, W - 90, 524), fill=(223, 245, 233), radius=18)
    draw.text((112, 458), "排序：做种人数（降序）", font=FONT_BODY, fill=(19, 90, 62))
    draw.text((700, 458), "自动置顶", font=FONT_SMALL, fill=(16, 122, 82))

    y = 560
    items = [
        ("ArchLinux-2026-x86_64", "做种 1420  下载 211", "辅种 5"),
        ("AI.Dataset.Bundle", "做种 830  下载 122", "辅种 3"),
        ("Fedora-Workstation", "做种 605  下载 99", "辅种 2"),
        ("NodeJS_Mirror", "做种 511  下载 76", "辅种 1"),
    ]
    for name, stats, cross in items:
        card(draw, (90, y, W - 90, y + 170), fill=(255, 255, 255), radius=20, outline=(210, 236, 223))
        draw.text((116, y + 28), name, font=FONT_H3, fill=(24, 61, 43))
        draw.text((116, y + 84), stats, font=FONT_SMALL, fill=(64, 116, 95))
        chip(draw, W - 250, y + 102, cross, bg=(232, 245, 255), fg=(20, 88, 151))
        y += 184
    return img


def screenshot_03_detail_tabs():
    img, draw = draw_phone_canvas(
        (29, 49, 117),
        (74, 99, 183),
        "完整详情页",
        "信息 / 服务器 / 用户 / 文件 一页管理",
    )
    card(draw, (54, 270, W - 54, H - 90), fill=(248, 250, 255), radius=34)

    x = 96
    for i, tab in enumerate(["信息", "服务器", "用户", "文件"]):
        bg = (32, 89, 184) if i == 0 else (228, 236, 252)
        fg = (255, 255, 255) if i == 0 else (43, 70, 120)
        x += chip(draw, x, 328, tab, bg=bg, fg=fg)

    y = 410
    rows = [
        ("名称", "Movie.Collection.4K"),
        ("保存路径", "/downloads/movies/4k"),
        ("分类", "Movies"),
        ("标签", "4k, hdr, favorite"),
        ("上传限速", "2 MB/s"),
        ("下载限速", "10 MB/s"),
        ("分享率", "1.50"),
    ]
    for k, v in rows:
        card(draw, (90, y, W - 90, y + 110), fill=(255, 255, 255), radius=16, outline=(222, 230, 246))
        draw.text((112, y + 24), k, font=FONT_SMALL, fill=(88, 104, 136))
        draw.text((300, y + 24), v, font=FONT_BODY, fill=(31, 50, 92))
        y += 126
    return img


def screenshot_04_add_torrent():
    img, draw = draw_phone_canvas(
        (115, 45, 153),
        (72, 63, 170),
        "快速添加种子",
        "支持磁力、URL、.torrent 文件与高级选项",
    )
    card(draw, (54, 270, W - 54, H - 90), fill=(252, 248, 255), radius=34)
    draw.text((92, 332), "添加种子", font=FONT_H2, fill=(84, 47, 135))

    card(draw, (90, 392, W - 90, 550), fill=(244, 236, 255), radius=18)
    draw.text((112, 420), "种子链接", font=FONT_BODY, fill=(99, 60, 144))
    draw.text((112, 468), "magnet:?xt=urn:btih:....", font=FONT_SMALL, fill=(117, 95, 146))
    draw.text((112, 504), "https://example.com/file.torrent", font=FONT_SMALL, fill=(117, 95, 146))

    card(draw, (90, 570, W - 90, 662), fill=(244, 236, 255), radius=18)
    draw.text((112, 600), "已选文件: ubuntu.torrent  +  movie_pack.torrent", font=FONT_SMALL, fill=(99, 60, 144))

    y = 690
    options = [
        "自动种子管理   开",
        "添加后暂停     关",
        "跳过哈希校验   关",
        "按顺序下载     开",
        "先下载首尾块   开",
    ]
    for op in options:
        card(draw, (90, y, W - 90, y + 86), fill=(255, 255, 255), radius=14, outline=(227, 216, 244))
        draw.text((112, y + 26), op, font=FONT_BODY, fill=(74, 51, 113))
        y += 98

    card(draw, (90, H - 250, W - 90, H - 160), fill=(115, 60, 178), radius=24)
    draw.text((W // 2 - 66, H - 224), "添加", font=FONT_H2, fill=(255, 255, 255))
    return img


def screenshot_05_multi_server():
    img, draw = draw_phone_canvas(
        (11, 88, 125),
        (6, 142, 177),
        "多服务器管理",
        "保存多个 qBittorrent 节点，一键切换",
    )
    card(draw, (54, 270, W - 54, H - 90), fill=(244, 251, 254), radius=34)
    draw.text((92, 332), "服务器管理", font=FONT_H2, fill=(18, 96, 134))

    y = 402
    servers = [
        ("家庭 NAS", "192.168.1.12:8080", True),
        ("办公室节点", "10.10.1.33:8080", False),
        ("云端备份", "qb.example.com:443", False),
    ]
    for name, host, current in servers:
        card(draw, (90, y, W - 90, y + 150), fill=(255, 255, 255), radius=18, outline=(212, 232, 242))
        draw.text((114, y + 26), name, font=FONT_H3, fill=(24, 85, 116))
        draw.text((114, y + 84), host, font=FONT_SMALL, fill=(86, 130, 152))
        if current:
            chip(draw, W - 260, y + 94, "当前", bg=(215, 244, 231), fg=(20, 117, 74))
        else:
            chip(draw, W - 260, y + 94, "切换", bg=(226, 240, 255), fg=(24, 93, 165))
        y += 170

    card(draw, (90, 980, W - 90, 1260), fill=(233, 247, 253), radius=18)
    draw.text((112, 1010), "新增服务器", font=FONT_H3, fill=(18, 96, 134))
    draw.text((112, 1070), "名称：下载机-4", font=FONT_BODY, fill=(41, 107, 139))
    draw.text((112, 1118), "主机：172.16.0.21   端口：8080", font=FONT_SMALL, fill=(77, 126, 148))
    draw.text((112, 1156), "用户名：admin      HTTPS：关", font=FONT_SMALL, fill=(77, 126, 148))
    card(draw, (112, 1188, 396, 1240), fill=(18, 112, 157), radius=12)
    draw.text((155, 1202), "保存并连接", font=FONT_SMALL, fill=(255, 255, 255))
    return img


def screenshot_06_dark_mode():
    img = gradient_bg((20, 23, 32), (32, 38, 56))
    draw = ImageDraw.Draw(img)
    draw_top_title(draw, "深色主题", "夜间使用更舒适，双击顶部快速回到最上方")
    card(draw, (54, 270, W - 54, H - 90), fill=(17, 22, 33), radius=34, outline=(44, 57, 82))

    card(draw, (90, 330, W - 90, 488), fill=(25, 33, 49), radius=18)
    draw.text((112, 356), "全局传输", font=FONT_BODY, fill=(170, 195, 230))
    draw.text((112, 404), "下载 28.4 MB/s   上传 4.9 MB/s", font=FONT_SMALL, fill=(120, 160, 215))

    x = 96
    for t in ["下载中 9", "上传中 26", "暂停中 4"]:
        x += chip(draw, x, 514, t, bg=(33, 45, 66), fg=(160, 190, 232))

    y = 590
    torrents = [
        ("Game.Backup.Collection", "下载中 | 8.1 MB/s"),
        ("Linux.ISO.Mirror", "做种中 | 1.2 MB/s"),
        ("Family.Photo.Archive", "已暂停"),
        ("Work.Sync.Package", "等待中"),
    ]
    for name, st in torrents:
        card(draw, (90, y, W - 90, y + 148), fill=(24, 31, 45), radius=16, outline=(45, 59, 86))
        draw.text((114, y + 28), name, font=FONT_H3, fill=(218, 229, 247))
        draw.text((114, y + 82), st, font=FONT_SMALL, fill=(142, 168, 210))
        y += 165
    return img


def main():
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    shots = [
        ("01_remote_dashboard.png", screenshot_01_dashboard()),
        ("02_unified_sorting.png", screenshot_02_unified_sort()),
        ("03_torrent_detail_tabs.png", screenshot_03_detail_tabs()),
        ("04_add_torrent.png", screenshot_04_add_torrent()),
        ("05_multi_server_profiles.png", screenshot_05_multi_server()),
        ("06_dark_theme_top_jump.png", screenshot_06_dark_mode()),
    ]
    for name, img in shots:
        img.save(OUT_DIR / name, format="PNG", optimize=True)
    print(f"Generated {len(shots)} screenshots in: {OUT_DIR}")


if __name__ == "__main__":
    main()
