#!/usr/bin/env python3
"""Builds map/regions.bin, the shapes of the first-level regions: states, provinces, counties...

The regions are the GeoNames ones (admin1CodesASCII.txt), so every city of the app knows its region.
Their shapes come from Natural Earth's 1:10m admin-1 map (public domain), whose units do not always
match: Natural Earth splits Italy into provinces and France into departments, GeoNames into regions.
So each Natural Earth unit goes to the GeoNames region of most of the cities inside it, and the units
of a region are merged. The regions are then cut to the country shapes of map/world.bin, so they
follow the same coasts and borders as the countries drawn by the app.

A Natural Earth unit that mixes several GeoNames regions (Kenya's old provinces hold many counties)
is left without a region rather than given to one of them.

Needs shapely 2.1 or later (pip install shapely) besides the files of build_assets.py:
  python3 tools/build_regions.py --raw /tmp/travel-diary-raw --out app/src/main/assets --download
"""
import argparse
import collections
import io
import json
import os
import struct
import sys
import zipfile

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import build_assets as base  # noqa: E402

try:
    import shapely
    from shapely.geometry import Point, Polygon, shape
except ImportError:
    sys.exit("This script needs shapely 2.1 or later: pip install shapely")

NE_ADMIN1 = "ne_10m_admin_1_states_provinces.geojson"
base.SOURCES[NE_ADMIN1] = base.NATURAL_EARTH + NE_ADMIN1

# A Natural Earth unit belongs to the GeoNames region of at least this share of the people of its
# cities. Weighting by population keeps villages that the two maps put on different sides of a
# border from outvoting the towns.
MIN_SHARE = 0.6
# Every shape is computed on this grid (degrees, about 10 cm), so neighbours share exact borders.
GRID = 1e-6
# How much the borders between regions are simplified (degrees, about 2 km, like the countries of
# world.bin). The coasts and the borders with other countries are kept as they are in world.bin.
SIMPLIFY_TOLERANCE = 0.02
# Cutting the detailed Natural Earth borders along the coarser coasts leaves extra points on straight
# lines, as many as the coast itself has; points within this distance (degrees, about 1 m) of a
# straight line are dropped.
CLEANUP_TOLERANCE = 1e-5
# Bits of land left between the coarser country shapes and the regions go to the region they touch
# most, or to the nearest one of the country within this distance (degrees).
MAX_GAP_DISTANCE = 0.5


def polygons_only(geometry):
    """The polygons of a geometry, dropping the lines and points that cutting can leave."""
    parts = shapely.get_parts(geometry)
    polygons = [part for part in parts if part.geom_type in ("Polygon", "MultiPolygon")]
    return shapely.union_all(polygons, grid_size=GRID) if polygons else Polygon()


def read_country_shapes(geojson_path, countries):
    shapes = collections.defaultdict(list)
    for code, rings in base.country_polygons(geojson_path, countries):
        shapes[code].append(shapely.make_valid(Polygon(rings[0], rings[1:])))
    return {code: polygons_only(shapely.union_all(parts, grid_size=GRID)) for code, parts in shapes.items()}


def read_city_regions(cities_zip):
    """Position, region code and population of every GeoNames place with a known region."""
    points, codes, populations = [], [], []
    with zipfile.ZipFile(cities_zip) as archive, archive.open("cities500.txt") as raw:
        for line in io.TextIOWrapper(raw, encoding="utf-8"):
            cols = line.rstrip("\n").split("\t")
            if cols[10] and cols[10] != "00":
                points.append(Point(float(cols[5]), float(cols[4])))
                codes.append(f"{cols[8]}.{cols[10]}")
                populations.append(int(cols[14] or 0))
    return points, codes, populations


def assign_units(units, cities, region_codes):
    """Region code of each Natural Earth unit (None for a mix of regions), from the cities inside it."""
    points, codes, populations = cities
    tree = shapely.STRtree(units)
    votes = collections.defaultdict(collections.Counter)
    for point_index, unit_index in zip(*tree.query(points, predicate="within")):
        votes[unit_index][codes[point_index]] += populations[point_index] + 1
    assigned = {}
    for index in range(len(units)):
        counter = votes.get(index)
        if not counter:
            continue  # No city at all: the gap filling below gives it to a neighbour.
        code, count = counter.most_common(1)[0]
        assigned[index] = code if code in region_codes and count / counter.total() >= MIN_SHARE else None
    return assigned


