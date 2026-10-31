#!/usr/bin/env python3
"""Builds the offline data that ships inside the Travel Diary APK.

Outputs (written to --out, normally app/src/main/assets):
  map/world.bin            country shapes (Natural Earth 1:50m, public domain)
  data/countries.tsv       ISO code, English name, continent, UN member flag (GeoNames, CC BY 4.0)
  data/admin1.tsv          first-level region names, e.g. "US.IL -> Illinois" (GeoNames, CC BY 4.0)
  data/cities.tsv          built-in city list in GeoNames format (GeoNames, CC BY 4.0)

Only the Python standard library is needed:
  python3 tools/build_assets.py --raw /tmp/travel-diary-raw --out app/src/main/assets --download
"""
import argparse
import io
import json
import os
import re
import struct
import sys
import urllib.request
import zipfile

NATURAL_EARTH = "https://raw.githubusercontent.com/nvkelso/natural-earth-vector/v5.1.2/geojson/"
GEONAMES = "https://download.geonames.org/export/dump/"
SOURCES = {
    "ne_50m_admin_0_countries.geojson": NATURAL_EARTH + "ne_50m_admin_0_countries.geojson",
    "countryInfo.txt": GEONAMES + "countryInfo.txt",
    "admin1CodesASCII.txt": GEONAMES + "admin1CodesASCII.txt",
    "cities15000.zip": GEONAMES + "cities15000.zip",
    "cities500.zip": GEONAMES + "cities500.zip",
}

# 193 UN member states plus the two observer states (Vatican City, Palestine).
UN_MEMBERS = set("""
AF AL DZ AD AO AG AR AM AU AT AZ BS BH BD BB BY BE BZ BJ BT BO BA BW BR BN BG BF BI CV KH
CM CA CF TD CL CN CO KM CG CR CI HR CU CY CZ KP CD DK DJ DM DO EC EG SV GQ ER EE SZ ET FJ
FI FR GA GM GE DE GH GR GD GT GN GW GY HT HN HU IS IN ID IR IQ IE IL IT JM JP JO KZ KE KI
KW KG LA LV LB LS LR LY LI LT LU MG MW MY MV ML MT MH MR MU MX FM MC MN ME MA MZ MM NA NR
NP NL NZ NI NE NG MK NO OM PK PW PA PG PY PE PH PL PT QA KR MD RO RU RW KN LC VC WS SM ST
SA SN RS SC SL SG SK SI SB SO ZA SS ES LK SD SR SE CH SY TJ TH TL TG TO TT TN TR TM TV UG
UA AE GB TZ US UY UZ VU VE VN YE ZM ZW
VA PS
""".split())

# Codes GeoNames still lists for countries that no longer exist.
OBSOLETE_CODES = {"CS", "AN"}

NAME_OVERRIDES = {
    "AX": "Åland Islands",
    "BL": "Saint Barthélemy",
    "BQ": "Caribbean Netherlands",
    "CC": "Cocos (Keeling) Islands",
    "CW": "Curaçao",
    "NL": "Netherlands",
    "PN": "Pitcairn Islands",
    "PS": "Palestine",
    "RE": "Réunion",
    "TL": "Timor-Leste",
    "VA": "Vatican City",
}

# Natural Earth features without an ISO code that belong to a GeoNames country.
NE_CODE_FIXES = {"Somaliland": "SO", "N. Cyprus": "CY"}
NE_SKIP = {"Siachen Glacier"}

# Natural Earth draws some territories as part of another country, while GeoNames (and therefore
# the city database) uses ISO 3166 codes and internationally recognised borders. Each rule moves
# the parts of [parent] whose centre falls in the box to [child]:
# (parent, child, lon_min, lon_max, lat_min, lat_max).
SPLITS = [
    ("FR", "GF", -55.0, -51.0, 1.0, 7.0),
    ("FR", "GP", -62.0, -60.9, 15.7, 16.7),
    ("FR", "MQ", -61.3, -60.7, 14.2, 15.0),
    ("FR", "RE", 55.0, 56.0, -22.0, -20.5),
    ("FR", "YT", 44.8, 45.5, -13.3, -12.4),
    ("NL", "BQ", -69.0, -62.5, 11.5, 18.0),
    ("NO", "SJ", -10.0, 40.0, 74.0, 82.0),
    ("NO", "SJ", -10.0, -7.0, 70.5, 71.5),
    ("AU", "CX", 105.0, 106.5, -11.0, -10.0),
    ("AU", "CC", 96.0, 97.5, -12.7, -11.5),
    ("NZ", "TK", -173.0, -171.0, -10.0, -8.0),
    ("RU", "UA", 32.4, 36.7, 44.3, 46.3),  # Crimea
]

