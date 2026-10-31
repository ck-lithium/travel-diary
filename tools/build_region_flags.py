#!/usr/bin/env python3
"""Renders the region flags in app/src/main/assets/flags/regions, one WebP per GeoNames region.

Wikidata (CC0) gives the flag of each first-level region, found by its GeoNames id; the images come
from Wikimedia Commons. Only files with a licence that allows redistribution are kept: public
domain, CC0, CC BY and CC BY-SA. The authors and licences go to credits.tsv next to the images,
which the app lists in Settings > About.

Needs Python 3, rsvg-convert (librsvg) and ImageMagick with WebP support:
  python3 tools/build_region_flags.py --raw /tmp/travel-diary-raw --work /tmp/travel-diary-region-flags
"""
import argparse
import concurrent.futures
import html
import json
import os
import re
import shutil
import subprocess
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

USER_AGENT = "TravelDiaryBuild/1.0 (https://github.com/ck-lithium/travel-diary)"
WIKIDATA_SPARQL = "https://query.wikidata.org/sparql"
COMMONS_API = "https://commons.wikimedia.org/w/api.php"

# Flags keep their own proportions (square for Swiss cantons, 2:1 for many US states): the app
# fits them to each region. Regions are smaller than countries on screen, so a lower height than
# the 360 px country flags is enough.
HEIGHT = 240
QUALITY = 80

ALLOWED_LICENCES = re.compile(r"^(pd|cc0|cc-by-(sa-)?\d\.\d(-[a-z]+)?)$")
# A logo is not a flag, and its owner may object to it being shown as one.
EXCLUDED_RESTRICTIONS = {"trademarked"}
# Wikidata links a few regions to designs that were never adopted.
NOT_ADOPTED = re.compile(r"hypothetical|unofficial|proposed|proposal|fictional|fictitious", re.IGNORECASE)


def request(url, data=None, accept=None):
    headers = {"User-Agent": USER_AGENT}
    if accept:
        headers["Accept"] = accept
    body = urllib.parse.urlencode(data).encode() if data else None
    for attempt in range(6):
        try:
            with urllib.request.urlopen(urllib.request.Request(url, data=body, headers=headers), timeout=120) as response:
                return response.read()
        except OSError as error:
            if attempt == 5 or (isinstance(error, urllib.error.HTTPError) and error.code < 500 and error.code != 429):
                raise
            # Wikimedia answers 429 when downloads come too fast, and says how long to wait.
            retry_after = error.headers.get("Retry-After") if isinstance(error, urllib.error.HTTPError) else None
            wait = int(retry_after) if retry_after and retry_after.isdigit() else 10 * (attempt + 1)
            print(f"  waiting {wait} s before retrying {url[:80]}: {error}")
            time.sleep(wait)


def read_regions(raw):
    """GeoNames id -> region code, e.g. 3174618 -> IT.09."""
    regions = {}
    with open(os.path.join(raw, "admin1CodesASCII.txt"), encoding="utf-8") as f:
        for line in f:
            cols = line.rstrip("\n").split("\t")
            if len(cols) >= 4 and cols[3]:
                regions[cols[3]] = cols[0]
    return regions


def query_flags(regions):
    """Region code -> flag file names on Commons, from the Wikidata items with that GeoNames id."""
    ids = sorted(regions)
    flags = {}
    for start in range(0, len(ids), 400):
        values = " ".join(f'"{i}"' for i in ids[start:start + 400])
        query = f"SELECT ?gn ?flag WHERE {{ VALUES ?gn {{ {values} }} ?item wdt:P1566 ?gn; wdt:P41 ?flag. }}"
        rows = request(WIKIDATA_SPARQL, {"query": query}, accept="text/tab-separated-values").decode().strip().split("\n")
        for row in rows[1:]:
            geonames_id, flag = row.split("\t")
            name = urllib.parse.unquote(flag.strip("<>").rsplit("/", 1)[1])
            flags.setdefault(regions[geonames_id.strip('"')], set()).add(name)
        time.sleep(1)
    return flags


def plain_text(value):
    text = html.unescape(re.sub(r"<[^>]+>", " ", value or ""))
    return re.sub(r"\s+", " ", text).strip()