def fill_gaps(regions, country, excluded):
    """Gives the parts of [country] that no region covers, other than [excluded], to a nearby region."""
    covered = shapely.union_all(list(regions.values()) + [excluded], grid_size=GRID)
    gaps = shapely.get_parts(polygons_only(shapely.difference(country, covered, grid_size=GRID)))
    if not len(gaps):
        return regions
    codes = list(regions)
    geometries = [regions[code] for code in codes]
    tree = shapely.STRtree(geometries)
    extra = collections.defaultdict(list)
    for gap in gaps:
        best, best_length = None, 0.0
        for index in tree.query(gap, predicate="intersects"):
            length = shapely.length(shapely.intersection(gap.boundary, geometries[index].boundary, grid_size=GRID))
            if length > best_length:
                best, best_length = index, length
        if best is None:
            nearest = tree.query_nearest(gap, max_distance=MAX_GAP_DISTANCE)
            best = nearest[0] if len(nearest) else None
        if best is not None:
            extra[codes[best]].append(gap)
    return {code: shapely.union_all([geometry] + extra[code], grid_size=GRID) if extra[code] else geometry
            for code, geometry in regions.items()}


def build_regions(paths, countries, region_codes):
    with open(paths[NE_ADMIN1], encoding="utf-8") as f:
        features = json.load(f)["features"]
    units = [shapely.set_precision(shapely.make_valid(shape(feature["geometry"])), GRID) for feature in features]
    assigned = assign_units(units, read_city_regions(paths["cities500.zip"]), region_codes)
    mixed = [units[index] for index, code in assigned.items() if code is None]
    print(f"  {len(units)} Natural Earth units: {sum(1 for c in assigned.values() if c)} matched, "
          f"{len(mixed)} mixing several regions, {len(units) - len(assigned)} without cities")

    by_code = collections.defaultdict(list)
    for index, code in assigned.items():
        if code:
            by_code[code].append(units[index])
    merged = {code: shapely.union_all(parts, grid_size=GRID) for code, parts in by_code.items()}
    mixed_area = shapely.union_all(mixed, grid_size=GRID)

    country_shapes = read_country_shapes(paths["ne_50m_admin_0_countries.geojson"], countries)
    result = {}
    for country_code, country in sorted(country_shapes.items()):
        regions = {}
        for code, geometry in merged.items():
            if code.split(".")[0] == country_code:
                part = polygons_only(shapely.intersection(geometry, country, grid_size=GRID))
                if not part.is_empty:
                    regions[code] = part
        if not regions:
            continue
        excluded = polygons_only(shapely.intersection(mixed_area, country, grid_size=GRID))
        regions = fill_gaps(regions, country, excluded)
        codes_here = sorted(regions)
        simplified = shapely.coverage_simplify([regions[code] for code in codes_here], SIMPLIFY_TOLERANCE,
                                               simplify_boundary=False)
        for code, geometry in zip(codes_here, simplified):
            geometry = polygons_only(shapely.make_valid(shapely.simplify(geometry, CLEANUP_TOLERANCE)))
            if not geometry.is_empty:
                result[code] = geometry
    return result


def rings_of(geometry):
    rings = []
    for polygon in shapely.get_parts(geometry):
        for ring in [polygon.exterior, *polygon.interiors]:
            points = list(ring.coords)[:-1]
            if len(points) >= 3:
                rings.append(points)
    return rings


def write_regions(regions, out_path):
    """Same layout as world.bin, with codes such as "IT.09" written with their length first."""
    total_points = 0
    with open(out_path, "wb") as out:
        out.write(b"TDR1")
        out.write(struct.pack(">i", len(regions)))
        for code in sorted(regions):
            rings = rings_of(regions[code])
            encoded = code.encode("ascii")
            out.write(struct.pack(">B", len(encoded)))
            out.write(encoded)
            out.write(struct.pack(">i", len(rings)))
            for ring in rings:
                out.write(struct.pack(">i", len(ring)))
                out.write(struct.pack(f">{2 * len(ring)}f", *(c for point in ring for c in point)))
                total_points += len(ring)
    print(f"  {len(regions)} region shapes, {total_points} points, {os.path.getsize(out_path) // 1024} KiB")


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--raw", required=True, help="directory for the downloaded source files")
    parser.add_argument("--out", required=True, help="assets directory, e.g. app/src/main/assets")
    parser.add_argument("--download", action="store_true", help="download missing source files")
    args = parser.parse_args()

    os.makedirs(args.raw, exist_ok=True)
    os.makedirs(os.path.join(args.out, "map"), exist_ok=True)
    names = ["ne_50m_admin_0_countries.geojson", NE_ADMIN1, "countryInfo.txt", "admin1CodesASCII.txt", "cities500.zip"]
    paths = {name: base.fetch(args.raw, name, args.download) for name in names}
    countries = base.read_countries(paths["countryInfo.txt"])
    with open(paths["admin1CodesASCII.txt"], encoding="utf-8") as f:
        region_codes = {line.split("\t")[0] for line in f if line.strip()}
    print("Regions")
    regions = build_regions(paths, countries, region_codes)
    write_regions(regions, os.path.join(args.out, "map", "regions.bin"))
    print(f"  regions without a shape: {len(region_codes - regions.keys())} of {len(region_codes)}")


if __name__ == "__main__":
    main()