# Natural Earth draws Morocco and the Moroccan-administered part of Western Sahara as one ring:
# cut it along the recognised border at 27°40'N. {code: (latitude, code for the part south of it)}
LATITUDE_CUTS = {"MA": (27 + 40 / 60, "EH")}

# Douglas-Peucker tolerance in degrees (about 2 km at the equator).
SIMPLIFY_TOLERANCE = 0.02

# Feature codes for places that no longer exist.
EXCLUDED_FEATURE_CODES = {"PPLH", "PPLQ", "PPLW", "PPLCH"}
# A country with fewer cities than this in cities15000 gets its largest places from cities500.
MIN_CITIES_PER_COUNTRY = 5
# City districts such as "Paris 15 Vaugirard", "Zürich (Kreis 3)" or "Sector 2": the city itself is enough.
# Keep in sync with GeoNamesParser.kt.
NUMBERED_DISTRICT = re.compile(r"^(Paris|Lyon|Marseille) \d|\(Kreis \d+\)")


def is_numbered_district(name, feature_code):
    if not any(char.isdigit() for char in name) or name[:1].isdigit():
        return False
    return feature_code in ("PPLX", "PPLA5") or NUMBERED_DISTRICT.search(name) is not None


def fetch(raw_dir, name, download):
    path = os.path.join(raw_dir, name)
    if not os.path.exists(path):
        if not download:
            sys.exit(f"Missing {path} (run with --download)")
        print(f"Downloading {SOURCES[name]}")
        with urllib.request.urlopen(SOURCES[name]) as response, open(path, "wb") as out:
            out.write(response.read())
    return path


def read_countries(path):
    countries = {}
    with open(path, encoding="utf-8") as f:
        for line in f:
            if line.startswith("#") or not line.strip():
                continue
            cols = line.rstrip("\n").split("\t")
            code, name, continent = cols[0], cols[4].strip(), cols[8]
            if code in OBSOLETE_CODES:
                continue
            countries[code] = (NAME_OVERRIDES.get(code, name), continent)
    missing = UN_MEMBERS - countries.keys()
    if len(UN_MEMBERS) != 195 or missing:
        sys.exit(f"UN member list is wrong: {len(UN_MEMBERS)} codes, missing {missing}")
    return countries


def dp(points, tol):
    """Iterative Douglas-Peucker that keeps the first and last point."""
    keep = [False] * len(points)
    keep[0] = keep[-1] = True
    stack = [(0, len(points) - 1)]
    tol2 = tol * tol
    while stack:
        start, end = stack.pop()
        if end <= start + 1:
            continue
        ax, ay = points[start]
        bx, by = points[end]
        dx, dy = bx - ax, by - ay
        length2 = dx * dx + dy * dy
        best, best_index = -1.0, -1
        for i in range(start + 1, end):
            px, py = points[i]
            t = 0.0 if length2 == 0 else max(0.0, min(1.0, ((px - ax) * dx + (py - ay) * dy) / length2))
            qx, qy = ax + t * dx, ay + t * dy
            d = (px - qx) ** 2 + (py - qy) ** 2
            if d > best:
                best, best_index = d, i
        if best > tol2:
            keep[best_index] = True
            stack.append((start, best_index))
            stack.append((best_index, end))
    return [p for p, k in zip(points, keep) if k]