def commons_info(files):
    """File name -> licence, author and download URL."""
    info = {}
    names = sorted(files)
    for start in range(0, len(names), 50):
        batch = names[start:start + 50]
        data = json.loads(request(COMMONS_API + "?" + urllib.parse.urlencode({
            "action": "query", "format": "json", "formatversion": "2", "prop": "imageinfo",
            "iiprop": "extmetadata|url",
            "iiextmetadatafilter": "License|LicenseShortName|Artist|Restrictions",
            "titles": "|".join("File:" + name for name in batch),
        })))
        normalized = {n["to"]: n["from"] for n in data["query"].get("normalized", [])}
        for page in data["query"]["pages"]:
            title = normalized.get(page["title"], page["title"])
            image = (page.get("imageinfo") or [{}])[0]
            meta = image.get("extmetadata", {})
            info[title.removeprefix("File:")] = {
                "licence": (meta.get("License", {}).get("value") or "").lower(),
                "licence_name": plain_text(meta.get("LicenseShortName", {}).get("value")),
                "author": plain_text(meta.get("Artist", {}).get("value")),
                "restrictions": set(filter(None, (meta.get("Restrictions", {}).get("value") or "").split("|"))),
                # Commons adds tracking parameters to the file URL.
                "url": (image.get("url") or "").split("?")[0],
                "page": image.get("descriptionurl") or "https://commons.wikimedia.org/wiki/File:" + urllib.parse.quote(
                    title.removeprefix("File:").replace(" ", "_")),
            }
        time.sleep(0.5)
    return info


def usable(meta):
    return (meta and meta["url"] and meta["url"].endswith(".svg") and ALLOWED_LICENCES.match(meta["licence"])
            and not meta["restrictions"] & EXCLUDED_RESTRICTIONS)


def choose(names, info):
    """One flag per region: a usable file, preferring the plainest name ("Flag of X" over variants)."""
    candidates = [name for name in names if usable(info.get(name)) and not NOT_ADOPTED.search(name)]
    return min(candidates, key=lambda name: (len(name), name)) if candidates else None


def render(svg, png, webp):
    subprocess.run(["rsvg-convert", "-h", str(HEIGHT), "-o", png, svg], check=True, timeout=120,
                   stdout=subprocess.DEVNULL, stderr=subprocess.PIPE)
    subprocess.run(["magick", png, "-strip", "-quality", str(QUALITY), "-define", "webp:method=6", webp],
                   check=True, timeout=120, stdout=subprocess.DEVNULL, stderr=subprocess.PIPE)


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--raw", required=True, help="directory with admin1CodesASCII.txt (see build_assets.py)")
    parser.add_argument("--work", default="/tmp/travel-diary-region-flags", help="download and render directory")
    parser.add_argument("--out", default=os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..",
                                                                       "app", "src", "main", "assets", "flags", "regions")))
    args = parser.parse_args()
    for tool in ("rsvg-convert", "magick"):
        if not shutil.which(tool):
            sys.exit(f"{tool} not found")

    regions = read_regions(args.raw)
    print(f"{len(regions)} regions")
    flags = query_flags(regions)
    print(f"{len(flags)} regions have a flag on Wikidata")
    info = commons_info(set().union(*flags.values()))
    chosen = {code: name for code, names in flags.items() if (name := choose(names, info))}
    print(f"{len(chosen)} with a usable licence")

    svg_dir = os.path.join(args.work, "svg")
    png_dir = os.path.join(args.work, "png")
    os.makedirs(svg_dir, exist_ok=True)
    os.makedirs(png_dir, exist_ok=True)
    for name in sorted(set(chosen.values())):
        path = os.path.join(svg_dir, name.replace("/", "_"))
        if not os.path.exists(path):
            data = request(info[name]["url"])
            with open(path + ".part", "wb") as out:
                out.write(data)
            os.replace(path + ".part", path)
            time.sleep(1)

    shutil.rmtree(args.out, ignore_errors=True)
    os.makedirs(args.out)
    rendered = {}
    with concurrent.futures.ThreadPoolExecutor(max_workers=os.cpu_count()) as pool:
        jobs = {}
        for code, name in chosen.items():
            base = code.lower()
            svg = os.path.join(svg_dir, name.replace("/", "_"))
            jobs[pool.submit(render, svg, os.path.join(png_dir, base + ".png"), os.path.join(args.out, base + ".webp"))] = code
        for job in concurrent.futures.as_completed(jobs):
            code = jobs[job]
            try:
                job.result()
                rendered[code] = chosen[code]
            except (subprocess.CalledProcessError, subprocess.TimeoutExpired) as error:
                print(f"  {code}: could not render {chosen[code]}: {error}")

    with open(os.path.join(args.out, "credits.tsv"), "w", encoding="utf-8") as out:
        out.write("region\tfile\tauthor\tlicence\tsource\n")
        for code in sorted(rendered):
            meta = info[rendered[code]]
            fields = [code, rendered[code], meta["author"] or "Unknown", meta["licence_name"] or meta["licence"], meta["page"]]
            out.write("\t".join(field.replace("\t", " ") for field in fields) + "\n")
    total = sum(os.path.getsize(os.path.join(args.out, f)) for f in os.listdir(args.out))
    print(f"Wrote {len(rendered)} region flags, {total // 1024} KiB, to {args.out}")


if __name__ == "__main__":
    main()