def simplify_ring(ring, tol):
    points = ring[:-1] if ring[0] == ring[-1] else list(ring)
    if len(points) <= 4:
        return points
    # A closed ring has no natural end points: split it at the vertex farthest from the first one.
    x0, y0 = points[0]
    far = max(range(len(points)), key=lambda i: (points[i][0] - x0) ** 2 + (points[i][1] - y0) ** 2)
    first = dp(points[: far + 1], tol)
    second = dp(points[far:] + [points[0]], tol)
    simplified = first[:-1] + second[:-1]
    # Tiny islands would collapse into a line; keep them as they are.
    return simplified if len(simplified) >= 3 else points


def clip_ring(ring, latitude, keep_north):
    """Sutherland-Hodgman clipping of a ring to the side of a latitude line."""
    points = ring[:-1] if ring[0] == ring[-1] else list(ring)

    def inside(point):
        return point[1] >= latitude if keep_north else point[1] <= latitude

    def crossing(a, b):
        t = (latitude - a[1]) / (b[1] - a[1])
        return [a[0] + t * (b[0] - a[0]), latitude]

    clipped = []
    for i, current in enumerate(points):
        previous = points[i - 1]
        if inside(current):
            if not inside(previous):
                clipped.append(crossing(previous, current))
            clipped.append(current)
        elif inside(previous):
            clipped.append(crossing(previous, current))
    return clipped


def cut_ring(code, ring):
    """Splits a ring of a country listed in LATITUDE_CUTS into its northern and southern parts."""
    if code not in LATITUDE_CUTS:
        return [(code, ring)]
    latitude, south_code = LATITUDE_CUTS[code]
    return [(code, clip_ring(ring, latitude, keep_north=True)), (south_code, clip_ring(ring, latitude, keep_north=False))]


def split_code(code, outer_ring):
    lon = sum(p[0] for p in outer_ring) / len(outer_ring)
    lat = sum(p[1] for p in outer_ring) / len(outer_ring)
    for parent, child, lon_min, lon_max, lat_min, lat_max in SPLITS:
        if code == parent and lon_min <= lon <= lon_max and lat_min <= lat <= lat_max:
            return child
    return code


def country_polygons(geojson_path, countries):
    """Yields (code, rings) for every polygon of the map, simplified, its outer ring first.

    Also used by build_regions.py, which cuts the regions to these exact shapes.
    """
    with open(geojson_path, encoding="utf-8") as f:
        features = json.load(f)["features"]
    for feature in features:
        props = feature["properties"]
        name = props["NAME"]
        if name in NE_SKIP:
            continue
        code = props["ISO_A2_EH"]
        if code == "-99":
            code = NE_CODE_FIXES.get(name)
        if code not in countries:
            print(f"  skipping map feature {name!r} ({code})")
            continue
        geometry = feature["geometry"]
        polygons = geometry["coordinates"] if geometry["type"] == "MultiPolygon" else [geometry["coordinates"]]
        for polygon in polygons:
            part_code = split_code(code, polygon[0])
            # A latitude cut turns one polygon into one part on each side.
            parts = {}
            for ring in polygon:
                for ring_code, part in cut_ring(part_code, ring):
                    if len(part) >= 3:
                        parts.setdefault(ring_code, []).append(simplify_ring(part, SIMPLIFY_TOLERANCE))
            yield from parts.items()


def build_map(geojson_path, countries, out_path):
    shapes = {}  # code -> list of rings
    for code, rings in country_polygons(geojson_path, countries):
        shapes.setdefault(code, []).extend(rings)

    total_points = 0
    with open(out_path, "wb") as out:
        out.write(b"TDM1")
        out.write(struct.pack(">i", len(shapes)))
        for code in sorted(shapes):
            rings = shapes[code]
            out.write(code.encode("ascii"))
            out.write(struct.pack(">i", len(rings)))
            for ring in rings:
                out.write(struct.pack(">i", len(ring)))
                out.write(struct.pack(f">{2 * len(ring)}f", *(c for point in ring for c in point)))
                total_points += len(ring)
    no_shape = sorted(set(countries) - set(shapes))
    print(f"  {len(shapes)} map shapes, {total_points} points, {os.path.getsize(out_path) // 1024} KiB")
    print(f"  countries without a map shape (too small at this scale): {' '.join(no_shape)}")


def write_countries(countries, out_path):
    with open(out_path, "w", encoding="utf-8") as out:
        for code in sorted(countries):
            name, continent = countries[code]
            out.write(f"{code}\t{name}\t{continent}\t{1 if code in UN_MEMBERS else 0}\n")


def write_admin1(path, out_path):
    with open(path, encoding="utf-8") as f, open(out_path, "w", encoding="utf-8") as out:
        for line in f:
            cols = line.rstrip("\n").split("\t")
            if len(cols) >= 2 and cols[1]:
                out.write(f"{cols[0]}\t{cols[1]}\n")


def read_cities(zip_path):
    name = os.path.basename(zip_path).replace(".zip", ".txt")
    with zipfile.ZipFile(zip_path) as archive, archive.open(name) as raw:
        for line in io.TextIOWrapper(raw, encoding="utf-8"):
            cols = line.rstrip("\n").split("\t")
            if len(cols) >= 19 and cols[7] not in EXCLUDED_FEATURE_CODES and not is_numbered_district(cols[1], cols[7]):
                yield cols


def write_cities(cities15000_zip, cities500_zip, countries, out_path):
    chosen = {cols[0]: cols for cols in read_cities(cities15000_zip)}
    per_country = {}
    for cols in chosen.values():
        per_country[cols[8]] = per_country.get(cols[8], 0) + 1

    # Make sure every country has its capital and a handful of places, so tiny countries
    # (Vatican City, San Marino, Liechtenstein, ...) can be added without a download.
    small = {}
    for cols in read_cities(cities500_zip):
        if cols[7] in ("PPLC", "PPLG"):
            chosen.setdefault(cols[0], cols)
        elif per_country.get(cols[8], 0) < MIN_CITIES_PER_COUNTRY:
            small.setdefault(cols[8], []).append(cols)
    for code, candidates in small.items():
        candidates.sort(key=lambda c: int(c[14] or 0), reverse=True)
        for cols in candidates[: MIN_CITIES_PER_COUNTRY - per_country.get(code, 0)]:
            chosen.setdefault(cols[0], cols)

    rows = sorted(chosen.values(), key=lambda c: int(c[0]))
    # Plain text on purpose: the Android asset merger silently gunzips *.gz assets,
    # and the APK compresses the file anyway.
    with open(out_path, "w", encoding="utf-8") as out:
        for cols in rows:
            if cols[8] not in countries:
                continue
            # Same 19-column layout as the GeoNames dumps, with unused columns left empty,
            # so the app parses the built-in list and downloaded lists with the same code.
            kept = [cols[0], cols[1], cols[2], "", cols[4], cols[5], cols[6], cols[7], cols[8],
                    "", cols[10], "", "", "", cols[14], "", "", "", ""]
            out.write("\t".join(kept) + "\n")
    covered = {cols[8] for cols in rows}
    print(f"  {len(rows)} built-in cities, {os.path.getsize(out_path) // 1024} KiB")
    print(f"  countries without any city: {' '.join(sorted(set(countries) - covered))}")


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--raw", required=True, help="directory for the downloaded source files")
    parser.add_argument("--out", required=True, help="assets directory, e.g. app/src/main/assets")
    parser.add_argument("--download", action="store_true", help="download missing source files")
    args = parser.parse_args()

    os.makedirs(args.raw, exist_ok=True)
    os.makedirs(os.path.join(args.out, "map"), exist_ok=True)
    os.makedirs(os.path.join(args.out, "data"), exist_ok=True)
    paths = {name: fetch(args.raw, name, args.download) for name in SOURCES}

    countries = read_countries(paths["countryInfo.txt"])
    print("Countries")
    write_countries(countries, os.path.join(args.out, "data", "countries.tsv"))
    print(f"  {len(countries)} countries and territories")
    print("Map")
    build_map(paths["ne_50m_admin_0_countries.geojson"], countries, os.path.join(args.out, "map", "world.bin"))
    print("Regions")
    write_admin1(paths["admin1CodesASCII.txt"], os.path.join(args.out, "data", "admin1.tsv"))
    print("Cities")
    write_cities(paths["cities15000.zip"], paths["cities500.zip"], countries,
                 os.path.join(args.out, "data", "cities.tsv"))


if __name__ == "__main__":
    main()
